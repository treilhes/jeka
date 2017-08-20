package org.jerkar.integrationtest;

import static org.jerkar.tool.builtins.javabuild.JkJavaBuild.COMPILE;
import static org.jerkar.tool.builtins.javabuild.JkJavaBuild.RUNTIME;
import static org.jerkar.tool.builtins.javabuild.JkJavaBuild.TEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.jerkar.api.depmanagement.JkDependencies;
import org.jerkar.api.depmanagement.JkDependencyNode;
import org.jerkar.api.depmanagement.JkDependencyResolver;
import org.jerkar.api.depmanagement.JkModuleId;
import org.jerkar.api.depmanagement.JkPopularModules;
import org.jerkar.api.depmanagement.JkRepos;
import org.jerkar.api.depmanagement.JkResolutionParameters;
import org.jerkar.api.depmanagement.JkResolveResult;
import org.jerkar.api.depmanagement.JkScope;
import org.jerkar.api.depmanagement.JkVersionedModule;
import org.jerkar.tool.builtins.javabuild.JkJavaBuild;
import org.junit.Test;

public class ResolverWithScopeMapperIT {

    private static final JkRepos REPOS = JkRepos.mavenCentral();

    private static final JkScope SCOPE_A = JkScope.of("scopeA");

    @Test
    public void resolveWithDefaultScopeMappingOnResolver() {

        final JkDependencies deps = JkDependencies.builder()
                .on("org.springframework.boot:spring-boot-starter-test:1.5.3.RELEASE").scope(TEST)
                .build();
        final JkDependencyResolver resolver = JkDependencyResolver.managed(JkRepos.mavenCentral(), deps)
                .withParams(JkResolutionParameters.defaultScopeMapping(JkJavaBuild.DEFAULT_SCOPE_MAPPING));
        final JkResolveResult resolveResult = resolver.resolve(TEST);
        final Set<JkModuleId> moduleIds = resolveResult.dependencyTree().flattenToVersionProvider().moduleIds();

        // Time to time JUNIT is not part of the result. So we don't know exactly how many module is expected
        assertTrue(moduleIds.size() == 24 || moduleIds.size() == 25);
    }

    /*
     * Spring-boot 1.5.3 has a dependency on spring-core which is higher than 4.0.0.
     * Nevertheless, if we declare spring-core with version 4.0.0 as direct dependency,
     * this one should be taken in account, and not the the higher one coming transitive dependency.
     */
    @Test
    public void explicitExactVersionWin() {
        //JkLog.verbose(true);
        final JkModuleId starterWebModule = JkModuleId.of("org.springframework.boot:spring-boot-starter-web");
        final JkModuleId springCoreModule = JkModuleId.of("org.springframework:spring-core");
        final String directCoreVersion = "4.0.0.RELEASE";
        final JkDependencies deps = JkDependencies.builder()

                .on(springCoreModule, directCoreVersion).scope(COMPILE)  // force a version lower than the transitive jump starterWeb module
                .on(starterWebModule, "1.5.3.RELEASE").scope(COMPILE)
                .build();
        final JkDependencyResolver resolver = JkDependencyResolver.managed(JkRepos.mavenCentral(), deps)
                .withParams(JkResolutionParameters.defaultScopeMapping(JkJavaBuild.DEFAULT_SCOPE_MAPPING));
        final JkResolveResult resolveResult = resolver.resolve(JkJavaBuild.COMPILE);
        assertEquals(directCoreVersion, resolveResult.versionOf(springCoreModule).name());
    }

    /*
     * Spring-boot 1.5.3 has a dependency on spring-core which is higher than 4.0.0.
     * Nevertheless, if we declare spring-core with version 4.0.0 as direct dependency,
     * this one should be taken in account, and not the the higher one coming transitive dependency.
     */
    @Test
    public void resolveWithSeveralScopes() {
        final JkDependencies deps = JkDependencies.builder()
                .on(JkPopularModules.GUAVA, "19.0").scope(JkJavaBuild.COMPILE)
                .on(JkPopularModules.JAVAX_SERVLET_API, "3.1.0").scope(JkJavaBuild.PROVIDED)
                .build();
        final JkDependencyResolver resolver = JkDependencyResolver.managed(JkRepos.mavenCentral(), deps)
                .withParams(JkResolutionParameters.defaultScopeMapping(JkJavaBuild.DEFAULT_SCOPE_MAPPING));
        final JkResolveResult resolveResult = resolver.resolve(JkJavaBuild.COMPILE, JkJavaBuild.PROVIDED);
        assertTrue(resolveResult.contains(JkPopularModules.JAVAX_SERVLET_API));
        assertTrue(resolveResult.contains(JkPopularModules.GUAVA));
        assertEquals(2, resolveResult.dependencyTree().flattenToVersionProvider().moduleIds().size());
    }

    @Test
    public void getRuntimeTransitiveWithRuntime() {
        final JkVersionedModule holder = JkVersionedModule.of("mygroup:myname", "myversion2");
        final JkDependencies deps = JkDependencies.builder()
                .on("org.springframework.boot:spring-boot-starter:1.5.3.RELEASE").scope(COMPILE, RUNTIME)
                .build();
        final JkDependencyResolver resolver = JkDependencyResolver.managed(JkRepos.mavenCentral(), deps)
                .withParams(JkResolutionParameters.defaultScopeMapping(JkJavaBuild.DEFAULT_SCOPE_MAPPING))
                .withModuleHolder(holder);
        final JkResolveResult resolveResult = resolver.resolve(JkJavaBuild.RUNTIME);
        final boolean snakeyamlHere = resolveResult.contains( JkModuleId.of("org.yaml:snakeyaml"));
        assertTrue(snakeyamlHere);
    }

    @Test
    public void dontGetRuntimeTransitiveWithCompile() {
        final JkVersionedModule holder = JkVersionedModule.of("mygroup:myname", "myversion");
        final JkDependencies deps = JkDependencies.builder()
                .on("org.springframework.boot:spring-boot-starter:1.5.3.RELEASE").scope(COMPILE, RUNTIME)
                .build();
        final JkDependencyResolver resolver = JkDependencyResolver.managed(JkRepos.mavenCentral(), deps)
                .withParams(JkResolutionParameters.defaultScopeMapping(JkJavaBuild.DEFAULT_SCOPE_MAPPING))
                .withModuleHolder(holder);
        final JkResolveResult resolveResult = resolver.resolve(JkJavaBuild.COMPILE);
        final boolean snakeyamlHere = resolveResult.contains( JkModuleId.of("org.yaml:snakeyaml"));
        assertFalse(snakeyamlHere);
    }

    @Test
    public void treeRootIsCorrectWhenAnonymous() {
        final JkDependencies deps = JkDependencies.builder()
                .on(JkPopularModules.GUAVA, "19.0").scope(JkJavaBuild.COMPILE)
                .build();
        final JkDependencyResolver resolver = JkDependencyResolver.managed(JkRepos.mavenCentral(), deps)
                .withParams(JkResolutionParameters.defaultScopeMapping(JkJavaBuild.DEFAULT_SCOPE_MAPPING));
        final JkDependencyNode tree = resolver.resolve().dependencyTree();
        assertTrue(tree.moduleInfo().declaredScopes().isEmpty());
    }

}
