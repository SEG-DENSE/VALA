package soot.jimple.infoflow.pattern.patterndata;

import heros.solver.Pair;
import soot.*;
import soot.jimple.FieldRef;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOP;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPList;
import soot.jimple.infoflow.pattern.result.LCMethodSummaryResult;
import soot.jimple.infoflow.pattern.result.XmlConstants;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;
import soot.util.Chain;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.util.*;

public abstract class PatternData implements PatternInterface{
    protected BiDiInterproceduralCFG<Unit, SootMethod> icfg = null;
    protected Map<SootClass, PatternEntryData> involvedEntrypoints = null;
    protected Map<SootClass, PatternEntryData> initialInvolvedEntrypoints = null;
    protected MultiMap<SootClass, SootField> reportEntrypoints = null;
    protected String title = null;
    protected PatternDataHelper helper = null;
    protected boolean isForResourceLeak = false;
    protected boolean isForDataLoss = false;

    public PatternData(PatternDataHelper helper, String title) {
        this.title = title;
        this.helper = helper;
        this.initialInvolvedEntrypoints = new HashMap<>();
        this.involvedEntrypoints = new HashMap<>();
        this.reportEntrypoints = new HashMultiMap<>();
    }

    public String getPatternTitle() {
        return this.title;
    }
    public Map<SootClass, PatternEntryData> getInvolvedEntrypoints() {
        return this.involvedEntrypoints;
    }
    @Override
    public Set<SootMethod> getEntryMethods() {
        Set<SootMethod> entrymethods = new HashSet<>();
        if (!involvedEntrypoints.isEmpty()) {
            for (PatternEntryData data : involvedEntrypoints.values()) {
                entrymethods.addAll(data.getAllMethods());
            }
        }
        return entrymethods;
    }


    @Override
    public void updateInvolvedEntrypoints(Set<SootClass> allEntrypoints, IInfoflowCFG icfg) {
        this.involvedEntrypoints.clear();
        this.initialInvolvedEntrypoints.clear();
        if (null == icfg) {
            throw new RuntimeException("CFG was not constructed at Pattern Adaption!");
        }

        this.initialInvolvedEntrypoints = getInitialEntryClasses(allEntrypoints, icfg);
        Set<SootClass> tempKeys = new HashSet<>(this.initialInvolvedEntrypoints.keySet());
        for (SootClass tempKey : tempKeys) {
            if (tempKey.getName().startsWith("androidx.appcompat.app.") || tempKey.getName().startsWith("androidx.fragment.app.")) {
                this.initialInvolvedEntrypoints.remove(tempKey);
            }
        }

        this.involvedEntrypoints.putAll(this.initialInvolvedEntrypoints);
        for (SootClass initalClass : this.initialInvolvedEntrypoints.keySet()) {
            Hierarchy h = Scene.v().getActiveHierarchy();
//                    this.involvedEntrypoints.put(impleClass, methodName);
//                        this.involvedEntrypoints.put(impleClass, methodName);
            if (initalClass.isConcrete() || initalClass.isAbstract()) {
                for (SootClass subClass : h.getSubclassesOf(initalClass)) {
                    PatternEntryData cData = involvedEntrypoints.get(subClass);
                    if (null == cData) {
                        cData = new PatternEntryData(subClass);
                    }
                    updateEntryDataWithLCMethods(subClass, cData);
                    updateInvolvedFieldsInEntryDatas(icfg, subClass, cData);
                    this.involvedEntrypoints.put(subClass, cData);
                }
            }
        }
        for (SootClass cClass : this.involvedEntrypoints.keySet()) {
            t: for (SootField f : cClass.getFields()) {
                String fName = f.getName();
                for (String t : toSkipFieldNames) {
                    if (fName.equals(t)) {
                        continue t;
                    }
                }
                for (SootClass parent : Scene.v().getActiveHierarchy().getSuperclassesOf(cClass)) {
                    if (null != parent.getFieldByNameUnsafe(fName)) {
                        continue t;
                    }
                }
                this.reportEntrypoints.put(cClass, f);
            }
        }

        Map<SootClass, PatternEntryData> tempMap = new HashMap<>();
        Set<SootClass> keys = this.involvedEntrypoints.keySet();
        for (SootClass key : keys) {
            PatternEntryData data = this.involvedEntrypoints.get(key);
            if (!data.hasNotInvolvedFields()) {
                tempMap.put(key, data);
            }
        }
        this.involvedEntrypoints = tempMap;
    }
    protected abstract Map<SootClass, PatternEntryData> getInitialEntryClasses(Set<SootClass> allEntrypoints, IInfoflowCFG icfg);
    protected Set<SootField> removeFieldsAccordingToTypes(Set<SootField> initialInvolvedFields) {
        Iterator<SootField> it = initialInvolvedFields.iterator();
        while(it.hasNext()) {
            SootField f = it.next();
            String fieldname = f.getName();
            for (String toSkipName : toSkipFieldNames) {
                if (toSkipName.equals(fieldname)) {
                    it.remove();
                    break;
                }
            }
        }
        for (PatternData pattern : helper.currentPatterns.values()) {
            if (pattern.isForResourceLeak) {
                initialInvolvedFields = removeFieldsWithoutOPs(initialInvolvedFields);
                break;
            }
        }
        return initialInvolvedFields;
    }

