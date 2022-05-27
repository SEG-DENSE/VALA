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
package soot.jimple.infoflow.android;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

import javax.xml.stream.XMLStreamException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;

import heros.solver.Pair;
import soot.*;
import soot.jimple.infoflow.AbstractInfoflow;
import soot.jimple.infoflow.IInfoflow;
import soot.jimple.infoflow.Infoflow;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration.CallbackConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration.SootIntegrationMode;
import soot.jimple.infoflow.android.callbacks.AbstractCallbackAnalyzer;
import soot.jimple.infoflow.android.callbacks.CallbackDefinition;
import soot.jimple.infoflow.android.callbacks.CallbackDefinition.CallbackType;
import soot.jimple.infoflow.android.callbacks.DefaultCallbackAnalyzer;
import soot.jimple.infoflow.android.callbacks.FastCallbackAnalyzer;
import soot.jimple.infoflow.android.callbacks.filters.AlienFragmentFilter;
import soot.jimple.infoflow.android.callbacks.filters.AlienHostComponentFilter;
import soot.jimple.infoflow.android.callbacks.filters.ApplicationCallbackFilter;
import soot.jimple.infoflow.android.callbacks.filters.UnreachableConstructorFilter;
import soot.jimple.infoflow.android.config.SootConfigForAndroid;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.infoflow.android.resources.ARSCFileParser;
import soot.jimple.infoflow.android.resources.ARSCFileParser.AbstractResource;
import soot.jimple.infoflow.android.resources.ARSCFileParser.StringResource;
import soot.jimple.infoflow.android.resources.LayoutFileParser;
import soot.jimple.infoflow.android.resources.controls.AndroidLayoutControl;
import soot.jimple.infoflow.pattern.entryPointCreators.PatternMainEntryPointCreator;
import soot.jimple.infoflow.pattern.mappingmethods.MappingMethodHelper;
import soot.jimple.infoflow.pattern.mappingmethods.MappingMethodProvider;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPHelper;
import soot.jimple.infoflow.pattern.result.*;
import soot.jimple.infoflow.pattern.skipmethods.SkipMethodsHelper;
import soot.jimple.infoflow.pattern.sourceandsink.IPatternSourceSinkManager;
import soot.jimple.infoflow.pattern.sourceandsink.PatternOpDataProvider;
import soot.jimple.infoflow.pattern.sourceandsink.PatternSourceSinkManager;
import soot.jimple.infoflow.android.source.UnsupportedSourceSinkFormatException;
import soot.jimple.infoflow.cfg.BiDirICFGFactory;
import soot.jimple.infoflow.cfg.LibraryClassPatcher;
import soot.jimple.infoflow.config.IInfoflowConfig;
import soot.jimple.infoflow.handlers.PreAnalysisHandler;
import soot.jimple.infoflow.ipc.IIPCManager;
import soot.jimple.infoflow.memory.FlowDroidMemoryWatcher;
import soot.jimple.infoflow.memory.FlowDroidTimeoutWatcher;
import soot.jimple.infoflow.memory.IMemoryBoundedSolver;
import soot.jimple.infoflow.pattern.patterndata.PatternDataHelper;
import soot.jimple.infoflow.results.InfoflowPerformanceData;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.util.MyOutputer;
import soot.jimple.infoflow.util.MyOwnUtils;
import soot.jimple.infoflow.util.SystemClassHandler;
import soot.jimple.infoflow.values.IValueProvider;
import soot.options.Options;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

public class SetupApplication{

	private final Logger logger = LoggerFactory.getLogger(getClass());

	protected PatternOpDataProvider patternOpDataProvider;
	protected MappingMethodProvider mappingMethodProvider;
	protected MultiMap<SootClass, CallbackDefinition> callbackMethods = new HashMultiMap<>();
	protected MultiMap<SootClass, SootClass> fragmentClasses = new HashMultiMap<>();


	protected InfoflowAndroidConfiguration config = new InfoflowAndroidConfiguration();

	protected Set<SootClass> entrypoints = null;
	protected Set<String> callbackClasses = null;

	protected ARSCFileParser resources = null;
	protected ProcessManifest manifest = null;
	protected IValueProvider valueProvider = null;

	protected final boolean forceAndroidJar;

	protected IPatternSourceSinkManager sourceSinkManager = null;

	protected IInfoflowConfig sootConfig = new SootConfigForAndroid();
	protected BiDirICFGFactory cfgFactory = null;

	protected IIPCManager ipcManager = null;

	protected String callbackFile = "AndroidCallbacks.txt";
	protected SootClass scView = null;

	protected Set<PreAnalysisHandler> preprocessors = new HashSet<>();
	protected Set<LCMethodResultsAvailableHandler> resultsAvailableHandlers = new HashSet<>();

	protected IInPlaceInfoflow infoflow = null;

	/**
	 * Class for aggregating the data flow results obtained through multiple runs of
	 * the data flow solver.
	 * 
	 * @author Steven Arzt
	 *
	 */
	private static class MultiRunResultAggregator implements LCMethodResultsAvailableHandler {

		private final LCMethodSummaryResult aggregatedResults = new LCMethodSummaryResult();
		private LCMethodSummaryResult lastResults = null;
		private IInfoflowCFG lastICFG = null;

		@Override
		public void onResultsAvailable(IInfoflowCFG cfg, LCMethodSummaryResult results) {
			this.aggregatedResults.addAll(results);
			this.lastResults = results;
			this.lastICFG = cfg;
		}

		/**
		 * Gets all data flow results aggregated so far
		 * 
		 * @return All data flow results aggregated so far
		 */
		public LCMethodSummaryResult getAggregatedResults() {
			return this.aggregatedResults;
		}

		/**
		 * Gets the total number of source-to-sink connections from the last partial
		 * result that was added to this aggregator
		 * 
		 * @return The results from the last run of the data flow analysis
		 */
		public LCMethodSummaryResult getLastResults() {
			return this.lastResults;
		}

		/**
		 * Clears the stored result set from the last data flow run
		 */
		public void clearLastResults() {
			this.lastResults = null;
			this.lastICFG = null;
		}

		/**
		 * Gets the ICFG that was returned together with the last set of data flow
		 * results
		 * 
		 * @return The ICFG that was returned together with the last set of data flow
		 *         results
		 */
		public IInfoflowCFG getLastICFG() {
			return this.lastICFG;
		}

	}

	/**
	 * Creates a new instance of the {@link SetupApplication} class
	 * 
	 * @param config The data flow configuration to use
	 */
	public SetupApplication(InfoflowAndroidConfiguration config) {
		this(config, null);
	}

