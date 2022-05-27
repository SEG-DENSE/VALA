package soot.jimple.infoflow.results;

import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkDefinition;
import soot.tagkit.LineNumberTag;

import java.util.Arrays;
import java.util.List;

/**
 * Class for modeling information flowing into a specific sink
 * 
 * @author Steven Arzt
 * 
 */
public class ResultSinkInfo extends AbstractResultSourceSinkInfo {
	private Stmt[] path = null;
	private AccessPath[] pathAPs = null;

	public ResultSinkInfo(SourceSinkDefinition definition, AccessPath sink, Stmt context) {
		super(definition, sink, context);
	}

	private ResultSinkInfo(SourceSinkDefinition definition, AccessPath source, Stmt context, Object userData,
							Stmt[] path, AccessPath[] pathAPs) {
		super(definition, source, context, userData);
		this.path = path;
		this.pathAPs = pathAPs;
	}

	public ResultSinkInfo cloneWithPath(Stmt[] path, AccessPath[] pathAps) {
		ResultSinkInfo newOne = new ResultSinkInfo(definition, accessPath, stmt, userData, path, pathAps);
		return newOne;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(stmt == null ? accessPath.toString() : stmt.toString());

		if (stmt != null && stmt.hasTag("LineNumberTag"))
			sb.append(" on line ").append(((LineNumberTag) stmt.getTag("LineNumberTag")).getLineNumber());

		return sb.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();

		if (!InfoflowConfiguration.getPathAgnosticResults()) {
			if (path != null)
				result += prime * Arrays.hashCode(this.path);
			if (pathAPs != null)
				result += prime * Arrays.hashCode(this.pathAPs);
		}

		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		ResultSinkInfo other = (ResultSinkInfo) obj;
		if (!InfoflowConfiguration.getPathAgnosticResults()) {
			if (!Arrays.equals(path, other.path))
				return false;
			return Arrays.equals(pathAPs, other.pathAPs);
		}
		return true;
	}

	public Stmt[] getPath() {
		return this.path;
	}

	public AccessPath[] getPathAccessPaths() {
		return this.pathAPs;
	}

}
