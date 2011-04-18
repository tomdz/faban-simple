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

import com.sun.faban.driver.CycleType;
import com.sun.faban.driver.DefinitionException;
import com.sun.faban.driver.util.Random;

import java.io.Serializable;
import java.lang.annotation.Annotation;

/**
 * Super class of all distributions.
 */
public abstract class Cycle implements Serializable, Cloneable {

    CycleType cycleType;
    double cycleDeviation;

   
    /**
     * Makes a deep copy of this cycle object.
     * @return A deep copy of this cycle object.
     * @see java.lang.Object#clone()
     */
    @Override
	public Object clone() {
        Object clone = null;
        try {
            clone = super.clone();
        } catch (CloneNotSupportedException e) {
            // we implement cloneable and just swallow this exception.
            // It should never happen.
        }
        return clone;
    }

     /**
     * Initializes this cycle according to the annotation.
     * @param a The annotation
     * @throws DefinitionException If there is an error in the annotation
     */
    public abstract void init(Annotation a) throws DefinitionException;

    /**
     * Randoms/calculates the delay time for a thread based on its
     * supplied random number generator and the actual conditions in the
     * distribution.
     *
     * @param random        The random number generator used
     * @return The delay time
     */
    public abstract long getDelay(Random random);

    /**
     * Provides the maximum value to be represented inside a histogram.
     * @return The max reasonable delay to be presented in the output histogram.
     */
    public abstract double getHistogramMax();
}