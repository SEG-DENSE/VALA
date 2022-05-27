package soot.jimple.infoflow.pattern.patterndata;

import heros.solver.Pair;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.util.MultiMap;

import java.util.Map;
import java.util.Set;

public interface PatternInterface {
    void updateInvolvedEntrypoints(Set<SootClass> allEntrypoints, IInfoflowCFG nicfg);  
    Set<SootMethod> getEntryMethods();
    MultiMap<SootClass, SootField> getEntryFields();
    MultiMap<SootClass, SootField> getReportEntryFields();
    Map<SootClass, PatternEntryData> getInvolvedEntrypoints();
    Set<SootMethod> getCannotSkipMethods();
    void clear();
    static final String[] toSkipFieldNames = {"ipcResultIntent", "ipcIntent"};


}
