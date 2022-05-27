package soot.jimple.infoflow.results.rules;

import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.Ref;
import soot.jimple.Stmt;
import soot.jimple.infoflow.pattern.entryPointCreators.AndroidEntryPointConstants;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOP;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPList;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPType;
import soot.jimple.infoflow.pattern.result.ErrorUsageDetailedReport;

import java.util.List;
import java.util.Map;

public class NoActivityAssignmentRule implements ExceptionRule{
    @Override
    public boolean shouldBeExcluded(ErrorUsageDetailedReport report) {
        Map<String, LCResourceOPList> errorOps = report.errorOps;
        if (null == errorOps || errorOps.isEmpty())
            return false;
        for (String entrypointStr : errorOps.keySet()) {
            LCResourceOPList opList = errorOps.get(entrypointStr);
            List<LCResourceOP> ops = opList.getOpList();
            if (null == ops || ops.size() != 1) continue;
            LCResourceOP firstOP = ops.get(0);
            if (firstOP.getOpType() == LCResourceOPType.EDIT) {
                Stmt statement = firstOP.getStmt();
                if (statement instanceof AssignStmt) {
                    Value leftOp = ((AssignStmt) statement).getLeftOp();
                    if (leftOp.getType() instanceof RefType) {
                        SootClass leftClass = ((RefType) leftOp.getType()).getSootClass();
                        if (AndroidEntryPointConstants.isActivityOrFragment(leftClass)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
