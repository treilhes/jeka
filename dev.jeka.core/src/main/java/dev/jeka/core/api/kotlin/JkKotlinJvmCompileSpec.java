package dev.jeka.core.api.kotlin;

import dev.jeka.core.api.file.JkPathSequence;
import dev.jeka.core.api.file.JkPathTree;
import dev.jeka.core.api.file.JkPathTreeSet;
import dev.jeka.core.api.java.JkJavaCompiler;
import dev.jeka.core.api.java.JkJavaVersion;
import dev.jeka.core.api.utils.JkUtilsIterable;
import dev.jeka.core.api.utils.JkUtilsPath;
import dev.jeka.core.api.utils.JkUtilsString;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Stands for a compilation settings as source and target version, encoding, annotation processing
 * or any option supported by the compileRunner.
 */
public final class JkKotlinJvmCompileSpec {

    public static final String SOURCE_OPTS = "-source";

    public static final String TARGET_OPTS = "-target";

    public static final String PROCESSOR_OPTS = "-processor";

    public static  final String ENCODING_OPTS = "-encoding";

    private static final String CLASSPATH_OPTS = "-cp";

    private static final String OUTPUT_DIR_OPTS = "-d";

    private final List<String> options = new LinkedList<>();

    private final List<Path> sourceFiles = new LinkedList<>();

    private JkKotlinJvmCompileSpec() {
    }

    public static JkKotlinJvmCompileSpec of() {
        return new JkKotlinJvmCompileSpec();
    }

    public JkKotlinJvmCompileSpec copy() {
        JkKotlinJvmCompileSpec result = new JkKotlinJvmCompileSpec();
        result.options.addAll(options);
        result.sourceFiles.addAll(sourceFiles);
        return result;
    }

    /**
     * Returns the specifications as a list of string directly usable in the {@link JkJavaCompiler} except
     * sourcepath
     */
    public List<String> getOptions() {
        return this.options;
    }

    // ------------ Output dir ------------------------

    public Path getOutputDir() {
        final String path = findValueAfter( OUTPUT_DIR_OPTS);
        return path == null ? null : Paths.get(path);
    }

    public JkKotlinJvmCompileSpec setOutputDir(Path outputDir) {
        return addOptions(OUTPUT_DIR_OPTS, outputDir.toString());
    }

    // ------- Java version & encoding ----------------


    public JkJavaVersion getTargetVersion() {
        final String rawResult = getNextValue(TARGET_OPTS);
        if (rawResult == null) {
            return null;
        }
        return JkJavaVersion.of(rawResult);
    }

    /**
     * Sets the target Java version for compiled classes. Normally it is the same as the
     * platform compileRunner shipped with but you can set it explicitly. Also, when set explicitly
     * {@link JkJavaCompiler} can choose to use the appropriate compileRunner to compile to the
     * specified target.
     */
    public JkKotlinJvmCompileSpec setTargetVersion(JkJavaVersion version) {
        if (version == null) {
            return this;
        }
        return setOption(TARGET_OPTS, version.get());
    }

    public String getEncoding() {
        return getNextValue(ENCODING_OPTS);
    }

    /**
     * Creates a copy of this {@link JkKotlinJvmCompileSpec} but using the
     * specified source encoding (e.g. UTF-8). If <code>null</code> is specified,
     * then default plateform encoding will be used.
     */
    public JkKotlinJvmCompileSpec setEncoding(String encoding) {
        if (encoding == null) {
            return this;
        }
        return setOption(ENCODING_OPTS, encoding);
    }

    // ----------------- source files -----------------------------------------

    /**
     * Adds specified source files to the set of java sources to compile.
     *
     **/
    public JkKotlinJvmCompileSpec addSources(Iterable<Path> paths) {
        List<Path> files = JkUtilsPath.disambiguate(paths);
        for (final Path file : files) {
            if (Files.isDirectory(file)) {
                this.sourceFiles.add(file);
            } else if (file.getFileName().toString().toLowerCase().endsWith(".kt")) {
                this.sourceFiles.add(file);
            }
        }
        return this;
    }

    public JkKotlinJvmCompileSpec addSources(JkPathTree tree) {
        if (!tree.isDefineMatcher()) {
            return addSources(tree.getRoot());
        }
        return addSources(tree.getFiles());
    }

    public JkKotlinJvmCompileSpec addSources(JkPathTreeSet treeSet) {
        treeSet.toList().forEach(this::addSources);
        return this;
    }

    /**
     * @see #addSources(Iterable)
     */
    public JkKotlinJvmCompileSpec addSources(Path path1, Path path2, Path... files) {
        return addSources(JkUtilsIterable.listOf2orMore(path1, path2, files));
    }

    /**
     * Returns all source files to be compiled.
     */
    public List<Path> getSourceFiles() {
        return Collections.unmodifiableList(this.sourceFiles);
    }

    // ------------------ classpath --------------------------------

    /**
     * Creates a copy of this {@link JkJavaCompiler} but with the specified
     * classpath.
     */
    public JkKotlinJvmCompileSpec setClasspath(Iterable<Path> files) {
        final String classpath = JkPathSequence.of(files).toString();
        return this.setOption(CLASSPATH_OPTS, classpath);
    }


    // ------------------ generic options -------------------------

    /**
     * Adds specified options to pass to the compileRunner.
     * Options are option you pass in javac command line as
     * -deprecation, -nowarn, ... For example, if you want something equivalent
     * to <code>javac -deprecation -cp path1 path2</code>, you should pass "-deprecation",
     * "-cp", "path1", "path2" parameters.
     */
    public JkKotlinJvmCompileSpec addOptions(String... options) {
        return this.addOptions(Arrays.asList(options));
    }

    /**
     * See {@link #addOptions(String...)}
     */
    public JkKotlinJvmCompileSpec addOptions(Iterable<String> options) {
        options.forEach(option -> this.options.add(option));
        return this;
    }

    /**
     * Some options of a compileRunner are set in a couple of name/value (version, classpath, .....).
     * So if you want to explicitly set such an option it is desirable to remove current value
     * instead of adding it at the queue of options. This method does this for you.
     */
    public JkKotlinJvmCompileSpec setOption(String optionName, String optionValue) {
        addOrReplace(optionName, optionValue);
        return this;
    }

    // --------------------------- Annotation processing -----------------------------------

    /**
     * Sets specified annotation classes instead of using the ones discovered by default Java 6 mechanism.
     */
    public JkKotlinJvmCompileSpec setAnnotationProcessors(String... annotationProcessorClassNames) {
        return setOption(PROCESSOR_OPTS, JkUtilsString.join(annotationProcessorClassNames, ","));
    }

    /**
     * Disable annotation processing.
     */
    public JkKotlinJvmCompileSpec disableAnnotationProcessing() {
        return addOptions("-proc:none");
    }

    /**
     * Only process annotation.
     */
    public JkKotlinJvmCompileSpec setAnnotationProcessingOnly() {
        return addOptions("-proc:only");
    }


    // ----- options as key/values

    public String getNextValue(String optionName) {
        return findValueAfter(optionName);
    }

    private String findValueAfter(String optionName) {
        final Iterator<String> it = options.iterator();
        while (it.hasNext()) {
            final String optionItem = it.next();
            if (optionItem.equals(optionName) && it.hasNext()) {
                return it.next();
            }
        }
        return null;
    }

    private void addOrReplace(String optionName, String value) {
        int index = options.indexOf(optionName);
        while ( index >= 0) {
            options.remove(index);
            if (index < options.size()) {
                options.remove(index);
            }
            index = options.indexOf(optionName);

        }
        options.add(optionName);
        options.add(value);
    }

}
