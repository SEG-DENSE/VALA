package soot.jimple.infoflow.pattern.patternresource.definition;

import soot.SootMethod;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPConstant;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPType;
import soot.jimple.infoflow.pattern.patternresource.enumeration.MergeStrategy;

import java.util.Set;

public class LCSPMethodDefinition extends LCResourceOPDefinition{
    public LCSPMethodDefinition(LCResourceOPType optype) {
        super("default", LCResourceOPConstant.DEFAULT, optype, null, MergeStrategy.PRE_SAMETYPE);
    }
    public static LCSPMethodDefinition getLCSPMethodDefinition(SootMethod spmethod) {
        String subsig = spmethod.getSubSignature().toLowerCase();
        LCResourceOPType optype = LCResourceOPType.UNKNOWN;
        if (subsig.contains("get")) {
            optype = LCResourceOPType.SP_GET;
        } else if (subsig.contains("set") || subsig.contains("put")) {
            optype = LCResourceOPType.SP_SET;
        }
        return new LCSPMethodDefinition(optype);
    }
    public LCResourceOPType getOpType() {
        return opType;
    }
}
