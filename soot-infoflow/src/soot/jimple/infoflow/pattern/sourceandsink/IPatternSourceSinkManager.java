package soot.jimple.infoflow.pattern.sourceandsink;

import soot.SootClass;
import soot.SootField;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowManager;
import soot.jimple.infoflow.sourcesSinks.manager.SinkInfo;
import soot.jimple.infoflow.sourcesSinks.manager.SourceInfo;
import soot.util.MultiMap;

import java.util.Set;

public interface IPatternSourceSinkManager {
    void initialize();

    SourceInfo getSourceInfo(Stmt sCallSite, PatternInfoflowManager manager, SootClass entryClass);

    SourceInfo createSourceInfo(Stmt sCallSite, PatternInfoflowManager manager);

    SinkInfo getSinkInfo(Stmt sCallSite, PatternInfoflowManager manager, AccessPath ap);

    void updateStaticFields(Stmt s);

}
