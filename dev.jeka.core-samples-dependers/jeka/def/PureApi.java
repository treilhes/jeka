import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkScope;
import dev.jeka.core.api.java.project.JkJavaProject;
import dev.jeka.core.api.system.JkLog;

public class PureApi {

    public static void main(String[] args) {
        JkLog.setConsumer(JkLog.Style.INDENT);  // activate console logging

        // A project with ala Maven layout (src/main/javaPlugin, src/test/javaPlugin, ...)
        JkJavaProject coreProject = JkJavaProject.of().simpleFacade()
                .setBaseDir("../dev.jeka.core-samples")
                .addDependencies(JkDependencySet.of()
                    .and("junit:junit:4.13", JkScope.TEST)).getProject();

        // A project depending on the first project + Guava
        JkJavaProject dependerProject = JkJavaProject.of().simpleFacade()
                .addDependencies(JkDependencySet.of()
                    .and("com.google.guava:guava:22.0")
                    .and(coreProject.toDependency()))
                .setPublishedModuleId("mygroup:depender")
                .setPublishedVersion("1.0-SNAPSHOT").getProject();

        dependerProject.getPublication().getArtifactProducer().makeAllArtifacts();
        dependerProject.getPublication().publish();
    }
}
