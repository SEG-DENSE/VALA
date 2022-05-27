package soot.jimple.infoflow.resourceleak;

import soot.Scene;
import soot.SootClass;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public class ResourceLeakConstants {
    public static final String ContentProviderOnCreateMethodSubSig = "boolean onCreate()";
    public static final String ServiceOnCreateMethodSubSig = "void onCreate()";

    public static final String contentProviderClassName = "android.content.ContentProvider";
    public static final String serviceClassName = "android.app.Service";
    private static SootClass scContentProvider = null;
    private static boolean hasLoadedContentProvider = false;
    private static SootClass scService = null;
    private static boolean hasLoadedService = false;
    public static boolean isContentProvider(SootClass testone) {
        if (!hasLoadedContentProvider) {
            scContentProvider = Scene.v().getSootClass(contentProviderClassName);
        }
        if (null == scContentProvider) {
            return false;
        }
        return Scene.v().getActiveHierarchy().isClassSubclassOf(testone, scContentProvider);
    }

    public static boolean isService(SootClass testone) {
        if (!hasLoadedService) {
            scService = Scene.v().getSootClass(serviceClassName);
        }
        if (null == scService) {
            return false;
        }
        return Scene.v().getActiveHierarchy().isClassSubclassOf(testone, scService);
    }


}
