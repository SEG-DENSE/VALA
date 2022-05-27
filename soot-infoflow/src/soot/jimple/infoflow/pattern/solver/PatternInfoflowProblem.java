package soot.jimple.infoflow.pattern.solver;

import heros.FlowFunctions;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.infoflow.cfg.FlowDroidEssentialMethodTag;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.pattern.alias.PatternAliasing;
import soot.jimple.infoflow.pattern.mappingmethods.MappingMethodHelper;
import soot.jimple.infoflow.pattern.mappingmethods.SootMissingMethodHelper;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPHelper;
import soot.jimple.infoflow.pattern.skipmethods.SkipMethodsHelper;
import soot.jimple.infoflow.pattern.sourceandsink.IPatternSourceSinkManager;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.sourcesSinks.manager.IOneSourceAtATimeManager;
import soot.jimple.infoflow.util.SystemClassHandler;

import java.util.*;

public abstract class PatternInfoflowProblem {
    protected final PatternInfoflowManager manager;
    protected NormalState zeroValue = null;
    protected IInfoflowCFG icfg = null;
    protected final Map<Unit, Set<NormalState>> initialSeeds = new HashMap<>();
    public PatternInfoflowManager getManager() {return this.manager;}

    private final Set<SootMethod> mappingmethods;
    private final SootMissingMethodHelper missingMethodHelper;
    private final SkipMethodsHelper skipMethodHelper;
    private final SootField currentOneSource;
    private final Set<SootMethod> opmethods;


    public PatternInfoflowProblem(PatternInfoflowManager manager) {
        this.manager = manager;
        this.icfg = manager.getICFG();
        LCResourceOPHelper opHelper = manager.getLCResourceOPHelper();
        IPatternSourceSinkManager sourceSinkManager = manager.getSourceSinkManager();
        currentOneSource = sourceSinkManager instanceof IOneSourceAtATimeManager? ((IOneSourceAtATimeManager)sourceSinkManager).isOneSourceAtATimeEnabled()?((IOneSourceAtATimeManager)sourceSinkManager).getCurrentSourceField():null: null;
        opmethods = opHelper.getAllOpMethods();
        mappingmethods = MappingMethodHelper.v().getMappingmethods().keySet();
        missingMethodHelper = SootMissingMethodHelper.v();
        skipMethodHelper = SkipMethodsHelper.v();
    }

    public NormalState zeroValue(){
        if (null == zeroValue) {
            zeroValue = createZeroValue();
        }
        return zeroValue;
    }

    public NormalState createZeroValue() {
        if (zeroValue == null)
            zeroValue = NormalState.getInitialState();
        return zeroValue;
    }
    public IInfoflowCFG interproceduralCFG() {
        return this.icfg;
    }
    public int numThreads() {
        return Runtime.getRuntime().availableProcessors();
    }

    public void addInitialSeeds(Unit unit, Set<NormalState> initialStates) {
        if (!this.initialSeeds.containsKey(unit))
            this.initialSeeds.put(unit, initialStates);
    }

    public Map<Unit, Set<NormalState>> initialSeeds() {
        return this.initialSeeds;
    }
    public boolean hasInitialSeeds() {return !this.initialSeeds.isEmpty();}

    abstract public FlowFunctions<Unit, NormalState, SootMethod> flowFunctions();

    protected boolean isExcluded(InvokeExpr invokeExpr, SootMethod sm, NormalState state) {
        // Is this an essential method?
        if (sm.hasTag(FlowDroidEssentialMethodTag.TAG_NAME))
            return false;

        // We can exclude Soot library classes
        if (null != sm.getDeclaringClass() && sm.getDeclaringClass().isLibraryClass())
            return true;

        // We can ignore system classes according to FlowDroid's definition
        if (SystemClassHandler.isClassInSystemPackage(sm.getDeclaringClass().getName()))
            return true;

        if (missingMethodHelper.isMethodMissing(sm))
            return true;

        if (opmethods.contains(sm))
            return true;

        if (mappingmethods.contains(sm)) {
            return true;
        }
//        return false;

        SootField targetField = null;
        targetField = state.getField() == null? currentOneSource: state.getField();

        //Check ap relevance
        Set<AccessPath> allAps = new HashSet<>();
        allAps.addAll(state.aps);
        allAps.addAll(state.inactiveAps);
        if (!PatternAliasing.isAPRelevantToInvocation(invokeExpr, allAps) && skipMethodHelper.canBeExcluded(targetField, sm)) {
            return true;
        } else {
            return false;
        }
    }
}
