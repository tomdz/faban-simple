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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.faban.driver.Background;
import com.sun.faban.driver.BenchmarkDriver;
import com.sun.faban.driver.BenchmarkOperation;
import com.sun.faban.driver.ConfigurationException;
import com.sun.faban.driver.CycleType;
import com.sun.faban.driver.DefinitionException;
import com.sun.faban.driver.InitialDelay;
import com.sun.faban.driver.OnceAfter;
import com.sun.faban.driver.OnceBefore;
import com.sun.faban.driver.RunControl;

/**
 * Implements the basic benchmark, driver, and operation definitions.
 *
 * @author Akara Sucharitakul
 */
public class BenchmarkDefinition implements Serializable, Cloneable {

	private static final long serialVersionUID = 1L;
    
	String name;
    String version;
    Driver[] drivers;
    RunControl runControl;
    String metric;
    String scaleName;
    String scaleUnit;
    boolean configPrecedence;

    static BenchmarkDefinition read(String defClassName) throws DefinitionException, ConfigurationException {
        BenchmarkDefinition def = new BenchmarkDefinition();
        String className = def.getClass().getName();
        Logger logger = Logger.getLogger(className);
        Class<?> defClass;

        try {
            defClass = Class.forName(defClassName);
        }
        catch (ClassNotFoundException e) {
            //Did not find the class in the default classloader, 
            //look first in the faban.tmpdir then in java.io.tmpdir
            //for the generated class
            String tempDir = System.getProperty("faban.tmpdir");
            
            if (tempDir==null){
                tempDir = System.getProperty("java.io.tmpdir");
            }
            
            File classFile = new File(tempDir);
            URL url[]= new URL[1];
            
            try {
                url[0] = classFile.toURI().toURL();
            }
            catch (MalformedURLException ex) {
                logger.log(Level.SEVERE, "Bad file URL for generated java class!");
                throw new ConfigurationException(ex);
            }

            URLClassLoader loader = new URLClassLoader(url, BenchmarkDefinition.class.getClassLoader());
            
            try {
                defClass=loader.loadClass(defClassName);
                
            }
            catch(ClassNotFoundException cnfex) {
                ConfigurationException ce = new ConfigurationException(e);
                logger.log(Level.SEVERE, e.getMessage(), ce);
                throw ce;

            }
            
        }

        if (!defClass.isAnnotationPresent(com.sun.faban.driver.BenchmarkDefinition.class)) {
            String msg = "Class " + defClassName + " is not a benchmark definition.";
            logger.severe(msg);
            throw new ConfigurationException(msg);
        }

        com.sun.faban.driver.BenchmarkDefinition benchDefAnnotation = defClass.getAnnotation(com.sun.faban.driver.BenchmarkDefinition.class);

        def.name = benchDefAnnotation.name();
        def.version = benchDefAnnotation.version();
        def.runControl = benchDefAnnotation.runControl();
        def.metric = benchDefAnnotation.metric();
        def.scaleName = benchDefAnnotation.scaleName();
        def.scaleUnit = benchDefAnnotation.scaleUnit();
        def.configPrecedence = benchDefAnnotation.configPrecedence();

        ArrayList<Class<?>> driverClassList = new ArrayList<Class<?>>();

        // Get all the driver classes
        for (Class<?> driverClass : benchDefAnnotation.drivers()) {
			if (driverClass != Object.class && driverClass.isAnnotationPresent(BenchmarkDriver.class)) {
				driverClassList.add(driverClass);
			}
		}

        // If defClass is not in list and is a driver, prepend
        if (driverClassList.indexOf(defClass) < 0 && defClass.isAnnotationPresent(BenchmarkDriver.class)) {
			driverClassList.add(0, defClass);
		}

        // Check that we have at least one driver
        if (driverClassList.size() <= 0) {
            String msg = "No driver classes found";
            logger.severe(msg);
            throw new DefinitionException(msg);
        }

        // Transfer the classes to an array
        Class<?>[] driverClasses = new Class<?>[driverClassList.size()];
        driverClasses = driverClassList.toArray(driverClasses);

        def.drivers = new Driver[driverClasses.length];

        // Obtain all driver and driver class names
        for (int i = 0; i < driverClasses.length; i++) {
            BenchmarkDriver benchDriver = driverClasses[i].getAnnotation(BenchmarkDriver.class);
            def.drivers[i] = new Driver();
            def.drivers[i].name = benchDriver.name();
            def.drivers[i].metric = benchDriver.metric();
            def.drivers[i].opsUnit = benchDriver.opsUnit();
            def.drivers[i].threadPerScale = benchDriver.threadPerScale();

            String[] percentiles = benchDriver.percentiles();
            def.drivers[i].percentiles = new double[percentiles.length];
            def.drivers[i].pctString = new String[percentiles.length];
            def.drivers[i].pctSuffix = new String[percentiles.length];

            // Parse the percentiles.
            for (int j = 0; j < percentiles.length; j++) {
                String percentile = percentiles[j];
                int length = percentile.length();
                if (percentile.endsWith("%")) {
                    --length;
                }
                if (!Character.isDigit(percentile.charAt(length - 1))) {
                    String suffix = percentile.substring(length - 2, length);
                    if (!suffix.equals("th") && !suffix.equals("st") && !suffix.equals("nd") && !suffix.equals("rd")) {
                        throw new DefinitionException("Invalid percentile suffix %s", suffix);
                    }
                    def.drivers[i].pctSuffix[j] = suffix;
                    length -= 2;
                }
                percentile = percentile.substring(0, length);
                def.drivers[i].pctString[j] = percentile;

                try {
                    def.drivers[i].percentiles[j] = Double.parseDouble(percentile);
                }
                catch (NumberFormatException e) {
                    throw new DefinitionException(e, "%s not a number.", percentile);
                }

                if (def.drivers[i].percentiles[j] <= 0d || def.drivers[i].percentiles[j] >= 100d) {
                    throw new DefinitionException("Percentile %s must be greater than 0 and less than 100.", percentile);
                }
            }

            def.drivers[i].responseTimeUnit = benchDriver.responseTimeUnit();
            def.drivers[i].histogramTimeUnit = benchDriver.histogramTimeUnit();
            if (def.drivers[i].responseTimeUnit.equals(TimeUnit.NANOSECONDS)) {
                throw new DefinitionException("@BenchmarkDriver responseTimeUnit must not be NANOSECONDS");
            }
            def.drivers[i].className = driverClasses[i].getName();
            populatePrePost(driverClasses[i], def.drivers[i]);
            getBackground(driverClasses[i], def.drivers[i]);
            def.drivers[i].mix[0] = Mix.getMix(driverClasses[i]);
            def.drivers[i].initialDelay[0] = getInitialDelay(driverClasses[i]);
            int totalOps = def.drivers[i].mix[0].operations.length;
            if (def.drivers[i].mix[1] != null) {
				totalOps += def.drivers[i].mix[1].operations.length;
			}

            // Copy operation references into a flat array.
            def.drivers[i].operations =
                    new Operation[totalOps];
            System.arraycopy(def.drivers[i].mix[0].operations, 0,
                    def.drivers[i].operations, 0,
                    def.drivers[i].mix[0].operations.length);
            if (def.drivers[i].mix[1] != null) {
                System.arraycopy(def.drivers[i].mix[1].operations, 0,
                        def.drivers[i].operations,
                        def.drivers[i].mix[0].operations.length,
                        def.drivers[i].mix[1].operations.length);
			}

            // Check the percentile limit on each operation
            double maxPctLimit = -1.0;
            for (Operation op : def.drivers[i].operations) {
                if (op.getPercentileLimits().length != def.drivers[i].percentiles.length) {
                    throw new DefinitionException("@BenchmarkOperation %s percentileLimits array must be the same length as @BenchmarkDriver percentiles", op.getName());
                }
                for (double limit : op.getPercentileLimits()) {
                    if (limit > 0.0 && limit > maxPctLimit) {
                        maxPctLimit = limit;
                    }
                }
            }
            if (def.drivers[i].percentiles.length > 0) {
                if (maxPctLimit <= 0.0) {
                    throw new DefinitionException("At least one percentile limit must be specified.");
                }
            }
            else { // Old style...
                for (Operation op : def.drivers[i].operations) {
                    if (op.getMax90th() > 0.0 && op.getMax90th()> maxPctLimit) {
                        maxPctLimit = op.getMax90th();
                    }
                }
                if (maxPctLimit <= 0.0) {
                    throw new DefinitionException("At least one max90th must be specified.");
                }
            }

            def.drivers[i].maxPercentile = maxPctLimit;
            def.drivers[i].driverClass = driverClasses[i];
        }
        return def;
    }


