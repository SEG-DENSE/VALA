package soot.jimple.infoflow.pattern.patterndata;

import heros.solver.Pair;
import soot.*;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;

import java.util.*;

public class PatternSkip2Data extends PatternData {
    public PatternSkip2Data(PatternDataHelper helper, String title){
        super(helper, title);
        this.isForResourceLeak = true;
    }
    protected Set<SootMethod> cannotSkipMethods = null;
    @Override
    protected Map<SootClass, PatternEntryData> getInitialEntryClasses(Set<SootClass> allEntrypoints, IInfoflowCFG icfg) {
        Set<SootMethod> setRetainMethods = new HashSet<>();
        SootMethod setRetainMethod = null;
        try {
            setRetainMethod = Scene.v().getMethod(PatternDataConstant.SETRETAININSTANCESIG1);
            if (null != setRetainMethod) setRetainMethods.add(setRetainMethod);
        } catch (RuntimeException e) {
            setRetainMethod = null;
        }
        try {
            setRetainMethod = Scene.v().getMethod(PatternDataConstant.SETRETAININSTANCESIG2);
            if (null != setRetainMethod) setRetainMethods.add(setRetainMethod);
        } catch (RuntimeException e) {
            setRetainMethod = null;
        }
        if (setRetainMethods.isEmpty()) {
            return Collections.EMPTY_MAP;
        }
        Set<SootMethod> callSetRetainMethods = new HashSet<>();
        Collection<Unit> setRetainInvocations = new HashSet<>();
        for (SootMethod m : setRetainMethods) {
            setRetainInvocations.addAll(icfg.getCallersOf(m));
        }
        for (Unit u : setRetainInvocations) {
            callSetRetainMethods.add(icfg.getMethodOf(u));
        }

        Stack<SootMethod> currentCallingMs = new Stack<>();
        currentCallingMs.addAll(callSetRetainMethods);
        while (!currentCallingMs.isEmpty()) {
            SootMethod cM = currentCallingMs.pop();
            Collection<Unit> invocations = icfg.getCallersOf(cM);
            for (Unit u : invocations) {
                SootMethod m  = icfg.getMethodOf(u);
                if (!callSetRetainMethods.contains(m)) {
                    callSetRetainMethods.add(m);
                    currentCallingMs.add(m);
                }
            }
        }
        cannotSkipMethods = new HashSet<>();
        cannotSkipMethods.addAll(callSetRetainMethods);
        Map<SootClass, PatternEntryData> initalEntrypoints = new HashMap<>();
        for (SootMethod m : callSetRetainMethods) {
            String setRetainTag = m.getSubSignature();
            if (setRetainTag.equals(PatternDataConstant.FRAGMENT_ONCREATE) || setRetainTag.equals(PatternDataConstant.FRAGMENT_ONATTACH) ||
                setRetainTag.equals(PatternDataConstant.FRAGMENT_ONCREATEVIEW) || setRetainTag.equals(PatternDataConstant.FRAGMENT_ONVIEWCREATED) ||
                    setRetainTag.equals(PatternDataConstant.FRAGMENT_ONACTIVITYCREATED)) {
                SootClass cClass = m.getDeclaringClass();
                Set<SootClass> allClassToCheck = new HashSet<>();
                allClassToCheck.addAll(Scene.v().getActiveHierarchy().getSubclassesOfIncluding(cClass));
                for (SootClass tClass : allClassToCheck) {
                    PatternEntryData tData = initalEntrypoints.get(tClass);
                    if (null == tData) {
                        tData = new PatternEntryData(tClass);
                    }
                    if (!updateEntryDataWithLCMethods(tClass, tData)) {
                        continue;
                    }
                    updateInvolvedFieldsInEntryDatas(icfg, tClass, tData);
                    initalEntrypoints.put(tClass, tData);
                }
            }
        }
        return  initalEntrypoints;
    }

    @Override
    protected boolean updateEntryDataWithLCMethods(SootClass cClass, PatternEntryData cData) {
        String[] methodsigs = new String[] {PatternDataConstant.FRAGMENT_ONDESTROY};
        for (String methodsig : methodsigs) {
            SootMethod lcmethod = cClass.getMethodUnsafe(methodsig);
            if (null != lcmethod) {
                Pair<String, SootMethod> pair = new Pair<>(methodsig, lcmethod);
                cData.add(pair);
            } else {
                return false;
            }
        }
        String[] extrmethodsigs = new String[] {PatternDataConstant.FRAGMENT_ONCREATEVIEW, PatternDataConstant.FRAGMENT_ONVIEWCREATED,
                PatternDataConstant.FRAGMENT_ONSTART};
        boolean containsCreate = false;
        for (String methodsig : extrmethodsigs) {
            SootMethod lcmethod = cClass.getMethodUnsafe(methodsig);
            if (null != lcmethod) {
                Pair<String, SootMethod> pair = new Pair<>(methodsig, lcmethod);
                cData.addExtra(pair);
                containsCreate = true;
            }
        }
        if (!containsCreate) {
            return false;
        }
        return true;
    }

}
