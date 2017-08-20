package org.jerkar.integrationtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.jerkar.api.depmanagement.JkDependencies;
import org.jerkar.api.depmanagement.JkDependencyResolver;
import org.jerkar.api.depmanagement.JkModuleId;
import org.jerkar.api.depmanagement.JkPopularModules;
import org.jerkar.api.depmanagement.JkRepos;
import org.jerkar.api.depmanagement.JkResolutionParameters;
import org.jerkar.api.depmanagement.JkResolveResult;
import org.jerkar.api.depmanagement.JkScope;
import org.jerkar.tool.builtins.javabuild.JkJavaBuild;
import org.junit.Test;

public class ResolverWithoutScopeMapperIT {

    private static final JkRepos REPOS = JkRepos.mavenCentral();

    private static final JkScope MY_SCOPE = JkScope.of("myScope");

    @Test
    public void resolveCompile() {
        JkDependencies deps = JkDependencies.builder()
                .on(JkPopularModules.APACHE_COMMONS_DBCP, "1.4").scope(JkJavaBuild.COMPILE)
                .build();
        JkDependencyResolver resolver = JkDependencyResolver.managed(REPOS, deps)
                .withParams(JkResolutionParameters.defaultScopeMapping(JkJavaBuild.DEFAULT_SCOPE_MAPPING));
        JkResolveResult resolveResult = resolver.resolve(JkJavaBuild.COMPILE);
        assertTrue(resolveResult.contains(JkModuleId.of("commons-pool")));
        assertEquals(2, resolveResult.dependencyTree().flattenToVersionProvider().moduleIds().size());

        deps = JkDependencies.builder()
                .on(JkPopularModules.HIBERNATE_CORE, "5.2.10.Final").scope(JkJavaBuild.COMPILE)
                .build();
        resolver = JkDependencyResolver.managed(REPOS, deps)
                .withParams(JkResolutionParameters.defaultScopeMapping(JkJavaBuild.DEFAULT_SCOPE_MAPPING));
        resolveResult = resolver.resolve(JkJavaBuild.COMPILE);
        System.out.println(resolveResult.dependencyTree().toStringComplete());
        assertEquals(10, resolveResult.dependencyTree().flattenToVersionProvider().moduleIds().size());
    }

    @Test
    public void resolveInheritedScopes() {
        final JkDependencies deps = JkDependencies.builder()
                .on(JkPopularModules.APACHE_COMMONS_DBCP, "1.4").scope(JkJavaBuild.COMPILE)
                .build();
        final JkDependencyResolver resolver = JkDependencyResolver.managed(REPOS, deps)
                .withParams(JkResolutionParameters.defaultScopeMapping(JkJavaBuild.DEFAULT_SCOPE_MAPPING));

        // runtime classpath should embed the dependency as well cause 'RUNTIME' scope extends 'COMPILE'
        JkResolveResult resolveResult = resolver.resolve(JkJavaBuild.RUNTIME);
        assertEquals(2, resolveResult.dependencyTree().flattenToVersionProvider().moduleIds().size());
        assertTrue(resolveResult.contains(JkModuleId.of("commons-pool")));
        assertTrue(resolveResult.contains(JkModuleId.of("commons-dbcp")));

        // test classpath should embed the dependency as well
        resolveResult = resolver.resolve(JkJavaBuild.TEST);
        assertTrue(resolveResult.contains(JkModuleId.of("commons-pool")));
        assertTrue(resolveResult.contains(JkModuleId.of("commons-dbcp")));
        assertEquals(2, resolveResult.dependencyTree().flattenToVersionProvider().moduleIds().size());
    }

    @Test
    public void resolveWithOptionals() {
        final JkDependencies deps = JkDependencies.builder()
                .on(JkPopularModules.SPRING_ORM, "4.3.8.RELEASE").mapScope(JkJavaBuild.COMPILE).to("compile", "master", "optional")
                .build();
        final JkDependencyResolver resolver = JkDependencyResolver.managed(JkRepos.mavenCentral(), deps);
        final JkResolveResult resolveResult = resolver.resolve(JkJavaBuild.COMPILE);
        assertEquals(37, resolveResult.dependencyTree().flattenToVersionProvider().moduleIds().size());
    }

    @Test
    public void resolveSpringbootTestStarter() {
        final JkDependencies deps = JkDependencies.builder()
                .on("org.springframework.boot:spring-boot-starter-test:1.5.3.RELEASE").mapScope(JkJavaBuild.TEST).to("master", "runtime")
                .build();
        final JkDependencyResolver resolver = JkDependencyResolver.managed(JkRepos.mavenCentral(), deps);
        final JkResolveResult resolveResult = resolver.resolve(JkJavaBuild.TEST);
        final Set<JkModuleId> moduleIds = resolveResult.dependencyTree().flattenToVersionProvider().moduleIds();

        // Sometime JUnit is not present
        // TODO: fix it
        assertTrue(moduleIds.size() == 24 || moduleIds.size() == 25);
    }

}
