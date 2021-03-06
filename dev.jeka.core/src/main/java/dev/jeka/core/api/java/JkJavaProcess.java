package dev.jeka.core.api.java;

import dev.jeka.core.api.file.JkPathSequence;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.system.JkProcess;
import dev.jeka.core.api.utils.*;
import dev.jeka.core.api.utils.JkUtilsIO.JkStreamGobbler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Offers fluent interface for launching Java processes.
 *
 * @author Jerome Angibaud
 */
public final class JkJavaProcess {

    private static final Path CURRENT_JAVA_DIR = Paths.get(System.getProperty("java.home")).resolve("bin");

    private final Map<String, String> systemProperties;

    private final Path javaDir;

    private final JkPathSequence classpath;

    private final List<AgentLibAndOption> agents;

    private final Collection<String> options;

    private final Path workingDir;

    private final boolean printCommand;

    private final Map<String, String> environment;

    private JkJavaProcess(Path javaDir, Map<String, String> systemProperties, JkPathSequence classpath,
            List<AgentLibAndOption> agents, Collection<String> options, Path workingDir,
            Map<String, String> environment, boolean printCommand) {
        super();
        this.javaDir = javaDir;
        this.systemProperties = systemProperties;
        this.classpath = classpath;
        this.agents = agents;
        this.options = options;
        this.workingDir = workingDir;
        this.environment = environment;
        this.printCommand = printCommand;
    }

    /**
     * Initializes a <code>JkJavaProcess</code> using the same JRE then the one
     * currently running.
     */
    public static JkJavaProcess of() {
        return ofJavaHome(CURRENT_JAVA_DIR);
    }

    /**
     * Initializes a <code>JkJavaProcess</code> using the Java executable
     * located in the specified directory.
     */
    @SuppressWarnings("unchecked")
    public static JkJavaProcess ofJavaHome(Path javaDir) {
        return new JkJavaProcess(javaDir, Collections.EMPTY_MAP, JkPathSequence.of(),
                Collections.EMPTY_LIST, Collections.EMPTY_LIST, null, Collections.EMPTY_MAP, true);
    }

    /**
     * Returns a {@link JkJavaProcess} identical to this one but augmented with the
     * specified agent library and option.
     */
    public JkJavaProcess andAgent(Path agentLib, String agentOption) {
        if (agentLib == null) {
            throw new IllegalArgumentException("agentLib can't be null.");
        }
        if (!Files.exists(agentLib)) {
            throw new IllegalArgumentException("agentLib " + agentLib + " not found.");
        }
        if (!Files.isRegularFile(agentLib)) {
            throw new IllegalArgumentException("agentLib " + agentLib + " is a directory, should be a file.");
        }
        final List<AgentLibAndOption> list = new ArrayList<>(
                this.agents);
        list.add(new AgentLibAndOption(agentLib.toAbsolutePath().toString(), agentOption));
        return new JkJavaProcess(this.javaDir, this.systemProperties, this.classpath, list,
                this.options, this.workingDir, this.environment, this.printCommand);
    }

    /**
     * Returns a {@link JkJavaProcess} identical to this one but augnmented with the
     * specified agent library.
     */
    public JkJavaProcess andAgent(Path agentLib) {
        return andAgent(agentLib, null);
    }

    /**
     * Returns a {@link JkJavaProcess} identical to this one but with the
     * specified java options.
     */
    public JkJavaProcess andOptions(Collection<String> options) {
        final List<String> list = new ArrayList<>(this.options);
        list.addAll(options);
        return new JkJavaProcess(this.javaDir, this.systemProperties, this.classpath, this.agents,
                list, this.workingDir, this.environment, this.printCommand);
    }

    /**
     * Same as {@link #andOptions(Collection)} but effective only if the specified condition
     * is <code>true</code>.
     */
    public JkJavaProcess andOptionsIf(boolean condition, String... options) {
        if (condition) {
            return andOptions(options);
        }
        return this;
    }

