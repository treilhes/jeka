package org.jerkar.samples;

import static org.jerkar.api.depmanagement.JkJavaDepScopes.TEST;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.jerkar.api.depmanagement.JkDependencySet;
import org.jerkar.api.java.JkJavaCompiler;
import org.jerkar.api.java.JkJavaVersion;
import org.jerkar.api.project.java.JkJavaProject;
import org.jerkar.tool.JkImport;
import org.jerkar.tool.JkInit;
import org.jerkar.tool.builtins.java.JkJavaProjectBuild;

/**
 * This build is equivalent to {@link MavenStyleBuild} but removing the needless
 * part cause we respect the convention project folder name =
 * groupName.projectName and the projectVersion number is taken from build.properties
 * (default behavior)
 *
 * @author Jerome Angibaud
 * @formatter:off
 */
@JkImport("org.eclipse.jdt.core.compiler:ecj:4.6.1")
public class AClassicBuild extends JkJavaProjectBuild {

    @Override
    protected void setupOptionDefaults() {
	    java().pack.checksums = "sha1";
	    java().pack.tests = true;
	    java().pack.javadoc = true;
    }
    
    @Override
    protected void configurePlugins() {
        JkJavaProject project = project()
                .setSourceVersion(JkJavaVersion.V7)
                .setDependencies(JkDependencySet.of()
                        .and("com.google.guava:guava:21.0")
                        .and("com.sun.jersey:jersey-server:1.19")
                        .and("junit:junit:4.11", TEST));
        project.maker().setCompiler(JkJavaCompiler.of(new EclipseCompiler()));
        project.maker().defineFatJarArtifact("fat");  // project will produce a fat jar as well.
    }
    
    public static void main(String[] args) throws Exception {
	    JkInit.instanceOf(AClassicBuild.class, args).doDefault();
    }

}
