/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package soot.jimple.infoflow.pattern.entryPointCreators;

import soot.Hierarchy;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class containing constants for the well-known Android lifecycle methods
 */
public class AndroidEntryPointConstants {

	/*
	 * ========================================================================
	 */

	public static final String ACTIVITYCLASS = "android.app.Activity";
	public static final String SERVICECLASS = "android.app.Service";
	public static final String GCMBASEINTENTSERVICECLASS = "com.google.android.gcm.GCMBaseIntentService";
	public static final String GCMLISTENERSERVICECLASS = "com.google.android.gms.gcm.GcmListenerService";
	public static final String BROADCASTRECEIVERCLASS = "android.content.BroadcastReceiver";
	public static final String CONTENTPROVIDERCLASS = "android.content.ContentProvider";
	public static final String APPLICATIONCLASS = "android.app.Application";
	public static final String FRAGMENTCLASS = "android.app.Fragment";
	public static final String SUPPORTFRAGMENTCLASS = "android.support.v4.app.Fragment";
	public static final String SERVICECONNECTIONINTERFACE = "android.content.ServiceConnection";

	public static final String APPCOMPATACTIVITYCLASS_V4 = "android.support.v4.app.AppCompatActivity";
	public static final String APPCOMPATACTIVITYCLASS_V7 = "android.support.v7.app.AppCompatActivity";
	public static final String APPCOMPATACTIVITYCLASS_ANDROIDX = "androidx.appcompat.app.AppCompatActivity";

	public static final String ANDROIDXCONTEXTTHEMEWRAPPER = "android.view.ContextThemeWrapper";
	public static final String ANDROIDXCONTEXTWRAPPER = "android.content.ContextWrapper";
	public static final String ANDROIDXCONTEXT = "android.content.Context";
	public static final String ANDROIDXOBJECT = "java.lang.Object";
	public static final String APP_V4 = "android.support.v4.app.";

	public static final String NEWFRAGMENTCLASS = "androidx.fragment.app.Fragment";
	public static final String NEWAPPCOMPATACTIVITYCLASS = "androidx.fragment.app.FragmentActivity";


	public static final String ACTIVITY_ONCREATE = "void onCreate(android.os.Bundle)";
	public static final String ACTIVITY_ONSTART = "void onStart()";
	public static final String ACTIVITY_ONRESTOREINSTANCESTATE = "void onRestoreInstanceState(android.os.Bundle)";
	public static final String ACTIVITY_ONPOSTCREATE = "void onPostCreate(android.os.Bundle)";
	public static final String ACTIVITY_ONRESUME = "void onResume()";
	public static final String ACTIVITY_ONPOSTRESUME = "void onPostResume()";
	public static final String ACTIVITY_ONCREATEDESCRIPTION = "java.lang.CharSequence onCreateDescription()";
	public static final String ACTIVITY_ONSAVEINSTANCESTATE = "void onSaveInstanceState(android.os.Bundle)";
	public static final String ACTIVITY_ONPAUSE = "void onPause()";
	public static final String ACTIVITY_ONSTOP = "void onStop()";
	public static final String ACTIVITY_ONRESTART = "void onRestart()";
	public static final String ACTIVITY_ONDESTROY = "void onDestroy()";
	public static final String ACTIVITY_ONATTACHFRAGMENT = "void onAttachFragment(android.app.Fragment)";

	public static final String SERVICE_ONCREATE = "void onCreate()";
	public static final String SERVICE_ONSTART1 = "void onStart(android.content.Intent,int)";
	public static final String SERVICE_ONSTART2 = "int onStartCommand(android.content.Intent,int,int)";
	public static final String SERVICE_ONBIND = "android.os.IBinder onBind(android.content.Intent)";
	public static final String SERVICE_ONREBIND = "void onRebind(android.content.Intent)";
	public static final String SERVICE_ONUNBIND = "boolean onUnbind(android.content.Intent)";
	public static final String SERVICE_ONDESTROY = "void onDestroy()";

