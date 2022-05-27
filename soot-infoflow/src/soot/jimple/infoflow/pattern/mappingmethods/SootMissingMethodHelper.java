package soot.jimple.infoflow.pattern.mappingmethods;

import soot.SootMethod;
import soot.jimple.infoflow.collect.ConcurrentHashSet;

import java.util.HashSet;
import java.util.Set;

public class SootMissingMethodHelper {
    protected Set<SootMethod> missingMethods;
    private static SootMissingMethodHelper instance = new SootMissingMethodHelper();
    private SootMissingMethodHelper() {missingMethods = new ConcurrentHashSet<>();}
    public static SootMissingMethodHelper v(){return instance;}
    public void addMissingMethod(SootMethod m) {
        this.missingMethods.add(m);
    }
    public boolean isMethodMissing(SootMethod m) {
        return this.missingMethods.contains(m);
    }
}
