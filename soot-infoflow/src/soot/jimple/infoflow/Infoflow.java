/*******************************************************************************
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package soot.jimple.infoflow;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.*;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowConfiguration.AccessPathConfiguration;
import soot.jimple.infoflow.InfoflowConfiguration.CallgraphAlgorithm;
import soot.jimple.infoflow.InfoflowConfiguration.DataFlowSolver;
import soot.jimple.infoflow.InfoflowConfiguration.SolverConfiguration;
import soot.jimple.infoflow.InfoflowConfiguration.StaticFieldTrackingMode;
import soot.jimple.infoflow.cfg.BiDirICFGFactory;
import soot.jimple.infoflow.codeOptimization.DeadCodeEliminator;
import soot.jimple.infoflow.codeOptimization.ICodeOptimizer;
import soot.jimple.infoflow.data.AccessPathFactory;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.infoflow.handlers.ResultsAvailableHandler;
import soot.jimple.infoflow.memory.FlowDroidMemoryWatcher;
import soot.jimple.infoflow.memory.FlowDroidTimeoutWatcher;
import soot.jimple.infoflow.memory.IMemoryBoundedSolver;
import soot.jimple.infoflow.memory.ISolverTerminationReason;
import soot.jimple.infoflow.memory.reasons.AbortRequestedReason;
import soot.jimple.infoflow.memory.reasons.OutOfMemoryReason;
import soot.jimple.infoflow.memory.reasons.TimeoutReason;
import soot.jimple.infoflow.pattern.alias.PatternAliasStrategy;
import soot.jimple.infoflow.pattern.alias.PatternAliasing;
import soot.jimple.infoflow.pattern.patterndata.*;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPHelper;
import soot.jimple.infoflow.pattern.result.LCMethodResultsAvailableHandler;
import soot.jimple.infoflow.pattern.result.LCMethodSummaryResult;
import soot.jimple.infoflow.pattern.skipmethods.SkipMethodsHelper;
import soot.jimple.infoflow.pattern.solver.*;
import soot.jimple.infoflow.pattern.sourceandsink.IPatternSourceSinkManager;
import soot.jimple.infoflow.pattern.problems.BWPatternAliasInfoflowProblem;
import soot.jimple.infoflow.pattern.problems.FWPatternInfoflowProblem;
import soot.jimple.infoflow.results.InfoflowPerformanceData;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.solver.cfg.BackwardsInfoflowCFG;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.solver.executors.InterruptableExecutor;
import soot.jimple.infoflow.sourcesSinks.manager.IOneSourceAtATimeManager;
import soot.jimple.infoflow.threading.DefaultExecutorFactory;
import soot.jimple.infoflow.threading.IExecutorFactory;
import soot.jimple.infoflow.util.MyOutputer;
import soot.jimple.infoflow.util.SystemClassHandler;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.tagkit.Tag;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

/**
 * main infoflow class which triggers the analysis and offers method to
 * customize it.
 *
 */
public class Infoflow extends AbstractInfoflow {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	protected LCMethodSummaryResult results = null;
	protected PatternInfoflowManager manager;

	protected Set<LCMethodResultsAvailableHandler> onResultsAvailable = new HashSet<>();
	protected IExecutorFactory executorFactory = new DefaultExecutorFactory();

	protected FlowDroidMemoryWatcher memoryWatcher = null;

	protected Set<Stmt> collectedSources = null;
	protected Set<Stmt> collectedSinks = null;

	protected SootMethod dummyMainMethod = null;
	protected Collection<SootMethod> additionalEntryPointMethods = null;

	protected LCResourceOPHelper ophelper = null;
	public void setLCResourceOPHelper(LCResourceOPHelper ophelper) {
		this.ophelper = ophelper;
	}
	public LCResourceOPHelper getLCResourceOPHelper(){
		return this.ophelper;
	}

	/**
	 * Creates a new instance of the InfoFlow class for analyzing plain Java code
	 * without any references to APKs or the Android SDK.
	 */
	public Infoflow() {
		super();
	}

	public Infoflow(String androidPath, boolean forceAndroidJar, BiDirICFGFactory icfgFactory) {
		super(icfgFactory, androidPath, forceAndroidJar);
	}

	/**
	 * Releases the callgraph and all intermediate objects associated with it
	 */
	private void releaseCallgraph() {
		Scene.v().releaseCallGraph();
		Scene.v().releasePointsToAnalysis();
		Scene.v().releaseReachableMethods();
		G.v().resetSpark();
	}

