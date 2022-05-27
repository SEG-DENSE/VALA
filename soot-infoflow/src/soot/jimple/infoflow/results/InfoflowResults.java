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
package soot.jimple.infoflow.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import heros.solver.Pair;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkDefinition;
import soot.util.ConcurrentHashMultiMap;
import soot.util.MultiMap;

/**
 * Class for collecting information flow results
 * 
 * @author Steven Arzt
 */
public class InfoflowResults {

	public final static int TERMINATION_SUCCESS = 0;
	public final static int TERMINATION_DATA_FLOW_TIMEOUT = 1;
	public final static int TERMINATION_DATA_FLOW_OOM = 2;
	public final static int TERMINATION_PATH_RECONSTRUCTION_TIMEOUT = 4;
	public final static int TERMINATION_PATH_RECONSTRUCTION_OOM = 8;

	private static final Logger logger = LoggerFactory.getLogger(InfoflowResults.class);

	private volatile MultiMap<ResultSinkInfo, ResultSourceInfo> results = null;
	//lifecycle-add
	private volatile MultiMap<ResultSourceInfo, ResultSinkInfo> reresults = null;
	private volatile InfoflowPerformanceData performanceData = null;
	private volatile List<String> exceptions = null;
	private int terminationState = TERMINATION_SUCCESS;

	/**
	 * Gets the exceptions that have happened during the data flow analysis. This
	 * collection is immutable.
	 * 
	 * @return
	 */
	public List<String> getExceptions() {
		return exceptions;
	}

	/**
	 * Adds an exception that has occurred during the data flow analysis to this
	 * result object
	 * 
	 * @param ex The exception to add
	 */
	public void addException(String ex) {
		if (exceptions == null) {
			synchronized (this) {
				if (exceptions == null)
					exceptions = new ArrayList<String>();
			}
		}
		exceptions.add(ex);
	}

	public InfoflowResults() {

	}

	/**
	 * Gets the number of entries in this result object
	 * 
	 * @return The number of entries in this result object
	 */
	public int size() {
		return this.results == null ? 0 : this.results.size();
	}

	public int resize() {
		return this.reresults == null ? 0 : this.reresults.size();
	}

	/**
	 * Gets the total number of source-to-sink connections. If there are two
	 * connections along different paths between the same source and sink, size()
	 * will return 1, but numConnections() will return 2.
	 * 
	 * @return The number of source-to-sink connections in this result object
	 */
//		int num = 0;
//		if (this.results != null)
//			for (ResultSinkInfo sink : this.results.keySet())
//				num += this.results.get(sink).size();
//		return num;

	/**
	 * Gets whether this result object is empty, i.e. contains no information flows
	 * 
	 * @return True if this result object is empty, otherwise false.
	 */
	public boolean isEmpty() {
		return this.results == null || this.results.isEmpty();
	}

	/**
	 * Adds the given result to this data structure
	 * 
	 * @param sinkDefinition        The definition of the sink
	 * @param sink                  The access path that arrived at the sink
	 *                              statement
	 * @param sinkStmt              The sink statement
	 * @param sourceDefinition      The definition of the source
	 * @param source                The access path that originated from the source
	 *                              statement
	 * @param sourceStmt            The source statement
	 * @param userData              Optional user data to associate with the source
	 * @param propagationPath       The statements over which the data flow was
	 *                              propagated
	 * @param propagationAccessPath The access paths along the data flow propagation
	 *                              path
	 * @return The new data flow result
	 */
	public Pair<ResultSourceInfo, ResultSinkInfo> addResult(SourceSinkDefinition sinkDefinition, AccessPath sink,
			Stmt sinkStmt, SourceSinkDefinition sourceDefinition, AccessPath source, Stmt sourceStmt, Object userData,
			List<Stmt> propagationPath, List<AccessPath> propagationAccessPath) {
		ResultSourceInfo sourceObj = new ResultSourceInfo(sourceDefinition, source, sourceStmt, userData,
				propagationPath, propagationAccessPath);
		ResultSinkInfo sinkObj = new ResultSinkInfo(sinkDefinition, sink, sinkStmt);

		this.addResult(sinkObj, sourceObj);
		return new Pair<>(sourceObj, sinkObj);
	}

	/**
	 * Adds the given data flow result to this data structure
	 * 
	 * @param res The data flow result to add
	 */
	public void addResult(DataFlowResult res) {
		if (res != null) {
			addResult(res.getSink(), res.getSource());
		}
	}

