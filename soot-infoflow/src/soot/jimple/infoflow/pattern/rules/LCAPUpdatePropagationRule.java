package soot.jimple.infoflow.pattern.rules;

import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.pattern.alias.PatternAliasing;
import soot.jimple.infoflow.pattern.result.LCMethodSummaryResult;
import soot.jimple.infoflow.pattern.solver.NormalState;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowManager;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.util.BaseSelector;
import soot.jimple.infoflow.util.ByReferenceBoolean;
import soot.jimple.infoflow.util.TypeUtils;

import java.util.*;

public class LCAPUpdatePropagationRule extends AbstractLCStatePropagationRule {
    public LCAPUpdatePropagationRule(PatternInfoflowManager manager, LCMethodSummaryResult results) {
        super(manager, results);
    }

    @Override
    NormalState propagateNormalFlow(NormalState source, Stmt curr, Stmt destStmt, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        if (curr instanceof AssignStmt) {
            final AssignStmt assignStmt = (AssignStmt) curr;
            final Value right = assignStmt.getRightOp();
            final Value[] rightVals = BaseSelector.selectBaseList(right, true);

            // Create the new taints that may be created by this
            // assignment
            NormalState newSource = createNewTaintOnAssignment(assignStmt, rightVals, source, hasGeneratedNewState, killAll);

            return newSource;
        }
        return source;
    }
    private NormalState createNewTaintOnAssignment(final AssignStmt assignStmt, final Value[] rightVals,
                                                   NormalState source, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        final Value leftValue = assignStmt.getLeftOp();
        final Value rightValue = assignStmt.getRightOp();

        // i = lengthof a. This is handled by a rule.
        if (rightValue instanceof LengthExpr || source.getAps().isEmpty()) {
            return source;
        }
        if (rightValue instanceof InstanceOfExpr) {
            return source;
        }
        // Do not propagate non-active primitive taints
//        if (!source.isAbstractionActive() && (assignStmt.getLeftOp().getType() instanceof PrimType
//                && !source.canHaveImmutableAliases())))
//            return source;

        // If we have a = x with the taint "x" being inactive,
        // we must not taint the left side. We can only taint
        // the left side if the tainted value is some "x.y".
        boolean aliasOverwritten = PatternAliasing.baseMatchesStrict(rightValue, source) && rightValue.getType() instanceof RefType;
        boolean addLeftValue = false;
        boolean cutFirstField = false;
        AccessPath mappedAP = null;
        Type targetType = null;

        // If we can't reason about aliases, there's little we can do here
        final PatternAliasing aliasing = manager.getAliasing();
        total : for (AccessPath ap : source.getAps()) {
            cutFirstField = false;
            mappedAP = ap;
            targetType = null;
            if (!aliasOverwritten) {
                for (Value rightVal : rightVals) {
                    if (rightVal instanceof FieldRef) {
                        // Get the field reference
                        FieldRef rightRef = (FieldRef) rightVal;
                        // If the right side references a NULL field, we
                        // kill the taint
                        if (rightRef instanceof InstanceFieldRef
                                && ((InstanceFieldRef) rightRef).getBase().getType() instanceof NullType)
                            break total;
                        // Check for aliasing
                        mappedAP = aliasing.mayAlias(ap, rightRef);
                        // check if static variable is tainted (same name,
                        // same class)
                        // y = X.f && X.f tainted --> y, X.f tainted
                        if (rightVal instanceof StaticFieldRef) {
                            if (manager.getConfig().getStaticFieldTrackingMode() != InfoflowConfiguration.StaticFieldTrackingMode.None
                                    && mappedAP != null) {
                                addLeftValue = true;
                                cutFirstField = true;
                            }
                        }
                        // check for field references
                        // y = x.f && x tainted --> y, x tainted
                        // y = x.f && x.f tainted --> y, x tainted
                        else if (rightVal instanceof InstanceFieldRef) {
                            Local rightBase = (Local) ((InstanceFieldRef) rightRef).getBase();
                            Local sourceBase = ap.getPlainValue();
                            final SootField rightField = rightRef.getField();

                            // We need to compare the access path on the
                            // right side
                            // with the start of the given one
                            if (mappedAP != null) {
                                addLeftValue = true;
                                cutFirstField = (mappedAP.getFieldCount() > 0
                                        && mappedAP.getFirstField() == rightField);
                            } else if (aliasing.mayAlias(rightBase, sourceBase)
                                    && ap.getFieldCount() == 0
                                    && ap.getTaintSubFields()) {
                                addLeftValue = true;
                                targetType = rightField.getType();
                                if (mappedAP == null)
                                    mappedAP = manager.getAccessPathFactory().createAccessPath(rightBase, true);
                            }
                        }
                    }
                    // indirect taint propagation:
                    // if rightvalue is local and source is instancefield of
                    // this local:
                    // y = x && x.f tainted --> y.f, x.f tainted
                    // y.g = x && x.f tainted --> y.g.f, x.f tainted
                    else if (rightVal instanceof Local && ap.isInstanceFieldRef()) {
                        Local base = ap.getPlainValue();
                        if (aliasing.mayAlias(rightVal, base)) {
                            addLeftValue = true;
                            targetType = ap.getBaseType();
                        }
                    }
                    // generic case, is true for Locals, ArrayRefs that are
                    // equal etc..
                    // y = x && x tainted --> y, x tainted
                    else if (aliasing.mayAlias(rightVal, ap.getPlainValue())) {
                        if (!(assignStmt.getRightOp() instanceof NewArrayExpr)) {
                            if (manager.getConfig().getEnableArraySizeTainting()
                                    || !(rightValue instanceof NewArrayExpr)) {
                                addLeftValue = true;
                                targetType = ap.getBaseType();
                            }
                        }
                    }
                    // One reason to taint the left side is enough
                    if (addLeftValue)
                        break total;
                }
            }
        }

        // If we have nothing to add, we quit
        if (!addLeftValue)
            return source;
        //        Abstraction targetAB = mappedAP.equals(newSource.getAccessPath()) ? newSource
//                : newSource.deriveNewAbstraction(mappedAP, null);
        NormalState newState = addTaintViaStmt(source, assignStmt, mappedAP, cutFirstField,
                getManager().getICFG().getMethodOf(assignStmt), targetType, hasGeneratedNewState, killAll);
        return newState;
    }

