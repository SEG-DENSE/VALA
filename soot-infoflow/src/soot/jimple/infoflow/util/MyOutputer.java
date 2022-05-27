package soot.jimple.infoflow.util;

import soot.*;
import soot.jimple.infoflow.pattern.entryPointCreators.AndroidEntryPointConstants;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import static soot.jimple.infoflow.pattern.entryPointCreators.AndroidEntryPointConstants.*;

public class MyOutputer {
//    private int currentIndex = -1;
    private static MyOutputer instance = null;
    private File outputFile = null;
    private float currentLCAnalysisTime = -1;
    private int currentEntryNum = -1;
    private int currentEntryFieldNum = -1;
    private int currentCFGEdgeNum = -1;
    private MyOutputer() {
        edgeNums = new HashMap<>();
        involvedEntries = new HashMap<>();
        involvedEntryFields = new HashMap<>();
        minSdks = new HashMap<>();
        targetSdks = new HashMap<>();
    }
    private Map<Integer, Integer> edgeNums = null;
    private MultiMap<SootClass, SootMethod> allEntries = null;
    private MultiMap<SootClass, SootField> allEntryFields = null;
    private MultiMap<SootClass, SootField> reportEntryFields = null;
    private Map<Integer, Integer> involvedEntries = null;
    private Map<Integer, MultiMap<SootClass, SootField>> involvedEntryFields = null;

    private Map<Integer, Integer> minSdks = null;
    private Map<Integer, Integer> targetSdks = null;
    public static MyOutputer getInstance() {
        if(null == instance) {
            instance = new MyOutputer();
        }
        return instance;
    }
//        currentIndex = index;
//        this.currentLCAnalysisTime = newTime;
    public float getCurrentLCAnalysisTime() {
        return this.currentLCAnalysisTime;
    }
    public void updateEdgeNums(int index, int num) {
        this.currentCFGEdgeNum = num;
        edgeNums.put(index, num);
    }
    public int getCurrentCFGEdgeNum() {
        return this.currentCFGEdgeNum;
    }
    public void updateInvolvedEntries(int index, int num) {
        this.currentEntryNum = num;
        involvedEntries.put(index, num);
    }
    public int getCurrntInvolvedEntryNum() {
        return this.currentEntryNum;
    }
    public void updateInvolvedEntryFields(int index, MultiMap<SootClass, SootField> fieldMultiMap) {
        this.currentEntryFieldNum = 0;
        for (SootField fields : fieldMultiMap.values()) {
            currentEntryFieldNum++;
        }
        involvedEntryFields.put(index, fieldMultiMap);
    }
    public int getCurrentEntryFieldNum() {
        return this.currentEntryFieldNum;
    }
    public void updateMinSdk(int index, int minsdk) {
        minSdks.put(index, minsdk);
    }
    public void updateTargetSdk(int index, int targetsdk) {
        targetSdks.put(index, targetsdk);
    }
    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
        if (!this.outputFile.exists()) {
            try {
                this.outputFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public void updateReportEntrypoints(MultiMap<SootClass, SootField> reportEntrypoints, Set<SootClass> allEntrypoints) {
        this.reportEntryFields = new HashMultiMap<>();
//        Set<SootClass> systemComponents = new HashSet<>();
//            SootClass cClass = Scene.v().getSootClassUnsafe(cSig);
//                systemComponents.add(cClass);

        Hierarchy h = Scene.v().getActiveHierarchy();
        Set<SootClass> reportComponents = new HashSet<>();
        for (SootClass subC : reportEntrypoints.keySet()) {
            String name = subC.getName();
            if (subC.isApplicationClass() && subC.isConcrete() && allEntrypoints.contains(subC)) {
                        reportComponents.add(subC);
            }
        }

        for (SootClass cClass : reportComponents) {
            for (SootField f : cClass.getFields()) {
                String fName = f.getName();
                if (!fName.equals("ipcIntent") && !fName.equals("ipcResultIntent")) {
                    reportEntryFields.put(cClass, f);
                }
            }
        }
    }
    public void updateAllEntrypoints(Set<SootClass> allEntryClass) {
        this.allEntries = new HashMultiMap<>();
        this.allEntryFields = new HashMultiMap<>();
        for (SootClass c : allEntryClass) {
            for (String subsig : AndroidEntryPointConstants.getActivityLifecycleMethods()) {
                SootMethod m = findMethod(c, subsig);
                if (null != m) {
                    this.allEntries.put(c, m);
                }
            }
            for (String subsig : AndroidEntryPointConstants.getFragmentLifecycleMethods()) {
                SootMethod m = findMethod(c, subsig);
                if (null != m) {
                    this.allEntries.put(c, m);
                }
            }
            for (String subsig : AndroidEntryPointConstants.getBroadcastLifecycleMethods()) {
                SootMethod m = findMethod(c, subsig);
                if (null != m) {
                    this.allEntries.put(c, m);
                }
            }
            for (String subsig : AndroidEntryPointConstants.getServiceLifecycleMethods()) {
                SootMethod m = findMethod(c, subsig);
                if (null != m) {
                    this.allEntries.put(c, m);
                }
            }
            for (SootField f : c.getFields()) {
                String fName = f.getName();
                if (!fName.equals("ipcIntent") && !fName.equals("ipcResultIntent")) {
                    this.allEntryFields.put(c, f);
                }
            }
        }
    }

    protected SootMethod findMethod(SootClass currentClass, String subsignature) {
        SootMethod m = currentClass.getMethodUnsafe(subsignature);
        if (m != null) {
            return m;
        }
        if (currentClass.hasSuperclass()) {
            return findMethod(currentClass.getSuperclass(), subsignature);
        }
        return null;
    }

    public int getAllEntryClassNum() {
        return this.allEntries.keySet().size();
    }

    public int getReportEntryClassNum() {
        return this.reportEntryFields.keySet().size();
    }

    public int getReportEntryFieldNum() {
        return this.reportEntryFields.values().size();
    }
    public int getAllEntryPointNum() {
        return this.allEntries.values().size();
    }
    public int getAllEntryFieldsNum() {
        return this.allEntryFields.values().size();
    }
    public synchronized void output() {
//        try {
//            BufferedWriter bw = new BufferedWriter(new FileWriter(this.outputFile));
//            for (Integer i : edgeNums.keySet()) {
//                int edges = edgeNums.get(i);
//                if ( involvedEntries.containsKey(i)) {
//                    int entries = involvedEntries.get(i);
//                    MultiMap<SootClass, SootField> entryFields = involvedEntryFields.get(i);
//                    bw.write(i + "th app has " + edges + " edges and " + entries + " entries" + "and " + entryFields.values().size() + " entryFields");
//                    if (null != entryFields && !entryFields.isEmpty()) {
//                        for (SootClass keyClass : entryFields.keySet()) {
//                            bw.write(keyClass.toString());
//                            bw.newLine();
//                            for (SootField involvedField : entryFields.get(keyClass)) {
//                                bw.write(involvedField.getName() + "\t");
//                            }
//                            bw.newLine();
//                            bw.newLine();
//                        }
//                    }
//
//
//                    if (minSdks.containsKey(i) && targetSdks.containsKey(i)) {
//                        bw.write("\t\t minSDK: " + minSdks.get(i) + "    targetSDK: " + targetSdks.get(i));
//                    }
//                } else {
//                    bw.write(i + "th app has " + edges + " edges and no sources");
//                }
//                bw.newLine();
//                bw.newLine();
//            }
//            bw.flush();
//            bw.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }
}