	protected void runAnalysis(final IPatternSourceSinkManager sourcesSinks) {
		runAnalysis(sourcesSinks, null);
	}

	private void runAnalysis(final IPatternSourceSinkManager sourcesSinks, final Set<String> additionalSeeds) {
		final InfoflowPerformanceData performanceData = new InfoflowPerformanceData();
		try {

			// Clear the data from previous runs
			results = new LCMethodSummaryResult();

			// Print and check our configuration
			checkAndFixConfiguration();
			config.printSummary();

			// Register a memory watcher
			if (memoryWatcher != null) {
				memoryWatcher.clearSolvers();
				memoryWatcher = null;
			}
			memoryWatcher = new FlowDroidMemoryWatcher(results, config.getMemoryThreshold());

			// Build the callgraph
			long beforeCallgraph = System.nanoTime();
			constructCallgraph();
			performanceData
					.setCallgraphConstructionSeconds((int) Math.round((System.nanoTime() - beforeCallgraph) / 1E9));
			logger.info(String.format("Callgraph construction took %d seconds",
					performanceData.getCallgraphConstructionSeconds()));

			// Initialize the source sink manager
			if (sourcesSinks != null)
				sourcesSinks.initialize();

			// Perform constant propagation and remove dead code
//				long currentMillis = System.nanoTime();
//				eliminateDeadCode(sourcesSinks);
//				logger.info("Dead code elimination took " + (System.nanoTime() - currentMillis) / 1E9 + " seconds");

			// After constant value propagation, we might find more call edges
			// for reflective method calls
			if (config.getEnableReflection()) {
				releaseCallgraph();
				constructCallgraph();
			}

			if (config.getCallgraphAlgorithm() != CallgraphAlgorithm.OnDemand) {
				logger.info("Callgraph has {} edges", Scene.v().getCallGraph().size());
				MyOutputer.getInstance().updateEdgeNums(config.getIndex(), Scene.v().getCallGraph().size());
			}

			if (!config.isTaintAnalysisEnabled())
				return;

			logger.info("Starting Taint Analysis");
			IInfoflowCFG iCfg = icfgFactory.buildBiDirICFG(config.getCallgraphAlgorithm(),
					config.getEnableExceptionTracking());
			SkipMethodsHelper.v().init(iCfg, ophelper.getNoCheckOpMethods(), PatternDataHelper.v().getEntryFields());

			// Check whether we need to run with one source at a time
			IOneSourceAtATimeManager oneSourceAtATime = sourcesSinks instanceof IOneSourceAtATimeManager ?
														(IOneSourceAtATimeManager) sourcesSinks	: null;
			if (!oneSourceAtATime.isOneSourceAtATimeEnabled()) {
				oneSourceAtATime = null;
			}

//			// Reset the current source
//			if (oneSourceAtATime != null)
//				oneSourceAtATime.resetCurrentSource();
			boolean hasMoreSources = oneSourceAtATime == null || oneSourceAtATime.hasNextSource();

			while (hasMoreSources) {
				// Fetch the next source
				if (oneSourceAtATime != null) {
					oneSourceAtATime.nextSource();
				}

				// Create the executor that takes care of the workers
				int numThreads = Runtime.getRuntime().availableProcessors();
				InterruptableExecutor executor = executorFactory.createExecutor(numThreads, true, config);
				executor.setThreadFactory(new ThreadFactory() {

					@Override
					public Thread newThread(Runnable r) {
						Thread thrIFDS = new Thread(r);
						thrIFDS.setDaemon(true);
						thrIFDS.setName("FlowDroid");
						return thrIFDS;
					}

				});

//				IMemoryManager<Abstraction, Unit> memoryManager = createMemoryManager();

				// Initialize our infrastructure for global taints
				final Set<NormalSolver> solvers = new HashSet<>();
//				GlobalTaintManager globalTaintManager = new GlobalTaintManager(solvers);

				// Initialize the data flow manager
				manager = initializeInfoflowManager(sourcesSinks, iCfg);

				manager.setResult(results);

				// Initialize the alias analysis
				PatternAliasStrategy aliasingStrategy = createAliasAnalysis(sourcesSinks, iCfg, executor);
				NormalSolver backwardSolver = aliasingStrategy.getSolver();
				if (backwardSolver != null) {
					solvers.add(backwardSolver);
				}

				// Initialize the aliasing infrastructure
				PatternAliasing aliasing = new PatternAliasing(aliasingStrategy, manager);
//				if (dummyMainMethod != null)
//					aliasing.excludeMethodFromMustAlias(dummyMainMethod);
				manager.setAliasing(aliasing);

				// Initialize the data flow problem
				PatternInfoflowProblem forwardProblem = new FWPatternInfoflowProblem(manager);

				// We need to create the right data flow solver
				NormalSolver forwardSolver = createPatternSolver(executor, forwardProblem);

				// Set the options
				manager.setForwardSolver(forwardSolver);
				backwardSolver.getProblem().getManager().setForwardSolver(forwardSolver);
				if (aliasingStrategy.getSolver() != null)
					aliasingStrategy.getSolver().getProblem().getManager().setForwardSolver(forwardSolver);
				solvers.add(forwardSolver);

				memoryWatcher.addSolver((IMemoryBoundedSolver) forwardSolver);

//				forwardSolver.setMemoryManager(memoryManager);
				// forwardSolver.setEnableMergePointChecking(true);
//				forwardProblem.setTaintPropagationHandler(taintPropagationHandler);
//				forwardProblem.setTaintWrapper(taintWrapper);
//				if (nativeCallHandler != null)
//					forwardProblem.setNativeCallHandler(nativeCallHandler);

//					aliasingStrategy.getSolver().getProblem().setActivationUnitsToCallSites(forwardProblem);

				// Start a thread for enforcing the timeout
				FlowDroidTimeoutWatcher timeoutWatcher = null;
				FlowDroidTimeoutWatcher pathTimeoutWatcher = null;
				if (config.getDataFlowTimeout() > 0) {
					timeoutWatcher = new FlowDroidTimeoutWatcher(config.getDataFlowTimeout(), results);
					timeoutWatcher.addSolver((IMemoryBoundedSolver) forwardSolver);
					if (aliasingStrategy.getSolver() != null)
						timeoutWatcher.addSolver((IMemoryBoundedSolver) aliasingStrategy.getSolver());
					timeoutWatcher.start();
				}

				InterruptableExecutor resultExecutor = null;
				long beforePathReconstruction = 0;
				try {
					// Print our configuration
					if (config.getFlowSensitiveAliasing()
							&& config.getSolverConfiguration().getMaxJoinPointAbstractions() > 0)
						logger.warn("Running with limited join point abstractions can break context-"
								+ "sensitive path builders");

					// We have to look through the complete program to find
					// sources which are then taken as seeds.
					int sinkCount = 0;
					logger.info("Looking for sources and sinks...");
					for (SootMethod sm : getMethodsForSeeds(iCfg))
						sinkCount += scanMethodForSeed(sourcesSinks, forwardProblem, sm);

					// We optionally also allow additional seeds to be specified
					if (additionalSeeds != null)
						for (String meth : additionalSeeds) {
							SootMethod m = Scene.v().getMethod(meth);
							if (!m.hasActiveBody()) {
								logger.warn("Seed method {} has no active body", m);
								continue;
							}
							forwardProblem.addInitialSeeds(m.getActiveBody().getUnits().getFirst(),
									Collections.singleton(forwardProblem.zeroValue()));
						}
					{
						Set<PatternData> patterns = PatternDataHelper.v().getAllPatterns();
						boolean containsEntryPoints = false;
						Set<SootClass> vlgComponents = new HashSet<>();
						Set<SootField> vlgFields = new HashSet<>();
						System.out.println("all Activities: " + PatternDataHelper.v().allActivities.size() + ", fields: " + PatternDataHelper.v().allActivityFields.size());
						System.out.println("all Fragments: " + PatternDataHelper.v().allFragments.size() + ", fields: " + PatternDataHelper.v().allFragmentFields.size());
						System.out.println("all Components: " + (PatternDataHelper.v().allActivities.size() + PatternDataHelper.v().allFragments.size()) + ", fields: " + (PatternDataHelper.v().allFragmentFields.size() + PatternDataHelper.v().allActivityFields.size()));

						for (PatternData data : patterns) {
							if (null != data) {
								if (data.getReportEntryFields().isEmpty()) {
									MyOutputer.getInstance().updateInvolvedEntries(config.getIndex(), 0);
									MyOutputer.getInstance().updateInvolvedEntryFields(config.getIndex(), new HashMultiMap<>());
									MyOutputer.getInstance().output();
//									logger.error("Pattern 1 has no involvedentrypoints!!!!");
								} else {
									MultiMap<SootClass, SootField> reportEntrypoints = data.getReportEntryFields();
									vlgComponents.addAll(reportEntrypoints.keySet());
									vlgFields.addAll(reportEntrypoints.values());
									System.out.println(data.getClass().getName() + " has " + reportEntrypoints.keySet().size() + " involved components, " + reportEntrypoints.values().size() + " entryfields!!!!!");
//										System.out.println(c.getName());
//										Set<SootField> fs = reportEntrypoints.get(c);
//										System.out.println();
//									MyOutputer.getInstance().updateInvolvedEntries(config.getIndex(), data.getInvolvedEntrypoints().size());
//									MyOutputer.getInstance().updateInvolvedEntryFields(config.getIndex(), data.getEntryFields());
//									MyOutputer.getInstance().output();
									containsEntryPoints = true;
								}

							}
						}
						System.out.println("total vlg-relevant has " + vlgComponents.size() + " involved components, " + vlgFields.size() + " entryfields!!!!!");


						if (!containsEntryPoints) {
							logger.info("no entrypoints found!!!!!!!!!!!!!!!!!!!!!!");
							//						performanceData.addTaintPropagationSeconds(0);
//						performanceData.updateMaxMemoryConsumption(getUsedMemory());
//						performanceData.setTotalRuntimeSeconds((int) Math.round((System.nanoTime() - beforeCallgraph) / 1E9));
//						results.setPerformanceData(performanceData);
//						for (LCMethodResultsAvailableHandler handler : onResultsAvailable)
//							handler.onResultsAvailable(iCfg, results);
							continue;
						}

					}
					// Report on the sources and sinks we have found
					if (!forwardProblem.hasInitialSeeds()) {
						logger.error("No sources found, aborting analysis");
						continue;
					}

					logger.info("entrypoints lookup done, found {} entrypoints.",
							forwardProblem.initialSeeds().size());

					// Update the performance statistics
					performanceData.setSourceCount(forwardProblem.initialSeeds().size());

					// Initialize the taint wrapper if we have one
//					if (taintWrapper != null)
//						taintWrapper.initialize(manager);
//					if (nativeCallHandler != null)
//						nativeCallHandler.initialize(manager);

					// Register the handler for interim results
//					TaintPropagationResults propagationResults = forwardProblem.getResults();
					resultExecutor = executorFactory.createExecutor(numThreads, false, config);
					resultExecutor.setThreadFactory(new ThreadFactory() {

						@Override
						public Thread newThread(Runnable r) {
							Thread thrPath = new Thread(r);
							thrPath.setDaemon(true);
							thrPath.setName("FlowDroid Path Reconstruction");
							return thrPath;
						}
					});

//					// Create the path builder
//					final IAbstractionPathBuilder builder = new BatchPathBuilder(manager,
//							pathBuilderFactory.createPathBuilder(manager, resultExecutor));
////					config.setIncrementalResultReporting(true);
//					// If we want incremental result reporting, we have to
//					// initialize it before we start the taint tracking
//					if (config.getIncrementalResultReporting())
//						initializeIncrementalResultReporting(propagationResults, builder);

					// Initialize the performance data
					if (performanceData.getTaintPropagationSeconds() < 0)
						performanceData.setTaintPropagationSeconds(0);
					long beforeTaintPropagation = System.nanoTime();

					forwardSolver.solve();

					// Not really nice, but sometimes Heros returns before all
					// executor tasks are actually done. This way, we give it a
					// chance to terminate gracefully before moving on.
					int terminateTries = 0;
					while (terminateTries < 10) {
						if (executor.getActiveCount() != 0 || !executor.isTerminated()) {
							terminateTries++;
							try {
								Thread.sleep(500);
							} catch (InterruptedException e) {
								logger.error("Could not wait for executor termination", e);
							}
						} else
							break;
					}
					if (executor.getActiveCount() != 0 || !executor.isTerminated())
						logger.error("Executor did not terminate gracefully");

					// Update performance statistics
					performanceData.updateMaxMemoryConsumption(getUsedMemory());
					int taintPropagationSeconds = (int) Math.round((System.nanoTime() - beforeTaintPropagation) / 1E9);
					performanceData.addTaintPropagationSeconds(taintPropagationSeconds);


					// Give derived classes a chance to do whatever they need before we remove stuff
					// from memory
					onTaintPropagationCompleted();

//					propagationResults.initialDataProcessing();
					// Get the result abstractions
//					Set<AbstractionAtSink> res = propagationResults.getResults();

					// We need to prune access paths that are entailed by
					// another one

					// Shut down the native call handler
					if (nativeCallHandler != null)
						nativeCallHandler.shutdown();

					logger.info("IFDS problem with {} forward and {} backward edges solved...",
							forwardSolver.getPropagationCount(), aliasingStrategy.getSolver().getPropagationCount());

					// Update the statistics
					{
						ISolverTerminationReason reason = ((IMemoryBoundedSolver) forwardSolver).getTerminationReason();
						if (reason != null) {
							if (reason instanceof OutOfMemoryReason)
								results.setTerminationState(
										results.getTerminationState() | InfoflowResults.TERMINATION_DATA_FLOW_OOM);
							else if (reason instanceof TimeoutReason)
								results.setTerminationState(
										results.getTerminationState() | InfoflowResults.TERMINATION_DATA_FLOW_TIMEOUT);
						}
					}

					// Force a cleanup. Everything we need is reachable through
					// the results set, the other abstractions can be killed
					// now.
					performanceData.updateMaxMemoryConsumption(getUsedMemory());
					logger.info(String.format("Current memory consumption: %d MB", getUsedMemory()));

					if (timeoutWatcher != null)
						timeoutWatcher.stop();
					memoryWatcher.removeSolver((IMemoryBoundedSolver) forwardSolver);
//					forwardSolver.cleanup();
					forwardSolver = null;
					forwardProblem = null;

					// Remove the alias analysis from memory
					aliasing = null;
					if (aliasingStrategy.getSolver() != null)
						memoryWatcher.removeSolver((IMemoryBoundedSolver) aliasingStrategy.getSolver());
					aliasingStrategy.cleanup();
					aliasingStrategy = null;

					iCfg.purge();

					// Clean up the manager. Make sure to free objects, even if
					// the manager is still held by other objects
					if (manager != null)
						manager.cleanup();
					manager = null;


					// Report the remaining memory consumption
					Runtime.getRuntime().gc();
					performanceData.updateMaxMemoryConsumption(getUsedMemory());
					logger.info(String.format("Memory consumption after cleanup: %d MB", getUsedMemory()));

					// Apply the timeout to path reconstruction
					if (config.getPathConfiguration().getPathReconstructionTimeout() > 0) {
						pathTimeoutWatcher = new FlowDroidTimeoutWatcher(
								config.getPathConfiguration().getPathReconstructionTimeout(), results);
//						pathTimeoutWatcher.addSolver(builder);
						pathTimeoutWatcher.start();
					}
					beforePathReconstruction = System.nanoTime();

				} finally {
					// Terminate the executor
					if (resultExecutor != null)
						resultExecutor.shutdown();

					// Make sure to stop the watcher thread
					if (timeoutWatcher != null)
						timeoutWatcher.stop();
					if (pathTimeoutWatcher != null)
						pathTimeoutWatcher.stop();

					// Do we have any more sources?
					hasMoreSources = oneSourceAtATime != null && oneSourceAtATime.hasNextSource();

//					TaintPropagationResults.clearLCResults();
					// Shut down the memory watcher
					memoryWatcher.close();

					// Get rid of all the stuff that's still floating around in
					// memory
					forwardProblem = null;
					forwardSolver = null;
					if (manager != null)
						manager.cleanup();
					manager = null;
				}

				// Make sure that we are in a sensible state even if we ran out
				// of memory before
				Runtime.getRuntime().gc();
				performanceData.updateMaxMemoryConsumption(getUsedMemory());
				performanceData.setPathReconstructionSeconds(
						(int) Math.round((System.nanoTime() - beforePathReconstruction) / 1E9));

				logger.info(String.format("Memory consumption after path building: %d MB", getUsedMemory()));
				logger.info(String.format("Path reconstruction took %d seconds",
						performanceData.getPathReconstructionSeconds()));
			}
			DataLogger.getInstance().output();
			if (results == null || results.isEmpty())
				logger.warn("No results found.");
			else if (logger.isInfoEnabled()) {
//							iCfg.getMethodOf(source.getStmt()).getSignature());

//							iCfg.getMethodOf(sink.getStmt()).getSignature());
			}

			// Gather performance data
			performanceData.setTotalRuntimeSeconds((int) Math.round((System.nanoTime() - beforeCallgraph) / 1E9));
			Runtime.getRuntime().gc();
			performanceData.updateMaxMemoryConsumption(getUsedMemory());
			logger.info(String.format("Data flow solver took %d seconds. Maximum memory consumption: %d MB",
					performanceData.getTotalRuntimeSeconds(), performanceData.getMaxMemoryConsumption()));

			// Provide the handler with the final results
			results.setPerformanceData(performanceData);
			for (LCMethodResultsAvailableHandler handler : onResultsAvailable)
				handler.onResultsAvailable(iCfg, results);

			// Write the Jimple files to disk if requested
			if (config.getWriteOutputFiles())
				PackManager.v().writeOutput();
		} catch (Exception ex) {
			StringWriter stacktrace = new StringWriter();
			PrintWriter pw = new PrintWriter(stacktrace);
			ex.printStackTrace(pw);
			results.addException(ex.getClass().getName() + ": " + ex.getMessage() + "\n" + stacktrace.toString());
			logger.error("Exception during data flow analysis", ex);
		}
	}

