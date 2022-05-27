package soot.jimple.infoflow.pattern.rules;

import soot.SootMethod;
import soot.Value;
import soot.jimple.*;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.pattern.alias.PatternAliasing;
import soot.jimple.infoflow.pattern.mappingmethods.MappingMethodDefinition;
import soot.jimple.infoflow.pattern.mappingmethods.MappingMethodHelper;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPHelper;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPList;
import soot.jimple.infoflow.pattern.result.LCMethodSummaryResult;
import soot.jimple.infoflow.pattern.solver.NormalState;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowManager;
import soot.jimple.infoflow.util.ByReferenceBoolean;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LCSPMethodPropagationRule extends AbstractLCStatePropagationRule{
    protected Map<SootMethod, MappingMethodDefinition> spmethodmap;
    protected LCResourceOPHelper opHelper;
    public LCSPMethodPropagationRule(PatternInfoflowManager manager, LCMethodSummaryResult results) {
        super(manager, results);
        spmethodmap = MappingMethodHelper.v().getMappingmethods();
        opHelper = manager.getLCResourceOPHelper();
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
        MappingMethodDefinition def = spmethodmap.get(callee);
        if (null == def) {return source;}
        InvokeExpr expr = stmt.getInvokeExpr();
        PatternAliasing aliasing = manager.getAliasing();
        boolean matched = false;
        Set<Value> checkedValues = new HashSet<>();
        Set<Value> taintValues = new HashSet<>();

        if (expr instanceof InstanceInvokeExpr) {
            Value base = ((InstanceInvokeExpr) expr).getBase();
            if (def.isCheckBase()) {
                checkedValues.add(base);
            } else if (def.isTaintBase()) {
                taintValues.add(base);
            }
        }
        if (stmt instanceof DefinitionStmt) {
            Value leftVal = ((DefinitionStmt) stmt).getLeftOp();
            if (def.isCheckLeft()) {
                checkedValues.add(leftVal);
            } else if (def.isTaintLeft()) {
                taintValues.add(leftVal);
            }
        }
        int[] checkArgs = def.getCheckArgs();
        List<Value> args = expr.getArgs();
        if (null != checkArgs) {
            for (int n : checkArgs) {
                checkedValues.add(args.get(n));
            }
        }
        int[] taintArgs = def.getTaintArgs();
        if (null != taintArgs) {
            for (int n : taintArgs) {
                taintValues.add(args.get(n));
            }
        }

        for (Value v : checkedValues) {
            for (AccessPath ap : source.getAps()) {
                if (null != aliasing.mayAlias(ap, v)) {
                    matched = true;
                    break;
                }
            }
        }
        if (!matched) {
            return source;
        } else {
            Set<AccessPath> newAps = new HashSet<>(source.getAps());
            for (Value taintValue : taintValues) {
                if (taintValue instanceof Constant) {continue;}
                AccessPath newAp = manager.getAccessPathFactory().createAccessPath(taintValue, false);
                assert null != newAp;
                newAps.add(newAp);
            }
            NormalState newState = source.deriveNewState(newAps, hasGeneratedNewState, stmt);
            if (def.shouldAddOp()) {
                LCResourceOPList newoplist = opHelper.mergeSPMethodCall(stmt, callee, newState.getOps());
                newState = newState.deriveNewOPListState(hasGeneratedNewState, newoplist, stmt);
            }
            return newState;
        }
    }

    @Override
    NormalState propagateReturnFlow(NormalState source, Stmt exitStmt, Stmt retSite, Stmt callSite, SootMethod calleeMethod, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        return source;
    }
}