    /**
     * Reads the Faban definition annotations and prints a DD to file.
     * @param defClassName The defining class name
     * @throws ConfigurationException Error in the benchmark configuration
     * @throws DefinitionException Error in the benchmark definition
     */
    public static void printFabanDD(String defClassName) throws ConfigurationException, DefinitionException {
        Logger logger = Logger.getLogger(BenchmarkDefinition.class.getName());
        logger.fine("Generating Faban DD.");
        Class<?> defClass;
        try {
            defClass = Class.forName(defClassName);
            logger.fine("Found benchmark definition class " + defClassName);
        }
        catch (ClassNotFoundException e) {
            File runXml = new File(System.getProperty("benchmark.config"));
            throw new ConfigurationException("Defining class %s" + defClassName +
                                            " in " + runXml.getName() +
                                            " not found in deployment.", e);
        }

        if (!defClass.isAnnotationPresent(
                com.sun.faban.driver.BenchmarkDefinition.class)) {
            String msg = "Defining class " + defClassName +
                    " is not a benchmark definition.";
            logger.severe(msg);
            throw new DefinitionException(msg);
        }

        com.sun.faban.driver.BenchmarkDefinition benchDefAnnotation = defClass.
                getAnnotation(com.sun.faban.driver.BenchmarkDefinition.class);
        logger.fine("Start building XML.");
        StringBuilder b = new StringBuilder(2048);
        b.append("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n");
        b.append("<!-- Generated Faban Driver Framework DD, please do not " +
                 "modify -->\n");
        b.append("<fabanDriver>\n");
        b.append("    <name>").append(benchDefAnnotation.name()).
                append("</name>\n");
        b.append("    <version>").append(benchDefAnnotation.version()).
                append("</version>\n");
        b.append("    <runControl>").append(benchDefAnnotation.runControl()).
                append("</runControl>\n");
        b.append("    <metric>").append(benchDefAnnotation.metric()).
                append("</metric>\n");
        b.append("    <scaleName>").append(benchDefAnnotation.scaleName()).
                append("</scaleName>\n");
        b.append("    <scaleUnit>").append(benchDefAnnotation.scaleUnit()).
                append("</scaleUnit>\n");
        b.append("    <configPrecedence>").append(benchDefAnnotation.
                configPrecedence()).append("</configPrecedence>\n");
        b.append("</fabanDriver>\n");

        String outputFile = System.getProperty("benchmark.ddfile");
        try {
            logger.fine("Writing DD file " + outputFile);
            FileOutputStream out = new FileOutputStream(outputFile);
            out.write(b.toString().getBytes());
            out.flush();
            out.close();
            logger.fine("Done writing " + outputFile);
        } catch (IOException e) {
            logger.severe(e.getMessage());
        }
    }