	/**
	 * Callback that is invoked when the main taint propagation has completed. This
	 * method is called before memory cleanup happens.
	 */
	protected void onTaintPropagationCompleted() {
		//
	}

	/**
	 * Initializes the data flow manager with which propagation rules can interact
	 * with the data flow engine
	 * 
	 * @param sourcesSinks       The source/sink definitions
	 * @param iCfg               The interprocedural control flow graph
	 * @return The data flow manager
	 */
	protected PatternInfoflowManager initializeInfoflowManager(final IPatternSourceSinkManager sourcesSinks, IInfoflowCFG iCfg) {
		return new PatternInfoflowManager(config, null, iCfg, sourcesSinks, this.getLCResourceOPHelper(), hierarchy, new AccessPathFactory(config));
	}

	/**
	 * Checks the configuration of the data flow solver for errors and automatically
	 * fixes some common issues
	 */
	private void checkAndFixConfiguration() {
		final AccessPathConfiguration accessPathConfig = config.getAccessPathConfiguration();
		if (config.getStaticFieldTrackingMode() != StaticFieldTrackingMode.None
				&& accessPathConfig.getAccessPathLength() == 0)
			throw new RuntimeException("Static field tracking must be disabled if the access path length is zero");
		if (config.getSolverConfiguration().getDataFlowSolver() == DataFlowSolver.FlowInsensitive) {
			config.setFlowSensitiveAliasing(false);
			config.setEnableTypeChecking(false);
			logger.warn("Disabled flow-sensitive aliasing because we are running with "
					+ "a flow-insensitive data flow solver");
		}
	}