	/**
	 * Creates a new instance of the {@link SetupApplication} class
	 * 
	 * @param androidJar      The path to the Android SDK's "platforms" directory if
	 *                        Soot shall automatically select the JAR file to be
	 *                        used or the path to a single JAR file to force one.
	 * @param apkFileLocation The path to the APK file to be analyzed
	 */
	public SetupApplication(String androidJar, String apkFileLocation) {
		this(getConfig(androidJar, apkFileLocation));
	}

	/**
	 * Creates a new instance of the {@link SetupApplication} class
	 * 
	 * @param androidJar      The path to the Android SDK's "platforms" directory if
	 *                        Soot shall automatically select the JAR file to be
	 *                        used or the path to a single JAR file to force one.
	 * @param apkFileLocation The path to the APK file to be analyzed
	 * @param ipcManager      The IPC manager to use for modeling inter-component
	 *                        and inter-application data flows
	 */
	public SetupApplication(String androidJar, String apkFileLocation, IIPCManager ipcManager) {
		this(getConfig(androidJar, apkFileLocation), ipcManager);
	}

	/**
	 * Creates a basic data flow configuration with only the input files set
	 * 
	 * @param androidJar      The path to the Android SDK's "platforms" directory if
	 *                        Soot shall automatically select the JAR file to be
	 *                        used or the path to a single JAR file to force one.
	 * @param apkFileLocation The path to the APK file to be analyzed
	 * @return The new configuration
	 */
	private static InfoflowAndroidConfiguration getConfig(String androidJar, String apkFileLocation) {
		InfoflowAndroidConfiguration config = new InfoflowAndroidConfiguration();
		config.getAnalysisFileConfig().setTargetAPKFile(apkFileLocation);
		config.getAnalysisFileConfig().setAndroidPlatformDir(androidJar);
		return config;
	}

	/**
	 * Creates a new instance of the {@link SetupApplication} class
	 * 
	 * @param config     The data flow configuration to use
	 * @param ipcManager The IPC manager to use for modelling inter-component and
	 *                   inter-application data flows
	 */
	public SetupApplication(InfoflowAndroidConfiguration config, IIPCManager ipcManager) {
		this.config = config;
		this.ipcManager = ipcManager;

		// We can use either a specific platform JAR file or automatically
		// select the right one
		if (config.getSootIntegrationMode() == SootIntegrationMode.CreateNewInstace) {
			String platformDir = config.getAnalysisFileConfig().getAndroidPlatformDir();
			if (platformDir == null || platformDir.isEmpty())
				throw new RuntimeException("Android platform directory not specified");
			File f = new File(platformDir);
			this.forceAndroidJar = f.isFile();
		} else {
			this.forceAndroidJar = false;
		}
	}


	/**
	 * Gets the set of classes containing entry point methods for the lifecycle
	 * 
	 * @return The set of classes containing entry point methods for the lifecycle
	 */
	public Set<SootClass> getEntrypointClasses() {
		return entrypoints;
	}

	/**
	 * Prints list of classes containing entry points to stdout
	 */
	public void printEntrypoints() {
		if (this.entrypoints == null)
			logger.error("Entry points not initialized");
		else {
			logger.info("Classes containing entry points:");
			for (SootClass sc : entrypoints)
				logger.info("\t" + sc.getName());
			logger.info("End of Entrypoints");
		}
	}

	/**
	 * Sets the class names of callbacks. If this value is null, it automatically
	 * loads the names from AndroidCallbacks.txt as the default behavior.
	 * 
	 * @param callbackClasses The class names of callbacks or null to use the
	 *                        default file.
	 */
	public void setCallbackClasses(Set<String> callbackClasses) {
		this.callbackClasses = callbackClasses;
	}

	public Set<String> getCallbackClasses() {
		return callbackClasses;
	}
	/**
	 * Parses common app resources such as the manifest file
	 * 
	 * @throws IOException            Thrown if the given source/sink file could not
	 *                                be read.
	 * @throws XmlPullParserException Thrown if the Android manifest file could not
	 *                                be read.
	 */
	protected void parseAppResources() throws IOException, XmlPullParserException {
		final String targetAPK = config.getAnalysisFileConfig().getTargetAPKFile();

		// To look for callbacks, we need to start somewhere. We use the Android
		// lifecycle methods for this purpose.
		this.manifest = new ProcessManifest(targetAPK);
		Set<String> entryPoints = manifest.getEntryPointClasses();
		this.entrypoints = new HashSet<>(entryPoints.size());
		for (String className : entryPoints) {
			SootClass sc = Scene.v().getSootClassUnsafe(className);
			if (sc != null)
				this.entrypoints.add(sc);
		}

		// Parse the resource file
		long beforeARSC = System.nanoTime();
		this.resources = new ARSCFileParser();
		this.resources.parse(targetAPK);
		logger.info("ARSC file parsing took " + (System.nanoTime() - beforeARSC) / 1E9 + " seconds");
	}


	/**
	 * Calculates the sets of sources, sinks, entry points, and callbacks methods
	 * for the entry point in the given APK file.
	 * @param entryPoint      The entry point for which to calculate the callbacks.
	 *                        Pass null to calculate callbacks for all entry points.
	 * @throws IOException            Thrown if the given source/sink file could not
	 *                                be read.
	 * @throws XmlPullParserException Thrown if the Android manifest file could not
	 *                                be read.
	 */
	private void calculateCallbacks(SootClass entryPoint)
			throws IOException, XmlPullParserException {
		// Add the callback methods
		LayoutFileParser lfp = null;
		if (config.getCallbackConfig().getEnableCallbacks()) {
			if (callbackClasses != null && callbackClasses.isEmpty()) {
				logger.warn("Callback definition file is empty, disabling callbacks");
			} else {
				lfp = createLayoutFileParser();
				switch (config.getCallbackConfig().getCallbackAnalyzer()) {
				case Fast:
					calculateCallbackMethodsFast(lfp, entryPoint);
					break;
				case Default:
					calculateCallbackMethods(lfp, entryPoint);
					break;
				default:
					throw new RuntimeException("Unknown callback analyzer");
				}
			}
		} else if (config.getSootIntegrationMode().needsToBuildCallgraph()) {
			// Create the new iteration of the main method
//			createMainMethod(null);
			constructCallgraphInternal();
			updateLCMethodRecords(null);
		}

		logger.info("Entry point calculation done.");

		if (this.patternOpDataProvider != null) {
			// Create the SourceSinkManager
			sourceSinkManager = createPatternSourceSinkManager();
//			sourceSinkManager = createSourceSinkManager(lfp, callbacks);
		}
	}

