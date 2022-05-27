package soot.jimple.infoflow.pattern.patterndata;
import heros.solver.Pair;
import soot.*;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.cfg.DefaultBiDiICFGFactory;
import soot.jimple.infoflow.pattern.entryPointCreators.AndroidEntryPointConstants;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPHelper;
import soot.jimple.infoflow.pattern.patterntag.LCLifeCycleMethodTag;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.util.ByReferenceBoolean;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.tagkit.Tag;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.util.*;

import static soot.jimple.infoflow.pattern.entryPointCreators.AndroidEntryPointConstants.*;

public class PatternDataHelper implements PatternInterface {
    public static String[] testPattern = new String[]{};
//    public static boolean adaptAllEntrypoints = false;

    String[] tags = null;
    Map<Integer, PatternData> currentPatterns = null;


    private static PatternDataHelper instance = new PatternDataHelper();
    private PatternDataHelper() {
        this.currentPatterns = new HashMap<>();
        this.allEntrypoints = new HashMap<>();
        this.allEntryMethods = new HashSet<>();
        this.tagsForAllEntryMethods = new HashMap<>();
//        this.allEntryComponentFields = new HashMultiMap<>();
        this.notRelatedTag = LCLifeCycleMethodTag.getNotRelatedInstance();
        this.notRelatedTagString =  this.notRelatedTag.getName();
        this.notRelatedTagList = new LinkedList<>();
        this.notRelatedTagList.add(this.notRelatedTag);
    }
    public static PatternDataHelper v() {
        return instance;
    }

    private Tag notRelatedTag = null;
    private List<Tag> notRelatedTagList = null;
    private String notRelatedTagString = null;
    private Map<SootClass, PatternEntryData> allEntrypoints = null;
    private Set<SootMethod> allEntryMethods = null;
    private Map<SootMethod, List<Tag>> tagsForAllEntryMethods = null;
    private MultiMap<SootClass, SootField> allEntryComponentFields = null;

    private InfoflowConfiguration config = null;
    private LCResourceOPHelper ophelper;

    @Override
    public void updateInvolvedEntrypoints(Set<SootClass> allEntryClasses, IInfoflowCFG icfg) {
        IInfoflowCFG nowicfg = icfg;
        if (null == nowicfg) {
            DefaultBiDiICFGFactory fa = new DefaultBiDiICFGFactory();
            fa.setIsAndroid(true);
            nowicfg = fa.buildBiDirICFG(null, true);
        }
//        getAllComponents();

        for (PatternData pattern : currentPatterns.values()) {
            pattern.updateInvolvedEntrypoints(allEntryClasses, nowicfg);
        }
        readEntrypointsFromPatterns();
        upadateTagsForInvolvedEntrypoints();
        performTagsAddition();
        nowicfg.purge();
    }


//        Hierarchy h = Scene.v().getActiveHierarchy();
//        Set<SootClass> allComponents = new HashSet<>();
//            SootClass cClass = Scene.v().getSootClassUnsafe(cSig);
//                    List<SootClass> subClasses = h.getSubclassesOf(cClass);
//                            allComponents.add(subC);
//                    continue;
//
//                String fName = f.getName();
//                    allEntryComponentFields.put(cClass, f);
//


    private void upadateTagsForInvolvedEntrypoints() {
        Map<SootClass, List<SootClass>> parentClassMap = new HashMap<>();
        Hierarchy h = Scene.v().getActiveHierarchy();
        for (SootClass cClass : allEntrypoints.keySet()) {
            List<SootClass> parents = new LinkedList<>(h.getSuperclassesOf(cClass));
            Iterator<SootClass> it = parents.iterator();
            while (it.hasNext()) {
                SootClass currentClass = it.next();
                if (AndroidEntryPointConstants.isLifecycleClass(currentClass.getName())) {
                    it.remove();
                }
            }
            if (!parents.isEmpty()) {parentClassMap.put(cClass, parents);}
        }
        for (SootClass cClass : allEntrypoints.keySet()) {
            PatternEntryData cdata = allEntrypoints.get(cClass);
            List<SootClass> parents = parentClassMap.get(cClass);
            for (Pair<String, SootMethod> pair : cdata.getEntrypoints()) {
                SootMethod currentMethod = pair.getO2();
                String methodsig = pair.getO1();
                Tag currentTag = new LCLifeCycleMethodTag(methodsig + "_" + cClass.getName());
                List<Tag> tempTags = tagsForAllEntryMethods.get(currentMethod);
                if (null == tempTags) {
                    tempTags = new LinkedList<>();
                    tagsForAllEntryMethods.put(currentMethod, tempTags);
                }
                tempTags.add(currentTag);
                if (null != parents) {
                    for (SootClass parent : parents) {
                        SootMethod parentMethod = parent.getMethodUnsafe(methodsig);
                        if (null != parentMethod) {
                            List<Tag> tempparentTags = tagsForAllEntryMethods.get(parentMethod);
                            if (null == tempparentTags) {
                                tempparentTags = new LinkedList<>();
                                tagsForAllEntryMethods.put(parentMethod, tempparentTags);
                            }
                            if (!tempparentTags.contains(currentTag))
                                tempparentTags.add(currentTag);
                        }
                    }
                }
            }
        }
    }