	/**
	 * Initializes the alias analysis
	 * 
	 * @param sourcesSinks  The set of sources and sinks
	 * @param iCfg          The interprocedural control flow graph
	 * @param executor      The executor in which to run concurrent tasks
	 * @return The alias analysis implementation to use for the data flow analysis
	 */
	private PatternAliasStrategy createAliasAnalysis(final IPatternSourceSinkManager sourcesSinks, IInfoflowCFG iCfg,
			InterruptableExecutor executor) {
		PatternAliasStrategy aliasingStrategy;
		PatternInfoflowManager backwardsManager = new PatternInfoflowManager(config, null, new BackwardsInfoflowCFG(iCfg), sourcesSinks, this.getLCResourceOPHelper(),
				hierarchy, manager.getAccessPathFactory());
		PatternAliasing aliasing = new PatternAliasing(null, backwardsManager);
		backwardsManager.setAliasing(aliasing);
		BWPatternAliasInfoflowProblem backProblem = new BWPatternAliasInfoflowProblem(backwardsManager);

		// We need to create the right data flow solver
		SolverConfiguration solverConfig = config.getSolverConfiguration();
		NormalSolver backSolver = new NormalSolver(backProblem, executor);
//		backSolver.setMemoryManager(memoryManager);
//		backSolver.setPredecessorShorteningMode(
//				pathConfigToShorteningMode(manager.getConfig().getPathConfiguration()));
//		// backSolver.setEnableMergePointChecking(true);
//		backSolver.setMaxJoinPointAbstractions(solverConfig.getMaxJoinPointAbstractions());
//		backSolver.setMaxCalleesPerCallSite(solverConfig.getMaxCalleesPerCallSite());
//		backSolver.setMaxAbstractionPathLength(solverConfig.getMaxAbstractionPathLength());
		backSolver.setSolverId(false);
//		if (nativeCallHandler != null)
//			backProblem.setNativeCallHandler(nativeCallHandler);

		memoryWatcher.addSolver((IMemoryBoundedSolver) backSolver);

		aliasingStrategy = new PatternAliasStrategy(manager, backSolver);
		return aliasingStrategy;
	}