    private static void getBackground(Class<?> driverClass, Driver driver) throws DefinitionException {
        if (!driverClass.isAnnotationPresent(Background.class)) {
            driver.mix[1] = null;
            driver.initialDelay[1] = null;
            return;
        }
        Background background = driverClass.getAnnotation(Background.class);
        String[] ops = background.operations();
        FixedSequence mix = new FixedSequence();
        mix.deviation = 2d;
        mix.operations = getOperationsNoCycles(driverClass, ops);
        com.sun.faban.driver.FixedTime[] timings = background.timings();
        if (timings.length == 0) { // No bg timing, use driver or method timing
            setCycles(mix.operations, driverClass);
        }
        else if (timings.length == 1) { // Apply to all the background ops
            FixedTime fixedTime = new FixedTime();
            fixedTime.init(timings[0]);
            for (Operation op : mix.operations) {
				op.setCycle(fixedTime);
			}
        }
        else if (timings.length > 1) { // Appy timing to each background op
            if (timings.length != mix.operations.length) {
				throw new DefinitionException("No of background ops must match the no of timings, currently %d vs %d", mix.operations.length, timings.length);
			}
            for (int i = 0; i < timings.length; i++) {
                if (mix.operations[i].getCycle() != null) {
					throw new DefinitionException("Duplicate operations entry in @Background");
				}
                Cycle cycle = new FixedTime();
                cycle.init(timings[i]);
                mix.operations[i].setCycle(cycle);
            }
        }
        driver.mix[1] = mix;
        driver.initialDelay[1] = getInitialDelay(background.initialDelay().max());
    }

