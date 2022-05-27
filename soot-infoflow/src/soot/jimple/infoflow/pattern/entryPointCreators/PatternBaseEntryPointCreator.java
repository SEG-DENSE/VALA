package soot.jimple.infoflow.pattern.entryPointCreators;

import soot.Local;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.jimple.infoflow.entryPointCreators.BaseEntryPointCreator;
import soot.jimple.infoflow.util.SystemClassHandler;

import java.util.Collection;
import java.util.Set;

public abstract class PatternBaseEntryPointCreator extends BaseEntryPointCreator {

    protected SootMethod searchAndBuildMethod(String subsignature, SootClass currentClass, Local classLocal,
                                              Set<SootClass> parentClasses) {
        if (currentClass == null || classLocal == null)
            return null;

        SootMethod method = findMethod(currentClass, subsignature);
        if (method == null) {
            logger.warn("Could not find Android entry point method: {}", subsignature);
            return null;
        }

        // If the method is in one of the predefined Android classes, it cannot
        // contain custom code, so we do not need to call it
        if (AndroidEntryPointConstants.isLifecycleClass(method.getDeclaringClass().getName()))
            return null;

        // If this method is part of the Android framework, we don't need to
        // call it
        if (SystemClassHandler.isClassInSystemPackage(method.getDeclaringClass().getName()))
            return null;

        assert method.isStatic() || classLocal != null : "Class local was null for non-static method "
                + method.getSignature();

        // write Method
        Stmt stmt = buildMethodCall(method, mainMethod.getActiveBody(), classLocal, generator, parentClasses);
        if (null == stmt) {
            return null;
        }
        return method;
    }

    @Override
    protected SootMethod createDummyMainInternal() {
        return null; 
    }


    @Override
    public Collection<String> getRequiredClasses() {
        return null;
    }

    @Override
    public Collection<SootMethod> getAdditionalMethods() {
        return null;
    }

    @Override
    public Collection<SootField> getAdditionalFields() {
        return null;
    }

}
