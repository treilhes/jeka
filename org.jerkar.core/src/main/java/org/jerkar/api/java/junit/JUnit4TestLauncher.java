package org.jerkar.api.java.junit;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

import org.jerkar.api.java.JkClassLoader;
import org.jerkar.api.java.JkClasspath;
import org.jerkar.api.java.JkJavaProcess;
import org.jerkar.api.java.junit.JkUnit.JunitReportDetail;
import org.jerkar.api.system.JkEvent;
import org.jerkar.api.system.JkLocator;

import org.jerkar.api.utils.JkUtilsIO;
import org.jerkar.api.utils.JkUtilsIterable;
import org.jerkar.api.utils.JkUtilsPath;

class JUnit4TestLauncher {

    @SuppressWarnings("rawtypes")
    public static JkTestSuiteResult launchInFork(JkJavaProcess javaProcess,
            boolean printEachTestOnConsole, JunitReportDetail reportDetail,
            Iterable<Class> classes, File reportDir) {
        final List<String> args = new LinkedList<>();
        final Path file = JkUtilsPath.createTempFile("testResult-", ".ser");
        args.add("\"" + file.toAbsolutePath() + "\"");
        args.add(Boolean.toString(printEachTestOnConsole));
        args.add(reportDetail.name());
        args.add("\"" + reportDir.getAbsolutePath() + "\"");
        for (final Class<?> clazz : classes) {
            args.add(clazz.getName());
        }
        final JkJavaProcess process;
        process = javaProcess.andClasspath(JkClasspath.of(JkLocator.jerkarJarPath()));
        process.runClassSync(JUnit4TestExecutor.class.getName(), args.toArray(new String[0]));
        return (JkTestSuiteResult) JkUtilsIO.deserialize(file);
    }


    /**
     * @param classes Non-empty <code>Iterable</code>.
     */
    public static JkTestSuiteResult launchInClassLoader(Iterable<Class> classes, boolean logRunningTest,
            JunitReportDetail reportDetail, File reportDir) {
        final JkClassLoader classloader = JkClassLoader.of(classes.iterator().next());
        final Class[] classArray = JkUtilsIterable.arrayOf(classes, Class.class);
        classloader.addEntry(JkLocator.jerkarJarPath());
        if (JkEvent.verbosity() == JkEvent.Verbosity.VERBOSE) {
            JkEvent.trace(JkTestSuiteResult.class, "Launching test using class loader : " + classloader.toString());
        }
        // initialise JkEvent for the launcher classloader
        JkEvent.initializeInClassLoader(classloader.classloader());
        return classloader.invokeStaticMethod(true, JUnit4TestExecutor.class.getName(),
                "launchInProcess", classArray, logRunningTest, reportDetail, reportDir, true);
    }

}
