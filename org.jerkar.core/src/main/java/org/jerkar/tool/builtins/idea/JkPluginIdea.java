package org.jerkar.tool.builtins.idea;

import org.jerkar.api.ide.idea.JkImlGenerator;
import org.jerkar.api.project.java.JkJavaProject;
import org.jerkar.api.system.JkLog;
import org.jerkar.api.utils.JkUtilsPath;
import org.jerkar.tool.*;
import org.jerkar.tool.builtins.java.JkJavaProjectBuild;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@JkDoc("Provides method to generate Idea Intellij metadata files.")
public final class JkPluginIdea extends JkPlugin2 {

    @JkDoc("If true, path to cache repository and to Jerkar install will be replaces by $JERKAR_REPO$ and $JERKAR_HOME$ path variable")
    boolean useVarPath = false;

    protected JkPluginIdea(JkBuild build) {
        super(build);
    }

    /** Generates Idea [my-module].iml file */
    @JkDoc("Generates Idea [my-module].iml file")
    public void generateIml() {

        final JkImlGenerator generator;
        if (build instanceof JkJavaProjectBuild) {
            final JkJavaProjectBuild projectBuild = (JkJavaProjectBuild) build;
            generator = new JkImlGenerator(projectBuild.java().project());
        } else {
            generator = new JkImlGenerator(build.baseDir());
        }
        final List<Path> depProjects = new LinkedList<>();
        for (final JkBuild depBuild : build.importedBuilds().directs()) {
            depProjects.add(depBuild.baseTree().root());
        }
        generator.setUseVarPath(useVarPath);
        generator.setBuildDependencies(build.buildDependencyResolver(), build.buildDependencies());

        generator.setImportedBuildProjects(depProjects);
        if (build instanceof JkJavaProjectBuild) {
            final JkJavaProjectBuild projectBuild = (JkJavaProjectBuild) build;
            JkJavaProject project = projectBuild.java().project();
            generator.setDependencies(project.maker().getDependencyResolver(), project.getDependencies());
            generator.setSourceJavaVersion(project.getSourceVersion());
            generator.setForceJdkVersion(true);
        }
        final String xml = generator.generate();
        final Path imlFile = build.baseDir().resolve(
                build.baseDir().getFileName().toString() + ".iml");
        JkUtilsPath.deleteFile(imlFile);
        JkUtilsPath.write(imlFile, xml.getBytes(Charset.forName("UTF-8")));
        JkLog.info(imlFile + " generated.");
    }

    /** Generate modules.xml files */
    @JkDoc("Generates ./idea/modules.xml file")
    public void generateModulesXml() {
        final Path current = build.baseTree().root();
        final Iterable<Path> imls = build.baseTree().accept("**.iml").files();
        final ModulesXmlGenerator modulesXmlGenerator = new ModulesXmlGenerator(current, imls);
        modulesXmlGenerator.generate();
    }

    @JkDoc("Generates iml files on this folder and its descendant recursively.")
    public void generateAllIml() {
        final Iterable<Path> folders = build.baseTree()
                .accept("**/" + JkConstants.BUILD_DEF_DIR, JkConstants.BUILD_DEF_DIR)
                .refuse("**/build/output/**")
                .stream().collect(Collectors.toList());
        for (final Path folder : folders) {
            final Path projectFolder = folder.getParent().getParent();
            JkLog.startln("Generating iml file on " + projectFolder);
            Main.exec(projectFolder, "idea#generateIml");
            JkLog.done();
        }
    }

    @JkDoc(("Shorthand for #generateAllIml + generateModulesXml"))
    public void generateAll() {
        generateAllIml();
        generateModulesXml();
    }


}