    private NormalState addTaintViaStmt(final NormalState source, final AssignStmt assignStmt, AccessPath mappedAP,
                                        boolean cutFirstField, SootMethod method, Type targetType, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        final Value leftValue = assignStmt.getLeftOp();
        final Value rightValue = assignStmt.getRightOp();
        // Special handling for array construction
        if (leftValue instanceof ArrayRef && targetType != null) {
            ArrayRef arrayRef = (ArrayRef) leftValue;
            targetType = TypeUtils.buildArrayOrAddDimension(targetType, arrayRef.getType().getArrayType());
        }

        // If this is an unrealizable typecast, drop the abstraction
        if (rightValue instanceof CastExpr) {
            // For casts, we must update our typing information
            CastExpr cast = (CastExpr) assignStmt.getRightOp();
            targetType = cast.getType();
        }

        // Do we taint the contents of an array? If we do not
        // differentiate, we do not set any special type.
        AccessPath.ArrayTaintType arrayTaintType = mappedAP.getArrayTaintType();
        if (leftValue instanceof ArrayRef && manager.getConfig().getEnableArraySizeTainting())
            arrayTaintType = AccessPath.ArrayTaintType.Contents;

        AccessPath newap = manager.getAccessPathFactory().copyWithNewValue(mappedAP,
                leftValue, targetType, cutFirstField, true, arrayTaintType);
        if (source.getAps().contains(newap)) {
            return source;
        } else {
            Set<AccessPath> newaps = new HashSet<>(source.getAps());
            newaps.add(newap);
            NormalState newState = source.deriveNewState(newaps, hasGeneratedNewState, assignStmt);
            final PatternAliasing aliasing = manager.getAliasing();
            if (aliasing.computeAliases(newState, newap, assignStmt, false)) {
                killAll.value = true;
                return null;
            }
            return newState;
        }
    }


