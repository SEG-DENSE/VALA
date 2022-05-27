package soot.jimple.infoflow.pattern.result;

import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;

public interface LCMethodResultsAvailableHandler {
    void onResultsAvailable(IInfoflowCFG cfg, LCMethodSummaryResult results);
}
