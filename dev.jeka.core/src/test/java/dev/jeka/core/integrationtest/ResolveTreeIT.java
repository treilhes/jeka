package dev.jeka.core.integrationtest;

import dev.jeka.core.api.depmanagement.*;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by angibaudj on 19-06-17.
 */
public class ResolveTreeIT {

    @Test
    public void treeIsCorrect() {
        JkVersionedModule holder = JkVersionedModule.of("mygroup:myname:myversion");
        JkDependencySet deps = JkDependencySet.of()
                .and("org.springframework.boot:spring-boot-starter-web:1.5.3.RELEASE", JkScope.COMPILE_AND_RUNTIME)
                .and("org.springframework.boot:spring-boot-starter-test:1.5.+", JkScope.TEST)
                .and("com.github.briandilley.jsonrpc4j:jsonrpc4j:1.5.0", JkScope.COMPILE);
        JkDependencyResolver resolver = JkDependencyResolver.of()
                .addRepos(JkRepo.ofMavenCentral())
                .setModuleHolder(holder);
        JkDependencyNode tree = resolver.resolve(deps).getDependencyTree();

        System.out.println(tree.toStringTree());

        JkDependencyNode.JkModuleNodeInfo root = tree.getModuleInfo();
        assertTrue(root.getDeclaredScopes().isEmpty());
        assertEquals(holder.getModuleId(), tree.getModuleInfo().getModuleId());
        assertEquals(3, tree.getChildren().size());

        JkDependencyNode starterwebNode = tree.getChildren().get(0);
        assertEquals(JkModuleId.of("org.springframework.boot:spring-boot-starter-web"), starterwebNode.getModuleInfo().getModuleId());
        assertEquals(2, starterwebNode.getModuleInfo().getDeclaredScopes().size());
        assertTrue(starterwebNode.getModuleInfo().getDeclaredScopes().contains(JkScope.COMPILE));
        assertTrue(starterwebNode.getModuleInfo().getDeclaredScopes().contains(JkScope.RUNTIME));

        JkDependencyNode starterNode = starterwebNode.getChildren().get(0);
        assertEquals(2, starterNode.getModuleInfo().getDeclaredScopes().size());
        Set<JkScope> scopes = starterNode.getModuleInfo().getDeclaredScopes();
        assertTrue(scopes.contains(JkScope.COMPILE));
        assertTrue(scopes.contains(JkScope.RUNTIME));

        List<JkDependencyNode> snakeYamlNodes = starterNode.getChildren(JkModuleId.of("org.yaml:snakeyaml"));
        assertEquals(1, snakeYamlNodes.size());
        JkDependencyNode snakeYamlNode = snakeYamlNodes.get(0);
        assertEquals(1, snakeYamlNode.getModuleInfo().getDeclaredScopes().size());
        scopes = snakeYamlNode.getModuleInfo().getDeclaredScopes();
        assertTrue(scopes.contains(JkScope.RUNTIME));

        assertEquals(5, starterNode.getChildren().size());

        List<JkDependencyNode> springCoreNodes = starterNode.getChildren(JkModuleId.of("org.springframework:spring-core"));
        assertEquals(1, springCoreNodes.size());
        JkDependencyNode springCoreNode = springCoreNodes.get(0);
        List<JkDependencyNode> commonLoggingNodes = springCoreNode.getChildren(JkModuleId.of("commons-logging:commons-logging"));
        assertEquals(1, commonLoggingNodes.size());
    }

    @Test
    public void treeDistinctDynamicAndResolvedVersion() {
        JkVersionedModule holder = JkVersionedModule.of("mygroup:myname:myversion");
        JkModuleId moduleId = JkModuleId.of("org.springframework.boot:spring-boot-starter-web");
        JkDependencySet deps = JkDependencySet.of().and(moduleId, "1.4.+", JkScope.TEST);
        JkDependencyResolver resolver = JkDependencyResolver.of()
                .addRepos(JkRepo.ofMavenCentral())
                .setModuleHolder(holder);
        JkDependencyNode tree = resolver.resolve(deps, JkScope.TEST).assertNoError().getDependencyTree();
        System.out.println(tree.toStrings());
        JkDependencyNode.JkModuleNodeInfo moduleNodeInfo = tree.getFirst(moduleId).getModuleInfo();
        assertTrue(moduleNodeInfo.getDeclaredVersion().getValue().equals("1.4.+"));
        String resolvedVersionName = moduleNodeInfo.getResolvedVersion().getValue();
        assertEquals("1.4.7.RELEASE", resolvedVersionName);
    }

