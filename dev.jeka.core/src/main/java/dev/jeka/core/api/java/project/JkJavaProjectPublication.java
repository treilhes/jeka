package dev.jeka.core.api.java.project;

import dev.jeka.core.api.depmanagement.*;
import dev.jeka.core.api.function.JkRunnables;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsAssert;

import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class JkJavaProjectPublication {

    private final JkJavaProject project;

    private JkModuleId moduleId;

    private Supplier<JkVersion> versionSupplier;

    private JkRepoSet publishRepos = JkRepoSet.of();

    private UnaryOperator<Path> signer;

    private UnaryOperator<JkDependencySet> dependenciesConfigurer = UnaryOperator.identity();

    private final JkPublishedPomMetadata<JkJavaProjectPublication> publishedPomMetadata;

    private final JkRunnables<JkJavaProjectPublication> postActions;

    /**
     * For parent chaining
     */
    public final JkJavaProject __;

    JkJavaProjectPublication(JkJavaProject project) {
        this.project = project;
        this.__ = project;
        this.publishedPomMetadata = JkPublishedPomMetadata.ofParent(this);
        this.versionSupplier = () -> JkVersion.UNSPECIFIED;
        this.postActions = JkRunnables.ofParent(this);
    }

    public JkJavaProjectPublication apply(Consumer<JkJavaProjectPublication> consumer) {
        consumer.accept(this);
        return this;
    }

    /**
     * Returns the module id (group and name) for this project. This information is used for naming produced artifact files,
     * publishing. It is also consumed by tools as SonarQube.
     * If no moduleId has been explicitly set, this methods a module id inferred from the project base directory name.
     */
    public JkModuleId getModuleId() {
        return moduleId != null ? moduleId : JkModuleId.of(project.getBaseDir().getFileName().toString());
    }

    /**
     * @see #getModuleId()
     */
    public JkJavaProjectPublication setModuleId(JkModuleId moduleId) {
        JkUtilsAssert.argument(moduleId != null, "ModuleId cannot be null.");
        this.moduleId = moduleId;
        return this;
    }

    /**
     * @see #getModuleId()
     */
    public JkJavaProjectPublication setModuleId(String groupAndName) { ;
        return this.setModuleId(JkModuleId.of(groupAndName));
    }

    /**
     * Set the version supplier used to compute this project version. For example, it can be : <ul>
     *     <li>A hardcoded supplier as : <code>() -> 1.0.0</code><</li>
     *     <li>A dynamic supplier based on VCS versioning as : <code>() -> JkGitWrapper.of(baseDir).getVersionFromTags</code></li>
     *     <li>Any code retrieving version from env var, property files, ....</li>
     * </ul>
     * This version information is used for publishing project artifacts on binary repository, but also may be used by
     * tools as Sonarqube to identify projects.<p>
     * The default value is {@link JkVersion#UNSPECIFIED}
     */
    public JkJavaProjectPublication setVersionSupplier(Supplier<JkVersion> versionSupplier) {
        JkUtilsAssert.argument(versionSupplier != null, "Version supplier cannot be null.");
        this.versionSupplier = versionSupplier;
        return this;
    }

    /**
     * @see #setVersionSupplier(Supplier)
     */
    public JkJavaProjectPublication setVersion(JkVersion version) {
        JkUtilsAssert.argument(version != null, "Version cannot be null.");
        return this.setVersionSupplier(() -> version);
    }

    /**
     * @see #setVersionSupplier(Supplier)
     */
    public JkJavaProjectPublication setVersion(String version) {
        return setVersion(JkVersion.of(version));
    }

    /**
     * @see #setVersionSupplier(Supplier)
     */
    public JkVersion getVersion() {
        JkVersion result = versionSupplier.get();
        JkUtilsAssert.state(result != null, "Version returned by supplier is null.");
        return this.versionSupplier.get();
    }

    public UnaryOperator<Path> getSigner() {
        return signer;
    }

    public JkPublishedPomMetadata<JkJavaProjectPublication> getPublishedPomMetadata() {
        return this.publishedPomMetadata;
    }

    public JkRepoSet getPublishRepos() {
        return this.publishRepos;
    }

    public JkRunnables<JkJavaProjectPublication> getPostActions() {
        return postActions;
    }

    public JkJavaProjectPublication setRepos(JkRepoSet publishRepos) {
        JkUtilsAssert.argument(publishRepos != null, "publish repos cannot be null.");
        this.publishRepos = publishRepos;
        return this;
    }

    public JkJavaProjectPublication addRepos(JkRepo ... repos) {
        for (JkRepo repo : repos) {
            this.publishRepos = this.publishRepos.and(repo);
        }
        return this;
    }

    public JkJavaProjectPublication setSigner(UnaryOperator<Path> signer) {
        this.signer = signer;
        return this;
    }

    public JkJavaProjectPublication setDependencies(UnaryOperator<JkDependencySet> dependenciesConfigurer) {
        JkUtilsAssert.argument(dependenciesConfigurer != null, "Dependency configurer cannot be null.");
        this.dependenciesConfigurer = dependenciesConfigurer;
        return this;
    }

    /**
     * Publishes all defined artifacts.
     */
    public void publish() {
        JkUtilsAssert.state(getVersion() != null, "No versioned module has been set on "
                + project + ". Can't publish.");
        JkRepoSet repos = this.publishRepos;
        if (repos == null) {
            repos = JkRepoSet.ofLocal();
            JkLog.warn("No publish repo has been mentioned. Publishing on local...");
        }
        publishMaven(repos);
        publishIvy(repos);
        postActions.run();
    }

    public void publishLocal() {
        JkUtilsAssert.state(getVersion() != null, "No versioned module has been set on "
                + project + ". Can't publish.");
        publishMaven(JkRepo.ofLocal().toSet());
        postActions.run();
    }

    public JkDependencySet getPublishedDependencies() {
        JkDependencySet deps = project.getDependencyManagement().getScopeDefaultedDependencies();
        return dependenciesConfigurer.apply(deps);
    }

    private void publishMaven(JkRepoSet repos) {
        JkMavenPublication publication = JkMavenPublication.of(project.getArtifactProducer(), publishedPomMetadata);
        JkDependencySet deps = getPublishedDependencies();
        JkPublisher.of(repos, project.getOutputDir())
                .withSigner(this.signer)
                .publishMaven(JkVersionedModule.of(getModuleId(), getVersion()), publication, deps);
    }

    private void publishIvy(JkRepoSet repos) {
        if (!repos.hasIvyRepo()) {
            return;
        }
        JkLog.startTask("Preparing Ivy publication");
        JkDependencySet deps = getPublishedDependencies();
        JkArtifactProducer artifactProducer = project.getArtifactProducer();
        final JkIvyPublication publication = JkIvyPublication.of(
                artifactProducer.getMainArtifactPath(),
                JkJavaDepScopes.COMPILE.getName())
                .andOptional(artifactProducer.getArtifactPath(JkJavaProject.SOURCES_ARTIFACT_ID), JkJavaDepScopes.SOURCES.getName())
                .andOptional(artifactProducer.getArtifactPath(JkJavaProject.JAVADOC_ARTIFACT_ID), JkJavaDepScopes.JAVADOC.getName());
        final JkVersionProvider resolvedVersions = project.getDependencyManagement().getResolver()
                .resolve(deps, deps.getInvolvedScopes()).getResolvedVersionProvider();
        JkLog.endTask();
        JkPublisher.of(repos, project.getOutputDir())
                .publishIvy(JkVersionedModule.of(getModuleId(), getVersion()), publication, deps,
                        JkJavaDepScopes.DEFAULT_SCOPE_MAPPING, Instant.now(), resolvedVersions);
    }

}