	/**
	 * Creates a new layout file parser. Derived classes can override this method to
	 * supply their own parser.
	 * 
	 * @return The newly created layout file parser.
	 */
	protected LayoutFileParser createLayoutFileParser() {
		return new LayoutFileParser(this.manifest.getPackageName(), this.resources);
	}

//	/**
//	 * shall consider as a source or sink, respectively.
//	 *
//	 * @param lfp       The parser that handles the layout XML files
//	 * @param callbacks The callbacks that have been collected so far
//	 * @return The new source sink manager
//	 */
//		AccessPathBasedSourceSinkManager sourceSinkManager = new AccessPathBasedSourceSinkManager(
//				this.sourceSinkProvider.getSources(), this.sourceSinkProvider.getSinks(), this.sourceSinkProvider.getLeakGroups(), callbacks, config,
//				lfp == null ? null : lfp.getUserControlsByID());
//
//		sourceSinkManager.setAppPackageName(this.manifest.getPackageName());
//		sourceSinkManager.setResourcePackages(this.resources.getPackages());
//		return sourceSinkManager;

	protected IPatternSourceSinkManager createPatternSourceSinkManager() {
		PatternSourceSinkManager sourceSinkManager = new PatternSourceSinkManager(PatternDataHelper.v().getInvolvedEntrypoints(), PatternDataHelper.v().getEntryFields());
		sourceSinkManager.setOneSourceAtATimeEnabled(this.config.getOneComponentAtATime());
		sourceSinkManager.setAppPackageName(this.manifest.getPackageName());
		return sourceSinkManager;
	}

	/**
	 * Triggers the callgraph construction in Soot
	 */
	private void constructCallgraphInternal() {
		// If we are configured to use an existing callgraph, we may not replace
		// it. However, we must make sure that there really is one.
		if (config.getSootIntegrationMode() == SootIntegrationMode.UseExistingCallgraph) {
			if (!Scene.v().hasCallGraph())
				throw new RuntimeException("FlowDroid is configured to use an existing callgraph, but there is none");
			return;
		}

		// Run the preprocessors
		for (PreAnalysisHandler handler : this.preprocessors)
			handler.onBeforeCallgraphConstruction();

		// Make sure that we don't have any weird leftovers
		releaseCallgraph();

		// If we didn't create the Soot instance, we can't be sure what its callgraph
		// configuration is
		if (config.getSootIntegrationMode() == SootIntegrationMode.UseExistingInstance)
			configureCallgraph();

		// Construct the actual callgraph
		logger.info("Constructing the callgraph...");
		PackManager.v().getPack("cg").apply();


		// Run the preprocessors
		for (PreAnalysisHandler handler : this.preprocessors)
			handler.onAfterCallgraphConstruction();

		// Make sure that we have a hierarchy
		Scene.v().getOrMakeFastHierarchy();
	}



	/**
	 * Calculates the set of callback methods declared in the XML resource files or
	 * the app's source code
	 * 
	 * @param lfp       The layout file parser to be used for analyzing UI controls
	 * @param component The Android component for which to compute the callbacks.
	 *                  Pass null to compute callbacks for all components.
	 * @throws IOException Thrown if a required configuration cannot be read
	 */
	private void calculateCallbackMethods(LayoutFileParser lfp, SootClass component) throws IOException {
		final CallbackConfiguration callbackConfig = config.getCallbackConfig();

		// Load the APK file
		if (config.getSootIntegrationMode().needsToBuildCallgraph())
			releaseCallgraph();

		// Make sure that we don't have any leftovers from previous runs
		PackManager.v().getPack("wjtp").remove("wjtp.lfp");
		PackManager.v().getPack("wjtp").remove("wjtp.ajc");

		// Get the classes for which to find callbacks
		Set<SootClass> entryPointClasses = getComponentsToAnalyze(component);


		// Collect the callback interfaces implemented in the app's
		// source code. Note that the filters should know all components to
		// filter out callbacks even if the respective component is only
		// analyzed later.
		AbstractCallbackAnalyzer jimpleClass = callbackClasses == null
				? new DefaultCallbackAnalyzer(config, entryPointClasses, callbackFile)
				: new DefaultCallbackAnalyzer(config, entryPointClasses, callbackClasses);
		if (valueProvider != null)
			jimpleClass.setValueProvider(valueProvider);
		jimpleClass.addCallbackFilter(new AlienHostComponentFilter(entrypoints));
		jimpleClass.addCallbackFilter(new ApplicationCallbackFilter(entrypoints));
		jimpleClass.addCallbackFilter(new UnreachableConstructorFilter());
		jimpleClass.collectCallbackMethods();

		// Find the user-defined sources in the layout XML files. This
		// only needs to be done once, but is a Soot phase.
		lfp.parseLayoutFile(config.getAnalysisFileConfig().getTargetAPKFile());

		// Watch the callback collection algorithm's memory consumption
		FlowDroidMemoryWatcher memoryWatcher = null;
		FlowDroidTimeoutWatcher timeoutWatcher = null;
		if (jimpleClass instanceof IMemoryBoundedSolver) {
			memoryWatcher = new FlowDroidMemoryWatcher(config.getMemoryThreshold());
			memoryWatcher.addSolver((IMemoryBoundedSolver) jimpleClass);

			// Make sure that we don't spend too much time in the callback
			// analysis
			if (callbackConfig.getCallbackAnalysisTimeout() > 0) {
				timeoutWatcher = new FlowDroidTimeoutWatcher(callbackConfig.getCallbackAnalysisTimeout());
				timeoutWatcher.addSolver((IMemoryBoundedSolver) jimpleClass);
				timeoutWatcher.start();
			}
		}

		try {
			int depthIdx = 0;
			boolean hasChanged = true;
			boolean isInitial = true;
			while (hasChanged) {
				hasChanged = false;

				// Check whether the solver has been aborted in the meantime
				if (jimpleClass instanceof IMemoryBoundedSolver) {
					if (((IMemoryBoundedSolver) jimpleClass).isKilled())
						break;
				}


				// Create the new iteration of the main method
//				SootMethod mainmethod = createMainMethod(component);


				// Since the gerenation of the main method can take some time,
				// we check again whether we need to stop.
				if (jimpleClass instanceof IMemoryBoundedSolver) {
					if (((IMemoryBoundedSolver) jimpleClass).isKilled())
						break;
				}

				if (!isInitial) {
					// Reset the callgraph
					releaseCallgraph();

					// We only want to parse the layout files once
					PackManager.v().getPack("wjtp").remove("wjtp.lfp");
				}
				isInitial = false;

				// Run the soot-based operations
				constructCallgraphInternal();
				if (!Scene.v().hasCallGraph())
					throw new RuntimeException("No callgraph in Scene even after creating one. That's very sad "
							+ "and should never happen.");
				PackManager.v().getPack("wjtp").apply();

				// Creating all callgraph takes time and memory. Check whether
				// the solver has been aborted in the meantime
				if (jimpleClass instanceof IMemoryBoundedSolver) {
					if (((IMemoryBoundedSolver) jimpleClass).isKilled()) {
						logger.warn("Aborted callback collection because of low memory");
						break;
					}
				}

				// Collect the results of the soot-based phases
				if (this.callbackMethods.putAll(jimpleClass.getCallbackMethods()))
					hasChanged = true;

				if (entrypoints.addAll(jimpleClass.getDynamicManifestComponents()))
					hasChanged = true;

				// Collect the XML-based callback methods
				if (collectXmlBasedCallbackMethods(lfp, jimpleClass))
					hasChanged = true;

				// Avoid callback overruns. If we are beyond the callback limit
				// for one entry point, we may not collect any further callbacks
				// for that entry point.
				if (callbackConfig.getMaxCallbacksPerComponent() > 0) {
					for (Iterator<SootClass> componentIt = this.callbackMethods.keySet().iterator(); componentIt
							.hasNext();) {
						SootClass callbackComponent = componentIt.next();
						if (this.callbackMethods.get(callbackComponent).size() > callbackConfig
								.getMaxCallbacksPerComponent()) {
							componentIt.remove();
							jimpleClass.excludeEntryPoint(callbackComponent);
						}
					}
				}

				// Check depth limiting
				depthIdx++;
				if (callbackConfig.getMaxAnalysisCallbackDepth() > 0
						&& depthIdx >= callbackConfig.getMaxAnalysisCallbackDepth())
					break;

				// If we work with an existing callgraph, the callgraph never
				// changes and thus it doesn't make any sense to go multiple
				// rounds
				if (config.getSootIntegrationMode() == SootIntegrationMode.UseExistingCallgraph)
					break;
			}



			releaseCallgraph();
			PackManager.v().getPack("wjtp").remove("wjtp.lfp");
			updateLCMethodRecords(this.entrypoints);
//			constructCallgraphInternal();
//			PackManager.v().getPack("wjtp").apply();


		} catch (Exception ex) {
			logger.error("Could not calculate callback methods", ex);
			throw ex;
		} finally {
			// Shut down the watchers
			if (timeoutWatcher != null)
				timeoutWatcher.stop();
			if (memoryWatcher != null)
				memoryWatcher.close();
		}



		// Filter out callbacks that belong to fragments that are not used by
		// the host activity
		AlienFragmentFilter fragmentFilter = new AlienFragmentFilter(invertMap(fragmentClasses));
		fragmentFilter.reset();
		for (Iterator<Pair<SootClass, CallbackDefinition>> cbIt = this.callbackMethods.iterator(); cbIt.hasNext();) {
			Pair<SootClass, CallbackDefinition> pair = cbIt.next();

			// Check whether the filter accepts the given mapping
			if (!fragmentFilter.accepts(pair.getO1(), pair.getO2().getTargetMethod()))
				cbIt.remove();
			else if (!fragmentFilter.accepts(pair.getO1(), pair.getO2().getTargetMethod().getDeclaringClass())) {
				cbIt.remove();
			}
		}

		// Avoid callback overruns
		if (callbackConfig.getMaxCallbacksPerComponent() > 0) {
			for (Iterator<SootClass> componentIt = this.callbackMethods.keySet().iterator(); componentIt.hasNext();) {
				SootClass callbackComponent = componentIt.next();
				if (this.callbackMethods.get(callbackComponent).size() > callbackConfig.getMaxCallbacksPerComponent())
					componentIt.remove();
			}
		}


		// Make sure that we don't retain any weird Soot phases
		PackManager.v().getPack("wjtp").remove("wjtp.lfp");
		PackManager.v().getPack("wjtp").remove("wjtp.ajc");

		// Warn the user if we had to abort the callback analysis early
		boolean abortedEarly = false;
		if (jimpleClass instanceof IMemoryBoundedSolver) {
			if (((IMemoryBoundedSolver) jimpleClass).isKilled()) {
				logger.warn("Callback analysis aborted early due to time or memory exhaustion");
				abortedEarly = true;
			}
		}
		if (!abortedEarly)
			logger.info("Callback analysis terminated normally");
	}

