package dev.jeka.core.samples;

import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkScope;
import dev.jeka.core.api.tooling.JkGitWrapper;
import dev.jeka.core.tool.JkCommandSet;
import dev.jeka.core.tool.JkDoc;
import dev.jeka.core.tool.JkInit;
import dev.jeka.core.tool.builtins.jacoco.JkPluginJacoco;
import dev.jeka.core.tool.builtins.java.JkPluginJava;
import dev.jeka.core.tool.builtins.sonar.JkPluginSonar;
import dev.jeka.core.tool.builtins.sonar.JkSonar;

import static dev.jeka.core.api.depmanagement.JkPopularModules.GUAVA;
import static dev.jeka.core.api.depmanagement.JkPopularModules.JUNIT;

/**
 * This build illustrate how to use SonarQube Plugin. <p>
 *
 * Jacoco plugin takes information from the {@link dev.jeka.core.api.java.project.JkJavaProject} instance of
 * Java plugin in order to send a complete request to a SonarQube server<p>.
 *
 * If your build does not relies upon Java plugin you can still easily use directly the {@link JkSonar} class
 * within the build class.<p>
 *
 * Note that SonarQube plugin combines quite well with the Jacoco one. It takes results generated by these one to
 * send it to the server.
 *
 */
public class SonarPluginBuild extends JkCommandSet {

    JkPluginJava java = getPlugin(JkPluginJava.class);

    JkPluginSonar sonar = getPlugin(JkPluginSonar.class);

    JkPluginJacoco jacoco = getPlugin(JkPluginJacoco.class);

    public boolean runSonar; // a flag to run sonar or not

    @JkDoc("Sonar server environment")
    public SonarEnv sonarEnv = SonarEnv.DEV;

    public SonarPluginBuild() {
        this.getPlugin(JkPluginSonar.class)
            .setProp(JkSonar.BRANCH, "myBranch");
    }
    
    @Override
    protected void setup() {
        sonar
            .setProp(JkSonar.HOST_URL, sonarEnv.url)
            .setProp(JkSonar.BRANCH, "myBranch");

        java.getProject()
            .getProduction()
                .getDependencyManagement()
                    .addDependencies(JkDependencySet.of()
                        .and(GUAVA, "18.0")
                        .and(JUNIT, "4.13", JkScope.TEST)).__.__
            .getPublication()
                .setModuleId("org.jerkar:samples")
                .setVersionSupplier(JkGitWrapper.of(getBaseDir())::getJkVersionFromTags);
    }

    public void cleanPackSonar() {
        clean(); java.pack();
        if (runSonar) sonar.run();
    }

    enum SonarEnv {
        DEV("https://localhost:81"),
        QA("https://qa.myhost:81"),
        PROD("https://prod.myhost:80");

        public final String url;

        SonarEnv(String url) {
            this.url = url;
        }
    }

    public static void main(String[] args) {
        JkInit.instanceOf(SonarPluginBuild.class, args).cleanPackSonar();
    }

}
