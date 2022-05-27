package soot.jimple.infoflow.pattern.solver;

import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPList;
import soot.jimple.infoflow.pattern.sourceandsink.PatternFieldSourceSinkDefinition;
import soot.jimple.infoflow.util.ByReferenceBoolean;
import soot.jimple.infoflow.util.MyOwnUtils;
import soot.tagkit.Tag;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.util.*;

public class NormalState implements Cloneable{
    protected Set<AccessPath> aps;
//    protected Stmt currentStmt = null;
    protected PatternFieldSourceSinkDefinition def = null;
    protected LCResourceOPList ops = null;
    protected SootMethod entrymethod = null; 
    protected Collection<Unit> finishStmts = null;
    protected SootClass entryClass = null; 
    protected List<Tag> lcmethodTags = null;  

    protected int hashCode = 0;

    protected NormalState zeroState = null; 
    protected int propagationPathLength = 0;
    protected boolean exceptionThrown = false;

    protected Stack<Stmt> callStack = new Stack<>();
    protected NormalState preState = null;
    protected List<Stmt> preStmts = null;

    protected Stack<Stmt> invocationStack = null;

    public static NormalState getInitialState() {
        NormalState zeroValue = new NormalState(Collections.EMPTY_SET, null);
        zeroValue.zeroState = zeroValue;
        return zeroValue;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        NormalState other = (NormalState) obj;

        // If we have already computed hash codes, we can use them for
        // comparison
        if (this.hashCode != 0 && other.hashCode != 0 && this.hashCode != other.hashCode)
            return false;
        if (this.def != other.def)
            return false;
        if (this.entrymethod != other.entrymethod)
            return false;
        if (this.lcmethodTags != other.lcmethodTags)
            return false;
        if (this.OPposition != other.OPposition)
            return false;
        if (null == this.ops) {
            if (other.ops != null) {
                return false;
            }
        } else {
            if (!this.ops.equals(other.ops))
                return false;
        }
        if (null == this.aps || this.aps.isEmpty()) {
            if (other.aps != null && !other.aps.isEmpty()) {
                return false;
            }
        } else {
            if (!MyOwnUtils.setCompare(this.aps, other.aps)) {
                return false;
            }
        }
        Set<AccessPath> inactiveaps = this.getInactiveAps();
        Set<AccessPath> otherinactiveaps = other.getInactiveAps();
        if (null == inactiveaps || inactiveaps.isEmpty()) {
            if (otherinactiveaps != null && !otherinactiveaps.isEmpty()) {
                return false;
            }
        } else {
            if (!MyOwnUtils.setCompare(inactiveaps,otherinactiveaps)) {
                return false;
            }
        }
        if (this.callStack.isEmpty()) {
            if (!other.callStack.isEmpty()) {
                return false;
            }
        } else {
            if (other.callStack.isEmpty()) {
                return false;
            }
//                return false;
//                return false;
//                int size = this.callStack.size();
//                        return false;
            int thisCallSize =  this.callStack.size();
            int otherCallSize = other.callStack.size();
//                return false;
            int size = thisCallSize > otherCallSize ? otherCallSize: thisCallSize;
            if (size > 3) {
                size = 3;
            }
            for (int i = 0; i < size; i++) {
                Stmt s1 = this.callStack.get(thisCallSize-i-1);
                Stmt s2 = other.callStack.get(otherCallSize-i-1);
                if (s1 != s2) {return false;}
            }
        }
        return true;
    }
    @Override
    public int hashCode() {
        if (this.hashCode != 0)
            return hashCode;

        final int prime = 31;
        int result = 1;

        // deliberately ignore prevAbs
        result = prime * result + ((def == null) ? 0 : def.hashCode());
        result = prime * result + ((entrymethod == null) ? 0 : entrymethod.hashCode());
        result = prime * result + ((lcmethodTags == null) ? 0 : lcmethodTags.hashCode());
        result = prime * result + OPposition;
        result = prime * result + (exceptionThrown ? 1231 : 1237);
        if (null != aps && !aps.isEmpty()) {
            int aphash = 0;
            for (AccessPath ap : aps) {
                if (null != ap)
                    aphash += ap.hashCode();
            }
            result = prime * result + aphash;
        }
        Set<AccessPath> inactiveApSet = getInactiveAps();
        if (null != inactiveApSet && !inactiveApSet.isEmpty()) {
            int aphash = 0;
            for (AccessPath ap : inactiveApSet) {
                aphash += ap.hashCode();
            }
            result = prime * result + aphash;
        }
        int callStmtHash = 0;
        if (!this.callStack.isEmpty()) {
            callStmtHash = this.callStack.peek().hashCode();
        }
        result = prime * result + callStmtHash;

//        result = prime * result + (null==aliasAp ? 0 : aliasAp.hashCode());

        this.hashCode = result;
        return this.hashCode;
    }
    public int getPathLength() {
        return propagationPathLength;
    }
    public NormalState getZeroState() { return this.zeroState;}
    public boolean isZeroState() { return this.zeroState == this;}
    public Set<AccessPath> getAps() {return aps;}
    public PatternFieldSourceSinkDefinition getDef() {return def;}
    public boolean isStatic() {if(null != def){return def.isStatic();}return false;}
    public SootField getField() {if(null != def){return def.getField();}return null;}
    public LCResourceOPList getOps() {return ops;}
    public SootMethod getEntryMethod() {return this.entrymethod;}
    public List<Tag> getLcmethodTags() {return this.lcmethodTags;}