    private void performTagsAddition() {
        Set<String> allLCMethodSbusigs = AndroidEntryPointConstants.getAllLCMethods();
        ReachableMethods reachableMethods = Scene.v().getReachableMethods();
        reachableMethods.update();
        for (Iterator<MethodOrMethodContext> iter = reachableMethods.listener(); iter.hasNext();) {
            SootMethod sm = iter.next().method();
            if (allLCMethodSbusigs.contains(sm.getSubSignature())) {
                for (Tag tag : getDirectLCMetodTag(sm)) {
                    sm.addTag(tag);
                }
            }
        }
    }

    public List<Tag> getDirectLCMetodTag(SootMethod givenMethod) {
        List<Tag> resultTags = this.tagsForAllEntryMethods.get(givenMethod);
        if (null == resultTags){
            return this.notRelatedTagList;
        } else {
            return resultTags;
        }
    }

    public List<Tag> getLCMethodTag(SootMethod givenMethod, List<Tag> currentTags, ByReferenceBoolean shouldKill) {
        if (givenMethod.hasTag(notRelatedTagString)) {
            shouldKill.value = true;
            return null;
        }
        List<Tag> givenMethodTags = givenMethod.getTags();
        if (givenMethodTags.containsAll(currentTags)) {
            return currentTags;
        } else {
            List<Tag> givenMethodLCTags = this.tagsForAllEntryMethods.get(givenMethod);
            if (currentTags.containsAll(givenMethodLCTags)) {
                return givenMethodLCTags;
            } else {
                shouldKill.value = true;
                return null;
            }
        }

    }

    private void readEntrypointsFromPatterns() {
        allEntrypoints.clear();
        allEntryMethods.clear();
        for (PatternData pattern : currentPatterns.values()) {
            for (SootClass cClass : pattern.getInvolvedEntrypoints().keySet()) {
                PatternEntryData cData = allEntrypoints.get(cClass);
                if (cData == null) {
                    cData = new PatternEntryData(cClass);
                    allEntrypoints.put(cClass, cData);
                }
                cData.merge(pattern.getInvolvedEntrypoints().get(cClass));
            }
        }
        for (PatternEntryData entrypoints : allEntrypoints.values()) {
            allEntryMethods.addAll(entrypoints.getAllMethods());
        }
    }

    @Override
    public Set<SootMethod> getEntryMethods() {
        if (null != allEntryMethods && !allEntryMethods.isEmpty()) {
            return allEntryMethods;
        }
        readEntrypointsFromPatterns();
        return allEntryMethods;
    }

    @Override
    public MultiMap<SootClass, SootField> getEntryFields() {
        MultiMap<SootClass, SootField> fields = new HashMultiMap<>();
        for (PatternData pattern : currentPatterns.values()) {
            fields.putAll(pattern.getEntryFields());
        }
        return fields;
    }

//        return allEntryComponentFields;

    public MultiMap<SootClass, SootField> getReportEntryFields() {
        MultiMap<SootClass, SootField> fields = new HashMultiMap<>();
        for (PatternData pattern : currentPatterns.values()) {
            fields.putAll(pattern.getReportEntryFields());
        }
        return fields;
    }

