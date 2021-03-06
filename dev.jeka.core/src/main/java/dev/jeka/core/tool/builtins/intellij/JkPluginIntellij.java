package dev.jeka.core.tool.builtins.intellij;

import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.java.project.JkJavaIdeSupport;
import dev.jeka.core.api.system.JkLocator;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.tooling.intellij.JkImlGenerator;
import dev.jeka.core.api.utils.JkUtilsPath;
import dev.jeka.core.api.utils.JkUtilsString;
import dev.jeka.core.tool.*;
import dev.jeka.core.tool.builtins.eclipse.JkPluginEclipse;
import dev.jeka.core.tool.builtins.java.JkPluginJava;

import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@JkDoc("Generation of Idea Intellij metadata files (*.iml and modules.xml).")
public final class JkPluginIntellij extends JkPlugin {

    @JkDoc("If true, dependency paths will be expressed relatively to $JEKA_REPO$ and $JEKA_HOME$ path variable instead of absolute paths.")
    public boolean useVarPath = true;

    @JkDoc("By default, generated iml files specify a JDK inherited from the project setup. " +
            "Set this option to 'true' for  forcing the JDK version to the one defined in JkJavaProject.")
    public boolean forceJdkVersion = false;

    @JkDoc("If true, the iml generation fails when a dependency can not be resolved. If false, it will be ignored " +
            "(only a warning will be notified).")
    public boolean failOnDepsResolutionError = true;

    @JkDoc("If true, dependency to Jeka jar will be excluded (assuming it will be got from a module dependencies")
    public boolean imlSkipJeka;

    @JkDoc("Comma separated modules to be added as dependencies for jeka classpath")
    public String imlJekaExtraModules;

    @JkDoc("Override JEKA_HOME environment variable")
    public Path jekaHome;

    protected JkPluginIntellij(JkClass run) {
        super(run);
    }

    /** Generates Idea [my-module].iml file */
    @JkDoc("Generates Idea [my-module].iml file.")
    public void iml() {
        final JkImlGenerator generator;
        JkClass jkClass = getJkClass();
        JkJavaIdeSupport projectIde = JkPluginEclipse.getProjectIde(jkClass);
        if (projectIde != null) {
            generator = JkImlGenerator.of(projectIde);
        } else {
            generator = JkImlGenerator.of(JkJavaIdeSupport.of(jkClass.getBaseDir()));
        }
        generator.setFailOnDepsResolutionError(failOnDepsResolutionError);
        generator.setUseVarPath(useVarPath);
        generator.setExplicitJekaHome(jekaHome);
        JkDependencySet defDependencies = jkClass.getDefDependencies();
        if (imlSkipJeka) {
            defDependencies = defDependencies.minusFiles(
                    path -> path.getFileName().equals(JkLocator.getJekaJarPath().getFileName()));
        }
        generator.setDefDependencies(defDependencies);
        generator.setDefDependencyResolver(jkClass.getDefDependencyResolver());
        List<String> jkClassModuleDeps = new LinkedList<>();
        if (!JkUtilsString.isBlank(this.imlJekaExtraModules)) {
            for (String module : JkUtilsString.splitTrimmed(this.imlJekaExtraModules, ",")) {
                jkClassModuleDeps.add(module);
            }
        }
        jkClass.getImportedJkClasses().getImportedJkClassRoots().stream()
                .map(path -> path.getFileName().toString())
                .forEach(jkClassModuleDeps::add);
        generator.setExtraJekaModules(jkClassModuleDeps);
        Path basePath = jkClass.getBaseDir();
        if (jkClass.getPlugins().hasLoaded(JkPluginJava.class)) {
            jkClass.getPlugins().get(JkPluginJava.class);
            generator.setForceJdkVersion(forceJdkVersion);
        }
        final String xml = generator.generate();
        final Path imlFile = findIml(basePath);
        JkUtilsPath.deleteIfExists(imlFile);
        JkUtilsPath.createDirectories(imlFile.getParent());
        JkUtilsPath.write(imlFile, xml.getBytes(Charset.forName("UTF-8")));
        JkLog.info("Iml file generated at " + imlFile);
    }

    private static Path findIml(Path dir) {
        String dirNameCandidate = dir.getFileName().toString() + ".iml";
        Path dirNameCandidatePath = dir.resolve(dirNameCandidate);
        if (Files.exists(dirNameCandidatePath)) {
            return dirNameCandidatePath;
        }
        Path ideaDir = dir.resolve(".idea");
        Path ideaDirNameCandidatePath = ideaDir.resolve(dirNameCandidate);
        if (Files.exists(ideaDirNameCandidatePath)) {
            return ideaDirNameCandidatePath;
        }
        Path firstInDir = JkUtilsPath.listDirectChildren(dir).stream()
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".iml"))
                .findFirst().orElse(null);
        if (firstInDir != null) {
            return firstInDir;
        }
        if (Files.exists(ideaDir) && Files.isDirectory(ideaDir)) {
            Path firstInIdea = JkUtilsPath.listDirectChildren(dir.resolve(".idea")).stream()
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".iml"))
                    .findFirst().orElse(null);
            if (firstInIdea != null) {
                return firstInIdea;
            }
        }
        return dirNameCandidatePath;
    }

    /** Generate modules.xml files */
    @JkDoc("Generates ./idea/modules.xml file.")
    public void modulesXml() {
        final Path current = getJkClass().getBaseTree().getRoot();
        final Iterable<Path> imls = getJkClass().getBaseTree().andMatching(true,"**.iml").getFiles();
        final ModulesXmlGenerator modulesXmlGenerator = new ModulesXmlGenerator(current, imls);
        modulesXmlGenerator.generate();
        JkLog.info("File generated at : " + modulesXmlGenerator.outputFile());
    }

    @JkDoc("Generates iml files on this folder and its descendant recursively.")
    public void allIml() {
        final Iterable<Path> folders = getJkClass().getBaseTree()
                .andMatching(true, "**/" + JkConstants.DEF_DIR, JkConstants.DEF_DIR)
                .andMatching(false, "**/" + JkConstants.OUTPUT_PATH + "/**")
                .stream().collect(Collectors.toList());
        for (final Path folder : folders) {
            final Path projectFolder = folder.getParent().getParent();
            JkLog.startTask("Generate iml file on " + projectFolder);
            try {
                Main.exec(projectFolder, "intellij#iml");
            } catch (Exception e) {
                JkLog.warn("Generating Iml failed : Try to generate it using -CC=JkClass option. Failure cause : ");
                JkLog.warn(e.getMessage());
                PrintWriter printWriter = new PrintWriter(JkLog.getErrorStream());
                e.printStackTrace(printWriter);
                printWriter.flush();
                try {
                    Main.exec(projectFolder, "intellij#iml", "-CC=JkClass");
                } catch (Exception e1) {
                    JkLog.warn("Generating Iml file failed;");
                }
            }
            JkLog.endTask();
        }
    }

    @JkDoc("Shorthand for intellij#allIml + intellij#modulesXml.")
    public void all() {
        allIml();
        modulesXml();
    }

}