    protected Set<SootField> removeFieldsWithoutOPs(Set<SootField> initialInvolvedFields) {
        Set<Type> allOPTypes = helper.getOphelper().getInvolvedTypesAccordingToPattern(this.helper.getAllPatterns());
        boolean shouldTaintSubFieldsAndChildren = helper.getConfig().shouldTaintSubfieldsAndChildren();
        Set<SootField> newFields = new HashSet<SootField>();
        for (SootField f : initialInvolvedFields) {
            Set<Type> allTypesToTest = new HashSet<>();
            allTypesToTest.add(f.getType());
            if (shouldTaintSubFieldsAndChildren) {
                for (SootField subF : getAllSubFieldsIt(f)) {
                    allTypesToTest.add(subF.getType());
                }
            }
            boolean containedAtLeastOneOp = false;
            for (Type t : allTypesToTest) {
                if (isTypeContained(t, allOPTypes)) {
                    containedAtLeastOneOp = true;
                    break;
                }
            }
            if (containedAtLeastOneOp) {
                newFields.add(f);
            }
        }

        return newFields;
    }
    private boolean isTypeContained(Type givenT, Set<Type> typeSet) {
        if (typeSet.contains(givenT)) return true;
        for (Type t : typeSet) {
            if (givenT instanceof RefType && t instanceof RefType) {
                SootClass c1 = ((RefType) givenT).getSootClass();
                SootClass c2 = ((RefType) t).getSootClass();
                if (c1.isInterface() && c2.isInterface() && Scene.v().getActiveHierarchy().isInterfaceSubinterfaceOf(c1, c2))
                    return true;
                if (!c1.isInterface() && Scene.v().getActiveHierarchy().isClassSubclassOf(c1, c2)){
                    return true;
                }
            }
        }

        return false;
    }

    protected Set<SootMethod> getCurrentAndSuperMethods(SootClass currentClass) {
        Hierarchy h = Scene.v().getActiveHierarchy();
        List<SootClass> allSuperClasses = null;
        if (currentClass.isInterface()) {
            allSuperClasses = h.getSuperinterfacesOfIncluding(currentClass);
        } else {
            allSuperClasses = h.getSuperclassesOfIncluding(currentClass);
        }
        Set<SootMethod> allSuperMethods = new HashSet<>();
        for (SootClass c : allSuperClasses) {
            allSuperMethods.addAll(c.getMethods());
        }
        return allSuperMethods;
    }

    protected Set<SootField> getAllSubFieldsIt(SootField f) {
        Set<SootField> allSubFields = new HashSet<>();
        Stack<SootField> stackedSubFields = new Stack<>();
        stackedSubFields.add(f);
        while (!stackedSubFields.isEmpty()) {
            SootField currentF = stackedSubFields.pop();
            Type t = currentF.getType();
            if (t instanceof RefType) {
                SootClass typeClass = ((RefType) t).getSootClass();
                for (SootField innerF : typeClass.getFields()) {
                    if (!allSubFields.contains(innerF)) {
                        allSubFields.add(innerF);
                        stackedSubFields.add(innerF);
                    }
                }
            }

        }
        return allSubFields;
    }