    @Override
    NormalState propagateCallFlow(NormalState source, Stmt callsite, SootMethod dest, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        if (source.isZeroState()) {
            return source;
        }
        final InvokeExpr ie = (callsite != null && callsite.containsInvokeExpr()) ? callsite.getInvokeExpr() : null;
        final Local[] paramLocals = dest.getActiveBody().getParameterLocals().toArray(new Local[0]);
        final Local thisLocal = dest.isStatic() ? null : dest.getActiveBody().getThisLocal();

        Set<AccessPath> newaps = mapAccessPathToCallee(dest, ie, paramLocals, thisLocal, source, hasGeneratedNewState);
        if (null == newaps || newaps.isEmpty()) {
            return source;
        } else {
            newaps.addAll(source.getAps());
            if (newaps.size() == source.getAps().size()) {
                return source;
            } else {
                return source.deriveNewState(newaps, hasGeneratedNewState, callsite);
            }
        }
//
////            SootField field = source.getField();
////                return source;
//        return newState;
    }
    private Set<AccessPath> mapAccessPathToCallee(final SootMethod callee, final InvokeExpr ie,
                                                  Value[] paramLocals, Local thisLocal, NormalState source, ByReferenceBoolean hasGeneratedNewState) {
        // We do not transfer empty access paths
        if (source.getAps().isEmpty())
            return null;

        IInfoflowCFG icfg = manager.getICFG();
        // Android executor methods are handled specially.
        // getSubSignature()
        // is slow, so we try to avoid it whenever we can
        final boolean isExecutorExecute = icfg.isExecutorExecute(ie, callee);
        Set<AccessPath> newaps = new HashSet<>();
        final PatternAliasing aliasing = manager.getAliasing();

        // Is this a virtual method call?
        Value baseLocal = null;
        if (!isExecutorExecute && !callee.isStatic()) {
            if (icfg.isReflectiveCallSite(ie)) {
                // Method.invoke(target, arg0, ..., argn)
                baseLocal = ie.getArg(0);
            } else {
                assert ie instanceof InstanceInvokeExpr;
                InstanceInvokeExpr vie = (InstanceInvokeExpr) ie;
                baseLocal = vie.getBase();
            }
        }

        boolean isReflectiveCallSite = icfg.isReflectiveCallSite(ie);
        // Get the parameter locals if we don't have them
        // yet
        if (paramLocals == null)
            paramLocals = callee.getActiveBody().getParameterLocals()
                    .toArray(new Local[callee.getParameterCount()]);
        for (AccessPath ap : source.getAps()) {
            // If we have a base local to map, we need to find the
            // corresponding "this" local
            if (baseLocal != null) {
                if (aliasing.mayAlias(baseLocal, ap.getPlainValue()))
                    if (manager.getTypeUtils().hasCompatibleTypesForCall(ap, callee.getDeclaringClass())) {
                        newaps.add(manager.getAccessPathFactory().copyWithNewValue(ap, thisLocal));
                    }
            }

            // special treatment for clinit methods - no param mapping
            // possible
            if (isExecutorExecute) {
                if (aliasing.mayAlias(ie.getArg(0), ap.getPlainValue())) {
                    newaps.add(manager.getAccessPathFactory().copyWithNewValue(ap,
                            callee.getActiveBody().getThisLocal()));
                }
            } else if (callee.getParameterCount() > 0) {

                // check if param is tainted:
                for (int i = isReflectiveCallSite ? 1 : 0; i < ie.getArgCount(); i++) {
                    if (aliasing.mayAlias(ie.getArg(i), ap.getPlainValue())) {
                        if (isReflectiveCallSite) {
                            // Taint all parameters in the callee if the
                            // argument array of a
                            // reflective method call is tainted
                            for (int j = 0; j < paramLocals.length; j++) {
                                AccessPath newAP = manager.getAccessPathFactory().copyWithNewValue(ap,
                                        paramLocals[j], null, false);
                                if (newAP != null)
                                    newaps.add(newAP);
                            }
                        } else {
                            // Taint the corresponding parameter local in
                            // the callee
                            AccessPath newAP = manager.getAccessPathFactory().copyWithNewValue(ap, paramLocals[i]);
                            if (newAP != null)
                                newaps.add(newAP);
                        }
                    }
                }
            }
        }

        Iterator<AccessPath> it = newaps.iterator();
        while(it.hasNext()) {
            AccessPath ap = it.next();
            if (!icfg.methodReadsValue(callee, ap.getPlainValue())) {
                it.remove();
            }
        }
        return newaps;
    }