	/**
	 * Inverts the given {@link MultiMap}. The keys become values and vice versa
	 * 
	 * @param original The map to invert
	 * @return An inverted copy of the given map
	 */
	private <K, V> MultiMap<K, V> invertMap(MultiMap<V, K> original) {
		MultiMap<K, V> newTag = new HashMultiMap<>();
		for (V key : original.keySet())
			for (K value : original.get(key))
				newTag.put(value, key);
		return newTag;
	}

	/**
	 * Releases the callgraph and all intermediate objects associated with it
	 */
	protected void releaseCallgraph() {
		// If we are configured to use an existing callgraph, we may not release
		// it
		if (config.getSootIntegrationMode() == SootIntegrationMode.UseExistingCallgraph)
			return;

		Scene.v().releaseCallGraph();
		Scene.v().releasePointsToAnalysis();
		Scene.v().releaseReachableMethods();
		G.v().resetSpark();
	}

	/**
	 * Collects the XML-based callback methods, e.g., Button.onClick() declared in
	 * layout XML files
	 * 
	 * @param lfp         The layout file parser
	 * @param jimpleClass The analysis class that gives us a mapping between layout
	 *                    IDs and components
	 * @return True if at least one new callback method has been added, otherwise
	 *         false
	 */
	private boolean collectXmlBasedCallbackMethods(LayoutFileParser lfp, AbstractCallbackAnalyzer jimpleClass) {
		SootMethod smViewOnClick = Scene.v()
				.grabMethod("<android.view.View$OnClickListener: void onClick(android.view.View)>");

		// Collect the XML-based callback methods
		boolean hasNewCallback = false;
		for (final SootClass callbackClass : jimpleClass.getLayoutClasses().keySet()) {
			if (jimpleClass.isExcludedEntryPoint(callbackClass))
				continue;

			Set<Integer> classIds = jimpleClass.getLayoutClasses().get(callbackClass);
			for (Integer classId : classIds) {
				AbstractResource resource = this.resources.findResource(classId);
				if (resource instanceof StringResource) {
					final String layoutFileName = ((StringResource) resource).getValue();

					// Add the callback methods for the given class
					Set<String> callbackMethods = lfp.getCallbackMethods().get(layoutFileName);
					if (callbackMethods != null) {
						for (String methodName : callbackMethods) {
							final String subSig = "void " + methodName + "(android.view.View)";

							// The callback may be declared directly in the
							// class or in one of the superclasses
							SootClass currentClass = callbackClass;
							while (true) {
								SootMethod callbackMethod = currentClass.getMethodUnsafe(subSig);
								if (callbackMethod != null) {
									if (this.callbackMethods.put(callbackClass,
											new CallbackDefinition(callbackMethod, smViewOnClick, CallbackType.Widget)))
										hasNewCallback = true;
									break;
								}
								SootClass sclass = currentClass.getSuperclassUnsafe();
								if (sclass == null) {
									logger.error(String.format("Callback method %s not found in class %s", methodName,
											callbackClass.getName()));
									break;
								}
								currentClass = sclass;
							}
						}
					}

					// Add the fragments for this class
					Set<SootClass> fragments = lfp.getFragments().get(layoutFileName);
					if (fragments != null)
						for (SootClass fragment : fragments)
							if (fragmentClasses.put(callbackClass, fragment)) {
								hasNewCallback = true;
							}

					// For user-defined views, we need to emulate their
					// callbacks
					Set<AndroidLayoutControl> controls = lfp.getUserControls().get(layoutFileName);
					if (controls != null) {
						for (AndroidLayoutControl lc : controls)
							if (!SystemClassHandler.isClassInSystemPackage(lc.getViewClass().getName()))
								registerCallbackMethodsForView(callbackClass, lc);
					}
				} else
					logger.error("Unexpected resource type for layout class");
			}
		}

		// Collect the fragments, merge the fragments created in the code with
		// those declared in Xml files
		if (fragmentClasses.putAll(jimpleClass.getFragmentClasses())) // Fragments
																		// declared
																		// in
																		// code
			hasNewCallback = true;

		if (fragmentClasses.putAll(jimpleClass.getNewFragmentClasses()))
			hasNewCallback = true;



		return hasNewCallback;
	}