    @Override
    public Map<SootClass, PatternEntryData> getInvolvedEntrypoints() {
        if (null != allEntrypoints && !allEntrypoints.isEmpty()) {
            return allEntrypoints;
        }
        readEntrypointsFromPatterns();
        return allEntrypoints;
    }

    Set<SootMethod> cannotSkilMethods = null;
    @Override
    public Set<SootMethod> getCannotSkipMethods() {
        if (null == cannotSkilMethods) {
            cannotSkilMethods = new HashSet<>();
            for (PatternData pattern : currentPatterns.values()) {
                cannotSkilMethods.addAll(pattern.getCannotSkipMethods());
            }
        }
        return cannotSkilMethods;
    }


//    private Set<Tag> allLCMethodTags = null;
//        allLCMethodTags = new HashSet();
//        Set<SootClass> allInvolvedEntrypoints = getEntrypoints();
//        //
//            List<SootClass> subs = Scene.v().getActiveHierarchy().getSubclassesOf(currentE);
//                    continue total;
//            LCLifeCycleMethodTag newTag = new LCLifeCycleMethodTag(currentE.getName());
//            Set<SootClass> allSupers = new HashSet<>();
//            allSupers.add(currentE);
//            allSupers.addAll(Scene.v().getActiveHierarchy().getSuperclassesOf(currentE));
//            tags2entrypoints.put(newTag, allSupers);
//
//        Set<SootClass> notInvolvedEntrypoints = new HashSet<>(allentrypoints);
//        notInvolvedEntrypoints.removeAll(allInvolvedEntrypoints);
//        tags2entrypoints.put(new LCLifeCycleMethodTag("none"), notInvolvedEntrypoints);
//
//        allLCMethodTags = tags2entrypoints.keySet();
//
//            Set<SootClass> classes = tags2entrypoints.get(keytak);
//                    SootMethod m = currentClass.getMethodUnsafe(subsig);
//                        m.addTag(keytak);
//        System.out.println();


//            pattern.updateInvolvedEntrypoints(allEntrypoints, null);
//


    @Override
    public void clear() {
        this.currentPatterns.clear();
        this.allEntrypoints.clear();
        this.allEntryMethods.clear();
        this.cannotSkilMethods = null;
//        PatternDataConstant.clear();
        for (PatternData pattern : currentPatterns.values()) {
            pattern.clear();
        }
    }

    public void init(String[] args, int targetSdk, int minSdk, LCResourceOPHelper ophelper, InfoflowConfiguration config){
        this.ophelper = ophelper;
        this.config = config;
        if (null != args && args.length != 0) {
            tags = args;
            for (String arg : args) {
                if (arg.equalsIgnoreCase("skip1")) {
                    currentPatterns.put(1, new PatternSkip1Data(this, "SKIP1"));
                } else if (arg.equalsIgnoreCase("swap")){
                    currentPatterns.put(2, new PatternSwap1Data(this, "SWAP", targetSdk, minSdk));
//                    currentPatterns.put(3, new Pattern3Data(config));
//                    currentPatterns.put(4, n)
                } else if (arg.equalsIgnoreCase("skip2")) {
                    currentPatterns.put(5, new PatternSkip2Data(this, "SKIP2"));
                } else {
                    System.err.println("Error: no such lifecycle error pattern " + arg);
                }
            }
        }
    }
    public Set<PatternData> getAllPatterns() {
        return new HashSet<>(currentPatterns.values());
    }

    public PatternSkip1Data getPattern1() {
        PatternData data = currentPatterns.get(1);
        return (null==data)?null:(PatternSkip1Data)data;
    }

    public boolean hasPattern1() {
        return null != currentPatterns.get(1);
    }

    public PatternSwap1Data getPattern2() {
        PatternData data = currentPatterns.get(2);
        return (null==data)?null:(PatternSwap1Data)data;
    }

    public boolean hasPattern2() {
        return null != currentPatterns.get(2);
    }
//        PatternData data = currentPatterns.get(3);
//        return (null==data)?null:(Pattern3Data)data;
//        return null != currentPatterns.get(3);

    protected InfoflowConfiguration getConfig() {
        return this.config;
    }
    protected LCResourceOPHelper getOphelper() {
        return this.ophelper;
    }

