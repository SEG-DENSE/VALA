package soot.jimple.infoflow.pattern.result;

import soot.SootField;
import soot.SootMethod;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPList;
import soot.util.MultiMap;

import java.util.LinkedList;
import java.util.Set;

public class LCMethodOpSummary {
    protected SootMethod lcmethod = null;
    protected Set<SootField> fields = null;
    protected MultiMap<SootField, LCResourceOPList> paths = null;
}
