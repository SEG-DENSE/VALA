package soot.jimple.infoflow.pattern.rules;

import soot.Local;
import soot.SootMethod;
import soot.Value;
import soot.jimple.*;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.pattern.alias.PatternAliasing;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPList;
import soot.jimple.infoflow.pattern.result.LCMethodSummaryResult;
import soot.jimple.infoflow.pattern.solver.NormalState;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowManager;
import soot.jimple.infoflow.pattern.sourceandsink.PatternSourceInfo;
import soot.jimple.infoflow.sourcesSinks.manager.SourceInfo;
import soot.jimple.infoflow.util.ByReferenceBoolean;

import java.util.HashSet;
import java.util.Set;

public class LCStrongUpdatePropagationRule extends AbstractLCStatePropagationRule{
    public LCStrongUpdatePropagationRule(PatternInfoflowManager manager, LCMethodSummaryResult results) {
        super(manager, results);
    }

    @Override
    NormalState propagateNormalFlow(NormalState source, Stmt stmt, Stmt destStmt, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        if (!(stmt instanceof AssignStmt) || source.isZeroState())
            return source;
        AssignStmt assignStmt = (AssignStmt) stmt;

        // if leftvalue contains the tainted value -> it is overwritten - remove taint:
        // but not for arrayRefs:
        // of collections
        // because we do not use a MUST-Alias analysis, we cannot delete aliases of
        // taints
        if (assignStmt.getLeftOp() instanceof ArrayRef)
            return source;

        Value rightValue = assignStmt.getRightOp();
        for (AccessPath ap : source.getAps()) {
            if (null != getAliasing().mayAlias(ap, rightValue)) {
                return source;
            }
        }

        //     r0.field = null;

        Value leftValue = assignStmt.getLeftOp();
        if (leftValue instanceof InstanceFieldRef || leftValue instanceof StaticFieldRef) {
            if (((FieldRef) leftValue).getField() == source.getDef().getField()) {
                final SourceInfo sourceInfo = getManager().getSourceSinkManager().getSourceInfo(stmt, getManager(), source.getEntryClass());
                // Is this a source?
                if (sourceInfo != null) {
                    NormalState newstate = source.deriveNewState(sourceInfo.getAccessPaths(), hasGeneratedNewState, stmt);
                    LCResourceOPList newoplist;
                    if (assignStmt.getRightOp() instanceof Constant) {
                        newoplist = manager.getLCResourceOPHelper().merge(stmt, newstate.getAps(), newstate.getDef(), newstate.getOps(), getAliasing(), null);
                    } else {
                        newoplist = manager.getLCResourceOPHelper().merge(stmt, newstate.getAps(), newstate.getDef(), LCResourceOPList.getInitialList(), getAliasing(), null);
                    }
                    newstate = newstate.deriveNewOPListState(hasGeneratedNewState, newoplist, stmt);
                    PatternSourceInfo pSourceInfo = (PatternSourceInfo)sourceInfo;
                    AccessPath apToSlice = pSourceInfo.getApToSlice();
                    if (null != apToSlice) {
                        if (getAliasing().computeAliases(newstate, apToSlice, stmt, true)) {
                            killAll.value = true;
                            return null;
                        }
                    }

                    return newstate;
                }
            } else if (manager.getConfig().shouldTaintSubfieldsAndChildren() && leftValue instanceof InstanceFieldRef) {
                Value leftBase = ((InstanceFieldRef) leftValue).getBase();
                for (AccessPath ap : source.getAps()) {
                    if (getAliasing().mayAlias(leftBase, ap.getPlainValue())
                            && ap.getFieldCount() == 0
                            && ap.getTaintSubFields()) {
                        Set<AccessPath> newaps = new HashSet<>();
                        AccessPath newleftap = manager.getAccessPathFactory().createAccessPath(leftValue, true);;
                        AccessPath newrightap = null;
                        if (!(rightValue instanceof  Constant)) {
                            newrightap = manager.getAccessPathFactory().createAccessPath(rightValue, true);
                            newaps.add(newleftap);
                        }
                        newaps.addAll(source.getAps());

                        NormalState newstate = source.deriveNewState(newaps, hasGeneratedNewState, stmt);
                        if (null != newrightap) {
                            if ( !newstate.getOps().isEmpty() && PatternAliasing.canHaveAliases(newrightap, true)) {
                                LCResourceOPList l = newstate.getOps().removeLastOP(stmt);
                                newstate = newstate.deriveNewOPListState(hasGeneratedNewState, l ,stmt);
                            }
                            if (getAliasing().computeAliases(newstate, newrightap, stmt, true)) {
                                killAll.value = true;
                                return null;
                            }
                        }

                    }
                }
            }
        }



        Set<AccessPath> removeAps = new HashSet<>();
        for (AccessPath ap : source.getAps()) {
            if (ap.isInstanceFieldRef()) {
                // Data Propagation: x.f = y && x.f tainted --> no taint propagated
                // Alias Propagation: Only kill the alias if we directly overwrite it,
                // otherwise it might just be the creation of yet another alias
                if (leftValue instanceof InstanceFieldRef) {
                    InstanceFieldRef leftRef = (InstanceFieldRef) leftValue;
                    boolean baseAliases = getAliasing().mustAlias((Local) leftRef.getBase(),
                                ap.getPlainValue(), assignStmt);
                    if (baseAliases) {
                        if (getAliasing().mustAlias(leftRef.getField(), ap.getFirstField())) {
                            removeAps.add(ap);
                        }
                    }
                }
                // x = y && x.f tainted -> no taint propagated. This must only check the precise
                // variable which gets replaced, but not any potential strong aliases
                else if (leftValue instanceof Local) {
                        if (getAliasing().mustAlias((Local) leftValue, ap.getPlainValue(),
                                stmt)) {
                            removeAps.add(ap);
                        }
                }
            }
            // X.f = y && X.f tainted -> no taint propagated. Kills are allowed even if
            // static field tracking is disabled
            else if (ap.isStaticFieldRef()) {
                if (leftValue instanceof StaticFieldRef && getAliasing().mustAlias(
                        ((StaticFieldRef) leftValue).getField(), ap.getFirstField())) {
                    removeAps.add(ap);
                }
            }
            // when the fields of an object are tainted, but the base object is overwritten
            // then the fields should not be tainted any more
            // x = y && x tainted -> no taint propagated
            else if (ap.isLocal() && assignStmt.getLeftOp() instanceof Local
                    && assignStmt.getLeftOp() == ap.getPlainValue()) {
                removeAps.add(ap);
            }
        }
        if (removeAps.isEmpty()) {
            return source;
        } else {
            Set<AccessPath> newaps = new HashSet<>(source.getAps());
            newaps.removeAll(removeAps);
            return source.deriveNewState(newaps, hasGeneratedNewState, assignStmt);
        }
    }

    @Override
    NormalState propagateCallFlow(NormalState source, Stmt stmt, SootMethod dest, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        return source;
    }

    @Override
    NormalState propagateCallToReturnFlow(NormalState source, Stmt stmt, SootMethod callee, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        return source;
    }

    @Override
    NormalState propagateReturnFlow(NormalState source, Stmt exitStmt, Stmt retSite, Stmt callSite,SootMethod calleeMethod, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        return source;
    }
}
