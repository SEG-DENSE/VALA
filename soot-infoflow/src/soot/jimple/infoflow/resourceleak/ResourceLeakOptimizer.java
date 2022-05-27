package soot.jimple.infoflow.resourceleak;

public class ResourceLeakOptimizer {
//    private static ResourceLeakOptimizer instance = new ResourceLeakOptimizer();
//    private ISourceSinkManager sourceSinkManager = null;
//        return instance;
//        this.sourceSinkManager = sourceSinkManager;
//
//    private Set<SootMethod> allNotRelatedMs = null;
//    private Set<SootMethod> allRelatedMs = null;
//        Stack<SootMethod> currentAllMs = new Stack<>();
//        currentAllMs.addAll(entrypoints);
//
//        Set<SootMethod> allMs = new HashSet<>(entrypoints);
//        allNotRelatedMs = new HashSet<>();
//        allRelatedMs = new HashSet<>();
//
//            SootMethod currentM = currentAllMs.pop();
//            Set<SootMethod> currentCalledMs = getAllCalledSootMethods(currentM, icfg);
//                    currentAllMs.push(innerM);
//                    allMs.add(innerM);
//
//        currentAllMs.addAll(allMs);
//        Set<SootMethod> nextTurnCurrentAllMs = null;
//        boolean hasReachedFixPoint = false;
//            int preAllNotRelatedMNum = allNotRelatedMs.size();
//            int preAllRelatedMNum = allRelatedMs.size();
//
//            nextTurnCurrentAllMs = new HashSet<>();
//                SootMethod currentM = currentAllMs.pop();
//                boolean shouldSave = true;
//                boolean isNotRelated = true;
////                    System.out.println();
//                Set<SootMethod> currentCalledMs = getAllCalledSootMethods(currentM, icfg);
//                        shouldSave = false;
//                        isNotRelated = false;
//                        allRelatedMs.add(currentM);
//                        break;
//                        isNotRelated = false;
//                    shouldSave = false;
//                    allNotRelatedMs.add(currentM);
//                    nextTurnCurrentAllMs.add(currentM);
//
//            int currentAllNotRelatedMNum = allNotRelatedMs.size();
//            int currentAllRelatedMNum = allRelatedMs.size();
//                hasReachedFixPoint = true;
//            currentAllMs.addAll(nextTurnCurrentAllMs);
//            allNotRelatedMs.addAll(nextTurnCurrentAllMs);
//
////                System.out.println();
////                System.out.println();
//
//
//        Set<SootMethod> results = new HashSet<>();
//                Stmt s = (Stmt)u;
//                    results.add(s.getInvokeExpr().getMethod());
//                    results.addAll(icfg.getCalleesOfCallAt(s));
//        return results;
//
//            return true;
//            return false;
//        return true;
}
