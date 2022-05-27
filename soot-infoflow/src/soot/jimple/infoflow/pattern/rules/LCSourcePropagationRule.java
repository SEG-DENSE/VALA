package soot.jimple.infoflow.pattern.rules;

import soot.SootMethod;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.pattern.result.LCMethodSummaryResult;
import soot.jimple.infoflow.pattern.solver.NormalState;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowManager;
import soot.jimple.infoflow.pattern.sourceandsink.PatternFieldSourceSinkDefinition;
import soot.jimple.infoflow.pattern.sourceandsink.PatternSourceInfo;
import soot.jimple.infoflow.sourcesSinks.manager.SourceInfo;
import soot.jimple.infoflow.util.ByReferenceBoolean;

public class LCSourcePropagationRule extends AbstractLCStatePropagationRule {
    public LCSourcePropagationRule(PatternInfoflowManager manager, LCMethodSummaryResult results) {
        super(manager, results);
    }

    @Override
    NormalState propagateNormalFlow(NormalState source, Stmt stmt, Stmt destStmt, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        if (source.isZeroState() || source.getAps().isEmpty()) {
            // Check whether this can be a source at all
            final SourceInfo sourceInfo = getManager().getSourceSinkManager().getSourceInfo(stmt, getManager(), source.getEntryClass());
            // Is this a source?
            if (sourceInfo != null && (source.getDef()== null || (source.getDef()!=null && source.getDef() == sourceInfo.getDefinition()))) {
                NormalState newstate = source.deriveNewState(sourceInfo.getAccessPaths(), (PatternFieldSourceSinkDefinition) sourceInfo.getDefinition(), hasGeneratedNewState, stmt);

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
        }

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
    NormalState propagateReturnFlow(NormalState source, Stmt exitStmt, Stmt retSite, Stmt callSite,SootMethod calleeMethod, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        return source;
    }

}
