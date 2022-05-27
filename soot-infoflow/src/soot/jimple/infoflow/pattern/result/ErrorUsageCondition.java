package soot.jimple.infoflow.pattern.result;

import soot.SootField;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOP;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPList;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPType;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import java.util.*;

public class ErrorUsageCondition {
    protected String entrypoint = null;
    protected String value = null;

    public ErrorUsageCondition(String entrypoint, String value) {
        this.entrypoint = entrypoint;
        this.value = value;
        translate();
    }
    protected Set<LCResourceOPType> involvedOpTypes = null;
    protected int patternNum = -1;
    protected List<ErrorUsageConditionNode> nodeList = null;

    public void translate() {
        List<String> values = new ArrayList<>();
        involvedOpTypes = new HashSet<>();
        if (!this.value.contains(ErrorUsageConstant.xml_splitor)) {
            patternNum = 1;
            values.add(value);
        } else {
            String[] tValues = value.split(ErrorUsageConstant.xml_splitor);
            values = Arrays.asList(tValues);
        }
        nodeList = new ArrayList<>();
        for (String v : values) {
            ErrorUsageConditionNode newNode = new ErrorUsageConditionNode(v);
            nodeList.add(newNode);
            involvedOpTypes.add(newNode.opType);
        }
    }

    public Set<LCResourceOPList> check(Set<LCResourceOPList> originalLists) {
        Set<LCResourceOPList> satisfiedLists = new HashSet<>();
        for (LCResourceOPList opList : originalLists) {
            if (isListSatisfied(opList)) {
                satisfiedLists.add(opList);
            }
        }
        return satisfiedLists;
    }

    private boolean isListSatisfied(LCResourceOPList opList) {
        List<LCResourceOPType> opListWithInvolvedTypes = new ArrayList<>();
        for (LCResourceOP op : opList.getOpList()) {
            LCResourceOPType tOpType = op.getOpType();
            if (involvedOpTypes.contains(tOpType)) {
                opListWithInvolvedTypes.add(tOpType);
            }
        }

        CheckingState initialState = new CheckingState();
        Set<CheckingState> currentStates = new HashSet<>();
        currentStates.add(initialState);
        for (ErrorUsageConditionNode node : this.nodeList) {
            Set<CheckingState> newStates = new HashSet<>();
            for (CheckingState currentState : currentStates) {
                Set<CheckingState> results = node.check(currentState, opListWithInvolvedTypes);
                newStates.addAll(results);
            }
            if (newStates.isEmpty()) {
                return false;
            } else {
                currentStates = newStates;
            }
        }
        return true;
    }


    class ErrorUsageConditionNode {
        public LCResourceOPType opType = LCResourceOPType.UNKNOWN;
        public ErrorUsageConditionType conditionType = ErrorUsageConditionType.UNKNOWN;
        public ErrorUsageConditionNode(String value) {
            value = value.trim();
            if (!value.startsWith("(") || !value.contains(")")) {
                System.err.println("ErrorUsageConditionNode initial error!! the value is : " + value);
                return;
            }
            String opTypeStr = value.substring(1, value.lastIndexOf(')'));
            opType = LCResourceOPType.valueOf(opTypeStr.toUpperCase());
            String conditionTypeStr = value.substring(value.lastIndexOf(')')+1);
            conditionType = ErrorUsageConditionType.toErrorUsageConditionType(conditionTypeStr);
        }

        public Set<CheckingState> check(CheckingState givenState, List<LCResourceOPType> opListWithInvolvedTypes) {
            Set<CheckingState> results = new HashSet<>();
            switch(this.conditionType) {
                case ONEORMORE:{
                    for (int i = givenState.currentIndex; i < opListWithInvolvedTypes.size(); i++) {
                        LCResourceOPType currentType = opListWithInvolvedTypes.get(i);
                        if (currentType == this.opType) {
                            results.add(new CheckingState(i+1));
                        } else {
                            break;
                        }
                    }
                    break;
                }
                case NONE:{
                    boolean contained = false;
                    for (int i = givenState.currentIndex; i < opListWithInvolvedTypes.size(); i++) {
                        LCResourceOPType currentType = opListWithInvolvedTypes.get(i);
                        if (currentType == this.opType) {
                            contained = true;
                            break;
                        }
                    }
                    if (!contained) {
                        results.add(givenState);
                    }
                    break;
                }
                default:break;
            }
            return results;
        }
    }

    class CheckingState{
        public int currentIndex = -1;
        public CheckingState() {
            this.currentIndex = 0;
        }
        public CheckingState(int newIndex) {
            this.currentIndex = newIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CheckingState that = (CheckingState) o;
            return currentIndex == that.currentIndex;
        }

        @Override
        public int hashCode() {
            return Objects.hash(currentIndex);
        }
    }



}
