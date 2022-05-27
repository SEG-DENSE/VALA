package soot.jimple.infoflow.pattern.patternresource;

import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Value;
import soot.jimple.*;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.pattern.alias.PatternAliasing;
import soot.jimple.infoflow.pattern.patterndata.PatternData;
import soot.jimple.infoflow.pattern.patternresource.definition.LCEditOPDefinition;
import soot.jimple.infoflow.pattern.patternresource.definition.LCMethodDefinition;
import soot.jimple.infoflow.pattern.patternresource.definition.LCResourceOPDefinition;
import soot.jimple.infoflow.pattern.patternresource.definition.LCSPMethodDefinition;
import soot.jimple.infoflow.pattern.sourceandsink.PatternFieldSourceSinkDefinition;
import soot.jimple.infoflow.pattern.sourceandsink.PatternOpDataProvider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LCResourceOPHelper {
    private static LCResourceOPHelper instance = null;
    protected Set<LCResourceOPDefinition> defs = null;
    protected LCEditOPDefinition editdef = null;
    protected Map<SootMethod, LCResourceOPDefinition> methodDefMaps = null;
    private LCResourceOPHelper(PatternOpDataProvider provider) {
        defs = provider.getAllDefs();
        for (LCResourceOPDefinition def : defs) {
            if (def instanceof  LCEditOPDefinition){editdef = (LCEditOPDefinition)def; break;}
        }
        methodDefMaps = new HashMap<>();
        for (LCResourceOPDefinition def :defs) {
            Set<LCMethodDefinition> methoddefs = def.getMethodDefs();
            if (null != methoddefs && !methoddefs.isEmpty()) {
                for (LCMethodDefinition methoddef : methoddefs) {
                    methodDefMaps.put(methoddef.getMethod(), def);
                }
            }
        }
        spmethodDefMap = new ConcurrentHashMap<>();
    }
    public static LCResourceOPHelper init(PatternOpDataProvider provider) {
        instance = new LCResourceOPHelper(provider);
        return instance;
    }
    private Set<SootMethod> opMethods = null;
    public synchronized Set<SootMethod> getAllOpMethods() {
        if (null != opMethods) {
            return opMethods;
        }
        opMethods = new HashSet<>();
        opMethods.addAll(methodDefMaps.keySet());
        return opMethods;
    }
    private Set<SootMethod> noCheckMethods = null;
    public synchronized Set<SootMethod> getNoCheckOpMethods() {
        if (null != noCheckMethods) {
            return noCheckMethods;
        }
        noCheckMethods = new HashSet<>();
        total: for (SootMethod m : methodDefMaps.keySet()) {
            LCResourceOPDefinition def = methodDefMaps.get(m);
            Set<LCMethodDefinition> mdefs = def.getMethodDefs();
            if (null != mdefs) {
                for (LCMethodDefinition mdef : mdefs) {
                    if (mdef.needNotCheck()) {
                        noCheckMethods.add(m);
                        continue total;
                    }
                }
            }

        }
        return noCheckMethods;
    }

    public LCResourceOPList merge(Stmt stmt, Set<AccessPath> aps, PatternFieldSourceSinkDefinition sourceDef, LCResourceOPList originalList, PatternAliasing aliasing, Value extraVal) {
        boolean isrelevant = false;
        LCResourceOPDefinition def = null;
        String category = null;
        if (stmt.containsInvokeExpr()) {
            InvokeExpr expr = stmt.getInvokeExpr();
            def = methodDefMaps.get(expr.getMethod());
            if (null == def) {return originalList;}
            String originalCategory = originalList.getCategory();
            String newCategory = def.getCategory();
            if (!originalCategory.equals(newCategory)) {
                if (!originalCategory.equals(LCResourceOPConstant.DEFAULT) && ! newCategory.equals(LCResourceOPConstant.DEFAULT)) {
                    return originalList;
                } else if (originalCategory.equals(LCResourceOPConstant.DEFAULT)) {
                    category = newCategory;
                } else if (newCategory.equals(LCResourceOPConstant.DEFAULT)) {
                    category = originalCategory;
                } else {
                    assert false;
                }
            } else {
                category = originalCategory;
            }

            LCMethodDefinition methoddef = def.getMethodDef(expr.getMethod());
            isrelevant = isMethodInvocationRelevant(stmt, expr, aps, methoddef, aliasing);

        } else if (stmt instanceof AssignStmt) {
            Value leftOp = ((AssignStmt) stmt).getLeftOp();
            isrelevant = isAssignmentRelevant(leftOp, aps, sourceDef, aliasing);
            def = editdef;
            category = originalList.getCategory();
        } else if (stmt instanceof ReturnStmt) {
            Value leftOp = extraVal;
            isrelevant = isAssignmentRelevant(leftOp, aps, sourceDef, aliasing);
            def = editdef;
            category = originalList.getCategory();
        }
//            System.out.println();
        if (isrelevant) {
            LCResourceOP newop = new LCResourceOP(stmt, def.getOpType(), def);
            LCResourceOPList newlist = originalList.merge(newop, def.getMergeStrategy(), category);
            return newlist;
        }
        return originalList;
    }

    protected Map<SootMethod, LCSPMethodDefinition> spmethodDefMap = null;
    public LCResourceOPList mergeSPMethodCall(Stmt stmt, SootMethod m, LCResourceOPList originalList) {
        LCSPMethodDefinition spdef = spmethodDefMap.computeIfAbsent(m, k-> LCSPMethodDefinition.getLCSPMethodDefinition(m));
        LCResourceOP newop = new LCResourceOP(stmt, spdef.getOpType(), spdef);
        LCResourceOPList newlist = originalList.merge(newop, spdef.getMergeStrategy(), originalList.getCategory());
        return newlist;
    }

    private boolean isMethodInvocationRelevant(Stmt stmt, InvokeExpr expr, Set<AccessPath> aps, LCMethodDefinition methoddef, PatternAliasing aliasing) {
        if (methoddef.needNotCheck()) {
            return true;
        }

        Value leftop = stmt instanceof  AssignStmt ? ((AssignStmt) stmt).getLeftOp():null;
        Value base = expr instanceof InstanceInvokeExpr ? ((InstanceInvokeExpr) expr).getBase():null;
        for (AccessPath ap : aps) {
            if (methoddef.needCheckAssignedValue()) {
                if (null != aliasing.mayAlias(ap, leftop)) {
                    return true;
                }
            }
            if (methoddef.needCheckBase()) {
                if (null != aliasing.mayAlias(ap, base)) {
                    return true;
                }
            }
            if (null != methoddef.getCheckParams()) {
                for (int paramNum : methoddef.getCheckParams()) {
                    if (null != aliasing.mayAlias(ap, expr.getArg(paramNum))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isAssignmentRelevant(Value leftOp, Set<AccessPath> aps, PatternFieldSourceSinkDefinition sourceDef, PatternAliasing aliasing) {
        if (aps.isEmpty() || sourceDef == null) {return false;}
        if (leftOp instanceof InstanceFieldRef || leftOp instanceof StaticFieldRef) {
            SootField field = ((FieldRef) leftOp).getField();
            if (field == sourceDef.getField()) {
                return true;
            }
            for (AccessPath ap : aps) {
                if (null != aliasing.mayAlias(ap, leftOp)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<Type> getInvolvedTypesAccordingToPattern(Set<PatternData> currentPatterns) {
        Set<Type> involvedTypes = new HashSet<>();
        for (LCResourceOPDefinition def : defs) {
            involvedTypes.addAll(def.getInvovledType());
//            for (PatternData pattern : currentPatterns) {
//                if (pattern.getPatternTitle().toLowerCase().equals(def.getPatternName())) {
//                    involvedTypes.addAll(def.getInvovledType());
//                    break;
//                }
//            }
        }
        return involvedTypes;
    }
}