	public static final String GCMINTENTSERVICE_ONDELETEDMESSAGES = "void onDeletedMessages(android.content.Context,int)";
	public static final String GCMINTENTSERVICE_ONERROR = "void onError(android.content.Context,java.lang.String)";
	public static final String GCMINTENTSERVICE_ONMESSAGE = "void onMessage(android.content.Context,android.content.Intent)";
	public static final String GCMINTENTSERVICE_ONRECOVERABLEERROR = "void onRecoverableError(android.content.Context,java.lang.String)";
	public static final String GCMINTENTSERVICE_ONREGISTERED = "void onRegistered(android.content.Context,java.lang.String)";
	public static final String GCMINTENTSERVICE_ONUNREGISTERED = "void onUnregistered(android.content.Context,java.lang.String)";

	public static final String GCMLISTENERSERVICE_ONDELETEDMESSAGES = "void onDeletedMessages()";
	public static final String GCMLISTENERSERVICE_ONMESSAGERECEIVED = "void onMessageReceived(java.lang.String,android.os.Bundle)";
	public static final String GCMLISTENERSERVICE_ONMESSAGESENT = "void onMessageSent(java.lang.String)";
	public static final String GCMLISTENERSERVICE_ONSENDERROR = "void onSendError(java.lang.String,java.lang.String)";

	public static final String FRAGMENT_ONCREATE = "void onCreate(android.os.Bundle)";
	public static final String FRAGMENT_ONATTACH = "void onAttach(android.app.Activity)";
	public static final String FRAGMENT_ONCREATEVIEW = "android.view.View onCreateView(android.view.LayoutInflater,android.view.ViewGroup,android.os.Bundle)";
	public static final String FRAGMENT_ONVIEWCREATED = "void onViewCreated(android.view.View,android.os.Bundle)";
	public static final String FRAGMENT_ONSTART = "void onStart()";
	public static final String FRAGMENT_ONACTIVITYCREATED = "void onActivityCreated(android.os.Bundle)";
	public static final String FRAGMENT_ONVIEWSTATERESTORED = "void onViewStateRestored(android.app.Activity)";
	public static final String FRAGMENT_ONRESUME = "void onResume()";
	public static final String FRAGMENT_ONPAUSE = "void onPause()";
	public static final String FRAGMENT_ONSTOP = "void onStop()";
	public static final String FRAGMENT_ONDESTROYVIEW = "void onDestroyView()";
	public static final String FRAGMENT_ONDESTROY = "void onDestroy()";
	public static final String FRAGMENT_ONDETACH = "void onDetach()";
	public static final String FRAGMENT_ONSAVEINSTANCESTATE = "void onSaveInstanceState(android.os.Bundle)";

	public static final String BROADCAST_ONRECEIVE = "void onReceive(android.content.Context,android.content.Intent)";

	public static final String CONTENTPROVIDER_ONCREATE = "boolean onCreate()";

	public static final String APPLICATION_ONCREATE = "void onCreate()";
	public static final String APPLICATION_ONTERMINATE = "void onTerminate()";

	public static final String SERVICECONNECTION_ONSERVICECONNECTED = "void onServiceConnected(android.content.ComponentName,android.os.IBinder)";
	public static final String SERVICECONNECTION_ONSERVICEDISCONNECTED = "void onServiceDisconnected(android.content.ComponentName)";