    public SootClass getEntryClass() {return this.entryClass;}
    public boolean shouldFinish(Unit stmt) {if (finishStmts.contains(stmt)){return true;}else{return false;}}


    protected NormalState(Set<AccessPath> newaps, NormalState original) {
        if (original == null) {
            def = null;
            exceptionThrown = false;
            OPposition = -1;
            preStmts = Collections.EMPTY_LIST;
            finishStmts = Collections.EMPTY_SET;
            inActivationMap = new HashMultiMap<>();
            ops = LCResourceOPList.getInitialList();
        } else {
            def = original.def;
            exceptionThrown = original.exceptionThrown;
            OPposition = original.OPposition;
            inActivationMap = original.inActivationMap;
            aliasAp = original.aliasAp;
            ops = original.ops;
            preStmts = original.preStmts;
            zeroState = original.zeroState;
            entrymethod = original.entrymethod;
            finishStmts = original.finishStmts;
            lcmethodTags = original.lcmethodTags;
            callStack = original.callStack;
        }
        this.aps = newaps;
    }
    @Override
    public NormalState clone() {
        NormalState abs = new NormalState(this.aps, this);
        abs.propagationPathLength = propagationPathLength + 1;
        abs.preState = this;
        assert abs.equals(this);
        return abs;
    }


    public NormalState deriveFinishStmtCopy(Collection<Unit> finishStmts, SootMethod entryMethod, List<Tag> tags) {
        if (this.entrymethod == entryMethod) {
            return this;
        }
        NormalState a = clone();
        a.finishStmts = finishStmts;
        a.entrymethod = entryMethod;
        a.lcmethodTags = tags;
        a.zeroState = a;
        return a;
    }


    public NormalState deriveNewState(Set<AccessPath> newaps, PatternFieldSourceSinkDefinition def, ByReferenceBoolean hasgeneratedNewState, Stmt stmt) {
        if (this.aps == newaps && this.def == def) {
            return this;
        }
        NormalState newState = deriveNewState(newaps, hasgeneratedNewState, stmt);
        newState.def = def;
        return newState;
    }

    public NormalState deriveNewState(Set<AccessPath> newaps, ByReferenceBoolean hasgeneratedNewState, Stmt stmt) {
        if (this.aps == newaps) {
            return this;
        }
        NormalState a = null;
        if (hasgeneratedNewState.value) { a = this;} else {a = clone();hasgeneratedNewState.value = true;}
        a.aps = newaps;
        a.preStmts = new LinkedList<>(a.preStmts);
        a.preStmts.add(stmt);
        return a;
    }

