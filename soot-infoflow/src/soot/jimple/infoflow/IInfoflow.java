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
package soot.jimple.infoflow;

import java.util.Collection;
import java.util.Set;

import soot.jimple.Stmt;
import soot.jimple.infoflow.config.IInfoflowConfig;
import soot.jimple.infoflow.handlers.PostAnalysisHandler;
import soot.jimple.infoflow.handlers.PreAnalysisHandler;
import soot.jimple.infoflow.handlers.ResultsAvailableHandler;
import soot.jimple.infoflow.ipc.IIPCManager;
import soot.jimple.infoflow.nativeCallHandler.INativeCallHandler;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPHelper;
import soot.jimple.infoflow.pattern.result.LCMethodResultsAvailableHandler;
import soot.jimple.infoflow.pattern.result.LCMethodSummaryResult;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.threading.IExecutorFactory;

/**
 * interface for the main infoflow class
 *
 */
public interface IInfoflow {
	/**
	 * Gets the configuration to be used for the data flow analysis
	 * 
	 * @return The configuration to be used for the data flow analysis
	 */
    InfoflowConfiguration getConfig();

	/**
	 * Sets the configuration to be used for the data flow analysis
	 * 
	 * @param config The configuration to be used for the data flow analysis
	 */
    void setConfig(InfoflowConfiguration config);

	/**
	 * Sets the handler class to be used for modeling the effects of native methods
	 * on the taint state
	 * 
	 * @param handler The native call handler to use
	 */
    void setNativeCallHandler(INativeCallHandler handler);

	/**
	 * List of preprocessors that need to be executed in order before the
	 * information flow.
	 * 
	 * @param preprocessors the pre-processors
	 */
    void setPreProcessors(Collection<? extends PreAnalysisHandler> preprocessors);

	/**
	 * Sets the set of post-processors that shall be executed after the data flow
	 * analysis has finished
	 * 
	 * @param postprocessors The post-processors to execute on the results
	 */
    void setPostProcessors(Collection<? extends PostAnalysisHandler> postprocessors);

	/**
	 * getResults returns the results found by the analysis
	 * 
	 * @return the results
	 */
	LCMethodSummaryResult getResults();

	/**
	 * A result is available if the analysis has finished - so if this method
	 * returns false the analysis has not finished yet or was not started (e.g. no
	 * sources or sinks found)
	 * 
	 * @return boolean that states if a result is available
	 */
    boolean isResultAvailable();

	void setIPCManager(IIPCManager ipcManager);

	/**
	 * Sets the Soot configuration callback to be used for this analysis
	 * 
	 * @param config The configuration callback to be used for the analysis
	 */
    void setSootConfig(IInfoflowConfig config);
	/**
	 * Gets the concrete set of sources that have been collected in preparation for
	 * the taint analysis. This method will return null if source and sink logging
	 * has not been enabled (see InfoflowConfiguration. setLogSourcesAndSinks()),
	 * 
	 * @return The set of sources collected for taint analysis
	 */
    Set<Stmt> getCollectedSources();

	/**
	 * Gets the concrete set of sinks that have been collected in preparation for
	 * the taint analysis. This method will return null if source and sink logging
	 * has not been enabled (see InfoflowConfiguration. setLogSourcesAndSinks()),
	 * 
	 * @return The set of sinks collected for taint analysis
	 */
    Set<Stmt> getCollectedSinks();

	/**
	 * Adds a handler that is called when information flow results are available
	 * 
	 * @param handler The handler to add
	 */
    void addResultsAvailableHandler(LCMethodResultsAvailableHandler handler);

	/**
	 * Removes a handler that is called when information flow results are available
	 * 
	 * @param handler The handler to remove
	 */
    void removeResultsAvailableHandler(ResultsAvailableHandler handler);

	/**
	 * Aborts the data flow analysis. This is useful when the analysis controller is
	 * running in a different thread and the main thread (e.g., a GUI) wants to
	 * abort the analysis
	 */
    void abortAnalysis();


	/**
	 * Sets the factory to be used for creating thread pool executors
	 * 
	 * @param executorFactory The executor factory to use
	 */
    void setExecutorFactory(IExecutorFactory executorFactory);

	void setLCResourceOPHelper(LCResourceOPHelper ophelper);
	LCResourceOPHelper getLCResourceOPHelper();
}