	/**
	 * Creates the memory manager that helps reduce the memory consumption of the
	 * data flow analysis
	 * 
	 * @return The memory manager object
	 */
//		if (memoryManagerFactory == null)
//			return null;
//
//		PathDataErasureMode erasureMode;
//		if (config.getPathConfiguration().mustKeepStatements())
//			erasureMode = PathDataErasureMode.EraseNothing;
//		else if (pathBuilderFactory.supportsPathReconstruction())
//			erasureMode = PathDataErasureMode.EraseNothing;
//		else if (pathBuilderFactory.isContextSensitive())
//			erasureMode = PathDataErasureMode.KeepOnlyContextData;
//		else
//			erasureMode = PathDataErasureMode.EraseAll;
//		IMemoryManager<Abstraction, Unit> memoryManager = memoryManagerFactory.getMemoryManager(false, erasureMode);
//		return memoryManager;

	private NormalSolver createPatternSolver(InterruptableExecutor executor, PatternInfoflowProblem forwardProblem) {
		// Configure the solver
		NormalSolver forwardSolver = new NormalSolver(forwardProblem, executor);
		forwardSolver.setSolverId(true);
//		SolverConfiguration solverConfig = config.getSolverConfiguration();
//		forwardSolver.setPredecessorShorteningMode(pathConfigToShorteningMode(manager.getConfig().getPathConfiguration()));
//		forwardSolver.setMaxJoinPointAbstractions(solverConfig.getMaxJoinPointAbstractions());
//		forwardSolver.setMaxCalleesPerCallSite(solverConfig.getMaxCalleesPerCallSite());
//		forwardSolver.setMaxAbstractionPathLength(solverConfig.getMaxAbstractionPathLength())
		return forwardSolver;
	}

