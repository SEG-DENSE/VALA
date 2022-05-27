package soot.jimple.infoflow.pattern.entryPointCreators;

import heros.solver.Pair;
import soot.Hierarchy;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.util.*;

public class PatternMainEntryPointCreator {
    protected Map<SootClass, PatternActivityBaseEntryPointCreator> creators = null;
    protected Set<Pair<SootMethod, SootMethod>> entrypoints = null; 
    protected AndroidEntryPointUtils entryPointUtils = null;
    protected String applicationName = null;
    protected SootClass applicationClass = null;
    protected Set<SootClass> allComponents = null;
    protected MultiMap<SootClass, SootMethod> callbackFunctions = new HashMultiMap<>();
    private MultiMap<SootClass, String> activityLifecycleCallbacks = new HashMultiMap<>();
    protected boolean fragmentsInstead = false;

    public void resetAndRemoveAllGeneratedClasses() {
        if (!entrypoints.isEmpty()) {
            for (Pair<SootMethod, SootMethod> pair : entrypoints) {
                SootMethod generatedMethod = pair.getO2();
                generatedMethod.getDeclaringClass().removeMethod(generatedMethod);
            }
            entrypoints.clear();
        }
        allComponents.clear();
        callbackFunctions.clear();
        activityLifecycleCallbacks.clear();
        creators.clear();
    }

    public PatternMainEntryPointCreator(Set<SootClass> allComponents, boolean fragmentsInstead) {
        creators = new HashMap<>();
        this.allComponents = allComponents;
        entryPointUtils = new AndroidEntryPointUtils();
        this.fragmentsInstead = fragmentsInstead;

    }

    public Set<Pair<SootMethod, SootMethod>> create() {
        entrypoints = new HashSet<>();
        if (this.fragmentsInstead) {
            entrypoints.addAll(createForFragments());
        }
            entrypoints.addAll(createActivity());
        return  entrypoints;
    }
        public Set<Pair<SootMethod, SootMethod>> createActivity() {
        Set<Pair<SootMethod, SootMethod>> activityEntrypoints = new HashSet<>();
        initializeApplicationClass();
        for (SootClass c : allComponents) {
            AndroidEntryPointUtils.ComponentType componentType = entryPointUtils.getComponentType(c);
            switch (componentType) {
                case Activity : {
                    PatternActivityBaseEntryPointCreator subCreator = new PatternActivityBaseEntryPointCreator(c, activityLifecycleCallbacks);
                    activityEntrypoints.addAll(subCreator.createMainMethods());
                    break;
                }
                default:{
                    break;
                }
            }

        }
        return activityEntrypoints;
    }

    public Set<Pair<SootMethod, SootMethod>> createForFragments() {
        Set<Pair<SootMethod, SootMethod>> fragmentsEntrypoints = new HashSet<>();
        Set<SootClass> allBaseFragments = new HashSet<>();
        Set<SootClass> allFragments = new HashSet<>();
        SootClass osClassFragment = Scene.v().getSootClassUnsafe(AndroidEntryPointConstants.FRAGMENTCLASS);
        SootClass osClassSupportFragment = Scene.v().getSootClassUnsafe(AndroidEntryPointConstants.SUPPORTFRAGMENTCLASS);
        SootClass osClassNewFragment = Scene.v().getSootClassUnsafe(AndroidEntryPointConstants.NEWFRAGMENTCLASS);
        if (null != osClassFragment) allBaseFragments.add(osClassFragment);
        if (null != osClassSupportFragment) allBaseFragments.add(osClassSupportFragment);
        if (null != osClassNewFragment) allBaseFragments.add(osClassNewFragment);
        if (allBaseFragments.isEmpty()) {return fragmentsEntrypoints;}
        Hierarchy h = Scene.v().getActiveHierarchy();
        if (null == h) {return fragmentsEntrypoints;}
        for (SootClass baseFragment : allBaseFragments) {
            for (SootClass tempFragment : h.getSubclassesOf(baseFragment)) {
                if (!tempFragment.getPackageName().startsWith("androidx.fragment") && !tempFragment.getPackageName().startsWith("android.app")
                        && !tempFragment.getPackageName().startsWith("android.preference") && !tempFragment.getPackageName().startsWith("androidx.preference")) {
                    allFragments.add(tempFragment);
                }
            }
        }
        for (SootClass fragment : allFragments) {
            PatternFragmentBaseEntryPointCreator subCreator = new PatternFragmentBaseEntryPointCreator(fragment);
            fragmentsEntrypoints.addAll(subCreator.createMainMethods());
        }
        return fragmentsEntrypoints;
    }

    public void initializeApplicationClass() {

        if (applicationName == null || applicationName.isEmpty())
            return;
        // Find the application class
        for (SootClass currentClass : allComponents) {
            // Is this the application class?
            if (entryPointUtils.isApplicationClass(currentClass) && currentClass.getName().equals(applicationName)) {
                if (applicationClass != null && currentClass != applicationClass)
                    throw new RuntimeException("Multiple application classes in app");
                applicationClass = currentClass;
                break;
            }
        }

        // We can only look for callbacks if we have an application class
        if (applicationClass == null)
            return;

        // Look into the application class' callbacks
        SootClass scActCallbacks = Scene.v()
                .getSootClassUnsafe(AndroidEntryPointConstants.ACTIVITYLIFECYCLECALLBACKSINTERFACE);
        Collection<SootMethod> callbacks = callbackFunctions.get(applicationClass);
        if (callbacks != null) {
            for (SootMethod smCallback : callbacks) {
                // Is this a special callback class? We have callbacks that model activity
                // lifecycle events and ones that model generic events (e.g., low memory)
                if (scActCallbacks != null && Scene.v().getOrMakeFastHierarchy()
                        .canStoreType(smCallback.getDeclaringClass().getType(), scActCallbacks.getType()))
                    activityLifecycleCallbacks.put(smCallback.getDeclaringClass(), smCallback.getSignature());
//                else
//                    applicationCallbackClasses.put(smCallback.getDeclaringClass(), smCallback.getSignature());
            }
        }
    }

    public void setCallbackFunctions(MultiMap<SootClass, SootMethod> callbackFunctions) {
        this.callbackFunctions = callbackFunctions;
    }

//        Set<SootClass> allPatternRelatedActivities = patternComponents.keySet();
//        Set<Pair<SootMethod, SootMethod>> newEntrypoints = new HashSet<>();
//            SootMethod lcmethod = pair.getO1();
//            SootMethod lcmainmethod = pair.getO2();
//                newEntrypoints.add(pair);
//                lcmainmethod.getDeclaringClass().removeMethod(lcmainmethod);
}
