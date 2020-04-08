package dev.jeka.core.api.java.project;

import dev.jeka.core.api.depmanagement.*;
import dev.jeka.core.api.function.JkRunnables;
import dev.jeka.core.api.system.JkException;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsAssert;

import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class JkJavaProjectPublication {

    private final JkJavaProject project;

    private JkVersionedModule versionedModule;

    private JkRepoSet publishRepos = JkRepoSet.of();

    private UnaryOperator<Path> signer;

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
        this.postActions = JkRunnables.ofParent(this);
    }

    public JkJavaProjectPublication apply(Consumer<JkJavaProjectPublication> consumer) {
        consumer.accept(this);
        return this;
    }

    /**
     * Returns the module name and version of this project. This information is used for naming produced artifact files,
     * publishing. It is also consumed by tools as SonarQube.
     */
    public JkVersionedModule getVersionedModule() {
        return versionedModule;
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

    /**
     * Sets the specified module name and version for this project.
     * @see #getVersionedModule()
     */
    public JkJavaProjectPublication setVersionedModule(JkVersionedModule versionedModule) {
        JkUtilsAssert.notNull(versionedModule, "Can't set null value for versioned module.");
        this.versionedModule = versionedModule;
        return this;
    }

    /**
     * @see #setVersionedModule(JkVersionedModule)
     */
    public JkJavaProjectPublication setVersionedModule(String groupAndName, String version) {
        return setVersionedModule(JkModuleId.of(groupAndName).withVersion(version));
    }

    public JkJavaProjectPublication setRepos(JkRepoSet publishRepos) {
        JkUtilsAssert.notNull(publishRepos, "publish repos cannot be null.");
        this.publishRepos = publishRepos;
        return this;
    }

    public JkJavaProjectPublication addRepo(JkRepo publishRepo) {
        this.publishRepos = this.publishRepos.and(publishRepo);
        return this;
    }

    public void setSigner(UnaryOperator<Path> signer) {
        this.signer = signer;
    }

    /**
     * Publishes all defined artifacts.
     */
    public void publish() {
        JkException.throwIf(versionedModule == null, "No versioned module has been set on "
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
        JkException.throwIf(versionedModule == null, "No versioned module has been set on "
                + project + ". Can't publish.");
        publishMaven(JkRepo.ofLocal().toSet());
        postActions.run();
    }



    private void publishMaven(JkRepoSet repos) {
        JkMavenPublication publication = JkMavenPublication.of(project.getArtifactProducer(), publishedPomMetadata);
        JkPublisher.of(repos, project.getOutputDir())
                .withSigner(this.signer)
                .publishMaven(versionedModule, publication,
                        project.getDependencyManagement().getScopeDefaultedDependencies());
    }

    private void publishIvy(JkRepoSet repos) {
        if (!repos.hasIvyRepo()) {
            return;
        }
        JkLog.startTask("Preparing Ivy publication");
        final JkDependencySet dependencies = project.getDependencyManagement().getScopeDefaultedDependencies();
        JkArtifactProducer artifactProducer = project.getArtifactProducer();
        final JkIvyPublication publication = JkIvyPublication.of(
                artifactProducer.getMainArtifactPath(),
                JkJavaDepScopes.COMPILE.getName())
                .andOptional(artifactProducer.getArtifactPath(JkJavaProject.SOURCES_ARTIFACT_ID), JkJavaDepScopes.SOURCES.getName())
                .andOptional(artifactProducer.getArtifactPath(JkJavaProject.JAVADOC_ARTIFACT_ID), JkJavaDepScopes.JAVADOC.getName());
        final JkVersionProvider resolvedVersions = project.getDependencyManagement().getResolver()
                .resolve(dependencies, dependencies.getInvolvedScopes()).getResolvedVersionProvider();
        JkLog.endTask();
        JkPublisher.of(repos, project.getOutputDir())
                .publishIvy(versionedModule, publication, dependencies,
                        JkJavaDepScopes.DEFAULT_SCOPE_MAPPING, Instant.now(), resolvedVersions);
    }

}
