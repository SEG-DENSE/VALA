package soot.jimple.infoflow.pattern.patterndata;

import soot.SootMethod;
import soot.tagkit.Tag;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PatternDataConstant {
    public static final String ACTIVITY_ONCREATE = "void onCreate(android.os.Bundle)";
    public static final String ACTIVITY_ONSTART = "void onStart()";
    public static final String ACTIVITY_ONRESUME = "void onResume()";
    public static final String ACTIVITY_ONSAVEINSTANCESTATE = "void onSaveInstanceState(android.os.Bundle)";
    public static final String ACTIVITY_ONPAUSE = "void onPause()";
    public static final String ACTIVITY_ONSTOP = "void onStop()";
    public static final String ACTIVITY_ONRESTART = "void onRestart()";
    public static final String ACTIVITY_ONDESTROY = "void onDestroy()";

    public static final String[] ACTIVITY_LCMETHODS = {ACTIVITY_ONCREATE, ACTIVITY_ONSTART, ACTIVITY_ONRESUME,
            ACTIVITY_ONSAVEINSTANCESTATE, ACTIVITY_ONPAUSE, ACTIVITY_ONSTOP, ACTIVITY_ONRESTART, ACTIVITY_ONDESTROY};


    public static final String FINISHMETHODSIG = "<android.app.Activity: void finish()>";

    public static final String SETRETAININSTANCESIG1 = "<android.app.Fragment: void setRetainInstance(boolean)>";
    public static final String SETRETAININSTANCESIG2 = "<androidx.fragment.app.Fragment: void setRetainInstance(boolean)>";

    public static final String FRAGMENT_ONCREATE = "void onCreate(android.os.Bundle)";
    public static final String FRAGMENT_ONATTACH = "void onAttach(android.app.Activity)";
    public static final String FRAGMENT_ONCREATEVIEW = "android.view.View onCreateView(android.view.LayoutInflater,android.view.ViewGroup,android.os.Bundle)";
    public static final String FRAGMENT_ONVIEWCREATED = "void onViewCreated(android.view.View,android.os.Bundle)";
    public static final String FRAGMENT_ONACTIVITYCREATED = "void onActivityCreated(android.os.Bundle)";
    public static final String FRAGMENT_ONDESTROY = "void onDestroy()";
    public static final String FRAGMENT_ONSTART = "void onStart()";

    public static final String FRAGMENT_APP = "android.app.Fragment";
    public static final String FRAGMENT_V4 = "";



//    public static String LCMETHODSUFFIX = "lc_lc_method";
//    public static String LCMETHODNOTINSUFFIX = "lc_lc_method_none";
//
//
//    private static ConcurrentHashMap<SootMethod, Set<String>> tagcachemap = new ConcurrentHashMap<>();
//        tagcachemap.clear();
//        Set<String> currentTagnames = tagcachemap.get(m);
//            currentTagnames = new HashSet<>();
//            String tagname = tag.getName();
//                currentTagnames.add(tagname);
//            tagcachemap.putIfAbsent(m, Collections.emptySet());
//            tagcachemap.putIfAbsent(m, currentTagnames);
//        return tagcachemap.get(m);
}
