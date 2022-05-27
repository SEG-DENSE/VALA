package soot.jimple.infoflow.pattern.entryPointCreators;

import heros.solver.Pair;
import soot.*;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.NullConstant;
import soot.jimple.Stmt;
import soot.jimple.infoflow.entryPointCreators.BaseEntryPointCreator;
import soot.jimple.toolkits.scalar.NopEliminator;
import soot.util.MultiMap;

import java.util.*;

public class PatternFragmentBaseEntryPointCreator extends PatternBaseEntryPointCreator {
    protected Map<SootClass, Local> localVarsForClasses = new HashMap<>();
    protected SootClass component = null;
//    protected SootField resultIntentField = null;
//    protected SootField intentField = null;
    protected Local thisLocal = null;
//    protected Local intentLocal = null;
    protected String[] targetLCMethods = new String[]{AndroidEntryPointConstants.FRAGMENT_ONCREATE, AndroidEntryPointConstants.FRAGMENT_ONATTACH,
            AndroidEntryPointConstants.FRAGMENT_ONCREATEVIEW, AndroidEntryPointConstants.FRAGMENT_ONVIEWCREATED, AndroidEntryPointConstants.FRAGMENT_ONACTIVITYCREATED, AndroidEntryPointConstants.FRAGMENT_ONDESTROY};
    public PatternFragmentBaseEntryPointCreator(SootClass activityClass) {
        this.component = activityClass;
    }

    public Set<Pair<SootMethod, SootMethod>> createMainMethods() {
        Set<Pair<SootMethod, SootMethod>> entrypointsInComponent = new HashSet<>();
        createAdditionalFields();
        createAdditionalMethods();
        for (String lcmethodsubsig : targetLCMethods) {
            body = createMainBodyPrefix(lcmethodsubsig, component);
            if (null == body) {break;}
            SootMethod targetMethod = null;
            switch (lcmethodsubsig) {
                case AndroidEntryPointConstants.FRAGMENT_ONCREATE:{
                    targetMethod = buildOnCreate();
                    break;
                }
                case AndroidEntryPointConstants.FRAGMENT_ONATTACH: {
                    targetMethod = buildOnAttached();
                    break;
                }
                case AndroidEntryPointConstants.FRAGMENT_ONSTART: {
                    targetMethod = buildOnStart();
                    break;
                }
                case AndroidEntryPointConstants.FRAGMENT_ONCREATEVIEW: {
                    targetMethod = buildOnCreateView();
                    break;
                }
                case AndroidEntryPointConstants.FRAGMENT_ONVIEWCREATED: {
                    targetMethod = buildOnViewCreated();
                    break;
                }
                case AndroidEntryPointConstants.FRAGMENT_ONACTIVITYCREATED: {
                    targetMethod = buildOnActivityCreated();
                    break;
                }
                case AndroidEntryPointConstants.FRAGMENT_ONDESTROY: {
                    targetMethod = buildOnDestroy();
                    break;
                }
                default:{
                    break;
                }
            }
            if (null != targetMethod && null != mainMethod) {
                body.getUnits().add(Jimple.v().newReturnStmt(NullConstant.v()));
                NopEliminator.v().transform(body);
                entrypointsInComponent.add(new Pair<>(targetMethod, mainMethod));
            }
        }
        return entrypointsInComponent;
    }

    protected Body createMainBodyPrefix(String lcmethodsubsig, SootClass component) {
        body = createEmptyMainMethod(lcmethodsubsig, component);
        generator = new LocalGenerator(body);

        thisLocal = generateClassConstructor(component, body);
        if (thisLocal == null) {
            logger.warn("Constructor cannot be generated for {}", component.getName());
            SootClass mainClass = Scene.v().getSootClass(dummyClassName);
            mainClass.removeMethod(mainMethod);
            return null;
        }
        else {
            localVarsForClasses.put(component, thisLocal);

            // Store the intent
//            body.getUnits().add(Jimple.v()
//                    .newAssignStmt(Jimple.v().newInstanceFieldRef(thisLocal, intentField.makeRef()), intentLocal));

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
//        final SootClass intentClass = Scene.v().getSootClassUnsafe("android.content.Intent");
//        if (intentClass == null)
//            throw new RuntimeException("Could not find Android intent class");

        // Create the method
        List<Type> argList = new ArrayList<>();
//        argList.add(RefType.v(intentClass));
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
//        intentLocal = body.getParameterLocal(0);
//        localVarsForClasses.put(intentClass, intentLocal);
        return body;
    }

    protected SootMethod buildOnCreate() {
        SootMethod m = searchAndBuildMethod(AndroidEntryPointConstants.FRAGMENT_ONCREATE, component,
                thisLocal, Collections.<SootClass>emptySet());
        return m;
    }

    protected SootMethod buildOnStart() {
        SootMethod m = searchAndBuildMethod(AndroidEntryPointConstants.FRAGMENT_ONSTART, component,
                thisLocal, Collections.<SootClass>emptySet());
        return m;
    }

    protected SootMethod buildOnCreateView() {
        SootMethod m = searchAndBuildMethod(AndroidEntryPointConstants.FRAGMENT_ONCREATEVIEW, component,
                thisLocal, Collections.<SootClass>emptySet());
        return m;
    }

    protected SootMethod buildOnViewCreated() {
        SootMethod m = searchAndBuildMethod(AndroidEntryPointConstants.FRAGMENT_ONVIEWCREATED, component,
                thisLocal, Collections.<SootClass>emptySet());
        return m;
    }

    protected SootMethod buildOnActivityCreated() {
        SootMethod m = searchAndBuildMethod(AndroidEntryPointConstants.FRAGMENT_ONACTIVITYCREATED, component,
                thisLocal, Collections.<SootClass>emptySet());
        return m;
    }

    protected SootMethod buildOnAttached() {
        SootMethod m = searchAndBuildMethod(AndroidEntryPointConstants.FRAGMENT_ONATTACH, component,
                thisLocal, Collections.<SootClass>emptySet());
        return m;
    }

    protected SootMethod buildOnDestroy() {
        SootMethod m = searchAndBuildMethod(AndroidEntryPointConstants.FRAGMENT_ONDESTROY, component,
                thisLocal, Collections.<SootClass>emptySet());
        return m;
    }

}