	public static final String ACTIVITYLIFECYCLECALLBACKSINTERFACE = "android.app.Application$ActivityLifecycleCallbacks";
	public static final String ACTIVITYLIFECYCLECALLBACK_ONACTIVITYSTARTED = "void onActivityStarted(android.app.Activity)";
	public static final String ACTIVITYLIFECYCLECALLBACK_ONACTIVITYSTOPPED = "void onActivityStopped(android.app.Activity)";
	public static final String ACTIVITYLIFECYCLECALLBACK_ONACTIVITYSAVEINSTANCESTATE = "void onActivitySaveInstanceState(android.app.Activity,android.os.Bundle)";
	public static final String ACTIVITYLIFECYCLECALLBACK_ONACTIVITYRESUMED = "void onActivityResumed(android.app.Activity)";
	public static final String ACTIVITYLIFECYCLECALLBACK_ONACTIVITYPAUSED = "void onActivityPaused(android.app.Activity)";
	public static final String ACTIVITYLIFECYCLECALLBACK_ONACTIVITYDESTROYED = "void onActivityDestroyed(android.app.Activity)";
	public static final String ACTIVITYLIFECYCLECALLBACK_ONACTIVITYCREATED = "void onActivityCreated(android.app.Activity,android.os.Bundle)";

	public static final String COMPONENTCALLBACKSINTERFACE = "android.content.ComponentCallbacks";
	public static final String COMPONENTCALLBACKS_ONCONFIGURATIONCHANGED = "void onConfigurationChanged(android.content.res.Configuration)";

	public static final String COMPONENTCALLBACKS2INTERFACE = "android.content.ComponentCallbacks2";
	public static final String COMPONENTCALLBACKS2_ONTRIMMEMORY = "void onTrimMemory(int)";

	/*
	 * ========================================================================
	 */

	private static final String[] activityMethods = { ACTIVITY_ONCREATE, ACTIVITY_ONDESTROY, ACTIVITY_ONPAUSE,
			ACTIVITY_ONRESTART, ACTIVITY_ONRESUME, ACTIVITY_ONSTART, ACTIVITY_ONSTOP, ACTIVITY_ONSAVEINSTANCESTATE,
			ACTIVITY_ONRESTOREINSTANCESTATE, ACTIVITY_ONCREATEDESCRIPTION, ACTIVITY_ONPOSTCREATE,
			ACTIVITY_ONPOSTRESUME };
	private static final List<String> activityMethodList = Arrays.asList(activityMethods);

	private static final String[] serviceMethods = { SERVICE_ONCREATE, SERVICE_ONDESTROY, SERVICE_ONSTART1,
			SERVICE_ONSTART2, SERVICE_ONBIND, SERVICE_ONREBIND, SERVICE_ONUNBIND };
	private static final List<String> serviceMethodList = Arrays.asList(serviceMethods);

	private static final String[] fragmentMethods = { FRAGMENT_ONCREATE, FRAGMENT_ONDESTROY, FRAGMENT_ONPAUSE,
			FRAGMENT_ONATTACH, FRAGMENT_ONDESTROYVIEW, FRAGMENT_ONRESUME, FRAGMENT_ONSTART, FRAGMENT_ONSTOP,
			FRAGMENT_ONCREATEVIEW, FRAGMENT_ONACTIVITYCREATED, FRAGMENT_ONVIEWSTATERESTORED, FRAGMENT_ONDETACH };
	private static final List<String> fragmentMethodList = Arrays.asList(fragmentMethods);

	private static final String[] gcmIntentServiceMethods = { GCMINTENTSERVICE_ONDELETEDMESSAGES,
			GCMINTENTSERVICE_ONERROR, GCMINTENTSERVICE_ONMESSAGE, GCMINTENTSERVICE_ONRECOVERABLEERROR,
			GCMINTENTSERVICE_ONREGISTERED, GCMINTENTSERVICE_ONUNREGISTERED };
	private static final List<String> gcmIntentServiceMethodList = Arrays.asList(gcmIntentServiceMethods);

	private static final String[] gcmListenerServiceMethods = { GCMLISTENERSERVICE_ONDELETEDMESSAGES,
			GCMLISTENERSERVICE_ONMESSAGERECEIVED, GCMLISTENERSERVICE_ONMESSAGESENT, GCMLISTENERSERVICE_ONSENDERROR };
	private static final List<String> gcmListenerServiceMethodList = Arrays.asList(gcmListenerServiceMethods);

	private static final String[] broadcastMethods = { BROADCAST_ONRECEIVE };
	private static final List<String> broadcastMethodList = Arrays.asList(broadcastMethods);

