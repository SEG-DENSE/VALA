package soot.jimple.infoflow.pattern.problems;

import heros.FlowFunction;
import heros.FlowFunctions;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.cfg.FlowDroidEssentialMethodTag;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.pattern.alias.PatternAliasing;
import soot.jimple.infoflow.pattern.exceptions.TypeNotMatchException;
import soot.jimple.infoflow.pattern.mappingmethods.MappingMethodHelper;
import soot.jimple.infoflow.pattern.mappingmethods.SootMissingMethodHelper;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPHelper;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPList;
import soot.jimple.infoflow.pattern.solver.NormalState;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowManager;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowProblem;
import soot.jimple.infoflow.util.ByReferenceBoolean;
import soot.jimple.infoflow.util.SystemClassHandler;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BWPatternAliasInfoflowProblem extends PatternInfoflowProblem {
    public BWPatternAliasInfoflowProblem(PatternInfoflowManager manager) {
        super(manager);
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
                    ByReferenceBoolean hasGeneratedNewState = new ByReferenceBoolean();
                    NormalState newState = source.BWCheckCurrentStmt((Stmt)curr, hasGeneratedNewState);
                    if (curr instanceof AssignStmt) {
                        PatternAliasing aliasing = manager.getAliasing();
                        AssignStmt assign = (AssignStmt) curr;
                        AccessPath aliasAp = newState.getAliasAp();
                        Stmt activationStmt = newState.getActivationStmt();
                        AccessPath newAliasAp = aliasAp;
                        MultiMap<Stmt, AccessPath> inactiveApMap = newState.getInActivationMap();

                        Value leftVal = assign.getLeftOp();
                        Value rightVal = assign.getRightOp();
                        Type newtype = null;
                        if (rightVal instanceof CastExpr) {
                            newtype = ((CastExpr) rightVal).getCastType();
                            rightVal = ((CastExpr) rightVal).getOp();
                        }
                        MultiMap<Stmt, AccessPath> newInActiveApMap = null;
                        if (activationStmt == null) {
                            boolean shouldCutFirstField = false;
                            if (PatternAliasing.baseMatches(leftVal, aliasAp)) {
                                if (null == newInActiveApMap) {
                                    newInActiveApMap = new HashMultiMap<>(inactiveApMap);
                                }
                                shouldCutFirstField = aliasAp.isFieldRef();
                                newInActiveApMap.put(assign, aliasAp);
                                if (!(rightVal instanceof Constant) && !(rightVal instanceof AnyNewExpr)) {

                                    Type nowtype = newtype == null ? (shouldCutFirstField ? aliasAp.getFirstFieldType() : aliasAp.getBaseType()) : newtype;
                                    newAliasAp = manager.getAccessPathFactory().copyWithNewValue(aliasAp, rightVal, nowtype, shouldCutFirstField);
                                } else {
                                    newAliasAp = null;
                                }
                            }
                        } else {
                            if (aliasAp.isFieldRef()) {
                                if (rightVal instanceof Local && aliasing.mayAlias(aliasAp.getPlainValue(), rightVal)) {
                                    if (null == newInActiveApMap) {newInActiveApMap = new HashMultiMap<>(inactiveApMap);}
                                    AccessPath newInactiveAp = manager.getAccessPathFactory().copyWithNewValue(aliasAp, leftVal);
                                    if (null != newInactiveAp) {
                                        newInActiveApMap.put(activationStmt, newInactiveAp);
                                    }
                                }
                                if (leftVal instanceof Local && !(rightVal instanceof AnyNewExpr) && aliasing.mayAlias(aliasAp.getPlainValue(), leftVal)) {
                                    if (null == newInActiveApMap) {newInActiveApMap = new HashMultiMap<>(inactiveApMap);}
                                    newAliasAp = manager.getAccessPathFactory().copyWithNewValue(aliasAp, rightVal);
                                    if (null != newAliasAp) {
                                        newInActiveApMap.put(activationStmt, newAliasAp);
                                    }
                                }
                            }
                        }
                        if ( newAliasAp != aliasAp || newInActiveApMap != null) {
                            newState = newState.deriveNewAliasState(newAliasAp, newInActiveApMap, hasGeneratedNewState, assign);
                        }
                        if (newAliasAp == null) {
                            NormalState fwstate = newState.deriveNormalFromAliasState(assign, hasGeneratedNewState);
                            if (null != fwstate)
                                manager.getForwardSolver().propagate(assign, fwstate, null);
                            return null;
                        }
                    }
                    return Collections.singleton(newState);
                };
            }

            protected boolean mapCallee(PatternAliasing aliasing, Stmt stmt, Value leftVal, Value rightVal, AccessPathWrapper aliasApWrapper, Stmt activationStmt, MultiMap<Stmt, AccessPath> newInactiveApMaps)  {
                boolean haschanged = false;
                AccessPath aliasAp = aliasApWrapper.ap;
                if (null == aliasAp){return false;}
                if (null != activationStmt && aliasAp.isFieldRef() && aliasing.mayAlias(aliasAp.getPlainValue(), leftVal)) {
                    AccessPath newAp = manager.getAccessPathFactory().copyWithNewValue(aliasAp, rightVal);
                    aliasApWrapper.ap = newAp;
                    if (null != newAp) {
                        newInactiveApMaps.put(activationStmt, newAp);
                    }

                    haschanged = true;
                } else if (PatternAliasing.baseMatches(leftVal, aliasAp) && null == activationStmt) {
                    AccessPath newAp = manager.getAccessPathFactory().copyWithNewValue(aliasAp, rightVal);
                    aliasApWrapper.ap = newAp;
                    haschanged = true;
                }
                return haschanged;
            }

                 class AccessPathWrapper{
                public AccessPath ap = null;
                public AccessPathWrapper(AccessPath ap) {
                    this.ap = ap;
                }
            }

            @Override
            public FlowFunction<NormalState> getCallFlowFunction(Unit callStmt, SootMethod callee) {

                return new BWSolverCallFlowFunction() {
                    @Override
                    public Set<NormalState> computeCallTargets(NormalState source, Unit returnstmt, boolean isRelevant) {
                        final Stmt stmt = (Stmt) callStmt;
                        final InvokeExpr ie = (stmt != null && stmt.containsInvokeExpr()) ? stmt.getInvokeExpr() : null;

                        if (isExcluded(ie, callee, source))
                            return null;

                        final Value[] paramLocals = new Value[callee.getParameterCount()];
                        for (int i = 0; i < callee.getParameterCount(); i++)
                            paramLocals[i] = callee.getActiveBody().getParameterLocal(i);
                        final Local thisLocal = callee.isStatic() ? null : callee.getActiveBody().getThisLocal();
                        ByReferenceBoolean hasGeneratedNewState = new ByReferenceBoolean();
                        NormalState newState = source.deriveCallState((Stmt)callStmt, hasGeneratedNewState);
                        PatternAliasing aliasing = getManager().getAliasing();
                        newState = newState.BWCheckCurrentStmt((Stmt)callStmt, hasGeneratedNewState);
                        AccessPath aliasAp = newState.getAliasAp();
                        MultiMap<Stmt, AccessPath> inactiveApMap = newState.getInActivationMap();
                        MultiMap<Stmt, AccessPath> newInactiveApMap = new HashMultiMap<>(inactiveApMap);
                        AccessPathWrapper aliasApWrapper = new AccessPathWrapper(aliasAp);

                        boolean haschanged = false;
                        // if the returned value is tainted - taint values from
                        // return statements
                        if (callStmt instanceof DefinitionStmt && returnstmt instanceof ReturnStmt) {

                            DefinitionStmt defnStmt = (DefinitionStmt) callStmt;
                            Value leftOp = defnStmt.getLeftOp();
                            ReturnStmt rStmt = (ReturnStmt) returnstmt;
                            Value rightOp = rStmt.getOp();
                            if (rightOp instanceof Constant) {
                                Stmt activationStmt = newState.getActivationStmt();
                                if (null != activationStmt && aliasAp.isFieldRef() && aliasing.mayAlias(aliasAp.getPlainValue(), leftOp)) {
                                    haschanged = true;
                                } else if (PatternAliasing.baseMatches(leftOp, aliasAp) && null == activationStmt) {
                                    newInactiveApMap.put(rStmt, aliasAp);
                                    aliasApWrapper.ap = null;
                                    haschanged = true;
                                }
                                if (haschanged) {
                                    newState = newState.deriveNewAliasState(aliasApWrapper.ap, newInactiveApMap, hasGeneratedNewState, (Stmt)callStmt);
                                    NormalState fwstate = newState.deriveNormalFromAliasState((Stmt)callStmt, hasGeneratedNewState);
                                    if (null != fwstate)
                                        manager.getForwardSolver().propagate(returnstmt, fwstate, null);
                                    return null;
                                }
                            } else {
                                haschanged |= mapCallee(aliasing, (Stmt)returnstmt, leftOp, rStmt.getOp(), aliasApWrapper, source.getActivationStmt(), newInactiveApMap);
                            }
                        }

                        Expr invokeExpr = stmt.getInvokeExpr();
                        // checks: this/fields
                        if (!callee.isStatic() && invokeExpr instanceof InstanceInvokeExpr) {
                            InstanceInvokeExpr iIExpr = (InstanceInvokeExpr) invokeExpr;
                            Value callBase = iIExpr.getBase();
                            haschanged |= mapCallee(aliasing, (Stmt)returnstmt, callBase, thisLocal, aliasApWrapper, newState.getActivationStmt(), newInactiveApMap);
                        }

                        if (ie != null && callee.getParameterCount() > 0) {
                            assert callee.getParameterCount() == ie.getArgCount();
                            // check if param is tainted:
                            for (int i = 0; i < ie.getArgCount(); i++) {
                                Value leftVal = ie.getArg(i);
                                Value rightVal = paramLocals[i];
                                haschanged |= mapCallee(aliasing, (Stmt)returnstmt, leftVal, rightVal, aliasApWrapper, newState.getActivationStmt(), newInactiveApMap);
                            }
                        }

                        if (aliasApWrapper.ap == null) {
                            return null;
                        }
                        if (haschanged) {
                            newState = newState.deriveNewAliasState(aliasApWrapper.ap, newInactiveApMap, hasGeneratedNewState, (Stmt)callStmt);
                        }

                        return Collections.singleton(newState);
                    }
                };
            }

            @Override
            public FlowFunction<NormalState> getReturnFlowFunction(Unit callSite, SootMethod callee, final Unit exitStmt, Unit returnSite) {
                final Value[] paramLocals = new Value[callee.getParameterCount()];
                for (int i = 0; i < callee.getParameterCount(); i++)
                    paramLocals[i] = callee.getActiveBody().getParameterLocal(i);
                final Stmt stmt = (Stmt) callSite;
                final InvokeExpr ie = (stmt != null && stmt.containsInvokeExpr()) ? stmt.getInvokeExpr() : null;
//				final boolean isReflectiveCallSite = interproceduralCFG().isReflectiveCallSite(ie);
//				// This is not cached by Soot, so accesses are more expensive
//				// than one might think
                final Local thisLocal = callee.isStatic() ? null : callee.getActiveBody().getThisLocal();
                return source -> {
//						// If we have no caller, we have nowhere to propagate.
//						// This can happen when leaving the main method.
                    ByReferenceBoolean hasGeneratedNewState = new ByReferenceBoolean();
                    if (null != ie && ie.getMethod() == source.getEntryMethod()) {
                        NormalState fwstate = source.deriveNormalFromAliasState(stmt, hasGeneratedNewState);
                        if (null != fwstate)
                            manager.getForwardSolver().propagate(exitStmt, fwstate, null);
                        return null;
                    }
                    NormalState newState = source.deriveReturnState((Stmt)callSite, hasGeneratedNewState);
                    if (null == newState) {return null;}
                    newState = newState.BWCheckCurrentStmt((Stmt)callSite, hasGeneratedNewState);
                    PatternAliasing aliasing = getManager().getAliasing();
                    AccessPath aliasAp = newState.getAliasAp();
                    AccessPathWrapper aliasApWrapper = new AccessPathWrapper(aliasAp);
                    MultiMap<Stmt, AccessPath> inactiveApMap = newState.getInActivationMap();
                    MultiMap<Stmt, AccessPath> newInactiveApMap = new HashMultiMap<>(inactiveApMap);

                    boolean haschanged = false;

                    // Map the "this" local
                    if (!callee.isStatic() && callSite instanceof Stmt) {
                        Value leftVal = thisLocal;
                        Stmt callstmt = (Stmt) callSite;
                        if (callstmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
                            InstanceInvokeExpr iIExpr = (InstanceInvokeExpr) callstmt.getInvokeExpr();
                            Value callerBaseLocal = iIExpr.getBase();
                            haschanged |= mapCallee(aliasing, (Stmt)callSite, leftVal, callerBaseLocal, aliasApWrapper, newState.getActivationStmt(), newInactiveApMap);
                        }
                    }
                    for (int i = 0; i < paramLocals.length; i++) {
                        if (callSite instanceof Stmt) {
                            Value originalCallArg = ie.getArg(i);
                            // If this is a constant parameter, we
                            // can safely ignore it
                            if (!AccessPath.canContainValue(originalCallArg))
                                continue;

                            // If the variable was overwritten
                            // somewehere in the callee, we assume
                            // it to overwritten on all paths (yeah,
                            // I know ...) Otherwise, we need SSA
                            // or lots of bookkeeping to avoid FPs
                            // (BytecodeTests.flowSensitivityTest1).
                            if (interproceduralCFG().methodWritesValue(callee, paramLocals[i]))
                                continue;
                            haschanged |= mapCallee(aliasing, (Stmt)callSite, paramLocals[i], originalCallArg, aliasApWrapper, newState.getActivationStmt(), newInactiveApMap);
                        }
                    }
                    if (aliasApWrapper.ap == null) {
                        return null;
                    }

                    if (haschanged) {
                        newState = newState.deriveNewAliasState(aliasApWrapper.ap, newInactiveApMap, hasGeneratedNewState, (Stmt)callSite);
                    } else {
                        Stmt activationStmt = newState.getActivationStmt();
                        if (null != activationStmt && icfg.getMethodOf(activationStmt) == callee) {
                            NormalState fwstate = newState.deriveNormalFromAliasState((Stmt)exitStmt, hasGeneratedNewState);
                            if (null != fwstate)
                                manager.getForwardSolver().propagate((Stmt)exitStmt, fwstate, null);
                            return null;
                        }
                    }
                    return Collections.singleton(newState);
                };
            }

            @Override
            public FlowFunction<NormalState> getCallToReturnFlowFunction(Unit callSite, Unit returnSite) {
                return source -> {
                    if (!((Stmt)callSite).containsInvokeExpr()) {
                        return null;
                    }
                    InvokeExpr invExpr = ((Stmt) callSite).getInvokeExpr();
                    final SootMethod callee = invExpr.getMethod();
                    if (!isExcluded(invExpr, callee, source))
                        return null;
                    ByReferenceBoolean hasGeneratedNewState = new ByReferenceBoolean();
                    NormalState newState = source.BWCheckCurrentStmt((Stmt)callSite, hasGeneratedNewState);
                    if (callSite instanceof DefinitionStmt) {
                        Value leftVal = ((DefinitionStmt) callSite).getLeftOp();
                        AccessPath aliasAp = newState.getAliasAp();
                        if (PatternAliasing.baseMatches(leftVal, aliasAp)) {
                            Stmt activationStmt = newState.getActivationStmt();
                            if (null == activationStmt) {
                                MultiMap<Stmt, AccessPath> newInactiveMaps = new HashMultiMap<>(newState.getInActivationMap());
                                newInactiveMaps.put((Stmt)callSite, aliasAp);
                                newState = newState.deriveNewAliasState(null, newInactiveMaps, hasGeneratedNewState, (Stmt)callSite);
                            }
                            NormalState fwState = newState.deriveNormalFromAliasState((Stmt)callSite, hasGeneratedNewState);
                            if (null != fwState) {
                                manager.getForwardSolver().propagate(callSite, fwState, null);
                            }
                            return null;
                        }
                    }

                    return Collections.singleton(newState);
                };
            }
        };
    }


    private void originalUpdateActiveAndInactiveAps(NormalState newState, Stmt curr) {
        PatternAliasing aliasing = manager.getAliasing();
        AssignStmt assign = (AssignStmt)curr;
        Set<AccessPath> activeAps = newState.getAps();
        Set<AccessPath> inactiveAps = newState.getInactiveAps();
        Set<AccessPath> allAps = new HashSet<>();
        allAps.addAll(activeAps);
        allAps.addAll(inactiveAps);

        Value leftVal = assign.getLeftOp();
        Value rightVal = assign.getRightOp();
        Type newtype = null;
        if (rightVal instanceof  CastExpr) {
            rightVal = ((CastExpr)rightVal).getOp();
            newtype = rightVal.getType();
        }
        Set<AccessPath> newActiveAps = new HashSet<>(activeAps);
        MultiMap<Stmt, AccessPath> newInActiveApMap = new HashMultiMap<>(newState.getInActivationMap());
        boolean hasChanged = false;

        for (AccessPath ap : allAps) {
            AccessPath mappedAp = null;
            AccessPath newAp = null;

            if ((mappedAp = aliasing.mayAlias(ap, leftVal)) != null) {
                if (activeAps.contains(ap)) {
                    newActiveAps.remove(ap);
                    newInActiveApMap.put(assign, ap);
                    if (!(rightVal instanceof  Constant) && !(rightVal instanceof AnyNewExpr)) {
                        if (mappedAp.isFieldRef() && rightVal instanceof Local) {
                            newAp = manager.getAccessPathFactory().copyWithNewValue(mappedAp, rightVal, mappedAp.getBaseType(), true);
                        } else {
                            newAp = manager.getAccessPathFactory().copyWithNewValue(mappedAp, rightVal);
                        }
                        newActiveAps.add(newAp);
                    }
                } else {
                }
            } else if (!ap.isLocal() && aliasing.mayAlias(ap.getPlainValue(), leftVal)) {
                if (activeAps.contains(ap)) {
                    newActiveAps.remove(ap);
                    newInActiveApMap.put(assign, ap);
                    if (!(rightVal instanceof  Constant) && !(rightVal instanceof AnyNewExpr)) {
                        newAp = manager.getAccessPathFactory().copyWithNewValue(ap, rightVal);
                        newActiveAps.add(newAp);
                    }
                } else {
                }
            }

            if ((mappedAp = aliasing.mayAlias(ap, rightVal)) != null) {
                if (activeAps.contains(ap)) {
                } else {
                }
            } else if (!ap.isLocal() && aliasing.mayAlias(ap.getPlainValue(), rightVal)) {
                if (activeAps.contains(ap)) {
                } else {
                    Stmt activeStmt = null;
                    for (Stmt s : newState.getInActivationMap().keySet()) {
                        Set<AccessPath> tempaps = newState.getInactiveAps();
                        if (tempaps.contains(s)) {
                            activeStmt = s;
                            break;
                        }
                    }
                    assert activeStmt!=null;
                    AccessPath newap = manager.getAccessPathFactory().copyWithNewValue(ap, rightVal);
                    newInActiveApMap.put(activeStmt, newap);
                }

            }
        }
    }
}