	/**
	 * Adds the given result to this data structure
	 * 
	 * @param sink   The sink at which the taint arrived
	 * @param source The source from which the taint originated
	 */
	public void addResult(ResultSinkInfo sink, ResultSourceInfo source) {
		if (results == null) {
			synchronized (this) {
				if (results == null)
					results = new ConcurrentHashMultiMap<>();
			}
		}
		if (reresults == null) {
			synchronized (this) {
				if (reresults == null)
					reresults = new ConcurrentHashMultiMap<>();
			}
		}
		{
			ResultSinkInfo newsink = sink.cloneWithPath(source.getPath(), source.getPathAccessPaths());
			ResultSourceInfo newsource = source.cloneWithoutPath();
			this.reresults.put(newsource, newsink);

		}
		this.results.put(sink, source);
	}

	/**
	 * Adds all results from the given data structure to this one
	 * 
	 * @param results The data structure from which to copy the results
	 */
	public void addAll(InfoflowResults results) {
		if (results == null || results.isEmpty())
			return;

		if (results.getExceptions() != null) {
			for (String e : results.getExceptions())
				addException(e);
		}

		if (!results.getResults().isEmpty()) {
			for (ResultSinkInfo sink : results.getResults().keySet())
				for (ResultSourceInfo source : results.getResults().get(sink))
					addResult(sink, source);
		}

		// Sum up the performance data
		if (results.performanceData != null) {
			if (this.performanceData == null)
				this.performanceData = results.performanceData;
			else
				this.performanceData.add(results.performanceData);
		}
	}

	/**
	 * Adds the given data flow results to this result object
	 * 
	 * @param results The data flow results to add
	 */
	public void addAll(Set<DataFlowResult> results) {
		if (results == null || results.isEmpty())
			return;

		for (DataFlowResult res : results)
			addResult(res);
	}

	/**
	 * Gets all results in this object as a hash map from sinks to sets of sources.
	 * 
	 * @return All results in this object as a hash map.
	 */
	public MultiMap<ResultSinkInfo, ResultSourceInfo> getResults() {
		return this.results;
	}
	public MultiMap<ResultSourceInfo, ResultSinkInfo> getReResults() {
		return this.reresults;
	}

	/**
	 * Gets the data flow results in a flat set
	 * 
	 * @return The data flow results in a flat set. If no data flows are available,
	 *         the return value is null.
	 */
//			return null;
//
//		Set<DataFlowResult> set = new HashSet<>(results.size() * 10);
//			for (ResultSourceInfo source : results.get(sink))
//				set.add(new DataFlowResult(source, sink));
//		return set;

	/**
	 * Checks whether there is a path between the given source and sink.
	 * 
	 * @param sink   The sink to which there may be a path
	 * @param source The source from which there may be a path
	 * @return True if there is a path between the given source and sink, false
	 *         otherwise
	 */
//		if (this.results == null)
//			return false;
//
//		Set<ResultSourceInfo> sources = null;
//				sources = this.results.get(sI);
//				break;
//		if (sources == null)
//			return false;
//		for (ResultSourceInfo src : sources)
//			if (src.getAccessPath().equals(source))
//				return true;
//		return false;

	/**
	 * Checks whether there is a path between the given source and sink.
	 * 
	 * @param sink   The sink to which there may be a path
	 * @param source The source from which there may be a path
	 * @return True if there is a path between the given source and sink, false
	 *         otherwise
	 */
//		if (this.results == null)
//			return false;
//
//		for (ResultSinkInfo si : this.results.keySet())
//				Set<ResultSourceInfo> sources = this.results.get(si);
//				for (ResultSourceInfo src : sources)
//					if (src.getStmt().toString().contains(source))
//						return true;
//		return false;

	/**
	 * Checks whether there is an information flow between the two given methods
	 * (specified by their respective Soot signatures).
	 * 
	 * @param sinkSignature   The sink to which there may be a path
	 * @param sourceSignature The source from which there may be a path
	 * @return True if there is a path between the given source and sink, false
	 *         otherwise
	 */
//		List<ResultSinkInfo> sinkVals = findSinkByMethodSignature(sinkSignature);
//			Set<ResultSourceInfo> sources = this.results.get(si);
//			if (sources == null)
//				return false;
//			for (ResultSourceInfo src : sources)
//					InvokeExpr expr = src.getStmt().getInvokeExpr();
//					if (expr.getMethod().getSignature().equals(sourceSignature))
//						return true;
//		return false;