    public NormalState deriveNewAliasState(AccessPath newAliasAp, MultiMap<Stmt, AccessPath> newInactiveApMap, ByReferenceBoolean hasgeneratedNewState, Stmt stmt) {
        NormalState a = null;
        if (hasgeneratedNewState.value) { a = this;} else {a = clone();hasgeneratedNewState.value = true;}
        a.aliasAp = newAliasAp;
        if (newInactiveApMap != null) {
            a.inActivationMap = newInactiveApMap;
        }
        a.preStmts = new LinkedList<>(a.preStmts);
        a.preStmts.add(stmt);
        return a;
    }


    public NormalState deriveNewOPListState(ByReferenceBoolean hasgeneratedNewState, LCResourceOPList newops, Stmt stmt) {
        if (this.ops == newops || this.ops.equals(newops)) {
            return this;
        }
        NormalState a = null;
        if (hasgeneratedNewState.value) { a = this;} else {a = clone();hasgeneratedNewState.value = true;}
        a.ops = newops;
        a.preStmts = new LinkedList<>(a.preStmts);
        a.preStmts.add(stmt);
        return a;
    }

    public NormalState deriveNewTagListState(ByReferenceBoolean hasgeneratedNewState, List<Tag> newttags, Stmt stmt) {
        if (this.lcmethodTags == newttags) {
            return this;
        }
        NormalState a = null;
        if (hasgeneratedNewState.value) { a = this;} else {a = clone();hasgeneratedNewState.value = true;}
        a.lcmethodTags = newttags;
        a.preStmts = new LinkedList<>(a.preStmts);
        a.preStmts.add(stmt);
        return a;
    }

    public NormalState deriveNewZeroState(ByReferenceBoolean hasgeneratedNewState, LCResourceOPList newops, Stmt stmt) {
        if (this.ops == newops) {
            return this;
        }
        NormalState a = clone();
        assert  !hasgeneratedNewState.value;
        a.ops = newops;
        a.preStmts = new LinkedList<>(a.preStmts);
        a.preStmts.add(stmt);
        a.zeroState = a;
        return a;
    }


    protected MultiMap<Stmt, AccessPath> inActivationMap = null; 
    protected int OPposition = -1;
    public  MultiMap<Stmt, AccessPath> getInActivationMap() {return this.inActivationMap;}
    protected Stmt nextOpStmt = null;
    protected Set<AccessPath> inactiveAps = null;
    public boolean isActive() {return getInactiveAps().isEmpty();}
    public Set<AccessPath> getInactiveAps() {
        if (inactiveAps == null) {
            inactiveAps = new HashSet<>();
            if (null != inActivationMap) {
                inactiveAps.addAll(inActivationMap.values());
            }
        }
        return inactiveAps;
    }

    protected AccessPath aliasAp = null;
    protected Stmt activationStmt = null;
    public AccessPath getAliasAp() {return aliasAp;}
    public Stmt getActivationStmt() {return activationStmt;}
    public boolean shouldTaintSubFields() {
        if (null != this.aps && !this.aps.isEmpty()) {
            for (AccessPath ap : this.aps) {
                if (ap.getTaintSubFields()) {
                    return true;
                }
            }
        }
        return false;
    }
    public NormalState deriveAliasState(AccessPath apToBW, Stmt stmt, boolean isToSlice) {
        NormalState a = clone();
        a.aps = new HashSet<>();
        a.aliasAp = apToBW;
        a.activationStmt = isToSlice?null:stmt;
        a.preStmts = new LinkedList<>(a.preStmts);
        a.preStmts.add(stmt);
        a.inActivationMap = new HashMultiMap<>();
        a.inActivationMap.putAll(stmt, aps);


        if (a.ops.isEmpty()) {
            a.OPposition = 0;
        } else {
            a.OPposition = a.ops.getSize();
            a.nextOpStmt = a.ops.getStmt(a.OPposition-1);
            if (stmt == a.nextOpStmt) {
                a.OPposition--;
                if (a.OPposition != 0) {
                    a.nextOpStmt = a.ops.getStmt(a.OPposition-1);
                } else {
                    a.nextOpStmt = null;
                }
            }

        }
        return a;
    }

