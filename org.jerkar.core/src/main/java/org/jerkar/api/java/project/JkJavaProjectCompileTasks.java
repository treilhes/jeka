package org.jerkar.api.java.project;

import org.jerkar.api.depmanagement.JkJavaDepScopes;
import org.jerkar.api.file.JkPathSequence;
import org.jerkar.api.function.JkRunnables;
import org.jerkar.api.java.JkJavaCompileSpec;
import org.jerkar.api.java.JkJavaCompiler;
import org.jerkar.api.java.JkResourceProcessor;

import java.nio.charset.Charset;

public class JkJavaProjectCompileTasks {

    private final JkJavaProjectMaker maker;

    private final JkRunnables preCompile = JkRunnables.noOp();

    private final JkRunnables sourceGenerator = JkRunnables.noOp();

    private final JkRunnables resourceGenerator = JkRunnables.noOp();

    private final JkRunnables postActions = JkRunnables.noOp();

    private final JkRunnables resourceProcessor;

    private final JkRunnables compileRunner;

    private JkJavaCompiler compiler = JkJavaCompiler.of();

    JkJavaProjectCompileTasks(JkJavaProjectMaker maker, Charset charset) {
        this.maker = maker;
        resourceProcessor = JkRunnables.of(() -> JkResourceProcessor.of(maker.project.getSourceLayout().getResources())
                .and(maker.getOutLayout().getGeneratedResourceDir())
                .and(maker.project.getResourceInterpolators())
                .generateTo(maker.getOutLayout().getClassDir(), charset));
        compileRunner = JkRunnables.of(() -> {
            final JkJavaCompileSpec compileSpec = compileSourceSpec();
            compiler.compile(compileSpec);
        });
    }

    void run() {
        preCompile.run();
        sourceGenerator.run();
        resourceGenerator.run();
        compileRunner.run();
        resourceProcessor.run();
        postActions.run();
    }

    public JkRunnables getPreCompile() {
        return preCompile;
    }

    public JkRunnables getSourceGenerator() {
        return sourceGenerator;
    }

    public JkRunnables getResourceGenerator() {
        return resourceGenerator;
    }

    public JkRunnables getPostActions() {
        return postActions;
    }

    public JkRunnables getResourceProcessor() {
        return resourceProcessor;
    }

    public JkRunnables getCompileRunner() {
        return compileRunner;
    }

    public JkJavaCompiler getCompiler() {
        return compiler;
    }

    public JkJavaProjectCompileTasks setCompiler(JkJavaCompiler compiler) {
        this.compiler = compiler;
        return this;
    }

    public JkJavaProjectCompileTasks setFork(boolean fork, String ... params) {
        this.compiler = this.compiler.withForking(fork, params);
        return this;
    }

    private JkJavaCompileSpec compileSourceSpec() {
        JkJavaCompileSpec result = maker.project.getCompileSpec().copy();
        final JkPathSequence classpath = maker.fetchDependenciesFor(JkJavaDepScopes.SCOPES_FOR_COMPILATION);
        return result
                .setClasspath(classpath)
                .addSources(maker.project.getSourceLayout().getSources())
                .addSources(maker.getOutLayout().getGeneratedSourceDir())
                .setOutputDir(maker.getOutLayout().getClassDir());
    }
}