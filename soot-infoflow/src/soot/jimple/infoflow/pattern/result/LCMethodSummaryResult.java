package soot.jimple.infoflow.pattern.result;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.infoflow.collect.ConcurrentIdentityHashMultiMap;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOP;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPList;
import soot.jimple.infoflow.pattern.sourceandsink.PatternFieldSourceSinkDefinition;
import soot.jimple.infoflow.results.InfoflowPerformanceData;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class LCMethodSummaryResult{
    protected HashMultiMap<FieldAndEntryMethodKey, LCResourceOPList> entrysummaries = null;
    private ReentrantLock lock = new ReentrantLock();
    public LCMethodSummaryResult() {
        this.entrysummaries = new HashMultiMap<>();
    }
    protected class FieldAndEntryMethodKey {
        protected SootField field;
        protected SootMethod entrymethod;
        protected int hashcode = -1;

        public FieldAndEntryMethodKey(PatternFieldSourceSinkDefinition def, SootMethod entrymethod) {
            this.field = def.getField();
            this.entrymethod = entrymethod;
        }

        public FieldAndEntryMethodKey(SootField field, SootMethod entrymethod) {
            this.field = field;
            this.entrymethod = entrymethod;
        }
        public boolean equals(Object other) {
            if (null == other || !(other instanceof FieldAndEntryMethodKey)) {
                return false;
            }
            if (this.hashCode() != other.hashCode()) {return false;}

            FieldAndEntryMethodKey otherkey = (FieldAndEntryMethodKey)other;
            if (otherkey.field != this.field) {return false;}
            if (otherkey.entrymethod != this.entrymethod) {return false;}
            return true;
        }
        public int hashCode(){
            return this.field.hashCode() + this.entrymethod.hashCode();
        }
    }

    public boolean addResult(PatternFieldSourceSinkDefinition def, SootMethod entrymethod, LCResourceOPList oplist) {
        if (null == def) {return false;}
        FieldAndEntryMethodKey key = new FieldAndEntryMethodKey(def, entrymethod);
        lock.lock();
        boolean result = this.entrysummaries.put(key, oplist);
        lock.unlock();
        return result;
    }

    public Set<LCResourceOPList> getOPList(SootField field, SootMethod entrymethod) {
        FieldAndEntryMethodKey givenKey = new FieldAndEntryMethodKey(field, entrymethod);
        return this.entrysummaries.get(givenKey);
    }
    public MultiMap<SootField, LCResourceOPList> getMethodOPSummary(SootMethod entrymethod) {
        MultiMap<SootField, LCResourceOPList> methodOPsummaries = new HashMultiMap<>();
        for (FieldAndEntryMethodKey key : this.entrysummaries.keySet()) {
            if (key.entrymethod == entrymethod) {
                methodOPsummaries.putAll(key.field, this.entrysummaries.get(key));
            }
        }
        return methodOPsummaries;
    }


    public MultiMap<SootMethod, LCResourceOPList> getFieldOPSummary(SootField field) {
        MultiMap<SootMethod, LCResourceOPList> fieldOPsummaries = new HashMultiMap<>();
        for (FieldAndEntryMethodKey key : this.entrysummaries.keySet()) {
            if (key.field == field) {
                fieldOPsummaries.putAll(key.entrymethod, this.entrysummaries.get(key));
            }
        }
        return fieldOPsummaries;
    }


    public MultiMap<FieldAndEntryMethodKey, LCResourceOPList> getAllResults() {
        return this.entrysummaries;
    }
    public Map<SootField, MultiMap<String, LCResourceOPList>> getAllResultsSortedByFieldsAndEntrypoints() {
        Map<SootField, MultiMap<String, LCResourceOPList>> newMap = new HashMap<>();
        for (FieldAndEntryMethodKey key : this.entrysummaries.keySet()) {
            SootField f = key.field;
            Set<LCResourceOPList> lists = this.entrysummaries.get(key);
            SootMethod entrypoint = key.entrymethod;
            String entrypointStr = entrypoint.getName();
            MultiMap<String, LCResourceOPList> innerMap = null;
            if (newMap.containsKey(f)) {
                innerMap = newMap.get(f);
            } else {
                innerMap = new HashMultiMap<>();
                newMap.put(f, innerMap);
            }
            for (LCResourceOPList list : lists) {
                innerMap.put(entrypointStr, list);
            }
        }
        return newMap;
    }


    public final static int TERMINATION_SUCCESS = 0;
    public final static int TERMINATION_DATA_FLOW_TIMEOUT = 1;
    public final static int TERMINATION_DATA_FLOW_OOM = 2;
    public final static int TERMINATION_PATH_RECONSTRUCTION_TIMEOUT = 4;
    public final static int TERMINATION_PATH_RECONSTRUCTION_OOM = 8;
    private static final Logger logger = LoggerFactory.getLogger(LCMethodSummaryResult.class);
    private volatile InfoflowPerformanceData performanceData = null;
    private volatile List<String> exceptions = null;
    private int terminationState = TERMINATION_SUCCESS;

    public boolean isEmpty() {
        return this.entrysummaries == null || this.entrysummaries.isEmpty();
    }
    public void addException(String ex) {
        if (exceptions == null) {
            synchronized (this) {
                if (exceptions == null)
                    exceptions = new ArrayList<String>();
            }
        }
        exceptions.add(ex);
    }
    public List<String> getExceptions() {
        return exceptions;
    }
    public void clear() {this.entrysummaries.clear();}
    public int getTerminationState() {
        return terminationState;
    }
    public void setTerminationState(int terminationState) {
        this.terminationState = terminationState;
    }
    public void setPerformanceData(InfoflowPerformanceData performanceData) {
        this.performanceData = performanceData;
    }
    public InfoflowPerformanceData getPerformanceData() {return performanceData;}
    public int size() {
        return this.entrysummaries.isEmpty() ? 0 : this.entrysummaries.values().size();
    }

    public void addAll(LCMethodSummaryResult results) {
        if (results == null)
            return;

        if (results.getExceptions() != null) {
            for (String e : results.getExceptions())
                addException(e);
        }

        if (!results.entrysummaries.isEmpty()) {
            for (FieldAndEntryMethodKey key : results.entrysummaries.keySet())
                this.entrysummaries.putAll(key, results.entrysummaries.get(key));
        }

        // Sum up the performance data
        if (results.performanceData != null) {
            if (this.performanceData == null)
                this.performanceData = results.performanceData;
            else
                this.performanceData.add(results.performanceData);
        }
    }
}
