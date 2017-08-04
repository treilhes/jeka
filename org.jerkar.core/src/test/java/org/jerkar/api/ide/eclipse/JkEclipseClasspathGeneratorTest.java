package org.jerkar.api.ide.eclipse;

import org.jerkar.api.depmanagement.*;
import org.jerkar.api.java.build.JkJavaProject;
import org.jerkar.api.java.build.JkJavaProjectDepResolver;
import org.jerkar.api.java.build.JkJavaProjectStructure;
import org.jerkar.api.utils.JkUtilsFile;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;

/**
 * Created by angibaudj on 03-08-17.
 */
public class JkEclipseClasspathGeneratorTest {

    @Test
    @Ignore
    public void generate() throws Exception {
        File top = dir("sample-multi-scriptless.zip");

        File base = new File(top, "base");
        JkJavaProject baseProject = JkJavaProject.of(base);
        JkJavaProjectStructure structure = baseProject.structure();
        structure.relocaliseOutputDir("build/output");
        structure.setEditedSources("src");
        structure.setEditedResources("res");
        JkEclipseClasspathGenerator baseGenerator = new JkEclipseClasspathGenerator(structure);

        String result0 = baseGenerator.generate();
        System.out.println(result0);

        File core = new File(top, "core");
        JkJavaProject coreProject = JkJavaProject.of(core);
        structure = coreProject.structure();
        structure.relocaliseOutputDir("build/output");
        structure.setEditedSources("src");
        structure.setEditedResources("res").setEditedTestResources("res-test");
        structure.setTestSources("test");
        JkDependencies deps = JkDependencies.builder()
                .on(baseProject.asProjectDependency()).build();
        JkEclipseClasspathGenerator coreGenerator = new JkEclipseClasspathGenerator(structure);
        coreGenerator.setDependencyResolver(JkDependencyResolver.managed(JkRepos.mavenCentral(), deps));
        String result1 = coreGenerator.generate();
        System.out.println(result1);


        File desktop = new File(top, "desktop");
        JkJavaProjectStructure desktopStructure = structure.clone().relocaliseBaseDir(desktop);
        JkDependencies deps2 = JkDependencies.builder()
                .on(coreProject.asProjectDependency()).build();
        JkEclipseClasspathGenerator desktopGenerator = new JkEclipseClasspathGenerator(desktopStructure);
        desktopGenerator.setDependencyResolver(JkDependencyResolver.managed(JkRepos.mavenCentral(), deps2));
        String result2 = desktopGenerator.generate();
        System.out.println(result2);

        JkUtilsFile.deleteDir(top);
    }

    private static File dir(String zipName) {
        File dest = JkUtilsFile.createTempDir(JkEclipseClasspathGeneratorTest.class.getName());
        File zip = JkUtilsFile.toFile(JkEclipseClasspathGeneratorTest.class.getResource(zipName));
        JkUtilsFile.unzip(zip, dest);
        System.out.println("unzipped in " + dest.getAbsolutePath());
        return dest;
    }

}