package soot.jimple.infoflow.pattern.solver;

public class NormalEdge<N, D> {
    protected final N target;
    protected final D dTarget;
    protected final int hashCode;

    public NormalEdge(N target, D dTarget) {
        this.target = target;
        this.dTarget = dTarget;

        final int prime = 31;
        int result = 1;
        result = prime * result + ((dTarget == null) ? 0 : dTarget.hashCode());
        result = prime * result + ((target == null) ? 0 : target.hashCode());
        this.hashCode = result;
    }

    public N getTarget() {
        return target;
    }

    public D factAtTarget() {
        return dTarget;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        @SuppressWarnings("rawtypes")
        NormalEdge other = (NormalEdge) obj;
        if (dTarget == null) {
            if (other.dTarget != null)
                return false;
        } else if (!dTarget.equals(other.dTarget))
            return false;
        if (target == null) {
            if (other.target != null)
                return false;
        } else if (!target.equals(other.target))
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append("<");
        result.append(target.toString());
        result.append(",");
        result.append(dTarget);
        result.append(">");
        return result.toString();
    }

}
