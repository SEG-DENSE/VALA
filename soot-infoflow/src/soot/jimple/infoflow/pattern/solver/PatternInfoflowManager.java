package soot.jimple.infoflow.pattern.solver;

import soot.FastHierarchy;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.data.AccessPathFactory;
import soot.jimple.infoflow.memory.IMemoryBoundedSolver;
import soot.jimple.infoflow.pattern.alias.PatternAliasing;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPHelper;
import soot.jimple.infoflow.pattern.result.LCMethodSummaryResult;
import soot.jimple.infoflow.pattern.sourceandsink.IPatternSourceSinkManager;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.util.TypeUtils;

public class PatternInfoflowManager {
    private final InfoflowConfiguration config;
    private NormalSolver forwardSolver;
    private final IInfoflowCFG icfg;
    private final IPatternSourceSinkManager sourceSinkManager;
    private final TypeUtils typeUtils;
    private final FastHierarchy hierarchy;
    private final AccessPathFactory accessPathFactory;
    private PatternAliasing aliasing;
    private LCResourceOPHelper ophelper;
    private LCMethodSummaryResult result;

    public PatternInfoflowManager(InfoflowConfiguration config, NormalSolver forwardSolver, IInfoflowCFG icfg, IPatternSourceSinkManager sourceSinkManager, LCResourceOPHelper ophelper,
                                  FastHierarchy hierarchy, AccessPathFactory accessPathFactory) {
        this.config = config;
        this.forwardSolver = forwardSolver;
        this.icfg = icfg;
        this.ophelper = ophelper;
        this.sourceSinkManager = sourceSinkManager;
        this.typeUtils = new TypeUtils(this);;
        this.hierarchy = hierarchy;
        this.accessPathFactory = accessPathFactory;
    }
    public InfoflowConfiguration getConfig() {
        return this.config;
    }
    public void setForwardSolver(NormalSolver solver) {
        this.forwardSolver = solver;
    }
    public NormalSolver getForwardSolver() {
        return this.forwardSolver;
    }
    public IInfoflowCFG getICFG() {
        return this.icfg;
    }
    public IPatternSourceSinkManager getSourceSinkManager() {
        return this.sourceSinkManager;
    }
    public TypeUtils getTypeUtils() {
        return this.typeUtils;
    }
    public FastHierarchy getHierarchy() {
        return hierarchy;
    }
    public AccessPathFactory getAccessPathFactory() {
        return this.accessPathFactory;
    }
    public LCMethodSummaryResult getResult(){return this.result;}
    public void setResult(LCMethodSummaryResult result){this.result = result;}


    public boolean isAnalysisAborted() {
        if (forwardSolver instanceof IMemoryBoundedSolver)
            return ((IMemoryBoundedSolver) forwardSolver).isKilled();
        return false;
    }
    public void cleanup() {
        forwardSolver = null;
        aliasing = null;
    }
    public void setAliasing(PatternAliasing aliasing) {
        this.aliasing = aliasing;
    }
    public PatternAliasing getAliasing() {
        return aliasing;
    }
    public LCResourceOPHelper getLCResourceOPHelper(){return  this.ophelper;}
}
