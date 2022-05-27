package soot.jimple.infoflow.pattern.result;

import soot.jimple.infoflow.pattern.patterndata.PatternData;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPList;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.util.*;

public class ErrorUsageDefinition {
    protected String id = null;
    protected PatternData patternData = null;
    protected MultiMap<String, ErrorUsageCondition> conditions = null;
    public ErrorUsageDefinition(String id, PatternData patternData) {
        this.conditions = new HashMultiMap<>();
        this.patternData = patternData;
        this.id = id;
    }
    public void addCondition(String entrypoint, String value){
        ErrorUsageCondition condition = new ErrorUsageCondition(entrypoint, value);
        conditions.put(entrypoint, condition);
    }

    public ErrorUsageDetailedReport check(MultiMap<String, LCResourceOPList> lists) {
        ErrorUsageDetailedReport report = new ErrorUsageDetailedReport();
        report.violatedPattern = this.patternData;
        for (String entrypointStr : conditions.keySet()) {
            Set<ErrorUsageCondition> currentConditions = conditions.get(entrypointStr);
            Set<LCResourceOPList> currentLists = lists.get(entrypointStr);
            if (currentLists.isEmpty()) {
                currentLists = Collections.singleton(LCResourceOPList.getEmptyOPList());
            }
            for (ErrorUsageCondition currentCondition : currentConditions) {
                Set<LCResourceOPList> satisfiedLists = currentCondition.check(currentLists);
                currentLists = satisfiedLists;
            }
            if (currentLists.isEmpty()) {
                return null;
            } else {
                report.addSatisfiedOPListForEntrypoint(entrypointStr, (LCResourceOPList) currentLists.toArray()[0]);
            }
        }
        return report;
    }

}
