package soot.jimple.infoflow.pattern.sourceandsink;

import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkDefinition;
import soot.jimple.infoflow.sourcesSinks.manager.SourceInfo;

import java.util.Set;

public class PatternSourceInfo extends SourceInfo {
    protected AccessPath apToSlice = null;
    public PatternSourceInfo(SourceSinkDefinition definition, Set<AccessPath> bundle) {
        super(definition, bundle);
    }
    public void setApToSlice(AccessPath apToSlice) {
        this.apToSlice = apToSlice;
    }
    public AccessPath getApToSlice() {
        return this.apToSlice;
    }
}
