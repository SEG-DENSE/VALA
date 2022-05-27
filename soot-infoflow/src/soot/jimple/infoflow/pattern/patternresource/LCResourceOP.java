package soot.jimple.infoflow.pattern.patternresource;

import soot.jimple.Stmt;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.pattern.patternresource.definition.LCResourceOPDefinition;

public class LCResourceOP {
    protected LCResourceOPType type = null;
    protected LCResourceOPDefinition def = null;
    protected  Stmt stmt = null;
//    protected Object extradata = null;
    public LCResourceOP(Stmt stmt, LCResourceOPType type, LCResourceOPDefinition def) {
        this.stmt = stmt;
        this.type = type;
        this.def = def;
    }
    protected int hashcode = -1;

    public boolean equals(Object other) {
        if (null == other || !(other instanceof LCResourceOP)){return false;}
        LCResourceOP otherop = (LCResourceOP)other;
        if (this.hashcode != otherop.hashcode){return false;}

        if (this.type != otherop.type) {return false;}
        if (this.def != otherop.def) {return false;}
        if (this.stmt != otherop.stmt) {return false;}
        return true;
    }

    private final static int prime = 32;
    private final static int init = 1;
    public int hashCode() {
        if (-1 != hashcode){return hashcode;}
        hashcode = init * prime + type.hashCode();
        hashcode = hashcode * prime + def.hashCode();
        hashcode = hashcode * prime + stmt.hashCode();
        return hashcode;
    }
    public LCResourceOPType getOpType() {return this.type;}
    public String getTitle() {return this.type.toString();}
    public String getStmtStr() {return this.stmt.toString();}
    public Stmt getStmt() {return this.stmt;}

}