    /**
     * Same as {@link #andOptions(Collection)}.
     */
    public JkJavaProcess andOptions(String... options) {
        return this.andOptions(Arrays.asList(options));
    }

    /**
     * Takes the specified command line as is and add it to the process command
     * line. Example of command line is <i>-Xms2G -Xmx2G</i>.
     */
    public JkJavaProcess andCommandLine(String commandLine) {
        if (JkUtilsString.isBlank(commandLine)) {
            return this;
        }
        return this.andOptions(JkUtilsString.translateCommandline(commandLine));
    }

    /**
     * Returns a {@link JkJavaProcess} identical to this one but using the specified
     * working dir.
     */
    public JkJavaProcess withWorkingDir(Path workingDir) {
        return new JkJavaProcess(this.javaDir, this.systemProperties, this.classpath, this.agents,
                this.options, workingDir, this.environment, this.printCommand);
    }

    /**
     * Returns a {@link JkJavaProcess} identical to this one but using the specified
     * classpath.
     * @param paths As {@link Path} class implements { @link Iterable<Path> } the argument can be a single {@link Path}
     * instance, if so it will be interpreted as a list containing a single element which is this argument.
     *
     */
    public JkJavaProcess withClasspath(Iterable<Path> paths) {
        if (paths == null) {
            throw new IllegalArgumentException("Classpath can't be null.");
        }
        final JkPathSequence jkClasspath = JkPathSequence.of(JkUtilsPath.disambiguate(paths));
        return new JkJavaProcess(this.javaDir, this.systemProperties, jkClasspath, this.agents,
                this.options, this.workingDir, this.environment, this.printCommand);
    }

    /**
     * Returns a {@link JkJavaProcess} identical to this one specifying if the launch command should be printed
     * in the console
     */
    public JkJavaProcess withPrintCommand(boolean printCommand) {
        return new JkJavaProcess(this.javaDir, this.systemProperties, this.classpath, this.agents,
                this.options, this.workingDir, this.environment, printCommand);
    }

    /**
     * Returns a {@link JkJavaProcess} identical to this one but using the specified
     * classpath.
     */
    public JkJavaProcess withClasspath(Path path1, Path path2, Path... others) {
        return withClasspath(JkPathSequence.of(path1, path2, others));
    }

    /**
     * Returns a {@link JkJavaProcess} identical to this one but augmenting this
     * classpath with the specified one.
     */
    public JkJavaProcess andClasspath(Iterable<Path> classpath) {
        return withClasspath(this.classpath.and(JkUtilsPath.disambiguate(classpath)));
    }

    private ProcessBuilder processBuilder(List<String> command, Map<String, String> env) {
        final ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.environment().putAll(env);
        if (this.workingDir != null) {
            builder.directory(workingDir.toFile());
        }
        return builder;
    }

    private String getRunningJavaCommand() {
        return this.javaDir.toAbsolutePath()+ this.javaDir.getFileSystem().getSeparator() + "java";
    }

    public void runJarSync(Path jar, String... arguments) {
        runClassOrJarSync(null, jar, arguments);
    }

    /**
     * Runs the specified class and wait for termination. The class has to be on this classpath.
     */
    public void runClassSync(String mainClassName, String... arguments) {
        runClassOrJarSync(mainClassName, null, arguments);
    }

    /**
     * Returns a {@link JkProcess} ready to be run.
     */
    public JkProcess toProcess(String mainClassName, Path jar, String... arguments) {
        JkUtilsAssert.argument(jar != null || mainClassName != null,
                "main class name and jar can't be both null while launching a Java process, " +
                        "please set at least one of them.");
        final List<String> args = new LinkedList<>();
        final OptionAndEnv optionAndEnv = optionsAndEnv();
        args.add(getRunningJavaCommand());
        args.addAll(optionAndEnv.options);
        if (jar != null) {
            if (!Files.exists(jar)) {
                throw new IllegalStateException("Executable jar " + jar + " not found.");
            }
            args.add("-jar");
            args.add(jar.toString());
        }
        if (mainClassName != null) {
            args.add(mainClassName);
        }
        args.addAll(Arrays.asList(arguments));
        return JkProcess.of(getRunningJavaCommand(), args.toArray(new String[0]))
                .withLogCommand(printCommand);
    }

