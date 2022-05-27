package soot.jimple.infoflow.pattern.sourceandsink;

import javafx.util.Pair;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.pattern.patterndata.PatternEntryData;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowManager;
import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkDefinition;
import soot.jimple.infoflow.sourcesSinks.manager.IOneSourceAtATimeManager;
import soot.jimple.infoflow.sourcesSinks.manager.SinkInfo;
import soot.jimple.infoflow.sourcesSinks.manager.SourceInfo;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.util.*;

public class PatternSourceSinkManager implements IPatternSourceSinkManager, IOneSourceAtATimeManager {
    protected String appPackageName = null;
    protected Map<SootClass, PatternEntryData> entrypoints = null;
    protected MultiMap<SootClass, SootField> entryFields = null;
    protected Set<SootClass> allEntryClasses = null;
//    protected SootClass currentClass = null;
    protected Set<SootField> allActivityFields = null; 
    protected Set<SootField> allStaticFields = null;

    protected Map<SootField, SourceSinkDefinition> sources;
    protected Map<Stmt, SourceInfo> sourceInfos;
    //below for onesourceatatime
    protected boolean oneSourceAtATime = false;
    protected MultiMap<SootClass, SootField> currentEntryFields = null;
    protected Stack<Pair<SootClass, SootField>> restEntryFields = null;

    public PatternSourceSinkManager( Map<SootClass, PatternEntryData> entrypoints, MultiMap<SootClass, SootField> entryFields) {
        this.entrypoints = entrypoints;
        this.entryFields = entryFields;
    }

    @Override
    public void initialize() {
        allEntryClasses = new HashSet<>();
        allEntryClasses.addAll(this.entrypoints.keySet());
        allActivityFields = new HashSet<>();
        sources = new HashMap<>();
        sourceInfos = new HashMap<>();
        for (SootClass cClass : allEntryClasses) {
            allActivityFields.addAll(cClass.getFields());
        }
        allStaticFields = new HashSet<>();


        if (oneSourceAtATime) {
            restEntryFields = new Stack<>();
            for (SootClass c : entryFields.keySet()) {
                Set<SootField> fields = entryFields.get(c);
                for (SootField f : fields) {
                    restEntryFields.push(new Pair<>(c, f));
                }
            }
            currentEntryFields = null;
        } else {
            currentEntryFields = new HashMultiMap<>(this.entryFields);
        }

    }

    @Override
    public SourceInfo createSourceInfo(Stmt sCallSite, PatternInfoflowManager manager) {
        Set<SootClass> tEntryClasses = currentEntryFields.keySet();
        Set<SootField> tEntryFields = currentEntryFields.values();
        SourceSinkDefinition def = createSource(sCallSite, tEntryClasses, tEntryFields);
        return createSourceInfo(sCallSite, manager, def);
    }

    @Override  
    public SourceInfo getSourceInfo(Stmt sCallSite, PatternInfoflowManager manager, SootClass entryClass) {
        SourceSinkDefinition def = getSource(sCallSite, entryClass);
        return getSourceInfo(sCallSite, def);
    }

    @Override
    public SinkInfo getSinkInfo(Stmt sCallSite, PatternInfoflowManager manager, AccessPath ap) {
        return null;
    }


    @Override
    public void updateStaticFields(Stmt s) {
        SourceSinkDefinition def = getSource(s, null);
        if (null != def && def instanceof  PatternFieldSourceSinkDefinition && ((PatternFieldSourceSinkDefinition) def).isStatic()) {
            allStaticFields.add(((PatternFieldSourceSinkDefinition) def).getField());
        }
    }


    public void setAppPackageName(String appPackageName) {
        this.appPackageName = appPackageName;
    }

    protected SourceSinkDefinition getSource(Stmt sCallSite, SootClass entryClass) {
        if (!(sCallSite instanceof AssignStmt) || sCallSite.containsInvokeExpr()) {
            return null;
        }
        SootField f = null;
        Value rop = ((AssignStmt) sCallSite).getRightOp();
        if (rop instanceof InstanceFieldRef || rop instanceof StaticFieldRef) {
            f = ((FieldRef) rop).getField();
        }
        Value lop = ((AssignStmt) sCallSite).getLeftOp();
        if (lop instanceof InstanceFieldRef || lop instanceof StaticFieldRef) {
            f = ((FieldRef) lop).getField();
        }
        if (null != entryClass) {
            SootClass fieldClass = f.getDeclaringClass();
            if (fieldClass != entryClass && !Scene.v().getActiveHierarchy().isClassSubclassOf(fieldClass, entryClass)
                                       && !Scene.v().getActiveHierarchy().isClassSubclassOf(entryClass, fieldClass))
                return null;
        }

        {
            SourceSinkDefinition def = sources.get(f);
            if (def != null)
                return def;
        }
        return null;
    }

