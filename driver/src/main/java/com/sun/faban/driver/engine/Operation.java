package com.sun.faban.driver.engine;

import java.io.Serializable;
import java.lang.reflect.Method;

import com.sun.faban.driver.BenchmarkOperation;
import com.sun.faban.driver.Timing;

public class Operation implements Serializable, Cloneable {
	private static final long serialVersionUID = 2L;
	
	private final String name;
	private final double[] percentileLimits;
	private final double max90th;
	private final Timing timing;
	private final boolean countToMetric;
	private Cycle cycle;
	private transient Method method;

    public Operation(String name, double[] percentileLimits, double max90th, Timing timing, boolean countToMetric)
    {
        this.name = name;
        this.percentileLimits = percentileLimits;
        this.max90th = max90th;
        this.timing = timing;
        this.countToMetric = countToMetric;
    }

    public Operation(BenchmarkOperation op)
    {
        this.name = op.name();
        this.percentileLimits = op.percentileLimits();
        this.max90th = op.max90th();
        this.timing = op.timing();
        this.countToMetric = op.countToMetric();
    }
    
    public String getName()
    {
        return name;
    }

    public double[] getPercentileLimits()
    {
        return percentileLimits;
    }

    public double getMax90th()
    {
        return max90th;
    }

    public Timing getTiming()
    {
        return timing;
    }

    public boolean isCountToMetric()
    {
        return countToMetric;
    }

    public Cycle getCycle()
    {
        return cycle;
    }

    public void setCycle(Cycle cycle)
    {
        this.cycle = cycle;
    }

    public Method getMethod()
    {
        return method;
    }

    public void setMethod(Method method)
    {
        this.method = method;
    }

    /**
     * Creates an exact deep clone of this object.
     * 
     * @return a clone of this instance.
     * @throws CloneNotSupportedException if the object's class does not
     *                                    support the <code>Cloneable</code> interface. Subclasses
     *                                    that override the <code>clone</code> method can also
     *                                    throw this exception to indicate that an instance cannot
     *                                    be cloned.
     * @see Cloneable
     */
    @Override
	public Object clone() throws CloneNotSupportedException {
        Operation op = new Operation(name, percentileLimits, max90th, timing, countToMetric);
        op.setCycle(cycle);
        op.setMethod(method);
        return op;
    }
}