	/**
	 * Calculates the set of callback methods declared in the XML resource files or
	 * the app's source code. This method prefers performance over precision and
	 * scans the code including unreachable methods.
	 * 
	 * @param lfp        The layout file parser to be used for analyzing UI controls

	 * @throws IOException Thrown if a required configuration cannot be read
	 */
	private void calculateCallbackMethodsFast(LayoutFileParser lfp, SootClass component) throws IOException {
		// Construct the current callgraph
		releaseCallgraph();
//		createMainMethod(component);
		updateLCMethodRecords(null);
		constructCallgraphInternal();

		// Get the classes for which to find callbacks
		Set<SootClass> entryPointClasses = getComponentsToAnalyze(component);

		// Collect the callback interfaces implemented in the app's
		// source code
		AbstractCallbackAnalyzer jimpleClass = callbackClasses == null
				? new FastCallbackAnalyzer(config, entryPointClasses, callbackFile)
				: new FastCallbackAnalyzer(config, entryPointClasses, callbackClasses);
		if (valueProvider != null)
			jimpleClass.setValueProvider(valueProvider);
		jimpleClass.collectCallbackMethods();

		// Collect the results
		this.callbackMethods.putAll(jimpleClass.getCallbackMethods());
		this.entrypoints.addAll(jimpleClass.getDynamicManifestComponents());

		// Find the user-defined sources in the layout XML files. This
		// only needs to be done once, but is a Soot phase.
		lfp.parseLayoutFileDirect(config.getAnalysisFileConfig().getTargetAPKFile());

		// Collect the XML-based callback methods
		collectXmlBasedCallbackMethods(lfp, jimpleClass);

		// Construct the final callgraph
		releaseCallgraph();
		updateLCMethodRecords(null);
//		createMainMethod(component);
		constructCallgraphInternal();
	}

	/**
	 * Registers the callback methods in the given layout control so that they are
	 * included in the dummy main method
	 * 
	 * @param callbackClass The class with which to associate the layout callbacks
	 * @param lc            The layout control whose callbacks are to be associated
	 *                      with the given class
	 */
	private void registerCallbackMethodsForView(SootClass callbackClass, AndroidLayoutControl lc) {
		// Ignore system classes
		if (SystemClassHandler.isClassInSystemPackage(callbackClass.getName()))
			return;

		// Get common Android classes
		if (scView == null)
			scView = Scene.v().getSootClass("android.view.View");

		// Check whether the current class is actually a view
		if (!Scene.v().getOrMakeFastHierarchy().canStoreType(lc.getViewClass().getType(), scView.getType()))
			return;

		// There are also some classes that implement interesting callback
		// methods.
		// We model this as follows: Whenever the user overwrites a method in an
		// Android OS class, we treat it as a potential callback.
		SootClass sc = lc.getViewClass();
		Map<String, SootMethod> systemMethods = new HashMap<>(10000);
		for (SootClass parentClass : Scene.v().getActiveHierarchy().getSuperclassesOf(sc)) {
			if (parentClass.getName().startsWith("android."))
				for (SootMethod sm : parentClass.getMethods())
					if (!sm.isConstructor())
						systemMethods.put(sm.getSubSignature(), sm);
		}

		// Scan for methods that overwrite parent class methods
		for (SootMethod sm : sc.getMethods()) {
			if (!sm.isConstructor()) {
				SootMethod parentMethod = systemMethods.get(sm.getSubSignature());
				if (parentMethod != null)
					// This is a real callback method
					this.callbackMethods.put(callbackClass,
							new CallbackDefinition(sm, parentMethod, CallbackType.Widget));
			}
		}
	}

	private PatternMainEntryPointCreator entryPointCreator = null;
	private void updateLCMethodRecords(Set<SootClass> entrypoints) {
//		config.setEnableTypeChecking(false);
		PatternDataHelper helper = PatternDataHelper.v();
		if (entryPointCreator == null)
			entryPointCreator = new PatternMainEntryPointCreator(entrypoints, helper.hasPattern5());
		else {
			entryPointCreator.resetAndRemoveAllGeneratedClasses();
		}
		MultiMap<SootClass, SootMethod> callbackMethodSigs = new HashMultiMap<>();
		// Get all callbacks for all components
		for (SootClass sc : this.callbackMethods.keySet()) {
			Set<CallbackDefinition> callbackDefs = this.callbackMethods.get(sc);
			if (callbackDefs != null)
				for (CallbackDefinition cd : callbackDefs)
					callbackMethodSigs.put(sc, cd.getTargetMethod());
		}
		entryPointCreator.setCallbackFunctions(callbackMethodSigs);
//		entryPointCreator.setFragments(fragmentClasses);
		Set<Pair<SootMethod, SootMethod>> dummyMainMethods = entryPointCreator.create();
		List<SootMethod> tempEntrypoints = new LinkedList<>();
		for (Pair<SootMethod, SootMethod> pair : dummyMainMethods) {
			tempEntrypoints.add(pair.getO2());
		}

		Scene.v().setEntryPoints(tempEntrypoints);
		for (SootMethod m :tempEntrypoints) {
			if (!m.getDeclaringClass().isInScene())
				Scene.v().addClass(m.getDeclaringClass());
			m.getDeclaringClass().setApplicationClass();
		}
		constructCallgraphInternal();
		PackManager.v().getPack("wjtp").apply();
		helper.getAllFragmentsAndAllActivities();
		helper.updateInvolvedEntrypoints(entrypoints, null);
		MultiMap<SootClass, SootField> reportFields = helper.getReportEntryFields();
		MyOutputer.getInstance().updateReportEntrypoints(reportFields, entrypoints);
		MyOutputer.getInstance().updateAllEntrypoints(entrypoints);
//		System.out.println("-------------------------reportEntry--------------------------------------");
//			System.out.println(cClass.getName());
//			System.out.println();
//		System.out.println("-------------------------allEntry--------------------------------------");
//
//			System.out.println(cClass.getName());
//			System.out.println();
//		entryPointCreator.removeIrrelevantComponents(helper.getInvolvedEntrypoints());
//
//		releaseCallgraph();
//		PackManager.v().getPack("wjtp").remove("wjtp.lfp");

		Scene.v().setEntryPoints(new LinkedList<>(helper.getEntryMethods()));
	}