	/**
	 * Finds the entry for a sink method with the given signature
	 * 
	 * @param sinkSignature The sink's method signature to look for
	 * @return The key of the entry with the given method signature if such an entry
	 *         has been found, otherwise null.
	 */
//		if (this.results == null)
//			return Collections.emptyList();
//
//		List<ResultSinkInfo> sinkVals = new ArrayList<>();
//		for (ResultSinkInfo si : this.results.keySet())
//				InvokeExpr expr = si.getStmt().getInvokeExpr();
//				if (expr.getMethod().getSignature().equals(sinkSignature))
//					sinkVals.add(si);
//		return sinkVals;

	/**
	 * Prints all results stored in this object to the standard output
	 */
////		if (this.results == null)
////			return;
////
////				if (source.getPath() != null)

	/**
	 * Prints all results stored in this object to the given writer
	 * 
	 * @param wr The writer to which to print the results
	 * @throws IOException Thrown when data writing fails
	 */
//		if (this.results == null)
//			return;
//
//				if (source.getPath() != null)

	/**
	 * Removes all results from the data structure
	 */
	public void clear() {
		this.results = null;
		this.reresults = null;
	}

	/**
	 * Gets the termination state that describes whether the data flow analysis
	 * terminated normally or whether one or more phases terminated prematurely due
	 * to a timeout or an out-of-memory condition
	 * 
	 * @return The termination state
	 */
	public int getTerminationState() {
		return terminationState;
	}

	/**
	 * Sets the termination state that describes whether the data flow analysis
	 * terminated normally or whether one or more phases terminated prematurely due
	 * to a timeout or an out-of-memory condition
	 * 
	 * @param terminationState The termination state
	 */
	public void setTerminationState(int terminationState) {
		this.terminationState = terminationState;
	}

	/**
	 * Gets whether the analysis was aborted due to a timeout
	 * 
	 * @return True if the analysis was aborted due to a timeout, otherwise false
	 */
	public boolean wasAbortedTimeout() {
		return ((terminationState & TERMINATION_DATA_FLOW_TIMEOUT) == TERMINATION_DATA_FLOW_TIMEOUT)
				|| ((terminationState
						& TERMINATION_PATH_RECONSTRUCTION_TIMEOUT) == TERMINATION_PATH_RECONSTRUCTION_TIMEOUT);
	}

	/**
	 * Gets whether the analysis was terminated because it ran out of memory
	 * 
	 * @return True if the analysis was terminated because it ran out of memory,
	 *         otherwise false
	 */
	public boolean wasTerminatedOutOfMemory() {
		return ((terminationState & TERMINATION_DATA_FLOW_OOM) == TERMINATION_DATA_FLOW_OOM)
				|| ((terminationState & TERMINATION_PATH_RECONSTRUCTION_OOM) == TERMINATION_PATH_RECONSTRUCTION_OOM);
	}

	/**
	 * Gets the performance data on this FlowDroid run
	 * 
	 * @return The performance data on this FlowDroid run
	 */
	public InfoflowPerformanceData getPerformanceData() {
		return performanceData;
	}

	/**
	 * Sets the performance data on this FlowDroid run
	 * 
	 * @param performanceData The performance data on this FlowDroid run
	 */
	public void setPerformanceData(InfoflowPerformanceData performanceData) {
		this.performanceData = performanceData;
	}

	@Override
	public String toString() {
		if (this.results == null)
			return "<no results>";

		boolean isFirst = true;
		StringBuilder sb = new StringBuilder();
		for (ResultSinkInfo sink : this.results.keySet())
			for (ResultSourceInfo source : this.results.get(sink)) {
				if (!isFirst)
					sb.append(", ");
				isFirst = false;

				sb.append(source);
				sb.append(" -> ");
				sb.append(sink);
			}
		return sb.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((exceptions == null) ? 0 : exceptions.hashCode());
		result = prime * result + ((performanceData == null) ? 0 : performanceData.hashCode());
		result = prime * result + ((results == null) ? 0 : results.hashCode());
		result = prime * result + terminationState;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		InfoflowResults other = (InfoflowResults) obj;
		if (exceptions == null) {
			if (other.exceptions != null)
				return false;
		} else if (!exceptions.equals(other.exceptions))
			return false;
		if (performanceData == null) {
			if (other.performanceData != null)
				return false;
		} else if (!performanceData.equals(other.performanceData))
			return false;
		if (results == null) {
			if (other.results != null)
				return false;
		} else if (!results.equals(other.results))
			return false;
        return terminationState == other.terminationState;
    }

}