    public PatternSkip2Data getPattern5() {
        PatternData data = currentPatterns.get(5);
        return (null==data)?null:(PatternSkip2Data)data;
    }
    public boolean hasPattern5() {
        return null != currentPatterns.get(5);
    }

    public Set<SootClass> allFragments = null;
    public Set<SootField> allFragmentFields = null;
    public Set<SootClass> allActivities = null;
    public Set<SootField> allActivityFields = null;


    public void getAllFragmentsAndAllActivities() {
        allFragments = new HashSet<>();
        allFragmentFields = new HashSet<>();
        Set<SootClass> allBaseFragments = new HashSet<>();
        SootClass osClassFragment = Scene.v().getSootClassUnsafe(AndroidEntryPointConstants.FRAGMENTCLASS);
        SootClass osClassSupportFragment = Scene.v().getSootClassUnsafe(AndroidEntryPointConstants.SUPPORTFRAGMENTCLASS);
        SootClass osClassNewFragment = Scene.v().getSootClassUnsafe(AndroidEntryPointConstants.NEWFRAGMENTCLASS);
        if (null != osClassFragment) allBaseFragments.add(osClassFragment);
        if (null != osClassSupportFragment) allBaseFragments.add(osClassSupportFragment);
        if (null != osClassNewFragment) allBaseFragments.add(osClassNewFragment);
        Hierarchy h = Scene.v().getActiveHierarchy();
        for (SootClass baseFragment : allBaseFragments) {
            for (SootClass tempFragment : h.getSubclassesOf(baseFragment)) {
                if (!tempFragment.getPackageName().startsWith("androidx.fragment") && !tempFragment.getPackageName().startsWith("android.app")
                        && !tempFragment.getPackageName().startsWith("android.preference") && !tempFragment.getPackageName().startsWith("androidx.preference")) {
                    t : for (SootField f : tempFragment.getFields()) {
                        String name = f.getName();
                        for (String cusName: toSkipFieldNames) {
                            if (name.equals(cusName)) {
                                continue t;
                            }
                        }
                        allFragmentFields.add(f);
                    }
                    allFragments.add(tempFragment);
                }
            }
        }



        allActivities = new HashSet<>();
        allActivityFields = new HashSet<>();
        Set<SootClass> allBaseActivites = new HashSet<>();
        SootClass osClassActivity = Scene.v().getSootClassUnsafe(ACTIVITYCLASS);
        SootClass osClassAppcomActivity = Scene.v().getSootClassUnsafe(APPCOMPATACTIVITYCLASS_ANDROIDX);
        SootClass osClassNewActivity = Scene.v().getSootClassUnsafe(NEWAPPCOMPATACTIVITYCLASS);
        SootClass osClassV4Activity = Scene.v().getSootClassUnsafe(APPCOMPATACTIVITYCLASS_V4);
        SootClass osClassV7Activity = Scene.v().getSootClassUnsafe(APPCOMPATACTIVITYCLASS_V7);
        if (null != osClassActivity) allBaseActivites.add(osClassActivity);
        if (null != osClassAppcomActivity) allBaseActivites.add(osClassAppcomActivity);
        if (null != osClassNewActivity) allBaseActivites.add(osClassNewActivity);
        if (null != osClassV4Activity) allBaseActivites.add(osClassV4Activity);
        if (null != osClassV7Activity) allBaseActivites.add(osClassV7Activity);

        for (SootClass baseActivity : allBaseActivites) {
            try {
                for (SootClass tempActivity : h.getSubclassesOf(baseActivity)) {
                    if (!tempActivity.getPackageName().startsWith("androidx.") && !tempActivity.getPackageName().startsWith("android.app") && !tempActivity.getPackageName().startsWith("android.support")
                            && !tempActivity.getPackageName().startsWith("android.preference") && !tempActivity.getPackageName().startsWith("androidx.preference")) {
                        t : for (SootField f : tempActivity.getFields()) {
                            String name = f.getName();
                            for (String cusName: toSkipFieldNames) {
                                if (name.equals(cusName)) {
                                    continue t;
                                }
                            }
                            allActivityFields.add(f);
                        }
                        allActivities.add(tempActivity);
                    }
                }
            } catch (NullPointerException e) {
                System.out.println("no activity extends:" + baseActivity);
            }

        }
    }
}
