package soot.jimple.infoflow.pattern.rules;

import soot.SootMethod;
import soot.jimple.Stmt;
import soot.jimple.infoflow.pattern.entryPointCreators.AndroidEntryPointConstants;
import soot.jimple.infoflow.pattern.patterndata.PatternDataHelper;
import soot.jimple.infoflow.pattern.result.LCMethodSummaryResult;
import soot.jimple.infoflow.pattern.solver.NormalState;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowManager;
import soot.jimple.infoflow.util.ByReferenceBoolean;
import soot.tagkit.Tag;

import java.util.List;

public class LCFinishPropagationRule extends AbstractLCStatePropagationRule {
    public LCFinishPropagationRule(PatternInfoflowManager manager, LCMethodSummaryResult results) {
        super(manager, results);
    }

    @Override
    NormalState propagateNormalFlow(NormalState source, Stmt stmt, Stmt destStmt, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        return source;
    }

    @Override
    NormalState propagateCallFlow(NormalState source, Stmt stmt, SootMethod dest, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        NormalState newState = checkFinishCallAndReturn(source, stmt, dest, hasGeneratedNewState, killAll);
        if (killAll.value) {
            return null;
        } else {
            return newState;
        }
    }

    @Override
    NormalState propagateCallToReturnFlow(NormalState source, Stmt stmt, SootMethod callee, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        return source;
    }

    @Override
    NormalState propagateReturnFlow(NormalState source, Stmt exitStmt, Stmt retSite, Stmt callSite, SootMethod calleeMethod, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {

        if (source.shouldFinish(exitStmt)) {
//                System.out.println();
            if (manager.getResult().addResult(source.getDef(), source.getEntryMethod(), source.getOps())) {
            }
            killAll.value = true;
            return null;
        }
        SootMethod m = manager.getICFG().getMethodOf(exitStmt);
        NormalState newState = checkFinishCallAndReturn(source, exitStmt, m, hasGeneratedNewState, killAll);
        if (killAll.value) {
            return null;
        } else {
            return newState;
        }
    }

    private NormalState checkFinishCallAndReturn(NormalState source, Stmt currentStmt, SootMethod currentMethod, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        if (!AndroidEntryPointConstants.isLCMethod(currentMethod)) {
            return source;
        }

        List<Tag> newTags = PatternDataHelper.v().getLCMethodTag(currentMethod, source.getLcmethodTags(), killAll);
        if (killAll.value) {
            return null;
        }
        return source.deriveNewTagListState(hasGeneratedNewState, newTags, currentStmt);
    }

}
