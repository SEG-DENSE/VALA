package soot.jimple.infoflow.pattern.mappingmethods;

import soot.SootMethod;

import java.util.HashMap;
import java.util.Map;

public class MappingMethodHelper {
    private static MappingMethodHelper instance = null;
    protected Map<SootMethod, MappingMethodDefinition> mappingmethods;
    public static MappingMethodHelper v() {
        if (null == instance) {
            instance = new MappingMethodHelper();
        }
        return instance;
    }
    private  MappingMethodHelper() {
        mappingmethods= new HashMap<>();
    }
    public void init(Map<SootMethod, MappingMethodDefinition> mappingmethods) {
        this.mappingmethods = mappingmethods;
    }
    public boolean isEmpty() {return mappingmethods.isEmpty();}
    public Map<SootMethod, MappingMethodDefinition> getMappingmethods() {return this.mappingmethods;}
}
