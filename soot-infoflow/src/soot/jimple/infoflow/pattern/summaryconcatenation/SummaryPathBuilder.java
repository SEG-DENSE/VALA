package soot.jimple.infoflow.pattern.summaryconcatenation;

import soot.SootClass;
import soot.jimple.infoflow.pattern.patterndata.PatternSkip1Data;
import soot.jimple.infoflow.pattern.patterndata.PatternDataHelper;
import soot.jimple.infoflow.pattern.patterndata.PatternEntryData;
import soot.jimple.infoflow.pattern.result.LCMethodSummaryResult;

public class SummaryPathBuilder {
    LCMethodSummaryResult results = null;
    PatternDataHelper helper = null;
    public SummaryPathBuilder(LCMethodSummaryResult results) {
        this.results = results;
        helper = PatternDataHelper.v();
    }
    public void calculate() {
        PatternSkip1Data p1 = helper.getPattern1();
        if (null != p1) {
            for (SootClass entryClass : p1.getInvolvedEntrypoints().keySet()) {
                PatternEntryData data = p1.getInvolvedEntrypoints().get(entryClass);
            }
        }
    }
}
