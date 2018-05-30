package org.jerkar.distrib.all;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import org.jerkar.CoreBuild;
import org.jerkar.api.depmanagement.JkArtifactFileId;
import org.jerkar.api.file.JkPathTree;
import org.jerkar.api.file.JkPathTreeSet;
import org.jerkar.api.java.JkJavadocMaker;
import org.jerkar.api.project.java.JkJavaProject;
import org.jerkar.api.project.java.JkJavaProjectMaker;
import org.jerkar.api.system.JkLog;
import org.jerkar.plugins.jacoco.PluginsJacocoBuild;
import org.jerkar.plugins.sonar.PluginsSonarBuild;
import org.jerkar.tool.JkBuild;
import org.jerkar.tool.JkDoc;
import org.jerkar.tool.JkImportBuild;
import org.jerkar.tool.JkInit;

class DistribAllBuild extends JkBuild {

    @JkImportBuild("../org.jerkar.plugins-sonar")
    PluginsSonarBuild pluginsSonar;

    @JkImportBuild("../org.jerkar.plugins-jacoco")
    PluginsJacocoBuild pluginsJacoco;

    public boolean testSamples = false;

    public boolean skipTests = false;

    public boolean javadoc = true;

    @Override
    protected void postConfigure() {
        pluginsJacoco.core.java().tests.skip = skipTests;
    }

    @JkDoc("Construct a distrib assuming all dependent sub projects are already built.")
    public void distrib() throws IOException {

        JkLog.startln("Creating distribution file");

        JkLog.info("Copy core distribution locally.");
        CoreBuild core = pluginsJacoco.core; // The core project is got by transitivity
                Path distDir = this.outputDir().resolve("dist");
        JkPathTree dist = JkPathTree.of(distDir).merge(core.distribFolder);

        JkLog.info("Add plugins to the distribution");
        JkPathTree ext = dist.goTo("libs/builtins")
                .copyIn(pluginsSonar.java().project().maker().mainArtifactPath())
                .copyIn(pluginsJacoco.java().project().maker().mainArtifactPath());
        JkPathTree sourceDir = dist.goTo("libs-sources");
        sourceDir.copyIn(pluginsSonar.java().project().maker().artifactPath(JkJavaProjectMaker.SOURCES_FILE_ID))
                .copyIn(pluginsJacoco.java().project().maker().artifactPath(JkJavaProjectMaker.SOURCES_FILE_ID));

        JkLog.info("Add plugins to the fat jar");
        Path fat = dist.get(core.java().project().maker().artifactPath(JkArtifactFileId.of("all", "jar"))
                .getFileName().toString());
        Files.copy(core.java().project().maker().mainArtifactPath(), fat, StandardCopyOption.REPLACE_EXISTING);
        ext.accept("**.jar").stream().map(path -> JkPathTree.ofZip(path)).forEach(tree -> tree.zipTo(fat));

        JkLog.info("Create a fat source jar");
        Path fatSource = sourceDir.get("org.jerkar.core-all-sources.jar");
        sourceDir.accept("**.jar", "**.zip").refuse(fatSource.getFileName().toString()).stream()
                .map(path -> JkPathTree.ofZip(path)).forEach(tree -> tree.zipTo(fatSource));

        if (javadoc) {
            JkLog.info("Create javadoc");
            JkPathTreeSet sources = this.pluginsJacoco.core.java().project().getSourceLayout().sources()
                    .and(this.pluginsJacoco.java().project().getSourceLayout().sources())
                    .and(this.pluginsSonar.java().project().getSourceLayout().sources());
            Path javadocAllDir = this.outputDir().resolve("javadoc-all");
            Path javadocAllFile = dist.root().resolve("libs-javadoc/org.jerkar.core-fat-javadoc.jar");
            JkJavadocMaker.of(sources, javadocAllDir, javadocAllFile).process();
        }

        JkLog.info("Pack all");
        dist.zipTo(outputDir().resolve("jerkar-distrib.zip"));

        JkLog.done();
    }

    @JkDoc("End to end method to construct a distrib.")
    public void doDefault()  {
        clean();
        this.importedBuilds().all().forEach(JkBuild::clean);
        pluginsJacoco.core.java().project().maker().makeArtifactFile(CoreBuild.DISTRIB_FILE_ID);
        pluginsJacoco.java().project().maker().makeAllArtifactFiles();
        pluginsSonar.java().project().maker().makeAllArtifactFiles();
        try {
            distrib();
            if (testSamples) {
                testSamples();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void testSamples() throws IOException {
        JkLog.startHeaded("Testing Samples");
        SampleTester sampleTester = new SampleTester(this.baseTree());
        sampleTester.restoreEclipseClasspathFile = true;
        sampleTester.doTest();
        JkLog.done();
    }

    public static void main(String[] args) throws Exception {
        JkInit.instanceOf(DistribAllBuild.class, args).doDefault();
    }


}