	/**
	 * Creates the main method based on the current callback information, injects it
	 * into the Soot scene.
	 * 
	 * @param component The class name of a component to create a main method containing only
	 *            that component, or null to create main method for all components
	 */
//		// There is no need to create a main method if we don't want to generate
//		// a callgraph
//		if (config.getSootIntegrationMode() == SootIntegrationMode.UseExistingCallgraph)
//			return null;
//
//		// Always update the entry point creator to reflect the newest set
//		// of callback methods
//		entryPointCreator = createEntryPointCreator(component);
//		SootMethod dummyMainMethod = entryPointCreator.createDummyMain();
//		Scene.v().setEntryPoints(Collections.singletonList(dummyMainMethod));
//		if (!dummyMainMethod.getDeclaringClass().isInScene())
//			Scene.v().addClass(dummyMainMethod.getDeclaringClass());
//
//		// addClass() declares the given class as a library class. We need to
//		// fix this.
//		dummyMainMethod.getDeclaringClass().setApplicationClass();
//		return dummyMainMethod;

	/**
	 * Gets the source/sink manager constructed for FlowDroid. Make sure to call
	 * constructCallgraph() first, or you will get a null result.
	 * 
	 * @return FlowDroid's source/sink manager
	 */
	public IPatternSourceSinkManager getSourceSinkManager() {
		return sourceSinkManager;
	}

	/**
	 * Builds the classpath for this analysis
	 * 
	 * @return The classpath to be used for the taint analysis
	 */
	private String getClasspath() {
		final String androidJar = config.getAnalysisFileConfig().getAndroidPlatformDir();
		final String apkFileLocation = config.getAnalysisFileConfig().getTargetAPKFile();
		final String additionalClasspath = config.getAnalysisFileConfig().getAdditionalClasspath();

		String classpath = forceAndroidJar ? androidJar : Scene.v().getAndroidJarPath(androidJar, apkFileLocation);
		if (additionalClasspath != null && !additionalClasspath.isEmpty())
			classpath += File.pathSeparator + additionalClasspath;
		logger.debug("soot classpath: " + classpath);
		return classpath;
	}

	/**
	 * Initializes soot for running the soot-based phases of the application
	 * metadata analysis
	 */
	private void initializeSoot() {
		logger.info("Initializing Soot...");

		final String androidJar = config.getAnalysisFileConfig().getAndroidPlatformDir();
		final String apkFileLocation = config.getAnalysisFileConfig().getTargetAPKFile();

		// Clean up any old Soot instance we may have
		G.reset();

		Options.v().set_no_bodies_for_excluded(true);
		Options.v().set_allow_phantom_refs(true);
		if (config.getWriteOutputFiles())
			Options.v().set_output_format(Options.output_format_jimple);
		else
			Options.v().set_output_format(Options.output_format_none);
		Options.v().set_whole_program(true);
		Options.v().set_process_dir(Collections.singletonList(apkFileLocation));
		if (forceAndroidJar)
			Options.v().set_force_android_jar(androidJar);
		else
			Options.v().set_android_jars(androidJar);
		Options.v().set_src_prec(Options.src_prec_apk_class_jimple);
		Options.v().set_keep_line_number(false);
		Options.v().set_keep_offset(false);
		Options.v().set_throw_analysis(Options.throw_analysis_dalvik);
		Options.v().set_process_multiple_dex(config.getMergeDexFiles());
		Options.v().set_ignore_resolution_errors(true);
		Scene.v().addBasicClass("java.io.PrintStream", SootClass.SIGNATURES);
		Scene.v().addBasicClass("java.lang.System", SootClass.SIGNATURES);
		// Set the Soot configuration options. Note that this will needs to be
		// done before we compute the classpath.
		if (sootConfig != null)
			sootConfig.setSootOptions(Options.v(), config);
		Options.v().set_process_multiple_dex(true);
		Options.v().set_soot_classpath(getClasspath());
		Options.v().ignore_classpath_errors();
		Options.v().ignore_resolution_errors();
		Options.v().ignore_resolving_levels();

		Main.v().autoSetOptions();
		configureCallgraph();

		// Load whatever we need
		logger.info("Loading dex files...");
		Scene.v().loadNecessaryClasses();

		// Make sure that we have valid Jimple bodies
		PackManager.v().getPack("wjpp").apply();

		// Patch the callgraph to support additional edges. We do this now,
		// because during callback discovery, the context-insensitive callgraph
		// algorithm would flood us with invalid edges.
		LibraryClassPatcher patcher = new LibraryClassPatcher();
		patcher.patchLibraries();


	}

	/**
	 * Configures the callgraph options for Soot according to FlowDroid's settings
	 */
	protected void configureCallgraph() {
		// Configure the callgraph algorithm
		switch (config.getCallgraphAlgorithm()) {
		case AutomaticSelection:
		case SPARK:
			Options.v().setPhaseOption("cg.spark", "on");
			break;
		case GEOM:
			Options.v().setPhaseOption("cg.spark", "on");
			AbstractInfoflow.setGeomPtaSpecificOptions();
			break;
		case CHA:
			Options.v().setPhaseOption("cg.cha", "on");
			break;
		case RTA:
			Options.v().setPhaseOption("cg.spark", "on");
			Options.v().setPhaseOption("cg.spark", "rta:true");
			Options.v().setPhaseOption("cg.spark", "on-fly-cg:false");
			break;
		case VTA:
			Options.v().setPhaseOption("cg.spark", "on");
			Options.v().setPhaseOption("cg.spark", "vta:true");
			break;
		default:
			throw new RuntimeException("Invalid callgraph algorithm");
		}
		if (config.getEnableReflection())
			Options.v().setPhaseOption("cg", "types-for-invoke:true");
	}

