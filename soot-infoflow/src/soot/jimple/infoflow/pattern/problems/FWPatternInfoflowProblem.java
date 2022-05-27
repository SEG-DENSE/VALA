package soot.jimple.infoflow.pattern.problems;

import heros.FlowFunction;
import heros.FlowFunctions;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.infoflow.cfg.FlowDroidEssentialMethodTag;
import soot.jimple.infoflow.pattern.mappingmethods.MappingMethodHelper;
import soot.jimple.infoflow.pattern.mappingmethods.SootMissingMethodHelper;
import soot.jimple.infoflow.pattern.patterndata.PatternDataHelper;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPHelper;
import soot.jimple.infoflow.pattern.result.LCMethodSummaryResult;
import soot.jimple.infoflow.pattern.rules.PatternPropagationRuleManager;

import soot.jimple.infoflow.pattern.solver.NormalState;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowManager;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowProblem;
import soot.jimple.infoflow.util.ByReferenceBoolean;
import soot.jimple.infoflow.util.SystemClassHandler;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FWPatternInfoflowProblem extends PatternInfoflowProblem {
    private final PatternPropagationRuleManager propagationRules;
    private final LCMethodSummaryResult result;




    public FWPatternInfoflowProblem(PatternInfoflowManager manager) {
        super(manager);
        result = new LCMethodSummaryResult();

        propagationRules = new PatternPropagationRuleManager(manager, result);


    }

    private FlowFunctions<Unit, NormalState,SootMethod> flowFunctions;
    @Override
    public final FlowFunctions<Unit,NormalState,SootMethod> flowFunctions() {
        if(flowFunctions==null) {
            flowFunctions = createFlowFunctionsFactory();
        }
        return flowFunctions;
    }
    public FlowFunctions<Unit, NormalState, SootMethod> createFlowFunctionsFactory() {
        return new FlowFunctions<Unit, NormalState, SootMethod>() {

            @Override
            public FlowFunction<NormalState> getNormalFlowFunction(Unit curr, Unit succ) {
                return source -> {
                    NormalState newState;
                    ByReferenceBoolean hasGeneratedNewState = new ByReferenceBoolean();
                    newState = source.isActive()?source:source.FWCheckCurrentStmt((Stmt)curr, hasGeneratedNewState);

                    // Apply the propagation rules
                    ByReferenceBoolean killAll = new ByReferenceBoolean();
                    newState = propagationRules.applyNormalFlowFunction(newState, (Stmt)curr,
                            (Stmt) succ, hasGeneratedNewState, killAll);
                    if (killAll.value) {
                        if (source.isZeroState()) {
                            return Collections.singleton(source.getZeroState());
                        } else {
                            return Collections.emptySet();
                        }
                    }

                    Set<NormalState> newSources = new HashSet<>();
                    newState.updateActiveAps();
                    newSources.add(newState);
                    if (source.isZeroState() && !newState.isZeroState()) {
                        newSources.add(newState.getZeroState());
                    }
                    return newSources;
                };
            }

            @Override
            public FlowFunction<NormalState> getCallFlowFunction(Unit callStmt, SootMethod dest) {
                return new SolverCallFlowFunction() {
                    @Override
                    public Set<NormalState> computeCallTargets(NormalState source, boolean isRelevant) {
                        Stmt stmt = (Stmt)callStmt;
                        InvokeExpr invokeExpr = stmt.containsInvokeExpr()? stmt.getInvokeExpr() : null;
                        if (isExcluded(invokeExpr, dest, source))
                            return null;
                        Set<NormalState> newStates = new HashSet<>();
                        ByReferenceBoolean hasGeneratedNewState = new ByReferenceBoolean();
                        // We might need to activate the abstraction
                        NormalState newState = source.deriveCallState(stmt, hasGeneratedNewState);
                        if (newState.isZeroState()) {
                            newStates.add(newState);
                        }
                         newState = newState.isActive()?newState:newState.FWCheckCurrentStmt(stmt, hasGeneratedNewState);

                        ByReferenceBoolean killAll = new ByReferenceBoolean();
                        newState = propagationRules.applyCallFlowFunction(newState, stmt, dest, hasGeneratedNewState, killAll);
                        if (killAll.value) {
                            return newStates;
                        }

                        if (null != newState) {
                            newState.updateActiveAps();
                            newStates.add(newState);
                        }
                        return newStates;
                    }

                };
            }

            @Override
            public FlowFunction<NormalState> getReturnFlowFunction(Unit callSite, SootMethod calleeMethod, Unit exitStmt, Unit returnSite) {
                return source -> {
//                    if (source.isZeroState() && !source.shouldFinish(exitStmt))
//                        return Collections.singleton(source);

                    ByReferenceBoolean hasGeneratedNewState = new ByReferenceBoolean();
                    NormalState newState = source;
                    Set<NormalState> newStates = new HashSet<>();
                    if (callSite != null && !newState.shouldFinish(exitStmt)) {
                        newState = newState.deriveReturnState((Stmt)callSite, hasGeneratedNewState);
                        if (null == newState) {return null;}
                        if (newState.isZeroState()) {newStates.add(newState);}
                        newState = newState.isActive()?newState:newState.FWCheckCurrentStmt((Stmt)exitStmt, hasGeneratedNewState);
                    }


                    ByReferenceBoolean killAll = new ByReferenceBoolean();
                    newState = propagationRules.applyReturnFlowFunction(newState,
                            (Stmt) exitStmt, (Stmt) returnSite, (Stmt) callSite, calleeMethod, hasGeneratedNewState, killAll);
                    if (killAll.value)
                        return newStates;
                    // If we have no caller, we have nowhere to propagate.
                    // This can happen when leaving the main method.
                    if (callSite == null)
                        return null;
//                        System.out.println();
                    newState.updateActiveAps();
                    newStates.add(newState);
                    return newStates;
                };
            }

            @Override
            public FlowFunction<NormalState> getCallToReturnFlowFunction(Unit callSite, Unit returnSite) {
                return new SolverCallToReturnFlowFunction() {
                    @Override
                    public Set<NormalState> computeCallTargets(NormalState source, boolean isRelevant) {
                        if (!((Stmt)callSite).containsInvokeExpr()) {
                            return null;
                        }
                        InvokeExpr invExpr = ((Stmt) callSite).getInvokeExpr();
                        final SootMethod callee = invExpr.getMethod();
                        if (!isExcluded(invExpr, callee, source))
                            return null;

                        ByReferenceBoolean hasGeneratedNewState = new ByReferenceBoolean();
                        // check inactive elements:
                        NormalState newState = source.isActive()?source:source.FWCheckCurrentStmt((Stmt)callSite, hasGeneratedNewState);

                        ByReferenceBoolean killAll = new ByReferenceBoolean();
                        newState = propagationRules.applyCallToReturnFlowFunction(newState, (Stmt)callSite, callee,
                                hasGeneratedNewState, killAll);
                        if (killAll.value)
                            return null;
                        newState.updateActiveAps();
                        return Collections.singleton(newState);
                    }
                };
            }
        };
    }

}