    @Test
    public void treeHandleMultipleVersionWithLastestVersionWinConflictManager() {
        JkModuleId starterWebModule = JkModuleId.of("org.springframework.boot:spring-boot-starter-web");
        JkModuleId springCoreModule = JkModuleId.of("org.springframework:spring-core");
        String directCoreVersion = "4.3.6.RELEASE";
        JkDependencySet deps = JkDependencySet.of()
                .and(starterWebModule, "1.5.10.RELEASE", JkScope.COMPILE)
                .and(springCoreModule, directCoreVersion, JkScope.COMPILE);  // force a projectVersion lower than the transitive above
        JkDependencyResolver resolver = JkDependencyResolver.of()
                .addRepos(JkRepo.ofMavenCentral());
        JkResolveResult resolveResult = resolver.resolve(deps, JkScope.COMPILE);
        JkDependencyNode tree = resolveResult.getDependencyTree();

        JkDependencyNode bootNode = tree.getChildren().get(0);
        JkDependencyNode.JkModuleNodeInfo springCoreTransitiveModuleNodeInfo = bootNode.getFirst(springCoreModule).getModuleInfo();
        assertEquals("4.3.14.RELEASE", springCoreTransitiveModuleNodeInfo.getDeclaredVersion().getValue());
        assertEquals(directCoreVersion, springCoreTransitiveModuleNodeInfo.getResolvedVersion().getValue());  // cause evicted

        // As the spring-core projectVersion is declared as direct dependency and the declared projectVersion is exact (not dynamic)
        // then the resolved projectVersion should the one declared.
        JkDependencyNode.JkModuleNodeInfo springCoreDirectModuleNodeInfo = tree.getChildren().get(1).getModuleInfo();
        assertEquals(directCoreVersion, springCoreDirectModuleNodeInfo.getDeclaredVersion().getValue());
        assertEquals(directCoreVersion, springCoreDirectModuleNodeInfo.getResolvedVersion().getValue());
    }

    @Test
    public void triplePlayAnd() {
        JkDependencySet deps = JkDependencySet.of()
                .and("com.googlecode.playn:playn-core:1.4")
                .and("com.threerings:tripleplay:1.4")
                .withDefaultScopes(JkScope.COMPILE_AND_RUNTIME);
        JkDependencyResolver resolver = JkDependencyResolver.of()
                .addRepos(JkRepo.ofMavenCentral());
        JkResolveResult resolveResult = resolver.resolve(deps, JkScope.RUNTIME);
        JkDependencyNode tree = resolveResult.getDependencyTree();
        System.out.println(tree.toStringTree());
        System.out.println(resolveResult.getFiles());
    }

    @Test
    public void versionProvider() {
        JkDependencySet deps = JkDependencySet.of()
                .and("com.google.guava:guava")
                .withVersionProvider(JkVersionProvider.of("com.google.guava:guava", "22.0"))
                .withDefaultScopes(JkScope.COMPILE_AND_RUNTIME);
        JkDependencyResolver resolver = JkDependencyResolver.of()
                .addRepos(JkRepo.ofMavenCentral());
        JkResolveResult resolveResult = resolver.resolve(deps, JkScope.RUNTIME);
        JkDependencyNode tree = resolveResult.getDependencyTree();
        JkDependencyNode.JkModuleNodeInfo moduleNodeInfo = tree.getChildren().get(0).getModuleInfo();
        assertEquals("22.0", moduleNodeInfo.getDeclaredVersion().getValue());
    }

}
