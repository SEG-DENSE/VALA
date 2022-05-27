package soot.jimple.infoflow.pattern.problems;

import heros.FlowFunction;
import soot.Unit;
import soot.jimple.infoflow.pattern.solver.NormalState;

import java.util.Set;

public abstract class BWSolverCallFlowFunction implements FlowFunction<NormalState> {
    @Override
    public Set<NormalState> computeTargets(NormalState source) {
        return null;
    }

    public abstract Set<NormalState> computeCallTargets(NormalState source, Unit returnstmt, boolean isRelevant);
    }
