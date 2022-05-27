package soot.jimple.infoflow.sourcesSinks.definitions;

import soot.Value;
import soot.jimple.Stmt;

import java.util.HashSet;
import java.util.Set;

public class ExitSourceSinkDefinition  extends SourceSinkDefinition{
    private Set<AccessPathTuple> accessPaths = null;
    public ExitSourceSinkDefinition(Stmt stmt) {
        this.stmt = stmt;
    }
    public ExitSourceSinkDefinition(Stmt stmt, Set<AccessPathTuple> accessPaths) {
        this.stmt = stmt;
        this.accessPaths = new HashSet<AccessPathTuple>();
    }

    @Override
    public SourceSinkDefinition getSourceOnlyDefinition() {
        Set<AccessPathTuple> newSet = null;
        if (accessPaths != null) {
            newSet = new HashSet<>(accessPaths.size());
            for (AccessPathTuple apt : accessPaths) {
                SourceSinkType ssType = apt.getSourceSinkType();
                if (ssType == SourceSinkType.Source)
                    newSet.add(apt);
                else if (ssType == SourceSinkType.Both) {
                    newSet.add(new AccessPathTuple(apt.getBaseType(), apt.getFields(), apt.getFieldTypes(),
                            SourceSinkType.Source));
                }
            }
        }
        return new ExitSourceSinkDefinition(stmt, newSet);
    }

    @Override
    public SourceSinkDefinition getSinkOnlyDefinition() {
        Set<AccessPathTuple> newSet = null;
        if (accessPaths != null) {
            newSet = new HashSet<>(accessPaths.size());
            for (AccessPathTuple apt : accessPaths) {
                SourceSinkType ssType = apt.getSourceSinkType();
                if (ssType == SourceSinkType.Sink)
                    newSet.add(apt);
                else if (ssType == SourceSinkType.Both) {
                    newSet.add(new AccessPathTuple(apt.getBaseType(), apt.getFields(), apt.getFieldTypes(),
                            SourceSinkType.Sink));
                }
            }
        }
        return new ExitSourceSinkDefinition(stmt, newSet);
    }

    @Override
    public void merge(SourceSinkDefinition other) {

    }

    @Override
    public boolean isEmpty() {
        return true;
    }
}