	/**
	 * Gets the memory used by FlowDroid at the moment
	 * 
	 * @return FlowDroid's current memory consumption in megabytes
	 */
	private int getUsedMemory() {
		Runtime runtime = Runtime.getRuntime();
		return (int) Math.round((runtime.totalMemory() - runtime.freeMemory()) / 1E6);
	}

	/**
	 * Runs all code optimizers
	 * 
	 * @param sourcesSinks The SourceSinkManager
	 */
	private void eliminateDeadCode(IPatternSourceSinkManager sourcesSinks) {
		PatternInfoflowManager dceManager = new PatternInfoflowManager(config, null,
				icfgFactory.buildBiDirICFG(config.getCallgraphAlgorithm(), config.getEnableExceptionTracking()), null, null,
				null,  new AccessPathFactory(config));

		// We need to exclude the dummy main method and all other artificial methods
		// that the entry point creator may have generated as well
		Set<SootMethod> excludedMethods = new HashSet<>();
		if (additionalEntryPointMethods != null)
			excludedMethods.addAll(additionalEntryPointMethods);
		excludedMethods.addAll(Scene.v().getEntryPoints());

		ICodeOptimizer dce = new DeadCodeEliminator();
		dce.initialize(config);
		dce.run(dceManager, excludedMethods, sourcesSinks);
	}

