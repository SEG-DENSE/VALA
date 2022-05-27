package soot.jimple.infoflow.pattern.rules;

import soot.SootMethod;
import soot.jimple.Stmt;
import soot.jimple.infoflow.pattern.mappingmethods.MappingMethodHelper;
import soot.jimple.infoflow.pattern.result.LCMethodSummaryResult;
import soot.jimple.infoflow.pattern.solver.NormalState;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowManager;
import soot.jimple.infoflow.util.ByReferenceBoolean;

import java.util.*;

public class PatternPropagationRuleManager {
    protected final PatternInfoflowManager manager;
    protected final LCMethodSummaryResult results;
    protected final ArrayList<AbstractLCStatePropagationRule> rules;

    public PatternPropagationRuleManager(PatternInfoflowManager manager, LCMethodSummaryResult results) {
        this.manager = manager;
        this.results = results;

        ArrayList<AbstractLCStatePropagationRule> ruleList = new ArrayList<>();

        ruleList.add(new LCSourcePropagationRule(manager, results));
        ruleList.add(new LCOperationPropagationRule(manager, results));
        ruleList.add(new LCFinishPropagationRule(manager, results));
        ruleList.add(new LCStrongUpdatePropagationRule(manager, results));

        ruleList.add(new LCAPUpdatePropagationRule(manager, results));
        if (!MappingMethodHelper.v().isEmpty()) {
            ruleList.add(new LCSPMethodPropagationRule(manager, results));
        }
        this.rules = ruleList;
    }

    public NormalState applyNormalFlowFunction(NormalState source, Stmt stmt, Stmt destStmt,
                                               ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        NormalState newState = source;
        for (AbstractLCStatePropagationRule rule : rules) {
            newState = rule.propagateNormalFlow(newState, stmt, destStmt, hasGeneratedNewState, killAll);
            if (killAll != null && killAll.value || newState == null)
                return null;
        }
        return newState;
    }

    public NormalState applyCallFlowFunction(NormalState source, Stmt stmt, SootMethod destMethod,
                                               ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        NormalState newState = source;
        for (AbstractLCStatePropagationRule rule : rules) {
            newState = rule.propagateCallFlow(newState, stmt, destMethod, hasGeneratedNewState, killAll);
            if (killAll != null && killAll.value || newState == null)
                return null;
        }
        return newState;
    }

    public NormalState applyCallToReturnFlowFunction(NormalState source, Stmt stmt, SootMethod callee,
                                             ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        NormalState newState = source;
        for (AbstractLCStatePropagationRule rule : rules) {
            newState = rule.propagateCallToReturnFlow(newState, stmt, callee, hasGeneratedNewState, killAll);
            if (killAll != null && killAll.value || newState == null)
                return null;
        }
        return newState;
    }

    public NormalState applyReturnFlowFunction(NormalState source, Stmt exitstmt, Stmt retSite, Stmt callmethod, SootMethod calleeMethod,
                                                     ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        NormalState newState = source;
        for (AbstractLCStatePropagationRule rule : rules) {
            newState = rule.propagateReturnFlow(newState, exitstmt, retSite, callmethod, calleeMethod, hasGeneratedNewState, killAll);
            if (killAll != null && killAll.value || newState == null)
                return null;
        }
        return newState;
    }

}
