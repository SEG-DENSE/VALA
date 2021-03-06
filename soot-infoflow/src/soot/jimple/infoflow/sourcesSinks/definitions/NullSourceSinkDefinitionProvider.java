package soot.jimple.infoflow.sourcesSinks.definitions;

import soot.jimple.infoflow.resourceleak.ResourceLeakGroup;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Empty provider that simulates empty lists of sources and sinks
 * 
 * @author Steven Arzt
 *
 */
public class NullSourceSinkDefinitionProvider implements
		ISourceSinkDefinitionProvider {

	@Override
	public Set<SourceSinkDefinition> getSources() {
		return Collections.emptySet();
	}

	@Override
	public Set<SourceSinkDefinition> getSinks() {
		return Collections.emptySet();
	}

	@Override
	public Set<SourceSinkDefinition> getAllMethods() {
		return Collections.emptySet();
	}

	@Override
	public Map<ISourceSinkCategory, ResourceLeakGroup> getLeakGroups() {
		return  Collections.emptyMap();
	}

}