	protected Collection<SootMethod> getMethodsForSeeds(IInfoflowCFG icfg) {
		List<SootMethod> seeds = new LinkedList<SootMethod>();
		// If we have a callgraph, we retrieve the reachable methods. Otherwise,
		// we have no choice but take all application methods as an
		// approximation
		if (Scene.v().hasCallGraph()) {
			ReachableMethods reachableMethods = Scene.v().getReachableMethods();
			reachableMethods.update();
			for (Iterator<MethodOrMethodContext> iter = reachableMethods.listener(); iter.hasNext();) {
				SootMethod sm = iter.next().method();
				if (isValidSeedMethod(sm))
					seeds.add(sm);
			}
		} else {
			long beforeSeedMethods = System.nanoTime();
			Set<SootMethod> doneSet = new HashSet<SootMethod>();
			for (SootMethod sm : Scene.v().getEntryPoints())
				getMethodsForSeedsIncremental(sm, doneSet, seeds, icfg);
			logger.info("Collecting seed methods took {} seconds", (System.nanoTime() - beforeSeedMethods) / 1E9);
		}
		return seeds;
	}

	private void getMethodsForSeedsIncremental(SootMethod sm, Set<SootMethod> doneSet, List<SootMethod> seeds,
			IInfoflowCFG icfg) {
		assert Scene.v().hasFastHierarchy();
		if (!sm.isConcrete() || !sm.getDeclaringClass().isApplicationClass() || !doneSet.add(sm))
			return;
		seeds.add(sm);
		for (Unit u : sm.retrieveActiveBody().getUnits()) {
			Stmt stmt = (Stmt) u;
			if (stmt.containsInvokeExpr())
				for (SootMethod callee : icfg.getCalleesOfCallAt(stmt))
					if (isValidSeedMethod(callee))
						getMethodsForSeedsIncremental(callee, doneSet, seeds, icfg);
		}
	}

