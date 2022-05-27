package soot.jimple.infoflow.pattern.entryPointCreators;

import heros.solver.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.*;
import soot.jimple.infoflow.entryPointCreators.BaseEntryPointCreator;
import soot.jimple.infoflow.entryPointCreators.SimulatedCodeElementTag;
import soot.jimple.infoflow.util.SystemClassHandler;
import soot.jimple.toolkits.scalar.NopEliminator;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.util.*;

public class PatternActivityBaseEntryPointCreator extends PatternBaseEntryPointCreator {
    protected Map<SootClass, Local> localVarsForClasses = new HashMap<>();
    protected SootClass component = null;
    protected SootField resultIntentField = null;
    protected SootField intentField = null;
    protected Local thisLocal = null;
    protected Local intentLocal = null;
    private MultiMap<SootClass, String> activityLifecycleCallbacks = new HashMultiMap<>();
    protected String[] targetLCMethods = new String[]{AndroidEntryPointConstants.ACTIVITY_ONCREATE, AndroidEntryPointConstants.ACTIVITY_ONSTOP,
            AndroidEntryPointConstants.ACTIVITY_ONSAVEINSTANCESTATE, AndroidEntryPointConstants.ACTIVITY_ONDESTROY};
    public PatternActivityBaseEntryPointCreator(SootClass activityClass, MultiMap<SootClass, String> activityLifecycleCallbacks) {
        this.component = activityClass;
        this.activityLifecycleCallbacks = activityLifecycleCallbacks;
    }

    public Set<Pair<SootMethod, SootMethod>> createMainMethods() {
        Set<Pair<SootMethod, SootMethod>> entrypointsInComponent = new HashSet<>();
        createAdditionalFields();
        createAdditionalMethods();
        for (String lcmethodsubsig : targetLCMethods) {
            createMainBodyPrefix(lcmethodsubsig, component);
            SootMethod targetMethod = null;
            switch (lcmethodsubsig) {
                case AndroidEntryPointConstants.ACTIVITY_ONCREATE:{
                    targetMethod = buildOnCreate();
                    break;
                }
                case AndroidEntryPointConstants.ACTIVITY_ONSTOP: {
                    targetMethod = buildOnStop();
                    break;
                }
                case AndroidEntryPointConstants.ACTIVITY_ONSAVEINSTANCESTATE: {
                    targetMethod = buildOnSaveInstanceState();
                    break;
                }
                case AndroidEntryPointConstants.ACTIVITY_ONDESTROY: {
                    break;
                }
                default:{
                    break;
                }
            }
            if (null != targetMethod && null != mainMethod) {
                body.getUnits().add(Jimple.v().newReturnStmt(NullConstant.v()));
                entrypointsInComponent.add(new Pair<>(targetMethod, mainMethod));
                NopEliminator.v().transform(body);
            }
        }
        return entrypointsInComponent;
    }

    protected Body createMainBodyPrefix(String lcmethodsubsig, SootClass component) {
        body = createEmptyMainMethod(lcmethodsubsig, component);
        generator = new LocalGenerator(body);

        thisLocal = generateClassConstructor(component, body);
        if (thisLocal == null)
            logger.warn("Constructor cannot be generated for {}", component.getName());
        else {
            localVarsForClasses.put(component, thisLocal);

            // Store the intent
            body.getUnits().add(Jimple.v()
                    .newAssignStmt(Jimple.v().newInstanceFieldRef(thisLocal, intentField.makeRef()), intentLocal));

        }
//        // Make sure that we have an opaque predicate
//        conditionCounter = 0;
//        intCounter = generator.generateLocal(IntType.v());
//        body.getUnits().add(Jimple.v().newAssignStmt(intCounter, IntConstant.v(conditionCounter)));
        return body;
    }