    protected abstract boolean updateEntryDataWithLCMethods(SootClass cClass, PatternEntryData cData);
    protected boolean updateEntryDataWithLCMethods_Common(SootClass cClass, String[] methodsigs, PatternEntryData cData) {
        for (String methodsig : methodsigs) {
            SootMethod lcmethod = cClass.getMethodUnsafe(methodsig);
            if (null != lcmethod) {
                Pair<String, SootMethod> pair = new Pair<>(methodsig, lcmethod);
                cData.add(pair);
            } else {
                return false;
            }
        }
        return true;
    }
    protected boolean updateInvolvedFieldsInEntryDatas(IInfoflowCFG icfg, SootClass entryClass, PatternEntryData entryData) {
        Chain<SootField> fields =  entryClass.getFields();
        Set<SootField> involvedFields = new HashSet<>();
        for (SootField f : fields) {
            involvedFields.add(f);
        }
        for (SootMethod m : entryData.getMethods()) {
            involvedFields = getUsedFieldInsideMethod(icfg, m, involvedFields);
            if (involvedFields.isEmpty()){break;}
        }
        involvedFields = removeFieldsAccordingToTypes(involvedFields);

        if (involvedFields.isEmpty()) {
            return false;
        } else {
            entryData.updateInvolvedFields(involvedFields);
            return true;
        }
    }

    protected Set<SootField> getUsedFieldInsideMethod(IInfoflowCFG icfg, SootMethod m, Set<SootField> componentFields) {
        if (componentFields.isEmpty()){return componentFields;}

        Stack<SootMethod> currentCalledMethods = new Stack<>();
        Set<SootMethod> allCalledMethods = new HashSet<>();
        Set<SootField> allUsedFields = new HashSet<>();
        currentCalledMethods.add(m);
        allCalledMethods.add(m);
        while (!currentCalledMethods.isEmpty()) {
            SootMethod currentM = currentCalledMethods.pop();
            if (currentM.hasActiveBody()) {
                for (Unit s : currentM.getActiveBody().getUnits()) {
                    for (ValueBox box : s.getUseAndDefBoxes()) {
                        Value v = box.getValue();
                        if (v instanceof FieldRef){
                            SootField f = ((FieldRef) v).getField();
                            if (componentFields.contains(f)) {
                                allUsedFields.add(f);
                            }
                        } else if (s instanceof Stmt && ((Stmt)s).containsInvokeExpr()) {
                            Collection<SootMethod> invokedMethods = icfg.getCalleesOfCallAt(s);
                            for (SootMethod innerm : invokedMethods) {
                                if (!allCalledMethods.contains(innerm)) {
                                    allCalledMethods.add(innerm);
                                    currentCalledMethods.add(innerm);
                                }
                            }
                        }
                    }
                }
            }
        }
        return allUsedFields;
    }
    public void clear() {
        this.involvedEntrypoints.clear();
        this.initialInvolvedEntrypoints.clear();
    }
    @Override
    public MultiMap<SootClass, SootField> getEntryFields() {
        MultiMap<SootClass, SootField> entryfields = new HashMultiMap<>();
        for (SootClass entryClass : this.involvedEntrypoints.keySet()) {
            entryfields.putAll(entryClass, this.involvedEntrypoints.get(entryClass).getInvolvedFields());
        }
        return entryfields;
    }

    public MultiMap<SootClass, SootField> getReportEntryFields() {
        return this.reportEntrypoints;
    }

    public Set<SootMethod> getCannotSkipMethods(){
        return Collections.EMPTY_SET;
    }


    public void writeExtraResult(XMLStreamWriter writer) throws XMLStreamException {}

    public boolean isForResourceLeak() {
        return this.isForResourceLeak;
    }
    public boolean isForDataLoss() {
        return this.isForDataLoss;
    }
}
