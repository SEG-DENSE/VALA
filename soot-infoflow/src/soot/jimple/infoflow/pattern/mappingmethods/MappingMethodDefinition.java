package soot.jimple.infoflow.pattern.mappingmethods;

import soot.SootMethod;

public class MappingMethodDefinition {
    protected SootMethod method;

    public SootMethod getMethod() {
        return method;
    }

    public boolean isCheckBase() {
        return checkBase;
    }

    public int[] getCheckArgs() {
        return checkArgs;
    }

    public boolean isCheckLeft() {
        return checkLeft;
    }

    public boolean isTaintLeft() {
        return taintLeft;
    }

    public boolean isTaintBase() {
        return taintBase;
    }

    public int[] getTaintArgs() {
        return taintArgs;
    }

    public boolean shouldAddOp() {
        return shouldAddOp;
    }


    protected boolean checkBase;
    protected int[] checkArgs;
    protected boolean checkLeft;
    protected boolean taintLeft;
    protected boolean taintBase;
    protected int[] taintArgs;
    protected boolean shouldAddOp;



    public MappingMethodDefinition(SootMethod method, boolean checkBase, int[] checkArgs, boolean checkLeft, boolean taintBase,int[] taintArgs,boolean taintLeft, boolean shouldAddOp) {
        this.method = method;
        this.checkBase = checkBase;
        this.checkArgs = checkArgs;
        this.checkLeft = checkLeft;
        this.taintBase = taintBase;
        this.taintArgs = taintArgs;
        this.taintLeft = taintLeft;
        this.shouldAddOp = shouldAddOp;
    }
}
