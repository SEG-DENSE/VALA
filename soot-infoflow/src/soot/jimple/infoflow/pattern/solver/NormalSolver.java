package soot.jimple.infoflow.pattern.solver;

import com.google.common.cache.CacheBuilder;
import heros.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.infoflow.DataLogger;
import soot.jimple.infoflow.collect.MyConcurrentHashMap;
import soot.jimple.infoflow.memory.IMemoryBoundedSolver;
import soot.jimple.infoflow.memory.ISolverTerminationReason;
import soot.jimple.infoflow.pattern.alias.PatternAliasing;
import soot.jimple.infoflow.pattern.mappingmethods.SootMissingMethodHelper;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOP;
import soot.jimple.infoflow.pattern.problems.BWSolverCallFlowFunction;
import soot.jimple.infoflow.pattern.problems.SolverCallFlowFunction;
import soot.jimple.infoflow.pattern.problems.SolverCallToReturnFlowFunction;
import soot.jimple.infoflow.solver.PredecessorShorteningMode;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.solver.executors.InterruptableExecutor;
import soot.jimple.infoflow.solver.executors.SetPoolExecutor;
import soot.jimple.infoflow.solver.memory.IMemoryManager;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class NormalSolver implements IMemoryBoundedSolver{
    public static CacheBuilder<Object, Object> DEFAULT_CACHE_BUILDER = CacheBuilder.newBuilder()
            .concurrencyLevel(Runtime.getRuntime().availableProcessors()).initialCapacity(10000).softValues();

    protected static final Logger logger = LoggerFactory.getLogger(NormalSolver.class);

    // enable with -Dorg.slf4j.simpleLogger.defaultLogLevel=trace
    public static final boolean DEBUG = logger.isDebugEnabled();

    protected InterruptableExecutor executor;

    @DontSynchronize("only used by single thread")
    protected int numThreads;

    @SynchronizedBy("thread safe data structure, consistent locking when used")
    protected MyConcurrentHashMap<NormalEdge<Unit, NormalState>, NormalState> jumpFunctions = new MyConcurrentHashMap<>();

    @SynchronizedBy("thread safe data structure, only modified internally")
    protected final IInfoflowCFG icfg;

    @DontSynchronize("stateless")
    protected final FlowFunctions<Unit, NormalState, SootMethod> flowFunctions;

    @DontSynchronize("only used by single thread")
    protected final Map<Unit, Set<NormalState>> initialSeeds;

    @DontSynchronize("benign races")
    public long propagationCount;

    @DontSynchronize("stateless")
    protected final NormalState zeroValue;

    @DontSynchronize("readOnly")
    protected final FlowFunctionCache<Unit, NormalState, SootMethod> ffCache;

//    @DontSynchronize("readOnly")
//    protected final boolean followReturnsPastSeeds;

    @DontSynchronize("readOnly")
    protected PredecessorShorteningMode shorteningMode = PredecessorShorteningMode.NeverShorten;

    @DontSynchronize("readOnly")
    private int maxJoinPointAbstractions = -1;

    @DontSynchronize("readOnly")
    protected IMemoryManager<NormalState, Unit> memoryManager = null;

    protected boolean solverId;

    private Set<IMemoryBoundedSolver.IMemoryBoundedSolverStatusNotification> notificationListeners = new HashSet<>();
    private ISolverTerminationReason killFlag = null;

    private int maxCalleesPerCallSite = 75;
    private int maxAbstractionPathLength = 1000;
    protected PatternInfoflowProblem problem = null;

    /**
     * Creates a solver for the given problem, which caches flow functions and edge
     * functions. The solver must then be started by calling {@link #solve()}.
     */
    public NormalSolver(PatternInfoflowProblem tabulationProblem, InterruptableExecutor executor) {
        this(tabulationProblem, DEFAULT_CACHE_BUILDER);
        this.executor = executor;
    }
    public void setSolverId(boolean solverId) {
        this.solverId = solverId;
    }


    /**
     * Creates a solver for the given problem, constructing caches with the given
     * {@link CacheBuilder}. The solver must then be started by calling
     * {@link #solve()}.
     *
     * @param tabulationProblem        The tabulation problem to solve
     * @param flowFunctionCacheBuilder A valid {@link CacheBuilder} or
     *                                 <code>null</code> if no caching is to be used
     *                                 for flow functions.
     */
    public NormalSolver(PatternInfoflowProblem tabulationProblem,
                      @SuppressWarnings("rawtypes") CacheBuilder flowFunctionCacheBuilder) {
        if (logger.isDebugEnabled())
            flowFunctionCacheBuilder = flowFunctionCacheBuilder.recordStats();
        this.problem = tabulationProblem;
        this.zeroValue = tabulationProblem.zeroValue();
        this.icfg = tabulationProblem.interproceduralCFG();
        FlowFunctions<Unit, NormalState, SootMethod> flowFunctions = tabulationProblem.flowFunctions();
        if (flowFunctionCacheBuilder != null) {
            ffCache = new FlowFunctionCache<Unit, NormalState, SootMethod>(flowFunctions, flowFunctionCacheBuilder);
            flowFunctions = ffCache;
        } else {
            ffCache = null;
        }
        this.flowFunctions = flowFunctions;
        this.initialSeeds = tabulationProblem.initialSeeds();
        this.numThreads = Math.max(1, tabulationProblem.numThreads());
        this.executor = getExecutor();
    }

    protected InterruptableExecutor getExecutor() {
        SetPoolExecutor executor = new SetPoolExecutor(1, this.numThreads, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());
        executor.setThreadFactory(new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                Thread thrIFDS = new Thread(r);
                thrIFDS.setDaemon(true);
                thrIFDS.setName("IFDS Solver");
                return thrIFDS;
            }
        });
        return executor;
    }
    public void solve() {
        reset();
        // Notify the listeners that the solver has been started
        for (IMemoryBoundedSolver.IMemoryBoundedSolverStatusNotification listener : notificationListeners)
            listener.notifySolverStarted(this);

        submitInitialSeeds();
        awaitCompletionComputeValuesAndShutdown();

        // Notify the listeners that the solver has been terminated
        for (IMemoryBoundedSolver.IMemoryBoundedSolverStatusNotification listener : notificationListeners)
            listener.notifySolverTerminated(this);
    }

    /**
     * Schedules the processing of initial seeds, initiating the analysis. Clients
     * should only call this methods if performing synchronization on their own.
     * Normally, {@link #solve()} should be called instead.
     */
    protected void submitInitialSeeds() {
        for (Map.Entry<Unit, Set<NormalState>> seed : initialSeeds.entrySet()) {
            Unit startPoint = seed.getKey();
            for (NormalState val : seed.getValue())
                propagate(startPoint, val, null);
            addFunction(new NormalEdge<Unit, NormalState>(startPoint, zeroValue));
        }
    }

    /**
     * Records a jump function. The source statement is implicit.
     *
     * @see NormalEdge
     */
    public NormalState addFunction(NormalEdge<Unit, NormalState> edge) {
        return jumpFunctions.putIfAbsent(edge, edge.factAtTarget());
    }

    /**
     * Awaits the completion of the exploded super graph. When complete, computes
     * result values, shuts down the executor and returns.
     */
    protected void awaitCompletionComputeValuesAndShutdown() {
        {
            // run executor and await termination of tasks
            try {
                executor.awaitCompletion();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Throwable exception = executor.getException();
            if (exception != null) {
                throw new RuntimeException("There were exceptions during IFDS analysis. Exiting.", exception);
            }
        }

        // ask executor to shut down;
        // this will cause new submissions to the executor to be rejected,
        // but at this point all tasks should have completed anyway
        executor.shutdown();

        // Wait for the executor to be really gone
        while (!executor.isTerminated()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // silently ignore the exception, it's not an issue if the
                // thread gets aborted
            }
        }
    }

    public PatternInfoflowProblem getProblem() {return this.problem;}

    @Override
    public void forceTerminate(ISolverTerminationReason reason) {
        this.killFlag = reason;
        this.executor.interrupt();
        this.executor.shutdown();
    }

    @Override
    public boolean isTerminated() {
        return killFlag != null || this.executor.isFinished();
    }

    @Override
    public boolean isKilled() {
        return killFlag != null;
    }

    @Override
    public void reset() {
        this.killFlag = null;
    }

    @Override
    public void addStatusListener(IMemoryBoundedSolverStatusNotification listener) {
        this.notificationListeners.add(listener);
    }

    @Override
    public ISolverTerminationReason getTerminationReason() {
        return killFlag;
    }
    private static Set<Stmt> s = new HashSet<>();

    private class NormalEdgeProcessingTask implements Runnable {

        private final NormalEdge<Unit, NormalState> edge;
        private final boolean solverId;
        private final Unit previousN;

        public NormalEdgeProcessingTask(NormalEdge<Unit, NormalState> edge, boolean solverId, Unit previousN) {
            this.edge = edge;
            this.solverId = solverId;
            this.previousN = previousN;
        }

        public void run() {
            SootMethod m = icfg.getMethodOf(edge.getTarget());
            NormalState ab = (NormalState)edge.factAtTarget();
//                    System.out.println();
            Unit n = edge.getTarget();

//                    System.out.println();
            if (icfg.isCallStmt(edge.getTarget())) {
                processCall(edge);
            } else {
                // note that some statements, such as "throw" may be
                // both an exit statement and a "normal" statement
                if (icfg.isExitStmt(edge.getTarget())) {
                    processExit(edge);
                }
                if (!icfg.getSuccsOf(edge.getTarget()).isEmpty())
                    processNormalFlow(edge);
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((edge == null) ? 0 : edge.hashCode());
            result = prime * result + (solverId ? 1231 : 1237);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            NormalEdgeProcessingTask other = (NormalEdgeProcessingTask) obj;
            if (edge == null) {
                if (other.edge != null)
                    return false;
            } else if (!edge.equals(other.edge))
                return false;
            return solverId == other.solverId;
        }

    }

    public void propagate(Unit target, NormalState targetVal,
            /* deliberately exposed to clients */ Unit previousN) {

        // Check the path length
        if (maxAbstractionPathLength >= 0 && targetVal.getPathLength() > maxAbstractionPathLength)
            return;

        final NormalEdge<Unit, NormalState> edge = new NormalEdge<>(target, targetVal);
        final NormalState existingVal = addFunction(edge);
        if (existingVal != null) {
        } else {
            // If this is an inactive abstraction and we have already processed
            // its active counterpart, we can skip this one
//            NormalState activeVal = targetVal.getActiveCopy();
//                NormalEdge<Unit, NormalState> activeEdge = new NormalEdge<>(target, activeVal);
//                if (jumpFunctions.containsKey(activeEdge))
//                    return;
            scheduleEdgeProcessing(edge, previousN);
        }
    }

    /**
     * Dispatch the processing of a given edge. It may be executed in a different
     * thread.
     *
     * @param edge the edge to process
     */
    protected void scheduleEdgeProcessing(NormalEdge<Unit, NormalState> edge, Unit previousN) {
        // If the executor has been killed, there is little point
        // in submitting new tasks
        if (killFlag != null || executor.isTerminating() || executor.isTerminated())
            return;

        executor.execute(new NormalEdgeProcessingTask(edge, solverId, previousN));
        propagationCount++;
    }
    
    /**
     * Lines 33-37 of the algorithm. Simply propagate normal, intra-procedural
     * flows.
     *
     * @param edge
     */
    private void processNormalFlow(NormalEdge<Unit, NormalState> edge) {
        final Unit n = edge.getTarget();
        final NormalState d2 = edge.factAtTarget();
        List<Unit> nextNs = icfg.getSuccsOf(n);

        for (Unit m : nextNs) {
            // Early termination check
            if (killFlag != null)
                return;

            // Compute the flow function
            FlowFunction<NormalState> flowFunction = flowFunctions.getNormalFlowFunction(n, m);
            Set<NormalState> res = computeNormalFlowFunction(flowFunction, d2);
            if (res != null && !res.isEmpty()) {
                for (NormalState d3 : res) {
                    if (memoryManager != null && d2 != d3)
                        d3 = memoryManager.handleGeneratedMemoryObject(d2, d3);
                    if (d3 != null)
                        propagate(m, d3, n);
                }
            }
        }
    }
    protected Set<NormalState> computeNormalFlowFunction(FlowFunction<NormalState> flowFunction, NormalState d2) {
        return flowFunction.computeTargets(d2);
    }

    private void processCall(NormalEdge<Unit, NormalState> edge) {
        final Unit n = edge.getTarget(); // a call node; line 14...
        final NormalState d2 = edge.factAtTarget();
        assert d2 != null;
        Collection<Unit> returnSiteNs = icfg.getReturnSitesOfCallAt(n);
        // for each possible callee
        Collection<SootMethod> callees = icfg.getCalleesOfCallAt(n);
        if (callees.isEmpty() && ((Stmt)n).containsInvokeExpr()) {
            SootMissingMethodHelper.v().addMissingMethod(((Stmt) n).getInvokeExpr().getMethod());
        }
        if (maxCalleesPerCallSite < 0 || callees.size() <= maxCalleesPerCallSite) {
            for (SootMethod callee : callees) {
                if (callee.isAbstract()) continue;
                if (killFlag != null)
                    return;
                // compute the call-flow function
                FlowFunction<NormalState> function = flowFunctions.getCallFlowFunction(n, callee);
                Collection<Unit> startPointsOf = icfg.getStartPointsOf(callee);
                for (Unit sP : startPointsOf) {
                    Set<NormalState> res = computeCallFlowFunction(function, d2, sP, true);
                    if (res != null && !res.isEmpty()) {
                        for (NormalState d3 : res) {
                            if (d3 == null)
                                continue;
                            propagate(sP, d3, n); // line 15
                        }
                    }
                }

            }


//
//                @Override
//                    // Early termination check
//                    if (killFlag != null)
//                        return;
//                    // compute the call-flow function
//                    FlowFunction<NormalState> function = flowFunctions.getCallFlowFunction(n, sCalledProcN);
//                    Collection<Unit> startPointsOf = icfg.getStartPointsOf(sCalledProcN);
//                        Set<NormalState> res = computeCallFlowFunction(function, d2, sP, true);
////                            if (memoryManager != null)
////                                d3 = memoryManager.handleGeneratedMemoryObject(d2, d3);
//                                if (d3 == null)
//                                    continue;
//                                propagate(sP, d3, n); // line 15
//
//
//
//
        }

        for (Unit returnSiteN : returnSiteNs) {
            FlowFunction<NormalState> callToReturnFlowFunction = flowFunctions.getCallToReturnFlowFunction(n, returnSiteN);
            Set<NormalState> res = computeCallToReturnFlowFunction(callToReturnFlowFunction, d2, true);
            if (res != null && !res.isEmpty()) {
                for (NormalState d3 : res) {
//                    if (memoryManager != null)
//                        d3 = memoryManager.handleGeneratedMemoryObject(d2, d3);
                    if (d3 != null)
                        propagate(returnSiteN, d3, n);
                }
            }
        }
    }
    protected Set<NormalState> computeCallFlowFunction(FlowFunction<NormalState> callFlowFunction, NormalState d2, Unit nextStmt, boolean isRelevant) {
        if (callFlowFunction instanceof BWSolverCallFlowFunction) {
            return ((BWSolverCallFlowFunction) callFlowFunction).computeCallTargets(d2, nextStmt, isRelevant);
        } else if (callFlowFunction instanceof SolverCallFlowFunction) {
            return ((SolverCallFlowFunction) callFlowFunction).computeCallTargets(d2, isRelevant);
        } else {
            return callFlowFunction.computeTargets(d2);
        }
    }
    protected Set<NormalState> computeCallToReturnFlowFunction(FlowFunction<NormalState> callToReturnFlowFunction, NormalState d2, boolean isRelevant) {
        if (callToReturnFlowFunction instanceof SolverCallToReturnFlowFunction) {
            return ((SolverCallToReturnFlowFunction)callToReturnFlowFunction).computeCallTargets(d2, isRelevant);
        } else {
            return callToReturnFlowFunction.computeTargets(d2);
        }
    }

    /**
     * Lines 21-32 of the algorithm.
     *
     * Stores callee-side summaries. Also, at the side of the caller, propagates
     * intra-procedural flows to return sites using those newly computed summaries.
     *
     * @param edge an edge whose target node resembles a method exits
     */
    protected void processExit(NormalEdge<Unit, NormalState> edge) {
        final Unit n = edge.getTarget(); // an exit node; line 21...
        SootMethod methodThatNeedsSummary = icfg.getMethodOf(n);
        final NormalState d2 = edge.factAtTarget();

        Collection<Unit> callers = icfg.getCallersOf(methodThatNeedsSummary);
        for (Unit c : callers) {
            for (Unit retSiteC : icfg.getReturnSitesOfCallAt(c)) {
                FlowFunction<NormalState> retFunction = flowFunctions.getReturnFlowFunction(c, methodThatNeedsSummary, n,
                        retSiteC);
                Set<NormalState> targets = computeReturnFlowFunction(retFunction, d2, c);
                if (targets != null && !targets.isEmpty()) {
                    for (NormalState d5 : targets) {
                        if (d5 != null)
                            propagate(retSiteC, d5, n);
                    }
                }
            }
        }
        if (callers.isEmpty()) {
            FlowFunction<NormalState> retFunction = flowFunctions.getReturnFlowFunction(null, methodThatNeedsSummary, n,
                    null);
            retFunction.computeTargets(d2);
        }

    }
    protected Set<NormalState> computeReturnFlowFunction(FlowFunction<NormalState> retFunction, NormalState d2, Unit callSite) {
        return retFunction.computeTargets(d2);
    }

    public long getPropagationCount() {
        return propagationCount;
    }
}