    protected SourceSinkDefinition createSource(Stmt sCallSite, Set<SootClass> entrypoints, Set<SootField> involvedFields) {
        SourceSinkDefinition def = getSource(sCallSite, null);
        if (null != def) {
            return def;
        }
        if (!(sCallSite instanceof  AssignStmt)) {
            return null;
        }

        AssignStmt ass = (AssignStmt)sCallSite;
        Value rop = ass.getRightOp();
        if (rop instanceof InstanceFieldRef || rop instanceof StaticFieldRef) {
            SootField f = ((FieldRef) rop).getField();
            if (!involvedFields.contains(f)) {
                return null;
            }
            if (!entrypoints.contains(f.getDeclaringClass())) {
                return null;
            }
            SourceSinkDefinition newdef = new PatternFieldSourceSinkDefinition(f, rop instanceof  StaticFieldRef);
            sources.put(f, newdef);
            return newdef;
        }
        //a.field = r0;
        //r0.save
        Value lop = ass.getLeftOp();
        if (lop instanceof InstanceFieldRef || lop instanceof StaticFieldRef) {
            SootField f = ((FieldRef) lop).getField();
            if (!involvedFields.contains(f)) {
                return null;
            }
            if (!entrypoints.contains(f.getDeclaringClass())) {
                return null;
            }
            SourceSinkDefinition newdef = new PatternFieldSourceSinkDefinition(((FieldRef) lop).getField(), lop instanceof  StaticFieldRef);
            sources.put(f, newdef);
            return newdef;
        }
        return null;
    }

    protected SourceInfo getSourceInfo(Stmt sCallSite, SourceSinkDefinition def) {
        if (def == null)
            return null;
        SourceInfo info = sourceInfos.get(sCallSite);
        return info;
    }

    protected SourceInfo createSourceInfo(Stmt sCallSite, PatternInfoflowManager manager, SourceSinkDefinition def) {
        if (!(sCallSite instanceof AssignStmt)) {
            return null;
        }
        SourceInfo info = getSourceInfo(sCallSite, def);
        if (null != info) {
            return info;
        }
        boolean shouldtaintsubfields = manager.getConfig().shouldTaintSubfieldsAndChildren();

        if (def instanceof PatternFieldSourceSinkDefinition) {
            DefinitionStmt defStmt = (DefinitionStmt) sCallSite;
            Set<AccessPath> aps = new HashSet<>();
            Value leftop = defStmt.getLeftOp();
            aps.add(manager.getAccessPathFactory().createAccessPath(leftop, null,
                    null, null, shouldtaintsubfields, false, true, AccessPath.ArrayTaintType.ContentsAndLength, false));
            Value rightop = defStmt.getRightOp();
            AccessPath rightAp = null;
            if (rightop instanceof  Local || rightop instanceof FieldRef) {
                rightAp = manager.getAccessPathFactory().createAccessPath(rightop, null,
                        null, null, shouldtaintsubfields, false, true, AccessPath.ArrayTaintType.ContentsAndLength, false);
                aps.add(rightAp);
                if (rightAp instanceof Local) {

                }
            }
            PatternSourceInfo newsourceinfo = new PatternSourceInfo(def, aps);
            if (null != rightAp && rightop instanceof Local) {
                newsourceinfo.setApToSlice(rightAp);
            }
            sourceInfos.put(sCallSite, newsourceinfo);
            return newsourceinfo;
        }
        return null;

    }

    @Override
    public void setOneSourceAtATimeEnabled(boolean enabled) {
        this.oneSourceAtATime = enabled;
    }

    @Override
    public boolean isOneSourceAtATimeEnabled() {
        return this.oneSourceAtATime;
    }

    @Override
    public void nextSource() {
        Pair<SootClass, SootField> pair = restEntryFields.pop();
        System.out.println(this.getClass().getName() + ": current Source:" + pair.getKey().getName() + "   " + pair.getValue().getName());
        currentEntryFields = new HashMultiMap<>();
        currentEntryFields.put(pair.getKey(), pair.getValue());
    }

    @Override
    public boolean hasNextSource() {
        return !restEntryFields.isEmpty();
    }

    @Override
    public SootField getCurrentSourceField() {
        for (SootField f: currentEntryFields.values()) {
            return f;
        }
        return null;
    }
}
