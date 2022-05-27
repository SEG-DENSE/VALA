package soot.jimple.infoflow.pattern.patternresource;

import soot.jimple.Stmt;
import soot.jimple.infoflow.pattern.patternresource.enumeration.MergeStrategy;

import java.util.Iterator;
import java.util.LinkedList;

public class LCResourceOPList {
    private static LCResourceOPList emptyList = new LCResourceOPList();

    protected String type = LCResourceOPConstant.DEFAULT;
    protected LinkedList<LCResourceOP> oplist = null;



    private LCResourceOPList(){oplist = new LinkedList<>();}
    public static LCResourceOPList getInitialList(){
        return emptyList;
    }
    public boolean isDefaultCategory() {return this.type.equals(LCResourceOPConstant.DEFAULT);}
    public String getCategory() {return this.type;}
    public LinkedList<LCResourceOP> getOpList() {return this.oplist;}

    private LCResourceOPList(LCResourceOPList originalOp){
        this.type = originalOp.type;
        this.oplist = new LinkedList<>(originalOp.oplist);

    }
    public LCResourceOPList clone() {
        return new LCResourceOPList(this);
    }

    public LCResourceOPList mergeWithPosition(LCResourceOP newop, MergeStrategy strategy, String category, int position) {
        return null;
    }

    public LCResourceOPList merge(LCResourceOP newop, MergeStrategy strategy, String category) {
        LCResourceOPList newlist = null;
        switch (strategy) {
            case NEVER:{
                newlist = clone();
                if (this == emptyList) {
                    newlist.type = category;
                }
                newlist.oplist.add(newop);
                break;
            }
            case PRE_ANY:{
                if (this.oplist.size() >= 2) {
                    newlist = clone();
                    newlist.oplist.removeLast();
                    newlist.oplist.add(newop);
                } else {
                    newlist = new LCResourceOPList();
                    newlist.type = category;
                    newlist.oplist.add(newop);
                }
                break;
            }
            case TOTAL_ANY:{
                newlist = new LCResourceOPList();
                newlist.type = category;
                newlist.oplist.add(newop);
                break;
            }
            case PRE_SAMETYPE:{
                newlist = clone();
                if (this == emptyList) {
                    newlist.type = category;
                }
                if (newlist.oplist.size() > 0 && newlist.oplist.getLast().type == newop.type) {
                    newlist.oplist.removeLast();
                }
                newlist.oplist.add(newop);
                break;
            }
            case TOTAL_SAMETYPE:{
                newlist = clone();
                if (this == emptyList) {
                    newlist.type = category;
                }
                Iterator<LCResourceOP> it = newlist.oplist.iterator();
                while (it.hasNext()) {
                    LCResourceOP tempop = it.next();
                    if (tempop.type == newop.type) {
                        it.remove();
                    }
                }
                newlist.oplist.add(newop);
                break;
            }
        }
        return newlist;
    }

    public LCResourceOPList removeLastOP(Stmt stmt) {
        if (this.isEmpty()) {return this;}
        LCResourceOP lastOp = this.getOpList().getLast();
        if (lastOp.stmt != stmt) {return this;}
        if (this.oplist.size() == 1) {
            return emptyList;
        }
        LCResourceOPList newList = clone();
        newList.oplist.removeLast();
        return newList;
    }


    public boolean equals(Object other) {
        if (null == other || !(other instanceof  LCResourceOPList)) {return false;}
        LCResourceOPList otherlist = (LCResourceOPList)other;

        if (otherlist.hashCode() != hashCode()) {return false;}

        if (!this.type.equals(otherlist.type)) {return false;}
        if (this.oplist.size() != otherlist.oplist.size()){return false;}
        for (int i = 0; i < this.oplist.size(); i++) {
            if (!this.oplist.get(i).equals(otherlist.oplist.get(i))) {
                return false;
            }
        }
        return true;
    }
    private final static int prime = 32;
    private final static int init = 1;
    protected int hashcode = -1;
    public int hashCode() {
        if (hashcode != -1){return hashcode;}
        hashcode = init * prime + type.hashCode();
        int listhash = 0;
        for (LCResourceOP op : oplist) {
            listhash += op.hashCode();
        }
        hashcode = hashcode * prime + listhash;
        return hashcode;
    }

    public int getSize() {return this.oplist.size();}
    public boolean isEmpty() {return this == emptyList;}
    public static final LCResourceOPList getEmptyOPList() {return  emptyList;}
    public Stmt getStmt(int index) {return oplist.get(index).stmt;}
}
