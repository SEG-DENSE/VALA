package soot.jimple.infoflow.resourceleak;

import soot.jimple.infoflow.sourcesSinks.definitions.ISourceSinkCategory;
import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkDefinition;

import java.util.HashSet;
import java.util.Set;

public class ResourceLeakGroup {
    private Set<SourceSinkDefinition> defs = null;
    private Set<String> defStrs = null;
    private ISourceSinkCategory category = null;
    private Set<String> killSigs = null;
    private Set<String> objStrs = null;
    public ResourceLeakGroup() {
        this.defs = new HashSet<>();
        this.killSigs = new HashSet<>();
        this.defStrs = new HashSet<>();
        this.objStrs = new HashSet<>();
    }
    public void setCateGory(String title) {
        this.category = new ResourceLeakCategory(title);
    }

    public ISourceSinkCategory getCateGory() {
        return this.category;
    }

    public void addDefStr(String defStr) {
        this.defStrs.add(defStr);
    }

    public void addKillSig(String killStr) {
        this.killSigs.add(killStr);
    }

    public void addObjStrs(String objStr) {
        this.objStrs.add(objStr);
    }

    public boolean isEmpty() {
        return this.category == null;
    }

    public boolean updateDefinition(String mSig, SourceSinkDefinition def) {
        if (defStrs.contains(mSig)) {
            this.defs.add(def);
            return true;
        }
        return false;
    }

    public Set<String> getKillSigs() {
        return this.killSigs;
    }

    public Set<String> getObjStrs() {
        return this.objStrs;
    }
}
