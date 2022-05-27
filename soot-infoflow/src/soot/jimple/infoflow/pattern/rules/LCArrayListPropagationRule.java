package soot.jimple.infoflow.pattern.rules;

import soot.SootMethod;
import soot.jimple.Stmt;
import soot.jimple.infoflow.pattern.result.LCMethodSummaryResult;
import soot.jimple.infoflow.pattern.solver.NormalState;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowManager;
import soot.jimple.infoflow.util.ByReferenceBoolean;

public class LCArrayListPropagationRule extends  AbstractLCStatePropagationRule{
    public LCArrayListPropagationRule(PatternInfoflowManager manager, LCMethodSummaryResult results) {
        super(manager, results);
    }

    @Override
    NormalState propagateNormalFlow(NormalState source, Stmt stmt, Stmt destStmt, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        return source;
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
    NormalState propagateReturnFlow(NormalState source, Stmt exitStmt, Stmt retSite, Stmt callSite, SootMethod calleeMethod, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        return source;
    }
}
