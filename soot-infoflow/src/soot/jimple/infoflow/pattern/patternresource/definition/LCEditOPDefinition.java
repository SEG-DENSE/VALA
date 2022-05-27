package soot.jimple.infoflow.pattern.patternresource.definition;

import soot.SootMethod;
import soot.Value;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPType;
import soot.jimple.infoflow.pattern.patternresource.enumeration.MergeStrategy;

import java.util.Set;

public class LCEditOPDefinition extends LCResourceOPDefinition{
    private static LCEditOPDefinition instance = new LCEditOPDefinition("default", "default", LCResourceOPType.EDIT, MergeStrategy.PRE_SAMETYPE);

    public static LCEditOPDefinition getSingletonEditDefinition() {
        return instance;
    }
    public LCEditOPDefinition(String patternName, String category, LCResourceOPType opType, MergeStrategy mergeStrategy) {
        super(patternName, category, opType, null, mergeStrategy);
    }
}
