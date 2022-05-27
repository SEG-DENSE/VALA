package soot.jimple.infoflow.resourceleak;

import soot.jimple.infoflow.sourcesSinks.definitions.ISourceSinkCategory;

public class ResourceLeakCategory implements ISourceSinkCategory {
    private String id = null;
    public ResourceLeakCategory(String id) {
        this.id = id;
    }
    @Override
    public String getHumanReadableDescription() {
        return id;
    }

    @Override
    public String getID() {
        return id;
    }

    public boolean equals(Object other) {
        if (null == other || !(other instanceof  ResourceLeakCategory)) {
            return false;
        }
        ResourceLeakCategory otherC = (ResourceLeakCategory)other;
        return otherC.getID().equals(this.getID());
    }

    public String toString() {
        return "ResourceLeakCategory:  " + this.getID();
    }
}