    private static Cycle getInitialDelay(Class<?> driverClass) {
        InitialDelay initDelay = driverClass.getAnnotation(InitialDelay.class);
        int max;
        if (initDelay == null) {
			max = 0;
		} else {
			max = initDelay.max();
		}
        return getInitialDelay(max);
    }

    private static Cycle getInitialDelay(int max) {
        Cycle delay;
        if (max <= 0) {
            FixedTime fixed = new FixedTime();
            fixed.cycleTime = 0;
            delay = fixed;
        } else {
            Uniform uniform = new Uniform();
            uniform.cycleMin = 0;
            uniform.cycleMax = max;
            delay = uniform;
        }
        delay.cycleType = CycleType.CYCLETIME;
        delay.cycleDeviation = 0d;
        return delay;
    }

    private BenchmarkDefinition() {
    	super();
    }

    public static Operation[] getOperations(Class<?> driverClass, String[] names) throws DefinitionException
    {
        Operation[] ops = getOperationsNoCycles(driverClass, names);
        setCycles(ops, driverClass);
        return ops;
    }

    private static Map<String, Operation> getOperationsInternal(Class<?> driverClass) throws DefinitionException {
        Map<String, Operation> operations = new LinkedHashMap<String, Operation>();
        Method[] methods = driverClass.getMethods();
        for (Method m : methods) {
            if (m.isAnnotationPresent(BenchmarkOperation.class)) {
                BenchmarkOperation benchOp = m.getAnnotation(BenchmarkOperation.class);
                Operation op = new Operation(benchOp);
                op.setMethod(m);
                operations.put(op.getName(), op);
            }
        }

        return operations;
    }

    public static Operation[] getOperations(Class<?> driverClass) throws DefinitionException {
        Map<String, Operation> operations = getOperationsInternal(driverClass);
        Operation[] ops = operations.values().toArray(new Operation[operations.size()]);
        setCycles(ops, driverClass);
        return ops;
    }

    private static Operation[] getOperationsNoCycles(Class<?> driverClass, String[] names) throws DefinitionException {
        Map<String, Operation> operations = getOperationsInternal(driverClass);
        Operation[] ops = new Operation[names.length];

        // Then we list them according to the name
        for (int i = 0; i < names.length; i++) {
            ops[i] = operations.get(names[i]);
            if (ops[i] == null) {
				throw new DefinitionException("Operation \"%s\" listed in mix not found", names[i]);
			}
        }
        return ops;
    }