    private void runClassOrJarSync(String mainClassName, Path jar, String... arguments) {
        JkUtilsAssert.argument(jar != null || mainClassName != null,
                "main class name and jar can't be both null while launching a Java process, " +
                "please set at least one of them.");
        final List<String> command = new LinkedList<>();
        final OptionAndEnv optionAndEnv = optionsAndEnv();
        command.add(getRunningJavaCommand());
        command.addAll(optionAndEnv.options);
        String execPart = "";
        if (jar != null) {
            if (!Files.exists(jar)) {
                throw new IllegalStateException("Executable jar " + jar + " not found.");
            }
            command.add("-jar");
            command.add(jar.toString());
            execPart = execPart + jar.toString();
        }
        if (mainClassName != null) {
            command.add(mainClassName);
            execPart = execPart + " " + mainClassName;
        }
        command.addAll(Arrays.asList(arguments));
        if (printCommand) {
            JkLog.startTask("Start java program : " + execPart);
            JkLog.info(String.join("\n", command));
        }
        final int result;
        try {
            final Process process = processBuilder(command, optionAndEnv.env).start();
            final JkStreamGobbler outputStreamGobbler = JkUtilsIO.newStreamGobbler(
                    process.getInputStream(), JkLog.getOutputStream());
            final JkStreamGobbler errorStreamGobbler = JkUtilsIO.newStreamGobbler(
                    process.getErrorStream(), JkLog.getErrorStream());
            process.waitFor();
            outputStreamGobbler.join();
            errorStreamGobbler.join();
            result = process.exitValue();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        if (result != 0) {
            throw new IllegalStateException("Process terminated in error : exit value = " + result + ".");
        }
        if (printCommand) {
            JkLog.endTask();
        }
    }



    private OptionAndEnv optionsAndEnv() {
        final List<String> options = new LinkedList<>();
        final Map<String, String> env = new HashMap<>();
        if (classpath != null && !classpath.getEntries().isEmpty()) {
            final String classpathString = classpath.toString();
            if (JkUtilsSystem.IS_WINDOWS && classpathString.length() > 7500) {
                JkLog.warn("Classpath too long, classpath will be passed using CLASSPATH env variable.");
                env.put("CLASSPATH", classpathString);
            } else {
                options.add("-cp");
                options.add(classpath.toString());
            }
        }
        for (final AgentLibAndOption agentLibAndOption : agents) {
            final StringBuilder builder = new StringBuilder("-javaagent:")
                    .append(agentLibAndOption.lib);
            if (!JkUtilsString.isBlank(agentLibAndOption.options)) {
                builder.append("=").append(agentLibAndOption.options);
            }
            options.add(builder.toString());
        }
        for (final String key : this.systemProperties.keySet()) {
            final String value = this.systemProperties.get(key);
            options.add("-D" + key + "=" + value);
        }
        options.addAll(this.options);
        return new OptionAndEnv(options, env);
    }

    private static final class OptionAndEnv {

        public final List<String> options;
        public final Map<String, String> env;

        private OptionAndEnv(List<String> options, Map<String, String> env) {
            super();
            this.options = options;
            this.env = env;
        }

    }

    private static class AgentLibAndOption {

        public final String lib;

        public final String options;

        public AgentLibAndOption(String lib, String options) {
            super();
            this.lib = lib;
            this.options = options;
        }
    }

    /**
     * Returns the classpath of this {@link JkJavaProcess}.
     * @return
     */
    public JkPathSequence getClasspath() {
        return classpath;

    }

}