    protected Body createEmptyMainMethod(String lcmethodsubsig, SootClass component) {
        String componentPart = component.getName();
        if (componentPart.contains("."))
            componentPart = componentPart.replace("_", "__").replace(".", "_");
        final String baseMethodName = dummyMethodName + "_" + componentPart + "_" + lcmethodsubsig;

        String methodName = baseMethodName;
        SootClass mainClass = null;
        if (Scene.v().containsClass(dummyClassName)) {
            mainClass = Scene.v().getSootClass(dummyClassName);
        } else {
            mainClass = Scene.v().makeSootClass(dummyClassName);
            mainClass.setResolvingLevel(SootClass.BODIES);
            Scene.v().addClass(mainClass);
        }

        // Remove the existing main method if necessary. Do not clear the
        // existing one, this would take much too long.
        mainMethod = mainClass.getMethodByNameUnsafe(methodName);
        if (mainMethod != null) {
            mainClass.removeMethod(mainMethod);
            mainMethod = null;
        }

        final SootClass intentClass = Scene.v().getSootClassUnsafe("android.content.Intent");
        if (intentClass == null)
            throw new RuntimeException("Could not find Android intent class");

        // Create the method
        List<Type> argList = new ArrayList<>();
        argList.add(RefType.v(intentClass));
        mainMethod = Scene.v().makeSootMethod(methodName, argList, component.getType());

        // Create the body
        JimpleBody body = Jimple.v().newBody();
        body.setMethod(mainMethod);
        mainMethod.setActiveBody(body);

        // Add the method to the class
        mainClass.addMethod(mainMethod);

        // First add class to scene, then make it an application class
        // as addClass contains a call to "setLibraryClass"
        mainClass.setApplicationClass();
        mainMethod.setModifiers(java.lang.reflect.Modifier.PUBLIC | java.lang.reflect.Modifier.STATIC);

        // Add the identity statements to the body. This must be done after the
        // method has been properly declared.
        body.insertIdentityStmts();

        // Get the parameter locals
        intentLocal = body.getParameterLocal(0);
        localVarsForClasses.put(intentClass, intentLocal);
        return body;
    }

    protected void createAdditionalFields() {
        createipcIntentField();
        createipcResultIntent();
    }
    private void createipcIntentField() {
        String fieldName = "ipcIntent";
        int fieldIdx = 0;
        while (component.declaresFieldByName(fieldName))
            fieldName = "ipcIntent_" + fieldIdx++;

        // Create the field itself
        intentField = Scene.v().makeSootField(fieldName, RefType.v("android.content.Intent"), java.lang.reflect.Modifier.PUBLIC);
        component.addField(intentField);
    }
    private void createipcResultIntent() {
        // Create a name for a field for the result intent of this component
        String fieldName = "ipcResultIntent";
        int fieldIdx = 0;
        while (component.declaresFieldByName(fieldName))
            fieldName = "ipcResultIntent_" + fieldIdx++;

        // Create the field itself
        resultIntentField = Scene.v().makeSootField(fieldName, RefType.v("android.content.Intent"), Modifier.PUBLIC);
        component.addField(resultIntentField);
    }

    protected void createAdditionalMethods() {
        createGetIntentMethod();
        createSetIntentMethod();
        createSetResultMethod();
    }
    private void createGetIntentMethod() {
        // We need to create an implementation of "getIntent". If there is already such
        // an implementation, we don't touch it.
        if (component.declaresMethod("android.content.Intent getIntent()"))
            return;

        Type intentType = RefType.v("android.content.Intent");
        SootMethod sm = Scene.v().makeSootMethod("getIntent", Collections.<Type>emptyList(), intentType,
                Modifier.PUBLIC);
        component.addMethod(sm);
        sm.addTag(SimulatedCodeElementTag.TAG);

        JimpleBody b = Jimple.v().newBody(sm);
        sm.setActiveBody(b);
        b.insertIdentityStmts();

        LocalGenerator localGen = new LocalGenerator(b);
        Local lcIntent = localGen.generateLocal(intentType);
        b.getUnits().add(Jimple.v().newAssignStmt(lcIntent,
                Jimple.v().newInstanceFieldRef(b.getThisLocal(), intentField.makeRef())));
        b.getUnits().add(Jimple.v().newReturnStmt(lcIntent));
    }
    private void createSetIntentMethod() {
        // We need to create an implementation of "getIntent". If there is already such
        // an implementation, we don't touch it.
        if (component.declaresMethod("void setIntent(android.content.Intent)"))
            return;

        Type intentType = RefType.v("android.content.Intent");
        SootMethod sm = Scene.v().makeSootMethod("setIntent", Collections.singletonList(intentType), VoidType.v(),
                Modifier.PUBLIC);
        component.addMethod(sm);
        sm.addTag(SimulatedCodeElementTag.TAG);

        JimpleBody b = Jimple.v().newBody(sm);
        sm.setActiveBody(b);
        b.insertIdentityStmts();

        Local lcIntent = b.getParameterLocal(0);
        b.getUnits().add(Jimple.v()
                .newAssignStmt(Jimple.v().newInstanceFieldRef(b.getThisLocal(), intentField.makeRef()), lcIntent));
        b.getUnits().add(Jimple.v().newReturnVoidStmt());
    }
    private void createSetResultMethod() {
        // We need to create an implementation of "getIntent". If there is already such
        // an implementation, we don't touch it.
        if (component.declaresMethod("void setResult(int,android.content.Intent)"))
            return;

        Type intentType = RefType.v("android.content.Intent");
        List<Type> params = new ArrayList<>();
        params.add(IntType.v());
        params.add(intentType);
        SootMethod sm = Scene.v().makeSootMethod("setResult", params, VoidType.v(), Modifier.PUBLIC);
        component.addMethod(sm);
        sm.addTag(SimulatedCodeElementTag.TAG);

        JimpleBody b = Jimple.v().newBody(sm);
        sm.setActiveBody(b);
        b.insertIdentityStmts();

        Local lcIntent = b.getParameterLocal(1);
        b.getUnits().add(Jimple.v().newAssignStmt(
                Jimple.v().newInstanceFieldRef(b.getThisLocal(), resultIntentField.makeRef()), lcIntent));
        b.getUnits().add(Jimple.v().newReturnVoidStmt());

        // Activity.setResult() is final. We need to change that
        SootMethod smSetResult = Scene.v()
                .grabMethod("<android.app.Activity: void setResult(int,android.content.Intent)>");
        if (smSetResult != null && smSetResult.getDeclaringClass().isApplicationClass())
            smSetResult.setModifiers(smSetResult.getModifiers() & ~Modifier.FINAL);
    }



