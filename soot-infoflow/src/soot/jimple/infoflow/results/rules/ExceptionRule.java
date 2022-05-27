package soot.jimple.infoflow.results.rules;

import soot.jimple.infoflow.pattern.result.ErrorUsageDetailedReport;

public interface ExceptionRule {
    public boolean shouldBeExcluded(ErrorUsageDetailedReport report);
}