	/**
	 * Gets whether the given method is a valid seen when scanning for sources and
	 * sinks. A method is a valid seed it it (or one of its transitive callees) can
	 * contain calls to source or sink methods.
	 * 
	 * @param sm The method to check
	 * @return True if this method or one of its transitive callees can contain
	 *         sources or sinks, otherwise false
	 */
	protected boolean isValidSeedMethod(SootMethod sm) {
		if (sm == dummyMainMethod)
			return false;

		// Exclude system classes
		if (config.getIgnoreFlowsInSystemPackages()
				&& SystemClassHandler.isClassInSystemPackage(sm.getDeclaringClass().getName()))
			return false;

		return true;
	}

	/**
	 * Scans the given method for sources and sinks contained in it. Sinks are just
	 * counted, sources are added to the InfoflowProblem as seeds.
	 * 
	 * @param sourcesSinks   The SourceSinkManager to be used for identifying
	 *                       sources and sinks
	 * @param forwardProblem The InfoflowProblem in which to register the sources as
	 *                       seeds
	 * @param m              The method to scan for sources and sinks
	 * @return The number of sinks found in this method
	 */
	private int scanMethodForSeed(final IPatternSourceSinkManager sourcesSinks, PatternInfoflowProblem forwardProblem, SootMethod m) {
		if (getConfig().getLogSourcesAndSinks() && collectedSources == null) {
			collectedSources = new HashSet<>();
			collectedSinks = new HashSet<>();
		}
		Set<SootMethod> patternEntrypoints = PatternDataHelper.v().getEntryMethods();
		MultiMap<SootClass, SootField> patternSourceFields = PatternDataHelper.v().getEntryFields();
		Set<SootField> patternEntryFields = patternSourceFields.values();
		int sinkCount = 0;
		if (m.hasActiveBody()) {
			// Check whether this is a system class we need to ignore
			if (!isValidSeedMethod(m))
				return sinkCount;

			// Look for a source in the method. Also look for sinks. If we
			// have no sink in the program, we don't need to perform any
			// analysis
			PatchingChain<Unit> units = m.getActiveBody().getUnits();
			if (patternEntrypoints.contains(m)) {
				List<Tag> lcmethodtags = PatternDataHelper.v().getDirectLCMetodTag(m);
				NormalState newZeroState = forwardProblem.zeroValue().deriveFinishStmtCopy(manager.getICFG().getEndPointsOf(m), m, lcmethodtags);
				forwardProblem.addInitialSeeds(units.getFirst(), Collections.singleton(newZeroState));
			}

			for (Unit u : units) {
				Stmt s = (Stmt) u;
				if (sourcesSinks.createSourceInfo(s, manager) != null) {
					sourcesSinks.updateStaticFields(s);
					if (getConfig().getLogSourcesAndSinks())
						collectedSources.add(s);
					logger.debug("Source found: {}", u);
				}
			}

		}
		return sinkCount;
	}

	@Override
	public LCMethodSummaryResult getResults() {
		return results;
	}

	@Override
	public boolean isResultAvailable() {
		return results != null;
	}

	@Override
	public void addResultsAvailableHandler(LCMethodResultsAvailableHandler handler) {
		this.onResultsAvailable.add(handler);
	}

	@Override
	public void removeResultsAvailableHandler(ResultsAvailableHandler handler) {
		onResultsAvailable.remove(handler);
	}

	@Override
	public Set<Stmt> getCollectedSources() {
		return this.collectedSources;
	}

	@Override
	public Set<Stmt> getCollectedSinks() {
		return this.collectedSinks;
	}


	@Override
	public void setExecutorFactory(IExecutorFactory executorFactory) {
		this.executorFactory = executorFactory;
	}


	@Override
	public void abortAnalysis() {
		ISolverTerminationReason reason = new AbortRequestedReason();

		if (manager != null) {
			// Stop the forward taint analysis
			if (manager.getForwardSolver() instanceof IMemoryBoundedSolver) {
				IMemoryBoundedSolver boundedSolver = (IMemoryBoundedSolver) manager.getForwardSolver();
				boundedSolver.forceTerminate(reason);
			}
		}

		if (memoryWatcher != null) {
			// Stop all registered solvers
			memoryWatcher.forceTerminate(reason);
		}
	}

}