    @Override
    NormalState propagateCallToReturnFlow(NormalState source, Stmt stmt, SootMethod callee, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        if (!this.manager.getConfig().shouldTaintSubfieldsAndChildren() || !stmt.containsInvokeExpr() || !(stmt instanceof AssignStmt) || callee.getDeclaringClass().isApplicationClass()) {
            return source;
        }
        InvokeExpr expr = stmt.getInvokeExpr();
        Value leftVal = ((AssignStmt)stmt).getLeftOp();
        if (!(expr instanceof InstanceInvokeExpr)) {
            return source;
        }
        InstanceInvokeExpr instanceExp = (InstanceInvokeExpr)expr;
        Value base = instanceExp.getBase();
        final PatternAliasing aliasing = manager.getAliasing();
        Set<AccessPath> newaps = new HashSet<>();
        boolean hasChanged = false;
        for (AccessPath ap : source.getAps()) {
            AccessPath mappedAp = null;
            if ((mappedAp= aliasing.mayAlias(ap, base)) != null) {
                AccessPath newAP = manager.getAccessPathFactory().copyWithNewValue(mappedAp, leftVal, leftVal.getType(), false);
                newaps.add(newAP);
                hasChanged = true;
            }
        }
        if (hasChanged) {
            newaps.addAll(source.getAps());
            return source.deriveNewState(newaps, hasGeneratedNewState, stmt);
        } else {
            return source;
        }

    }

