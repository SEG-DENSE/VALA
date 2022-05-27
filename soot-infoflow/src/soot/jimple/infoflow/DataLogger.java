package soot.jimple.infoflow;

import soot.SootMethod;
import soot.jimple.Stmt;
import soot.jimple.infoflow.collect.ConcurrentHashSet;

import java.util.concurrent.ConcurrentHashMap;

public class DataLogger {
    private static DataLogger instance = new DataLogger();
    public static DataLogger getInstance() {return instance;}
    protected ConcurrentHashMap<SootMethod, Integer> methodinvocationmaps = new ConcurrentHashMap<>();
    public void addInvocation(SootMethod m) {
        if (methodinvocationmaps.containsKey(m)) {
            methodinvocationmaps.put(m, methodinvocationmaps.get(m) + 1);
        } else {
            methodinvocationmaps.put(m, 1);
        }
    }
    protected ConcurrentHashSet<Stmt> testStmts = new ConcurrentHashSet<>();
    public void addStmt(Stmt stmt) {
        testStmts.add(stmt);
    }
    public void output() {
        for (SootMethod m : methodinvocationmaps.keySet()) {
            System.out.print(m.getSignature() + "   :   " + methodinvocationmaps.get(m));
            System.out.println();
        }
        System.out.println();
        for (Stmt s : testStmts) {
            System.out.println(s.toString());
        }
    }
}
