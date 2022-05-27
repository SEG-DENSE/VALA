package soot.jimple.infoflow.pattern.skipmethods;

import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

public class SkipMethodsHelper {
    private static SkipMethodsHelper instance = new SkipMethodsHelper();
    private static MultiMap<SootField, SootMethod> cannotSkipMethodsForField = null;
    private static Set<SootMethod> cannotSkipMethodsForOperation = null;
    private static Set<SootMethod> connotSkipMethodsAllForField = null;
    private SkipMethodsHelper() {
        cannotSkipMethodsForField = new HashMultiMap<>();
        cannotSkipMethodsForOperation = new HashSet<>();
    }
    public static SkipMethodsHelper v(){return instance;}
    public void init(IInfoflowCFG icfg, Set<SootMethod> noCheckMethods, MultiMap<SootClass, SootField> entryFields) {
        cannotSkipMethodsForOperation = methodLoopsFor(icfg, noCheckMethods);

        Set<SootField> allEntryFields = entryFields.values();
        MultiMap<SootField, SootMethod> fieldsAccessMethods = new HashMultiMap<>();
        for (Unit u : icfg.allNonCallEndNodes()) {
            if (!(u instanceof AssignStmt)) {
                continue;
            }
            AssignStmt ass = (AssignStmt) u;
            Value leftOp = ass.getLeftOp();
            Value rightOp = ass.getRightOp();
            InstanceFieldRef fRef = null;
            if (leftOp instanceof InstanceFieldRef) {
                fRef = (InstanceFieldRef)leftOp;
            } else if (rightOp instanceof InstanceFieldRef) {
                fRef = (InstanceFieldRef)rightOp;
            }
            if (null != fRef) {
                SootField f = fRef.getField();
                if (allEntryFields.contains(f)) {
                    fieldsAccessMethods.put(f, icfg.getMethodOf(ass));
                }
            }
        }
        cannotSkipMethodsForField = new HashMultiMap<>();
        for (SootField f : fieldsAccessMethods.keySet()) {
            Set<SootMethod> entryMethods = fieldsAccessMethods.get(f);
            cannotSkipMethodsForField.putAll(f, methodLoopsFor(icfg, entryMethods));
        }
        connotSkipMethodsAllForField = cannotSkipMethodsForField.values();
    }

    public Set<SootMethod> methodLoopsFor(IInfoflowCFG icfg, Set<SootMethod> initialMethods) {
        Set<SootMethod> allCallingMs = new HashSet<>(initialMethods);
        Stack<SootMethod> currentCallingMs = new Stack<>();
        currentCallingMs.addAll(initialMethods);
        while (!currentCallingMs.isEmpty()) {
            SootMethod cM = currentCallingMs.pop();
            Collection<Unit> invocations = icfg.getCallersOf(cM);
            for (Unit u : invocations) {
                SootMethod m  = icfg.getMethodOf(u);
                if (!allCallingMs.contains(m)) {
                    allCallingMs.add(m);
                    currentCallingMs.add(m);
                }
            }
        }
        return allCallingMs;
    }
    public boolean canBeExcluded(SootField f, SootMethod m) {
        if (cannotSkipMethodsForOperation.contains(m)) {
            return false;
        }
        if ((null == f) && connotSkipMethodsAllForField.contains(m)) {
            return false;
        } else if (cannotSkipMethodsForField.contains(f, m)) {
            return false;
        }
        return true;
    }
}