    @Override
    NormalState propagateReturnFlow(NormalState source, Stmt exitStmt, Stmt retSite, Stmt callSite,SootMethod calleeMethod, ByReferenceBoolean hasGeneratedNewState, ByReferenceBoolean killAll) {
        if (callSite == null){
            killAll.value = true;
            return null;
        }

        PatternAliasing aliasing = manager.getAliasing();
        IInfoflowCFG icfg = manager.getICFG();
        final ReturnStmt returnStmt = (exitStmt instanceof ReturnStmt) ? (ReturnStmt) exitStmt : null;
        final Local[] paramLocals = calleeMethod.getActiveBody().getParameterLocals().toArray(new Local[0]);
        final boolean isReflectiveCallSite = callSite != null && icfg.isReflectiveCallSite(callSite);
        final Local thisLocal = calleeMethod.isStatic() ? null : calleeMethod.getActiveBody().getThisLocal();

        Set<AccessPath> newaps = new HashSet<>();
        if (!calleeMethod.isStaticInitializer()) {
            // if we have a returnStmt we have to look at the
            // returned value:
            if (returnStmt != null && callSite instanceof DefinitionStmt) {
                Value retLocal = returnStmt.getOp();
                DefinitionStmt defnStmt = (DefinitionStmt) callSite;
                Value leftOp = defnStmt.getLeftOp();
                for (AccessPath ap : source.getAps()) {
                    if (aliasing.mayAlias(retLocal, ap.getPlainValue()) && !isExceptionHandler(retSite)) {
                        AccessPath newap = manager.getAccessPathFactory().copyWithNewValue(ap, leftOp);
                        if (null != newap) {
                            newaps.add(newap);
                        }
                    }
                }
            }

            // Check parameters
            boolean parameterAliases = false;
            {
                Value originalCallArg = null;
                for (int i = 0; i < calleeMethod.getParameterCount(); i++) {
                    // If this parameter is overwritten, we
                    // cannot propagate the "old" taint over.
                    // Return value propagation must always
                    // happen explicitly.
                    if (callSite instanceof DefinitionStmt && !isExceptionHandler(retSite)) {
                        DefinitionStmt defnStmt = (DefinitionStmt) callSite;
                        Value leftOp = defnStmt.getLeftOp();
                        originalCallArg = defnStmt.getInvokeExpr().getArg(i);
                        if (originalCallArg == leftOp)
                            continue;
                    }
                    for (AccessPath ap : source.getAps()) {
                        Value sourceBase = ap.getPlainValue();
                        // Propagate over the parameter taint
                        if (aliasing.mayAlias(paramLocals[i], sourceBase)) {
                            parameterAliases = true;
                            originalCallArg = callSite.getInvokeExpr()
                                    .getArg(isReflectiveCallSite ? 1 : i);
                            if (!AccessPath.canContainValue(originalCallArg))
                                continue;
                            if (!isReflectiveCallSite && !manager.getTypeUtils()
                                    .checkCast(ap, originalCallArg.getType()))
                                continue;
                            // Primitive types and strings cannot
                            // have aliases and thus never need to
                            // be propagated back
                            if (ap.getBaseType() instanceof PrimType)
                                continue;
                            if (TypeUtils.isStringType(ap.getBaseType())
                                    && !ap.getCanHaveImmutableAliases())
                                continue;
                            // If only the object itself, but no
                            // field is tainted, we can safely
                            // ignore it
                            if (!ap.getTaintSubFields())
                                continue;
                            // If the variable was overwritten
                            // somewehere in the callee, we assume
                            // it to overwritten on all paths (yeah,
                            // I know ...) Otherwise, we need SSA
                            // or lots of bookkeeping to avoid FPs
                            // (BytecodeTests.flowSensitivityTest1).
                            if (icfg.methodWritesValue(calleeMethod, paramLocals[i]))
                                continue;
                            AccessPath newap = manager.getAccessPathFactory().copyWithNewValue(
                                    ap, originalCallArg, isReflectiveCallSite ? null : ap.getBaseType(),false);
                            if (null != newap) {
                                newaps.add(newap);
                            }

                        }
                    }
                }
            }

            // If this parameter is overwritten, we
            // cannot propagate the "old" taint over. Return
            // value propagation must always happen explicitly.
            boolean thisAliases = false;
            if (callSite instanceof DefinitionStmt && !isExceptionHandler(retSite)) {
                DefinitionStmt defnStmt = (DefinitionStmt) callSite;
                Value leftOp = defnStmt.getLeftOp();
                if (thisLocal == leftOp)
                    thisAliases = true;
            }



                // check if it is not one of the params
                // (then we have already fixed it)
            if (!parameterAliases && !thisAliases && callSite.getInvokeExpr() instanceof InstanceInvokeExpr) {
                for (AccessPath ap : source.getAps()) {
                    Value sourceBase = ap.getPlainValue();
                    if (ap.getTaintSubFields() && aliasing.mayAlias(thisLocal, sourceBase)) {
                        // Type check
                        if (manager.getTypeUtils().checkCast(ap, thisLocal.getType())) {
                            InstanceInvokeExpr iIExpr = (InstanceInvokeExpr) callSite.getInvokeExpr();

                            // Get the caller-side base local
                            // and create a new access path for it
                            Value callerBaseLocal = icfg.isReflectiveCallSite(iIExpr)
                                    ? iIExpr.getArg(0)
                                    : iIExpr.getBase();
                            AccessPath newap = manager.getAccessPathFactory().copyWithNewValue(
                                    ap, callerBaseLocal, isReflectiveCallSite ? null : ap.getBaseType(),false);
                            if (null != newap) {
                                newaps.add(newap);
                            }
                        }
                    }
                }
            }
        }
        if (newaps.isEmpty()) {
            return source;
        } else {
            newaps.addAll(source.getAps());
            if (newaps.size() == source.getAps().size()) {
                return source;
            } else {
                return source.deriveNewState(newaps, hasGeneratedNewState, exitStmt);
            }
        }
    }

    protected boolean isExceptionHandler(Unit u) {
        if (u instanceof DefinitionStmt) {
            DefinitionStmt defStmt = (DefinitionStmt) u;
            return defStmt.getRightOp() instanceof CaughtExceptionRef;
        }
        return false;
    }
    }

