package soot.jimple.infoflow.pattern.rules;

import soot.SootMethod;
import soot.Value;
import soot.jimple.Constant;
import soot.jimple.DefinitionStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPHelper;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPList;
import soot.jimple.infoflow.pattern.result.LCMethodSummaryResult;
import soot.jimple.infoflow.pattern.solver.NormalState;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowManager;
import soot.jimple.infoflow.util.ByReferenceBoolean;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class LCOperationPropagationRule extends AbstractLCStatePropagationRule {
    protected LCResourceOPHelper opHelper;
    public LCOperationPropagationRule(PatternInfoflowManager manager, LCMethodSummaryResult results) {
        super(manager, results);
        opHelper = manager.getLCResourceOPHelper();
    }

    @Override
    NormalState propagateNormalFlow(NormalState source, Stmt stmt, Stmt destStmt, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        return generateNewOPListState(source, stmt, hasGeneratedNewState, null);
    }

    @Override
    NormalState propagateCallFlow(NormalState source, Stmt stmt, SootMethod dest, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        return source;
    }

    @Override
    NormalState propagateCallToReturnFlow(NormalState source, Stmt stmt, SootMethod callee, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        return generateNewOPListState(source, stmt, hasGeneratedNewState, null);
    }

    @Override
    NormalState propagateReturnFlow(NormalState source, Stmt exitStmt, Stmt retSite, Stmt callSite,SootMethod calleeMethod, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        if (callSite instanceof DefinitionStmt && exitStmt instanceof ReturnStmt && ((ReturnStmt) exitStmt).getOp() instanceof Constant) {
            return generateNewOPListState(source, callSite, hasGeneratedNewState, ((DefinitionStmt) callSite).getLeftOp());
        } else {
            return source;
        }
    }

    private NormalState generateNewOPListState(NormalState originalState, Stmt stmt, ByReferenceBoolean hasGeneratedNewState, Value extraValue) {
        Set<AccessPath> allAps = new HashSet<>();
        if (originalState.isActivating()) {
            Set<AccessPath> tempAps = originalState.getTempAps();
            if (null != tempAps){allAps.addAll(tempAps);}
        } else {
            allAps.addAll(originalState.getAps());
        }

        LCResourceOPList newoplist = opHelper.merge(stmt, allAps, originalState.getDef(), originalState.getOps(), getAliasing(), extraValue);
        if (originalState.isZeroState()) {
            if (newoplist.equals(originalState.getOps())) {
                return originalState;
            } else {
                return originalState.deriveNewZeroState(hasGeneratedNewState, newoplist, stmt);
            }
        } else {
            return originalState.deriveNewOPListState(hasGeneratedNewState, newoplist, stmt);
        }
    }

}