	private static final String[] contentproviderMethods = { CONTENTPROVIDER_ONCREATE };
	private static final List<String> contentProviderMethodList = Arrays.asList(contentproviderMethods);

	private static final String[] applicationMethods = { APPLICATION_ONCREATE, APPLICATION_ONTERMINATE };
	private static final List<String> applicationMethodList = Arrays.asList(applicationMethods);

	private static final String[] activityLifecycleMethods = { ACTIVITYLIFECYCLECALLBACK_ONACTIVITYSTARTED,
			ACTIVITYLIFECYCLECALLBACK_ONACTIVITYSTOPPED, ACTIVITYLIFECYCLECALLBACK_ONACTIVITYSAVEINSTANCESTATE,
			ACTIVITYLIFECYCLECALLBACK_ONACTIVITYRESUMED, ACTIVITYLIFECYCLECALLBACK_ONACTIVITYPAUSED,
			ACTIVITYLIFECYCLECALLBACK_ONACTIVITYDESTROYED, ACTIVITYLIFECYCLECALLBACK_ONACTIVITYCREATED };
	private static final List<String> activityLifecycleMethodList = Arrays.asList(activityLifecycleMethods);

	private static final String[] componentCallbackMethods = { COMPONENTCALLBACKS_ONCONFIGURATIONCHANGED };
	private static final List<String> componentCallbackMethodList = Arrays.asList(componentCallbackMethods);

	private static final String[] componentCallback2Methods = { COMPONENTCALLBACKS2_ONTRIMMEMORY };
	private static final List<String> componentCallback2MethodList = Arrays.asList(componentCallback2Methods);

	private static final String[] serviceConnectionMethods = { SERVICECONNECTION_ONSERVICECONNECTED,
			SERVICECONNECTION_ONSERVICEDISCONNECTED };
	private static final List<String> serviceConnectionMethodList = Arrays.asList(serviceConnectionMethods);

	private static final String[] activityClasses = {ACTIVITYCLASS, APPCOMPATACTIVITYCLASS_V4, APPCOMPATACTIVITYCLASS_V7, APPCOMPATACTIVITYCLASS_ANDROIDX};
	private static final String[] fragmentClasses = {FRAGMENTCLASS, SUPPORTFRAGMENTCLASS, NEWFRAGMENTCLASS};
	/*
	 * ========================================================================
	 */

	public static List<String> getActivityLifecycleMethods() {
		return activityMethodList;
	}

	public static List<String> getServiceLifecycleMethods() {
		return serviceMethodList;
	}

	public static List<String> getFragmentLifecycleMethods() {
		return fragmentMethodList;
	}

	public static List<String> getGCMIntentServiceMethods() {
		return gcmIntentServiceMethodList;
	}

	public static List<String> getGCMListenerServiceMethods() {
		return gcmListenerServiceMethodList;
	}

	public static List<String> getBroadcastLifecycleMethods() {
		return broadcastMethodList;
	}

	public static List<String> getContentproviderLifecycleMethods() {
		return contentProviderMethodList;
	}

	public static List<String> getApplicationLifecycleMethods() {
		return applicationMethodList;
	}

	public static List<String> getActivityLifecycleCallbackMethods() {
		return activityLifecycleMethodList;
	}

	public static List<String> getComponentCallbackMethods() {
		return componentCallbackMethodList;
	}

	public static List<String> getComponentCallback2Methods() {
		return componentCallback2MethodList;
	}

	public static List<String> getServiceConnectionMethods() {
		return serviceConnectionMethodList;
	}
	/*
	 * ========================================================================
	 */

