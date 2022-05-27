package soot.jimple.infoflow.pattern.rules;

import soot.SootMethod;
import soot.jimple.Stmt;
import soot.jimple.infoflow.pattern.alias.PatternAliasing;
import soot.jimple.infoflow.pattern.result.LCMethodSummaryResult;
import soot.jimple.infoflow.pattern.solver.NormalState;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowManager;
import soot.jimple.infoflow.util.ByReferenceBoolean;

abstract public class AbstractLCStatePropagationRule {

    protected final PatternInfoflowManager manager;
    protected final LCMethodSummaryResult results;

    public AbstractLCStatePropagationRule(PatternInfoflowManager manager, LCMethodSummaryResult results) {
        this.manager = manager;
        this.results = results;
    }

    protected PatternInfoflowManager getManager() {
        return this.manager;
    }

    protected PatternAliasing getAliasing() {
        return this.manager.getAliasing();
    }

    protected LCMethodSummaryResult getResults() {
        return this.results;
    }

    abstract NormalState propagateNormalFlow(NormalState source, Stmt stmt, Stmt destStmt,
                                    ByReferenceBoolean hasGeneratedNewState,
                                    ByReferenceBoolean killAll);

    abstract NormalState propagateCallFlow(NormalState source, Stmt stmt, SootMethod dest,
                                           ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll);

    abstract NormalState propagateCallToReturnFlow(NormalState source, Stmt stmt, SootMethod callee,
                                                   ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll);

    abstract NormalState propagateReturnFlow(NormalState source, Stmt exitStmt, Stmt retSite, Stmt callSite, SootMethod calleeMethod,
                                             ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll);

}
