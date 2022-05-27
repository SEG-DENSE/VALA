package soot.jimple.infoflow.pattern.alias;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.pattern.solver.NormalSolver;
import soot.jimple.infoflow.pattern.solver.NormalState;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowManager;

public class PatternAliasStrategy implements IPatternAliasingStrategy{
    protected PatternInfoflowManager manager = null;
    protected NormalSolver bSolver = null;
    public PatternAliasStrategy(PatternInfoflowManager manager, NormalSolver backwardsSolver) {
        this.manager = manager;
        this.bSolver = backwardsSolver;
    }

    @Override
    public void computeAliasTaints(final NormalState aliasState, final Stmt stmt) {
        for (Unit next : manager.getICFG().getPredsOf(stmt)) {
            this.bSolver.propagate(next, aliasState, stmt);
        }
    }

    @Override
    public NormalSolver getSolver() {
        return this.bSolver;
    }

    @Override
    public void cleanup() {

    }
}