	private static final String[] lifecycleClasses = new String[]{ACTIVITYCLASS, SERVICECLASS, FRAGMENTCLASS,
			BROADCASTRECEIVERCLASS, CONTENTPROVIDERCLASS, APPLICATIONCLASS, APPCOMPATACTIVITYCLASS_V4, APPCOMPATACTIVITYCLASS_V7,
			NEWAPPCOMPATACTIVITYCLASS, APPCOMPATACTIVITYCLASS_ANDROIDX, ANDROIDXCONTEXTTHEMEWRAPPER, ANDROIDXCONTEXTWRAPPER, ANDROIDXCONTEXT,
			ANDROIDXOBJECT};
	public static final Set<String> lifecycleClassesSet = new HashSet<>(Arrays.asList(lifecycleClasses));

	public static boolean isLifecycleClass(String className) {
		if (lifecycleClassesSet.contains(className)) {
			return true;
		}
		if (className.startsWith(APP_V4)) {
			return true;
		}
		return false;
	}

	/**
	 * Do not allow anyone to instantiate this class.
	 */
	private AndroidEntryPointConstants() {

	}


	//lifecycle-add
	private static boolean init = false;
	private static Map<SootClass, FRAGMENTTYPE> cache = null;
	private static SootClass v4Fragment = null;
	private static SootClass androidFragment = null;
	private static SootClass androidxFragment = null;

	public static FRAGMENTTYPE getFrgamentType(SootClass fragmentClass){
		if (!init) {
			cache = new ConcurrentHashMap<>();
			v4Fragment = Scene.v().getSootClassUnsafe(SUPPORTFRAGMENTCLASS);
			androidFragment = Scene.v().getSootClassUnsafe(FRAGMENTCLASS);
			androidxFragment = Scene.v().getSootClassUnsafe(NEWFRAGMENTCLASS);
			init = true;
		}
		if (null == v4Fragment || androidFragment == null || fragmentClass == androidxFragment) {
			return FRAGMENTTYPE.ANDROIDX;
		}
		if (null == v4Fragment || androidxFragment == null || fragmentClass == androidFragment) {
			return FRAGMENTTYPE.ANDROID;
		}
		if (null == androidxFragment || androidFragment == null || fragmentClass == v4Fragment) {
			return FRAGMENTTYPE.V4;
		}
		if (cache.containsKey(fragmentClass)) {
			return cache.get(fragmentClass);
		}
		SootClass currentClass = fragmentClass;
		while (currentClass.hasSuperclass()) {
			currentClass = currentClass.getSuperclassUnsafe();
			if (currentClass == v4Fragment) {
				cache.put(fragmentClass, FRAGMENTTYPE.V4);
				return FRAGMENTTYPE.V4;
			} else if (currentClass == androidFragment) {
				cache.put(fragmentClass, FRAGMENTTYPE.ANDROID);
				return FRAGMENTTYPE.ANDROID;
			} else if (currentClass == androidxFragment) {
				cache.put(fragmentClass, FRAGMENTTYPE.ANDROIDX);
				return FRAGMENTTYPE.ANDROIDX;
			}
		}
		return FRAGMENTTYPE.UNKNOWN;
	}

	public static boolean isActivityOrFragment(SootClass givenClass) {
		Hierarchy h = Scene.v().getActiveHierarchy();
		Set<String> newSets = new HashSet<>(Arrays.asList(activityClasses));
		newSets.addAll(Arrays.asList(fragmentClasses));
		for (String componentName : newSets) {
			SootClass componentClass = Scene.v().getSootClassUnsafe(componentName);
			if(h.isClassSubclassOf(givenClass, componentClass)) {
				return true;
			}
		}
		return false;
	}


	private static Set<String> allLCMethods = null;
	static {
		allLCMethods = new HashSet<>();
		allLCMethods.addAll(activityMethodList);
		allLCMethods.addAll(serviceMethodList);
		allLCMethods.addAll(broadcastMethodList);
		allLCMethods.addAll(contentProviderMethodList);
		allLCMethods.addAll(serviceConnectionMethodList);
	}
	public static Set<String> getAllLCMethods() {
		return allLCMethods;
	}
	public static boolean isLCMethod(SootMethod m) {
		return (allLCMethods.contains(m.getSubSignature()));
	}

}
