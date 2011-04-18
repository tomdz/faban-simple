package com.sun.faban.driver.engine;

/**
 * TimingInfo is a value object that contains individual
 * timing records for each operation.
 */
public class TimingInfo {

    /** Constant value for specifying that the time is not set. */
    public static final long TIME_NOT_SET = Long.MIN_VALUE;

	/** Intended Invoke Time. */
    private long intendedInvokeTime = TIME_NOT_SET;

    /** Actual Invoke Time. */
    private long invokeTime = TIME_NOT_SET;

    /** Respond Time. */
    private long respondTime = TIME_NOT_SET;

    /** Last respond time, if any. */
    private long lastRespondTime = TIME_NOT_SET;

    /** Pause Time. */
    private long pauseTime = 0l;

    public boolean hasIntendedInvokeTime()
    {
        return intendedInvokeTime != TIME_NOT_SET;
    }

    public long getIntendedInvokeTime()
    {
        return intendedInvokeTime;
    }

    public void setIntendedInvokeTime(long intendedInvokeTime)
    {
        this.intendedInvokeTime = intendedInvokeTime;
    }

    public boolean hasInvokeTime()
    {
        return invokeTime != TIME_NOT_SET;
    }

    public long getInvokeTime()
    {
        return invokeTime;
    }

    public void setInvokeTime(long invokeTime)
    {
        this.invokeTime = invokeTime;
    }

    public void clearInvokeTime()
    {
        this.invokeTime = TIME_NOT_SET;
    }

    public boolean hasRespondTime()
    {
        return respondTime != TIME_NOT_SET;
    }

    public long getRespondTime()
    {
        return respondTime;
    }

    public void setRespondTime(long respondTime)
    {
        this.respondTime = respondTime;
    }

    public void clearRespondTime()
    {
        this.respondTime = TIME_NOT_SET;
    }

    public boolean hasLastRespondTime()
    {
        return lastRespondTime != TIME_NOT_SET;
    }

    public long getLastRespondTime()
    {
        return lastRespondTime;
    }

    public void setLastRespondTime(long lastRespondTime)
    {
        this.lastRespondTime = lastRespondTime;
    }

    public void clearLastRespondTime()
    {
        this.lastRespondTime = TIME_NOT_SET;
    }

    public long getPauseTime()
    {
        return pauseTime;
    }

    public void setPauseTime(long pauseTime)
    {
        this.pauseTime = pauseTime;
    }

    public void clearPauseTime()
    {
        this.pauseTime = 0l;
    }
}