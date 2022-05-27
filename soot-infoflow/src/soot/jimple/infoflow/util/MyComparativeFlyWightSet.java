package soot.jimple.infoflow.util;

import heros.solver.Pair;
import soot.jimple.IfStmt;
import soot.jimple.Stmt;
import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkDefinition;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MyComparativeFlyWightSet<V>{
    private ConcurrentHashMap<SourceSinkDefinition, ConcurrentHashMap<InnerMap, Set<V>>> map = null;
    private ConcurrentHashMap<SourceSinkDefinition, ConcurrentHashMap<Integer, Set<Set<V>>>> nummap = null;
    private ConcurrentHashMap<SourceSinkDefinition, Lock> lockmap = null;

    public MyComparativeFlyWightSet() {
        this.map = new ConcurrentHashMap<>();
        this.nummap = new ConcurrentHashMap<>();
        this.lockmap = new ConcurrentHashMap<>();
    }
    public Set<V> getSet(SourceSinkDefinition def, Set<V> originSet, V newV) {
        Lock lock = this.lockmap.computeIfAbsent(def, k -> new ReentrantLock());
        ConcurrentHashMap<InnerMap, Set<V>> innermaps = this.map.computeIfAbsent(def, k -> new ConcurrentHashMap<InnerMap, Set<V>>());
        InnerMap currentMap = new InnerMap(originSet, newV);
        if (innermaps.containsKey(currentMap)) {
            return innermaps.get(currentMap);
        }

        lock.lock();
        Set<V> result = innermaps.get(currentMap);
        if (null != result) {
            lock.unlock();
            return result;
        }
        result = new HashSet<>(originSet);
        result.add(newV);
        ConcurrentHashMap<Integer, Set<Set<V>>> innernummap = nummap.computeIfAbsent(def, k -> new ConcurrentHashMap<>());
        Set<Set<V>> samenumsets = innernummap.computeIfAbsent(result.size(), k -> new HashSet<>());
        boolean contains = false;
        for (Set<V> currentSet : samenumsets) {
            if (MyOwnUtils.setCompare(currentSet, result)) {
                result = currentSet;
                contains = true;
                break;
            }
        }
        if (!contains) {
            samenumsets.add(result);
        }
        innermaps.put(currentMap, result);
        lock.unlock();
        return result;
    }
    private static MyComparativeFlyWightSet<Pair<IfStmt, Boolean>> ifkillSetGenerator =  new MyComparativeFlyWightSet<Pair<IfStmt, Boolean>>();
    private static MyComparativeFlyWightSet<Stmt> killSetGenerator = new MyComparativeFlyWightSet<Stmt>();
    public static MyComparativeFlyWightSet<Pair<IfStmt, Boolean>> getIfKillSetGenerator() {
//            ifkillSetGenerator = new MyComparativeFlyWightSet<Pair<IfStmt, Boolean>>();
        return ifkillSetGenerator;
    }

    public static MyComparativeFlyWightSet<Stmt> getKillSetGenerator() {
        return killSetGenerator;
    }


    class InnerMap {
        public InnerMap(Set<V> originalStmts, V newStmt) {
            this.originalStmts = originalStmts;
            this.newStmt = newStmt;
        }

        Set<V> originalStmts = null;
        V newStmt = null;
        public boolean equals(Object other) {
            InnerMap otherMap = (InnerMap)other;
            if (originalStmts != otherMap.originalStmts) {
                return false;
            }
            if (newStmt != otherMap.newStmt) {
                return false;
            }
            return true;
        }

        public int hashCode() {
            int hashcode = 0;
            if (null != originalStmts && !originalStmts.isEmpty()) {
                for (V v : originalStmts) {
                    hashcode += v.hashCode();
                }
            }
            hashcode += newStmt.hashCode();
            return hashcode;
        }
    }

}