	/**
	 * Common interface for specialized versions of the {@link Infoflow} class that
	 * allows the data flow analysis to be run inside an existing Soot instance
	 * 
	 * @author Steven Arzt
	 *
	 */
	protected static interface IInPlaceInfoflow extends IInfoflow {

		public void runAnalysis(final IPatternSourceSinkManager sourcesSinks, SootMethod entryPoint);

	}

	/**
	 * Specialized {@link Infoflow} class that allows the data flow analysis to be
	 * run inside an existing Soot instance
	 * 
	 * @author Steven Arzt
	 *
	 */
	protected static class InPlaceInfoflow extends Infoflow implements IInPlaceInfoflow {

		/**
		 * Creates a new instance of the Infoflow class for analyzing Android APK files.
		 * 
		 * @param androidPath                 If forceAndroidJar is false, this is the
		 *                                    base directory of the platform files in
		 *                                    the Android SDK. If forceAndroidJar is
		 *                                    true, this is the full path of a single
		 *                                    android.jar file.
		 * @param forceAndroidJar             True if a single platform JAR file shall
		 *                                    be forced, false if Soot shall pick the
		 *                                    appropriate platform version
		 * @param icfgFactory                 The interprocedural CFG to be used by the
		 *                                    InfoFlowProblem
		 * @param additionalEntryPointMethods Additional methods generated by the entry
		 *                                    point creator that are not directly entry
		 *                                    ypoints on their own
		 */
		public InPlaceInfoflow(String androidPath, boolean forceAndroidJar, BiDirICFGFactory icfgFactory,
				Collection<SootMethod> additionalEntryPointMethods) {
			super(androidPath, forceAndroidJar, icfgFactory);
			this.additionalEntryPointMethods = additionalEntryPointMethods;
		}

		@Override
		public void runAnalysis(final IPatternSourceSinkManager sourcesSinks, SootMethod entryPoint) {
			this.dummyMainMethod = entryPoint;
			super.runAnalysis(sourcesSinks);
		}

	}

	/**
	 * Runs the data flow analysis.
	 * 
	 * @throws IOException            Thrown if the given source/sink file could not
	 *                                be read.
	 * @throws XmlPullParserException Thrown if the Android manifest file could not
	 *                                be read.
	 */
	public LCMethodSummaryResult runInfoflow() throws IOException {
		// If we don't have a source/sink file by now, we cannot run the data
		// flow analysis
		String sourceSinkFile = config.getAnalysisFileConfig().getSourceSinkFile();
		if (sourceSinkFile == null || sourceSinkFile.isEmpty())
			throw new RuntimeException("No source/sink file specified for the data flow analysis");
		String fileExtension = sourceSinkFile.substring(sourceSinkFile.lastIndexOf("."));
		fileExtension = fileExtension.toLowerCase();


//				parser = XMLSourceSinkParser.fromFile(sourceSinkFile,
//						new ConfigurationBasedCategoryFilter(config.getSourceSinkConfig()));
//				parser = PermissionMethodParser.fromFile(sourceSinkFile);
//			else if (fileExtension.equals(".rifl"))
//				parser = new RIFLSourceSinkDefinitionProvider(sourceSinkFile);
			if (!fileExtension.equals(".xml"))
				throw new UnsupportedSourceSinkFormatException("The Inputfile isn't a .txt or .xml file.");
//			throw new IOException("Could not read XML file", ex);

		return runInfoflow(sourceSinkFile);
	}

	private String guessPackageName() {
		String name = manifest.getPackageName();
		for (String c : manifest.getEntryPointClasses()) {
			String newname = MyOwnUtils.getSameStartStr(c, name);
			if (!"".equals(newname) && newname.split("\\.").length >= 3) {
				name = newname;
			}
		}
		return name;
	}

	/**
	 * Runs the data flow analysis.
	 *
	 */
	public LCMethodSummaryResult runInfoflow(String opxmlfilepath) throws IOException {
		// Reset our object state
		this.infoflow = null;

		// Perform some sanity checks on the configuration
		if (config.getSourceSinkConfig().getEnableLifecycleSources() && config.getIccConfig().isIccEnabled()) {
			logger.warn("ICC model specified, automatically disabling lifecycle sources");
			config.getSourceSinkConfig().setEnableLifecycleSources(false);
		}

		// Start a new Soot instance
		if (config.getSootIntegrationMode() == SootIntegrationMode.CreateNewInstace) {
			G.reset();
			initializeSoot();
		}
		patternOpDataProvider = PatternOpDataProvider.fromFile(opxmlfilepath);
		String mmfilename = config.getMappingMethodsFile();
		if (null != mmfilename) {
			mappingMethodProvider = MappingMethodProvider.fromFile(config, mmfilename);
			MappingMethodHelper.v().init(mappingMethodProvider.getAllDefs());
		}
		LCResourceOPHelper ophelper = LCResourceOPHelper.init(patternOpDataProvider);

		// Perform basic app parsing
		try {
			parseAppResources();
		} catch (IOException | XmlPullParserException e) {
			logger.error("Callgraph construction failed", e);
			throw new RuntimeException("Callgraph construction failed", e);
		}
//		String adaptedpakcagename = guessPackageName();
//				c.setLibraryClass();

		MultiRunResultAggregator resultAggregator = new MultiRunResultAggregator();

		// We need at least one entry point
		if (entrypoints == null || entrypoints.isEmpty()) {
			logger.warn("No entry points");
			return null;
		}
		//lifecycle-add
		PatternDataHelper.v().init(PatternDataHelper.testPattern, manifest.targetSdkVersion(), manifest.getMinSdkVersion(), ophelper, this.config);

		// In one-component-at-a-time, we do not have a single entry point
		// creator. For every entry point, run the data flow analysis.
		if (config.getOneComponentAtATime()) {
			List<SootClass> entrypointWorklist = new ArrayList<>(entrypoints);
			while (!entrypointWorklist.isEmpty()) {
				SootClass entrypoint = entrypointWorklist.remove(0);
				processEntryPoint(ophelper, resultAggregator, entrypointWorklist.size(), entrypoint);
			}
		} else
			processEntryPoint(ophelper, resultAggregator, -1, null);

		// Write the results to disk if requested
		LCMethodSummaryResult results = resultAggregator.getAggregatedResults();
		Set<ErrorUsageDetailedReport> reports = searchForErrorUsages(results);
		serializeResults(results, reports, resultAggregator.getLastICFG());
		// We return the aggregated results
		this.infoflow = null;
		resultAggregator.clearLastResults();
		PatternDataHelper.v().clear();
		return resultAggregator.getAggregatedResults();
	}

