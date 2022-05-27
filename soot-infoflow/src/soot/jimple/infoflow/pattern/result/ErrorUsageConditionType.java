package soot.jimple.infoflow.pattern.result;

public enum ErrorUsageConditionType {
    ONEORMORE("+"), NONE("-"), UNKNOWN("n");
    private String value = null;
    ErrorUsageConditionType(String value) {
        this.value = value;
    }

    public static ErrorUsageConditionType  toErrorUsageConditionType(String givenValue) {
        for (ErrorUsageConditionType type : ErrorUsageConditionType.values()) {
            if (givenValue.equalsIgnoreCase(type.value)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
