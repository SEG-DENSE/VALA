package soot.jimple.infoflow.pattern.patternresource.definition;

import soot.SootMethod;
import soot.Type;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.Stmt;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPConstant;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPType;
import soot.jimple.infoflow.pattern.patternresource.enumeration.MergeStrategy;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LCResourceOPDefinition {
    protected String patternName;
    protected String category;
    protected LCResourceOPType opType;
    protected Set<LCMethodDefinition> methodDefs;
    protected MergeStrategy mergeStrategy;

    public LCResourceOPDefinition(String patternName, String category, LCResourceOPType opType, Set<LCMethodDefinition> methodDefs, MergeStrategy mergeStrategy) {
        this.patternName = patternName;
        this.category = category;
        this.opType = opType;
        this.methodDefs = methodDefs;
        this.mergeStrategy = mergeStrategy;
    }
    public Set<Type> getInvovledType() {
        Set<Type> involvedTypes = new HashSet<>();
        if (null != methodDefs) {
            for (LCMethodDefinition d : methodDefs) {
                involvedTypes.addAll(d.getInvovledTypes());
            }
        }
        return involvedTypes;
    }

    public String getCategory() {
        return category;
    }

    public boolean isDefaultCategory() {return LCResourceOPConstant.DEFAULT.equals(category);}

    public LCResourceOPType getOpType() {
        return opType;
    }

    public Set<LCMethodDefinition> getMethodDefs() {
        return methodDefs;
    }

    public LCMethodDefinition getMethodDef(SootMethod method) {
        for (LCMethodDefinition methodDef : methodDefs) {
            if (methodDef.method == method) {
                return methodDef;
            }
        }
        return null;
    }

    public MergeStrategy getMergeStrategy() {
        return mergeStrategy;
    }

    public String getPatternName() {
        return patternName;
    }
}
