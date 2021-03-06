/* The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.sun.com/cddl/cddl.html or
 * install_dir/legal/LICENSE
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at install_dir/legal/LICENSE.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * $Id$
 *
 * Copyright 2005-2009 Sun Microsystems Inc. All Rights Reserved
 */
package com.sun.faban.driver.engine;

import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;


/**
 * A driver thread that controls the run by ramp up, steady state,
 * and ramp down cycles.
 *
 * @author Akara Sucharitakul
 */
public class CycleThread extends AgentThread {

    /**
     * Allocates and initializes the timing structures which is specific
     * to the pseudo-thread dimensions.
     */
	void initTimes() {
        delayTime = new long[1];
        startTime = new long[1];
        endTime = new long[1];
        previousOperation = new int[1];

        // This is the start and end time of the previous operation used to
        // calculate the start of the next operation. We set it to the current
        // time for the first operation to have a reference point. In fact,
        // any reference point is OK.
        startTime[0] = System.nanoTime();
        endTime[0] = startTime[0];
        previousOperation[0] = -1;
    }

    /**
     * Each thread executes in the doRun method until the benchmark time is up
     * The main loop chooses a tx. type according to the mix specified in
     * the parameter file and calls the appropriate transaction
     * method to do the job.
   	 * The stats for the entire run are stored in a Metrics object
   	 * which is returned to the Agent via the getResult() method.
     * @see Metrics
     */
	void doRun() {
        driverContext = new DriverContext(this, timer);

        try {
            driver = driverClass.newInstance();
        }
        catch (Throwable t) {
            Throwable cause = t.getCause();
            while (cause != null) {
                t = cause;
                cause = t.getCause();
            }
            logger.log(Level.SEVERE, name + ": Error initializing driver object.", t);
            agent.abortRun();
            return; // Terminate this thread immediately
        }
        // Call the preRun.
        preRun();

        // Notify the agent that we have started successfully.
        agent.threadStartLatch.countDown();
        
        if (runInfo.simultaneousStart) {
			waitStartTime();
		}

        // Calculate cycle counts
        endRampUp = runInfo.rampUp;
        endStdyState = endRampUp + runInfo.stdyState;
        endRampDown = endStdyState + runInfo.rampDown;

        selector = new Mix.Selector[1];
        selector[0] = driverConfig.mix[0].selector(random);

        logger.fine(name + ": Start of run.");

        // Loop until cycles are up
        driverLoop:
        while (!stopped) {

            // Save the previous operation
            previousOperation[mixId] = currentOperation;
            Operation previousOp = null;
            if (previousOperation[mixId] >= 0) {
				previousOp = driverConfig.operations[currentOperation];
			}

            // Select the operation
            currentOperation = selector[0].select();
            Operation op = driverConfig.operations[currentOperation];

            driverContext.setInvokeTime(getInvokeTime(previousOp, mixId));

            // Invoke the operation
            try {
                op.getMethod().invoke(driver);
                validateTimeCompletion(op);
                checkRamp();
                metrics.recordTx();
                metrics.recordDelayTime();
            }
            catch (InvocationTargetException e) {
                // An invocation target exception is caused by another
                // exception thrown by the operation directly.
                Throwable cause = e.getCause();
                checkFatal(cause, op);
                checkRamp();
                metrics.recordError();
                logError(cause, op);

                // We have to fix up the invoke/respond times to have valid
                // values and not -1.

                // In case of exception, invokeTime or even respondTime may
                // still be -1.
                TimingInfo timingInfo = driverContext.timingInfo;

                // The lastRespondTime may be set, though. if so, propagate
                // it back to respondTime.
                if (!timingInfo.hasRespondTime() && timingInfo.hasLastRespondTime()) {
                    logger.fine("Potential open request in operation " + op.getMethod().getName() + ".");
                    timingInfo.setRespondTime(timingInfo.getLastRespondTime());
                }

                // If it never waited, we'll see whether we can just use the
                // previous start and end times.
                if (!timingInfo.hasInvokeTime()) {
                    long currentTime = System.nanoTime();
                    if (currentTime < timingInfo.getIntendedInvokeTime()) {
                        timingInfo.setInvokeTime(startTime[mixId]);
                        timingInfo.setRespondTime(endTime[mixId]);
                    }
                    else {
                        // Too late, we'll need to use the real time
                        // for both invoke and respond time.
                        timingInfo.setInvokeTime(System.nanoTime());
                        timingInfo.setRespondTime(timingInfo.getInvokeTime());
                        // The delay time is invalid,
                        // we cannot record in this case.
                    }
                }
                else if (!timingInfo.hasRespondTime()) {
                    timingInfo.setRespondTime(timingInfo.getInvokeTime());
                    metrics.recordDelayTime();
                }
                else {
                    metrics.recordDelayTime();
                }
            }
            catch (IllegalAccessException e) {
                logger.log(Level.SEVERE, String.format("%s.%s: %s", name, op.getMethod().getName(), e.getMessage()), e);
                agent.abortRun();
                return;
            }

            startTime[mixId] = driverContext.timingInfo.getInvokeTime();
            endTime[mixId] = driverContext.timingInfo.getRespondTime();

            if (cycleCount > endRampDown) {
				break driverLoop;
			}
        }
        logger.fine(name + ": End of run.");
    }

    /**
     * Tests whether the last operation is in steady state or not. This is
     * called through the context from the driver from within the operation
     * so we need to be careful not to change run control parameters. This
     * method only reads the stats.
     * @return True if the last operation is in steady state, false otherwise.
     */
	boolean isSteadyState() {
        if (!startTimeSet) {
			return false;
		}
        // Copy out cycle count so we do not alternate it here.
        // Remember, this method is controlled by the user-implemented
        // driver through the context.
        int cycleCount = this.cycleCount + 1;

        if (cycleCount > endRampUp && cycleCount <= endStdyState) {
			return true;
		}
		return false;	
    }

    /**
     * Tests whether the time between start and end is in steady state or not.
     * For non time-based steady state, this will depend on the current cycle
     * count. Otherwise time is used.
     *
     * @param start The start of a time span
     * @param end   The end of a time span
     * @return true if this time span is in steady state, false otherwise.
     */
	boolean isSteadyState(long start, long end) {
        return isSteadyState();
    }

    /**
     * Checks whether the last operation is in the ramp-up or ramp-down or
     * not. Updates the inRamp parameter accordingly.
     */
	void checkRamp() {
        // Note: in cycle runs without simultaneous start, the startTimeSet
        // flag is only set once the start time has reached. Unlike time runs
        // without simultaneous starts where the startTimeSet is set to true
        // once the start time is actually set.
        if (!runInfo.simultaneousStart && !startTimeSet && agent.timeSetLatch.getCount() == 0) {
            long invoke = driverContext.timingInfo.getInvokeTime();
            if (invoke >= agent.startTime) {
                ++cycleCount; // Count this tx which started after bench start.
                startTimeSet = true;
                inRamp = true;
            }
            else if (invoke == -1 && System.nanoTime() >= agent.startTime) {
                // We need to set the start time as time has come.
                startTimeSet = true;
                inRamp = true;
            }
        }
        else if (startTimeSet) { // Cycle where start time set not counted.
            ++cycleCount;
            if (cycleCount > endRampUp && cycleCount <= endStdyState) {
				inRamp = false;
			}
            else {
				inRamp = true;
			}
        }
    }
}