    protected Set<AccessPath> tempAps = null;
    public NormalState FWCheckCurrentStmt(Stmt stmt, ByReferenceBoolean hasGeneratedNewState) {
        NormalState a = null;
        boolean shouldActive = this.inActivationMap.containsKey(stmt);
        boolean shouldMoveOpCursor = this.OPposition != -1 && nextOpStmt == stmt;
        if (!shouldActive && !shouldMoveOpCursor) {
            return this;
        }
        if (hasGeneratedNewState.value) {
            a = this;
        } else {
            a = clone();
            hasGeneratedNewState.value = true;
        }
        if (shouldActive) {
            a.inActivationMap = new HashMultiMap<>(this.inActivationMap);
            a.tempAps = this.inActivationMap.get(stmt);
            a.inActivationMap.remove(stmt);
            a.aps = new HashSet<>(this.aps);
        }
        if (shouldMoveOpCursor) {
            a.OPposition++;
            if (a.OPposition >= a.ops.getSize()) {
                a.OPposition = -1;
            } else {
                a.nextOpStmt = a.ops.getStmt(a.OPposition);
            }
        }
        return a;
    }
    public void updateActiveAps() {
        if (this.tempAps != null) {
            this.aps.addAll(tempAps);
            this.tempAps = null;
        }
    }
    public boolean isActivating() {
        return this.tempAps != null;
    }
    public Set<AccessPath> getTempAps() {
        return this.tempAps;
    }

    public NormalState BWCheckCurrentStmt(Stmt stmt, ByReferenceBoolean hasGeneratedNewState) {
        NormalState a = null;
        boolean shouldMoveOpCursor = this.OPposition != 0 && nextOpStmt == stmt;
        if (!shouldMoveOpCursor) {
            return this;
        }
        if (hasGeneratedNewState.value) {
            a = this;
        } else {
            a = clone();
            hasGeneratedNewState.value = true;
        }
        a.OPposition--;
        if (a.OPposition != 0) {
            a.nextOpStmt = a.ops.getStmt(a.OPposition-1);
        } else {
            a.nextOpStmt = null;
        }
        return a;
    }

    public NormalState deriveNormalFromAliasState(Stmt stmt, ByReferenceBoolean hasGeneratedNewState) {

        NormalState a;
        if (hasGeneratedNewState.value) {
            a = this;
        } else {
            a = clone();
            hasGeneratedNewState.value = true;
        }
        a.aliasAp = null;
        a.preStmts = new LinkedList<>(a.preStmts);
        a.preStmts.add(stmt);
        return a;
    }

    public NormalState deriveCallState(Stmt callStmt, ByReferenceBoolean hasGeneratedNewState) {
        NormalState a;
        if (hasGeneratedNewState.value) {
            a = this;
        } else {
            a = clone();
            hasGeneratedNewState.value = true;
        }
        Stack<Stmt> originalStack = this.callStack;
        a.callStack = new Stack<>();
        a.callStack.addAll(originalStack);
        a.callStack.push(callStmt);
        if (this.isZeroState()) {
            a.zeroState = a;
        }
        return a;
    }


    public NormalState deriveReturnState(Stmt callStmt, ByReferenceBoolean hasGeneratedNewState) {
        NormalState a;
        if (hasGeneratedNewState.value) {
            a = this;
        } else {
            a = clone();
            hasGeneratedNewState.value = true;
        }
        Stack<Stmt> originalStack = this.callStack;
        if (originalStack.isEmpty() || originalStack.peek() != callStmt) {return null;}

        a.callStack = new Stack<>();
        a.callStack.addAll(originalStack);
        a.callStack.pop();
        if (this.isZeroState()) {
            a.zeroState = a;
        }
        return a;
    }

    }
