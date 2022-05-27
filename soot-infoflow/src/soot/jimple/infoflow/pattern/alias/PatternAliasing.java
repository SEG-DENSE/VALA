package soot.jimple.infoflow.pattern.alias;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import fj.P;
import heros.solver.IDESolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.cfg.FlowDroidEssentialMethodTag;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.data.AccessPathFactory;
import soot.jimple.infoflow.pattern.patterndata.PatternDataHelper;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOP;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPList;
import soot.jimple.infoflow.pattern.solver.NormalState;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowManager;
import soot.jimple.infoflow.util.SystemClassHandler;
import soot.jimple.infoflow.util.TypeUtils;
import soot.jimple.toolkits.pointer.LocalMustAliasAnalysis;
import soot.jimple.toolkits.pointer.StrongLocalMustAliasAnalysis;
import soot.toolkits.graph.UnitGraph;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class PatternAliasing {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final IPatternAliasingStrategy aliasingStrategy;
    private final PatternInfoflowManager manager;

    private final Set<SootMethod> excludedFromMustAliasAnalysis = new HashSet<>();
    protected final LoadingCache<SootMethod, LocalMustAliasAnalysis> strongAliasAnalysis = IDESolver.DEFAULT_CACHE_BUILDER
            .build(new CacheLoader<SootMethod, LocalMustAliasAnalysis>() {
                @Override
                public LocalMustAliasAnalysis load(SootMethod method) throws Exception {
                    return new StrongLocalMustAliasAnalysis((UnitGraph) manager.getICFG().getOrCreateUnitGraph(method));
                }
            });
    public PatternAliasing(IPatternAliasingStrategy aliasingStrategy, PatternInfoflowManager manager) {
        this.aliasingStrategy = aliasingStrategy;
        this.manager = manager;
    }

    public static boolean isAPRelevantToInvocation(InvokeExpr exp, Set<AccessPath> aps) {
        if(aps.isEmpty()) {return false;}
        Set<Value> bases = new HashSet<>();
        if (exp instanceof  InstanceInvokeExpr) {
            bases.add(((InstanceInvokeExpr) exp).getBase());
        }
        bases.addAll(exp.getArgs());
        for (AccessPath ap : aps) {
            if (bases.contains(ap.getPlainValue())) {
                return true;
            }
        }
        return false;
    }



    public boolean computeAliases(final NormalState source, AccessPath apToBw, Stmt currentStmt, boolean bwForSlice) {
        // Can we have aliases at all?
        if (canHaveAliases(apToBw, bwForSlice)) {
            NormalState aliasState = source.deriveAliasState(apToBw, currentStmt, bwForSlice);
            aliasingStrategy.computeAliasTaints(aliasState, currentStmt);
            return true;
        }
        return false;
    }

    public static boolean canHaveAliases(AccessPath ap , boolean bwForSlice) {
        if (!canHaveAliases(ap)) {
            return false;
        }
        if (!bwForSlice && ap.isLocal()) {
            return false;
        }
        return true;
    }

    public static boolean canHaveAliases(AccessPath ap) {
        if (null == ap || ap.isEmpty()) {
            return false;
        }
        // String cannot have aliases
        if (TypeUtils.isStringType(ap.getBaseType()) && !ap.getCanHaveImmutableAliases())
            return false;
        // We never ever handle primitives as they can never have aliases
        if (ap.isStaticFieldRef() && !(ap.getFirstFieldType() instanceof PrimType)) {
            return true;
        }
        if (!(ap.getBaseType() instanceof PrimType)) {
            return true;
        }
        return false;
    }

    public static boolean baseMatches(final Value baseValue, AccessPath ap) {
        if (baseValue instanceof Local) {
            return baseValue.equals(ap.getPlainValue());
        } else if (baseValue instanceof InstanceFieldRef) {
            InstanceFieldRef ifr = (InstanceFieldRef) baseValue;
                return ifr.getBase().equals(ap.getPlainValue())
                        && ap.firstFieldMatches(ifr.getField());

        } else if (baseValue instanceof StaticFieldRef) {
            StaticFieldRef sfr = (StaticFieldRef) baseValue;
            return ap.firstFieldMatches(sfr.getField());
        }
        return false;
    }

    public static boolean baseMatchesStrict(final Value baseValue, NormalState state) {
        for (AccessPath ap : state.getAps()) {
            if (baseMatchesStrict(baseValue, ap)) {
                return true;
            }
        }
        return false;
    }



    private static boolean baseMatchesStrict(final Value baseValue, AccessPath ap) {
        if (!baseMatches(baseValue, ap))
            return false;

        if (baseValue instanceof Local)
            return ap.isLocal();
        else if (baseValue instanceof InstanceFieldRef || baseValue instanceof StaticFieldRef)
            return ap.getFieldCount() == 1;

        throw new RuntimeException("Unexpected left side");
    }

    /**
     * Gets whether a value and an access path may potentially point to the same
     * runtime object
     *
     * @param ap  The access path
     * @param val The value
     * @return The access path that actually matched if the given value and access
     *         path alias. In the simplest case, this is the given access path. When
     *         using recursive access paths, it can however also be a base
     *         expansion. If the given access path and value do not alias, null is
     *         returned.
     */
    public AccessPath mayAlias(AccessPath ap, Value val) {
        // What cannot be represented in an access path cannot alias
        if (!AccessPath.canContainValue(val))
            return null;

        // Constants can never alias
        if (val instanceof Constant)
            return null;

        // For instance field references, the base must match
        if (val instanceof Local)
            if (ap.getPlainValue() != val)
                return null;

        // For array references, the base must match
        if (val instanceof ArrayRef)
            if (ap.getPlainValue() != ((ArrayRef) val).getBase())
                return null;

        // For instance field references, the base local must match
        if (val instanceof InstanceFieldRef) {
            if (!ap.isLocal() && !ap.isInstanceFieldRef())
                return null;
            if (((InstanceFieldRef) val).getBase() != ap.getPlainValue())
                return null;
        }

        // If the value is a static field reference, the access path must be
        // static as well
        if (val instanceof StaticFieldRef)
            if (!ap.isStaticFieldRef())
                return null;

        // Get the field set from the value
        SootField[] fields = val instanceof FieldRef ? new SootField[] { ((FieldRef) val).getField() }
                : new SootField[0];
        return getReferencedAPBase(ap, fields);
    }

    public boolean mayAlias(Value val1, Value val2) {
        // What cannot be represented in an access path cannot alias
        if (!AccessPath.canContainValue(val1) || !AccessPath.canContainValue(val2))
            return false;
        // Constants can never alias
        if (val1 instanceof Constant || val2 instanceof Constant)
            return false;
        // If the two values are equal, they alias by definition
        if (val1 == val2)
            return true;
//        // If we have an interactive aliasing algorithm, we check that as well
//        if (aliasingStrategy.isInteractive())
//            return aliasingStrategy.mayAlias(manager.getAccessPathFactory().createAccessPath(val1, false),
//                    manager.getAccessPathFactory().createAccessPath(val2, false));
        return false;
    }

    public boolean mustAlias(SootField field1, SootField field2) {
        return field1 == field2;
    }

    public boolean mustAlias(Local val1, Local val2, Stmt position) {
        if (val1 == val2)
            return true;
        if (!(val1.getType() instanceof RefLikeType) || !(val2.getType() instanceof RefLikeType))
            return false;

        // We do not query aliases for certain excluded methods
        SootMethod method = manager.getICFG().getMethodOf(position);
        if (excludedFromMustAliasAnalysis.contains(method))
            return false;

        // The must-alias analysis can take time and memory. We therefore first
        // check whether the analysis was aborted.
        if (manager.isAnalysisAborted())
            return false;

        // Query the must-alias analysis
        try {
            LocalMustAliasAnalysis lmaa = strongAliasAnalysis.getUnchecked(method);
            return lmaa.mustAlias(val1, position, val2, position);
        } catch (Exception ex) {
            // The analysis in Soot is somewhat buggy. In that case, just resort to no alias
            // analysis for the respective method.
            logger.error("Error in local must alias analysis", ex);
            return false;
        }
    }

    private AccessPath getReferencedAPBase(AccessPath taintedAP, SootField[] referencedFields) {
        final Collection<AccessPathFactory.BasePair> bases = taintedAP.isStaticFieldRef()
                ? manager.getAccessPathFactory().getBaseForType(taintedAP.getFirstFieldType())
                : manager.getAccessPathFactory().getBaseForType(taintedAP.getBaseType());

        int fieldIdx = 0;
        while (fieldIdx < referencedFields.length) {
            // If we reference a.b.c, this only matches a.b.*, but not a.b
            if (fieldIdx >= taintedAP.getFieldCount()) {
                if (taintedAP.getTaintSubFields())
                    return taintedAP;
                else
                    return null;
            }

            // a.b does not match a.c
            if (taintedAP.getFields()[fieldIdx] != referencedFields[fieldIdx]) {
                // If the referenced field is a base, we add it in. Note that
                // the first field in a static reference is the base, so this
                // must be excluded from base matching.
                if (bases != null && !(taintedAP.isStaticFieldRef() && fieldIdx == 0)) {
                    for (AccessPathFactory.BasePair base : bases) {
                        if (base.getFields()[0] == referencedFields[fieldIdx]) {
                            // Build the access path against which we have
                            // actually matched
                            SootField[] cutFields = new SootField[taintedAP.getFieldCount() + base.getFields().length];
                            Type[] cutFieldTypes = new Type[cutFields.length];

                            System.arraycopy(taintedAP.getFields(), 0, cutFields, 0, fieldIdx);
                            System.arraycopy(base.getFields(), 0, cutFields, fieldIdx, base.getFields().length);
                            System.arraycopy(taintedAP.getFields(), fieldIdx, cutFields,
                                    fieldIdx + base.getFields().length, taintedAP.getFieldCount() - fieldIdx);

                            System.arraycopy(taintedAP.getFieldTypes(), 0, cutFieldTypes, 0, fieldIdx);
                            System.arraycopy(base.getTypes(), 0, cutFieldTypes, fieldIdx, base.getTypes().length);
                            System.arraycopy(taintedAP.getFieldTypes(), fieldIdx, cutFieldTypes,
                                    fieldIdx + base.getTypes().length, taintedAP.getFieldCount() - fieldIdx);

                            return manager.getAccessPathFactory().createAccessPath(taintedAP.getPlainValue(), cutFields,
                                    taintedAP.getBaseType(), cutFieldTypes, taintedAP.getTaintSubFields(), false, false,
                                    taintedAP.getArrayTaintType());
                        }
                    }

                }
                return null;
            }

            fieldIdx++;
        }

        return taintedAP;
    }

}
