package soot.jimple.infoflow.pattern.problems;

import heros.FlowFunction;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.infoflow.pattern.solver.NormalState;

import java.util.Set;

public abstract class SolverCallFlowFunction implements FlowFunction<NormalState> {
    @Override
    public Set<NormalState> computeTargets(NormalState source) {
        return null;
    }

    public abstract Set<NormalState> computeCallTargets(NormalState source, boolean isRelevant);
}