	/**
	 * Runs the data flow analysis on the given entry point class
	 *
	 * @param resultAggregator An object for aggregating the results from the
	 *                         individual data flow runs
	 * @param numEntryPoints   The total number of runs (for logging)
	 * @param entrypoint       The current entry point to analyze
	 */
	protected void processEntryPoint(LCResourceOPHelper ophelper,
			MultiRunResultAggregator resultAggregator, int numEntryPoints, SootClass entrypoint) {
		long beforeEntryPoint = System.nanoTime();

		// Get rid of leftovers from the last entry point
		resultAggregator.clearLastResults();

		// Perform basic app parsing
		long callbackDuration = System.nanoTime();
		try {
			if (config.getOneComponentAtATime())
				calculateCallbacks(entrypoint);
			else
				calculateCallbacks(null); 
		} catch (IOException | XmlPullParserException e) {
			logger.error("Callgraph construction failed: " + e.getMessage(), e);
			throw new RuntimeException("Callgraph construction failed", e);
		}
		callbackDuration = Math.round((System.nanoTime() - callbackDuration) / 1E9);
		logger.info(
				String.format("Collecting callbacks and building a callgraph took %d seconds", (int) callbackDuration));

		// Create a new entry point and compute the flows in it. If we
		// analyze all components together, we do not need a new callgraph,
		// but can reuse the one from the callback collection phase.
////			createMainMethod(entrypoint);
//			updateLCMethodRecords(null);
//			constructCallgraphInternal();

		// Create and run the data flow tracker
		infoflow = createInfoflow();
		infoflow.addResultsAvailableHandler(resultAggregator);
		infoflow.setLCResourceOPHelper(ophelper);

		infoflow.runAnalysis(sourceSinkManager, null); 


		// Print out the found results
		{
//			int summaryCount = resultAggregator.getLastResults() == null ? 0 : resultAggregator.getLastResults().size();
//			if (config.getOneComponentAtATime())
//			else
		}

		// Update the performance object with the real data
		{
			LCMethodSummaryResult lastResults = resultAggregator.getLastResults();
			if (lastResults != null) {
				InfoflowPerformanceData perfData = lastResults.getPerformanceData();
				perfData.setCallgraphConstructionSeconds((int) callbackDuration);
				perfData.setTotalRuntimeSeconds((int) Math.round((System.nanoTime() - beforeEntryPoint) / 1E9));
			}
		}

		// We don't need the computed callbacks anymore
		this.callbackMethods.clear();
		this.fragmentClasses.clear();
		// Notify our result handlers
		for (LCMethodResultsAvailableHandler handler : resultsAvailableHandlers)
			handler.onResultsAvailable(resultAggregator.getLastICFG(), resultAggregator.getLastResults());
	}

	/**
	 * Writes the given data flow results into the configured output file
	 * 
	 * @param results The data flow results to write out
	 * @param cfg     The control flow graph to use for writing out the results
	 */
	private void serializeResults(LCMethodSummaryResult results, Set<ErrorUsageDetailedReport> report, IInfoflowCFG cfg) {
		String resultsFile = config.getAnalysisFileConfig().getOutputFile();
		if (resultsFile != null && !resultsFile.isEmpty()) {
			PatternResultsSerializer serializer = new PatternResultsSerializer(cfg, config);
			try {
				serializer.serialize(results, report, resultsFile);
			} catch (FileNotFoundException ex) {
				System.err.println("Could not write data flow results to file: " + ex.getMessage());
				ex.printStackTrace();
			} catch (XMLStreamException ex) {
				System.err.println("Could not write data flow results to file: " + ex.getMessage());
				ex.printStackTrace();
			}
		}
	}

	private Set<ErrorUsageDetailedReport> searchForErrorUsages(LCMethodSummaryResult results) throws IOException {
		String usageFile = config.getErrorUsageFile();
		PatternErrorUsageChecker checker = new PatternErrorUsageChecker(PatternDataHelper.v().getAllPatterns(), usageFile);
		return checker.check(results);
	}

	/**
	 * Instantiates and configures the data flow engine
	 * 
	 * @return A properly configured instance of the {@link Infoflow} class
	 */
	private IInPlaceInfoflow createInfoflow() {
		// Some sanity checks
//			if (entryPointCreator == null)
//				throw new RuntimeException("No entry point available");
//			if (entryPointCreator.getComponentToEntryPointInfo() == null)
//				throw new RuntimeException("No information about component entry points available");

		// Get the component lifecycle methods
		Set<SootMethod> lifecycleMethods = new HashSet<>();
		lifecycleMethods.addAll(PatternDataHelper.v().getEntryMethods());

		// Initialize and configure the data flow tracker
		IInPlaceInfoflow info = createInfoflowInternal(lifecycleMethods);
		if (ipcManager != null)
			info.setIPCManager(ipcManager);
		info.setConfig(config);
		info.setSootConfig(sootConfig);
//		info.setTaintWrapper(taintWrapper);
//		info.setTaintPropagationHandler(taintPropagationHandler);
//		info.setBackwardsPropagationHandler(backwardsPropagationHandler);

//
//			@Override
//			public IMemoryManager<Abstraction, Unit> getMemoryManager(boolean tracingEnabled,
//				return new AndroidMemoryManager(tracingEnabled, erasePathData, entrypoints);
//
//		info.setMemoryManagerFactory(null);


		return info;
	}

	/**
	 * Creates the data flow engine on which to run the analysis. Derived classes
	 * can override this method to use other data flow engines.
	 * 
	 * @param lifecycleMethods The set of Android lifecycle methods to consider
	 * @return The data flow engine
	 */
	protected IInPlaceInfoflow createInfoflowInternal(Collection<SootMethod> lifecycleMethods) {
		final String androidJar = config.getAnalysisFileConfig().getAndroidPlatformDir();
		return new InPlaceInfoflow(androidJar, forceAndroidJar, cfgFactory, lifecycleMethods);
	}

	/**
	 * Gets the components to analyze. If the given component is not null, we assume
	 * that only this component and the application class (if any) shall be
	 * analyzed. Otherwise, all components are to be analyzed.
	 * 
	 * @param component A component class name to only analyze this class and the
	 *                  application class (if any), or null to analyze all classes.
	 * @return The set of classes to analyze
	 */
	private Set<SootClass> getComponentsToAnalyze(SootClass component) {
		if (component == null)
			return this.entrypoints;
		else {
			// We always analyze the application class together with each
			// component
			// as there might be interactions between the two
			Set<SootClass> components = new HashSet<>(2);
			components.add(component);

			String applicationName = manifest.getApplicationName();
			if (applicationName != null && !applicationName.isEmpty())
				components.add(Scene.v().getSootClassUnsafe(applicationName));
			return components;
		}
	}

	/**
	 * Gets the data flow configuration
	 * 
	 * @return The current data flow configuration
	 */
	public InfoflowAndroidConfiguration getConfig() {
		return this.config;
	}

}