    private static void setCycles(Operation[] operations, Class<?> driverClass) throws DefinitionException
    {
        Cycle classCycle = null;
        Annotation[] annotations = driverClass.getAnnotations();
        for (int i = 0; i < annotations.length; i++) {
            String typeName = annotations[i].annotationType().getName();
            int cnBegin = typeName.lastIndexOf('.') + 1;

            String annotationName = typeName.substring(cnBegin);
            String pkgName = typeName.substring(0, cnBegin);
            String cycleName = pkgName + "engine." + annotationName;
            Cycle cycle = null;
            try {
                cycle = Class.forName(cycleName).asSubclass(Cycle.class).newInstance();
            }
            catch (Exception e) {
                // If the annotation is not a cycle, we just ignore it here.
                // It is the responsibility of the corresponding facility to
                // pick it up.
                continue;
            }
            if (classCycle != null) {
                throw new DefinitionException("Duplicate class cycle annotation @%s", annotationName);
            }
            cycle.init(annotations[i]);
            classCycle = cycle;
        }

        // Now we make the same set of tests at the operation level
        for (Operation o : operations) {
            annotations = o.getMethod().getAnnotations();
            for (int i = 0; i < annotations.length; i++) {
                String typeName = annotations[i].annotationType().getName();
                int cnBegin = typeName.lastIndexOf('.') + 1;

                String annotationName = typeName.substring(cnBegin);
                String pkgName = typeName.substring(0, cnBegin);
                String cycleName = pkgName + "engine." + annotationName;
                Cycle cycle = null;
                try {
                    cycle = Class.forName(cycleName).asSubclass(Cycle.class).newInstance();
                }
                catch (Exception e) {
                    // If the annotation is not a cycle, we just ignore it here.
                    // It is the responsibility of the corresponding facility to
                    // pick it up.
                    continue;
                }
                if (o.getCycle() != null) {
                    throw new DefinitionException("Duplicate operation cycle annotation for operation %s @%s", o.getName(), annotationName);
                }
                cycle.init(annotations[i]);
                o.setCycle(cycle);
            }

            // Finally, we need to test for no cycle at all and handle the case
            if (o.getCycle() == null) {
                o.setCycle(classCycle);
            }
            if (o.getCycle() == null) {
                throw new DefinitionException("No cycle distribution annotation for operation %s", o.getName());
            }
        }
    }

    static void populatePrePost(Class<?> driverClass, Driver driver)
            throws DefinitionException {
        Method[] methods = driverClass.getMethods();
        for (Method m : methods) {
            OnceBefore onceBefore = m.getAnnotation(OnceBefore.class);
            OnceAfter onceAfter = m.getAnnotation(OnceAfter.class);

            if (onceBefore != null) {
                if (driver.preRun == null) {
                    driver.preRun = new DriverMethod();
                    driver.preRun.m = m;
                    driver.preRun.genericName = m.toGenericString();
                    driver.preRun.perAgent = onceBefore.perAgent();
                }
                else {
                    throw new DefinitionException("Found more than one @OnceBefore method, %s and %s.", driver.preRun.genericName, m.toGenericString());
                }
            }
			else if (onceAfter != null) {
                if (driver.postRun == null) {
                    driver.postRun = new DriverMethod();
                    driver.postRun.m = m;
                    driver.postRun.genericName = m.toGenericString();
                    driver.postRun.perAgent = onceAfter.perAgent();
                }
                else {
                    throw new DefinitionException("Found more than one @OnceAfter method, %s and %s.", driver.postRun.genericName, m.toGenericString());
                }
            }
		}
    }

    /**
     * The refillOperations method re-establishes the non-serializable
     * parts of the operations array.
     * @param driverClass The driver class
     * @param operations  The operation array
     */
    static void refillOperations(Class<?> driverClass, Operation[] operations) {

        {  // Use a separate code block as the vars in here are not used later
            int i;

            // Find a method in the list that is null
            for (i = 0; i < operations.length; i++ ) {
				if (operations[i].getMethod() == null) {
					break;
				}
			}

            // If none found, we do not need to do anything else.
            if (i == operations.length) {
				return;
			}
        }

        HashMap<String, Method> methodMap = new HashMap<String, Method>();

        // First we read all the operations that have the annotation
        Method[] methods = driverClass.getMethods();
        for (Method m : methods) {
			if (m.isAnnotationPresent(BenchmarkOperation.class)) {
                BenchmarkOperation benchOp = m.getAnnotation(BenchmarkOperation.class);
                methodMap.put(benchOp.name(), m);
            }
		}

        // Then we check each operation and get the method from the map.
        for (Operation o : operations) {
			if (o.getMethod() == null) {
				o.setMethod(methodMap.get(o.getName()));
			}
		}
    }

    /**
     * The refillMethod method re-establishes the non-serializable parts of
     * a DriverMethod object.
     * @param driverClass The driver class
     * @param method      The DriverMethod instance
     */
    static void refillMethod(Class<?> driverClass, DriverMethod method) {
        if (method != null && method.m == null) {
            Method[] methods = driverClass.getMethods();
            for (Method m : methods) {
				if (method.genericName.equals(m.toGenericString())) {
                    method.m = m;
                    break;
                }
			}
        }
    }

