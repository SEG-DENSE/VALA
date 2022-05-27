package soot.jimple.infoflow.pattern.patternresource.definition;

import soot.SootMethod;
import soot.Type;

import java.util.HashSet;
import java.util.Set;

public class LCMethodDefinition {
    protected SootMethod method = null;
    protected String methodsig = null;

    public SootMethod getMethod() {
        return method;
    }

    public String getMethodsig() {
        return methodsig;
    }

    public int[] getCheckParams() {
        return checkParams;
    }

    public boolean needCheckBase() {
        return checkBase;
    }

    public boolean needNotCheck() {return notcheck;}

    public boolean needCheckAssignedValue() {
        return checkAssignedValue;
    }

    protected int[] checkParams = null;
    protected boolean checkBase = true;
    protected boolean checkAssignedValue = false;
    protected boolean notcheck = false;
    public LCMethodDefinition(String methodsig, SootMethod method, boolean checkBase, boolean checkAssignedValue, int[] checkParams, boolean notcheck) {
        this.methodsig = methodsig;
        this.method = method;
        this.checkBase = checkBase;
        this.checkAssignedValue = checkAssignedValue;
        this.checkParams = checkParams;
        this.notcheck = notcheck;
    }

    public Set<Type> getInvovledTypes() {
        //to note, currently we only include the base types for resource usages
        Set<Type> involvedTypes = new HashSet<>();
        if (!this.notcheck) {
            if (this.checkBase) {
                involvedTypes.add(method.getDeclaringClass().getType());
            }
//            if (this.checkAssignedValue) {
//                involvedTypes.add(method.getReturnType());
//            }
//            if (null != this.checkParams) {
//                for (int p : checkParams) {
//                    involvedTypes.add(method.getParameterType(p));
//                }
//            }
        }
        return involvedTypes;
    }
}