    protected SootMethod buildOnCreate() {
        Set<SootClass> currentClassSet = Collections.singleton(component);
        SootMethod m = searchAndBuildMethod(AndroidEntryPointConstants.ACTIVITY_ONCREATE, component, thisLocal, Collections.<SootClass>emptySet());
        for (SootClass callbackClass : this.activityLifecycleCallbacks.keySet()) {
            searchAndBuildMethod(AndroidEntryPointConstants.ACTIVITYLIFECYCLECALLBACK_ONACTIVITYCREATED,
                    callbackClass, localVarsForClasses.get(callbackClass), currentClassSet);
        }
        return m;
    }

    protected SootMethod buildOnStop() {
        Set<SootClass> currentClassSet = Collections.singleton(component);
        SootMethod m = searchAndBuildMethod(AndroidEntryPointConstants.ACTIVITY_ONSTOP, component, thisLocal, Collections.<SootClass>emptySet());
        for (SootClass callbackClass : this.activityLifecycleCallbacks.keySet()) {
            searchAndBuildMethod(
                    AndroidEntryPointConstants.ACTIVITYLIFECYCLECALLBACK_ONACTIVITYSTOPPED, callbackClass,
                    localVarsForClasses.get(callbackClass), currentClassSet);
        }
        return m;
    }

    protected SootMethod buildOnSaveInstanceState() {
        Set<SootClass> currentClassSet = Collections.singleton(component);
        SootMethod m = searchAndBuildMethod(AndroidEntryPointConstants.ACTIVITY_ONSAVEINSTANCESTATE, component, thisLocal,  Collections.<SootClass>emptySet());
        for (SootClass callbackClass : this.activityLifecycleCallbacks.keySet()) {
            searchAndBuildMethod(AndroidEntryPointConstants.ACTIVITYLIFECYCLECALLBACK_ONACTIVITYSAVEINSTANCESTATE,
                    callbackClass, localVarsForClasses.get(callbackClass), currentClassSet);
        }

        return m;
    }





//            NopStmt thenStmt = Jimple.v().newNopStmt();
//            createIfStmt(thenStmt, body);
//            Local l = localVarsForClasses.get(callbackClass);
//                l = generateClassConstructor(callbackClass, body);
//                if (l != null)
//                    localVarsForClasses.put(callbackClass, l);
//            body.getUnits().add(thenStmt);
//
//            return null;
//        final Jimple jimple = Jimple.v();
//        EqExpr cond = jimple.newEqExpr(intCounter, IntConstant.v(conditionCounter++));
//        IfStmt ifStmt = jimple.newIfStmt(cond, target);
//        body.getUnits().add(ifStmt);
//        return ifStmt;


}