    /**
     * Creates and returns a copy of this object.  The precise meaning
     * of "copy" may depend on the class of the object. The general
     * intent is that, for any object <tt>x</tt>, the expression:
     * <blockquote>
     * <pre>
     * x.clone() != x</pre></blockquote>
     * will be true, and that the expression:
     * <blockquote>
     * <pre>
     * x.clone().getClass() == x.getClass()</pre></blockquote>
     * will be <tt>true</tt>, but these are not absolute requirements.
     * While it is typically the case that:
     * <blockquote>
     * <pre>
     * x.clone().equals(x)</pre></blockquote>
     * will be <tt>true</tt>, this is not an absolute requirement.
     * <p/>
     * By convention, the returned object should be obtained by calling
     * <tt>super.clone</tt>.  If a class and all of its superclasses (except
     * <tt>Object</tt>) obey this convention, it will be the case that
     * <tt>x.clone().getClass() == x.getClass()</tt>.
     * <p/>
     * By convention, the object returned by this method should be independent
     * of this object (which is being cloned).  To achieve this independence,
     * it may be necessary to modify one or more fields of the object returned
     * by <tt>super.clone</tt> before returning it.  Typically, this means
     * copying any mutable objects that comprise the internal "deep structure"
     * of the object being cloned and replacing the references to these
     * objects with references to the copies.  If a class contains only
     * primitive fields or references to immutable objects, then it is usually
     * the case that no fields in the object returned by <tt>super.clone</tt>
     * need to be modified.
     * <p/>
     * The method <tt>clone</tt> for class <tt>Object</tt> performs a
     * specific cloning operation. First, if the class of this object does
     * not implement the interface <tt>Cloneable</tt>, then a
     * <tt>CloneNotSupportedException</tt> is thrown. Note that all arrays
     * are considered to implement the interface <tt>Cloneable</tt>.
     * Otherwise, this method creates a new instance of the class of this
     * object and initializes all its fields with exactly the contents of
     * the corresponding fields of this object, as if by assignment; the
     * contents of the fields are not themselves cloned. Thus, this method
     * performs a "shallow copy" of this object, not a "deep copy" operation.
     * <p/>
     * The class <tt>Object</tt> does not itself implement the interface
     * <tt>Cloneable</tt>, so calling the <tt>clone</tt> method on an object
     * whose class is <tt>Object</tt> will result in throwing an
     * exception at run time.
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
        // Shallow copy for primitives and immutables
        BenchmarkDefinition clone = (BenchmarkDefinition) super.clone();

        // Then deep copy for the arrays and mutables.
        clone.drivers = new Driver[drivers.length];
        for (int i = 0; i < drivers.length; i++) {
			clone.drivers[i] = (Driver) drivers[i].clone();
		}

        return clone;
    }

    static class Driver implements Serializable, Cloneable {

		private static final long serialVersionUID = 1L;

		String name;
        String metric;
        String opsUnit;
        float threadPerScale;
        double[] percentiles;
        String[] pctString;
        String[] pctSuffix;
        TimeUnit responseTimeUnit;
        TimeUnit histogramTimeUnit;
        Mix[] mix = new Mix[2]; // Foreground (0) and background (1) mix.
        Cycle[] initialDelay = new Cycle[2]; // Foreground and background
        Operation[] operations;
        DriverMethod preRun;
        DriverMethod postRun;
        String className;
        double maxPercentile;

        // We try to send the whole driver class over to the agents so that all
        // agents will run consistent drivers. In case this is slow or does not
        //  work due to dependencies, we will need to set this field transient.
        // The receiving end must check this value for null and re-load the
        // class in such cases.
        Class<?> driverClass;

        /**
         * Creates and returns a copy of this object.  The precise meaning
         * of "copy" may depend on the class of the object. The general
         * intent is that, for any object <tt>x</tt>, the expression:
         * <blockquote>
         * <pre>
         * x.clone() != x</pre></blockquote>
         * will be true, and that the expression:
         * <blockquote>
         * <pre>
         * x.clone().getClass() == x.getClass()</pre></blockquote>
         * will be <tt>true</tt>, but these are not absolute requirements.
         * While it is typically the case that:
         * <blockquote>
         * <pre>
         * x.clone().equals(x)</pre></blockquote>
         * will be <tt>true</tt>, this is not an absolute requirement.
         * <p/>
         * By convention, the returned object should be obtained by calling
         * <tt>super.clone</tt>.  If a class and all of its superclasses (except
         * <tt>Object</tt>) obey this convention, it will be the case that
         * <tt>x.clone().getClass() == x.getClass()</tt>.
         * <p/>
         * By convention, the object returned by this method should be independent
         * of this object (which is being cloned).  To achieve this independence,
         * it may be necessary to modify one or more fields of the object returned
         * by <tt>super.clone</tt> before returning it.  Typically, this means
         * copying any mutable objects that comprise the internal "deep structure"
         * of the object being cloned and replacing the references to these
         * objects with references to the copies.  If a class contains only
         * primitive fields or references to immutable objects, then it is usually
         * the case that no fields in the object returned by <tt>super.clone</tt>
         * need to be modified.
         * <p/>
         * The method <tt>clone</tt> for class <tt>Object</tt> performs a
         * specific cloning operation. First, if the class of this object does
         * not implement the interface <tt>Cloneable</tt>, then a
         * <tt>CloneNotSupportedException</tt> is thrown. Note that all arrays
         * are considered to implement the interface <tt>Cloneable</tt>.
         * Otherwise, this method creates a new instance of the class of this
         * object and initializes all its fields with exactly the contents of
         * the corresponding fields of this object, as if by assignment; the
         * contents of the fields are not themselves cloned. Thus, this method
         * performs a "shallow copy" of this object, not a "deep copy" operation.
         * <p/>
         * The class <tt>Object</tt> does not itself implement the interface
         * <tt>Cloneable</tt>, so calling the <tt>clone</tt> method on an object
         * whose class is <tt>Object</tt> will result in throwing an
         * exception at run time.
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
            Driver clone = (Driver) super.clone();
            if (preRun != null) {
				clone.preRun = (DriverMethod) preRun.clone();
			}
            if (postRun != null) {
				clone.postRun = (DriverMethod) postRun.clone();
			}
            clone.mix[0] = (Mix) mix[0].clone();
            if (mix[1] != null) {
				clone.mix[1] = (Mix) mix[1].clone();
			}

            clone.percentiles = percentiles.clone();
            clone.pctString = pctString.clone();
            clone.pctSuffix = pctSuffix.clone();

            clone.initialDelay[0] = (Uniform) initialDelay[0].clone();
            if (initialDelay[1] != null) {
				clone.initialDelay[1] = (Uniform) initialDelay[1].clone();
			}

            // Copy operation references into a flat array.
            int totalOps = operations.length;
            clone.operations = new Operation[totalOps];
            System.arraycopy(clone.mix[0].operations, 0, clone.operations, 0,
                    mix[0].operations.length);
            System.arraycopy(clone.mix[1].operations, 0, clone.operations,
                    mix[0].operations.length, mix[1].operations.length);

            return clone;
        }
        
        /**
         * Obtains the global operation index based on the mix identifier and
         * operation identifier inside the mix.
         * 
         * @param mixId 0 for foreground and 1 for background mix
         * @param opId the id of the operation within the mix
         * @return The global index into the operation
         */
        public int getOperationIdx(int mixId, int opId) {
            int idx = 0;
            for (int i = 0; i < mixId; i++) {
				idx += mix[i].operations.length;
			}
            idx += opId;
            return idx;
        }
    }

    static class DriverMethod implements Serializable, Cloneable {

		private static final long serialVersionUID = 1L;

		String genericName;
		boolean perAgent;
        transient Method m;

        /**
         * Creates s shallow clone of this object.
         * @return a clone of this instance.
         * @see Cloneable
         */
        @Override
		public final Object clone() {
            Object clone = null;
            try {
                clone = super.clone();
            } catch (CloneNotSupportedException e) {
                Logger logger = Logger.getLogger(this.getClass().getName());
                logger.log(Level.SEVERE, "Unexpected exception!", e);
            }
            return clone;
        }
    }
}
