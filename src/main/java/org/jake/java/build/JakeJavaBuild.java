package org.jake.java.build;

import java.io.File;
import java.util.Date;

import org.jake.JakeBuild;
import org.jake.JakeClasspath;
import org.jake.JakeDir;
import org.jake.JakeDirSet;
import org.jake.JakeDoc;
import org.jake.JakeFileFilter;
import org.jake.JakeJavaCompiler;
import org.jake.JakeLog;
import org.jake.JakeOption;
import org.jake.JakePlugins;
import org.jake.JakePlugins.JakePlugin.JakePluginConfigurer;
import org.jake.depmanagement.JakeDependencies;
import org.jake.depmanagement.JakeDependency;
import org.jake.depmanagement.JakeDependencyResolver;
import org.jake.depmanagement.JakeResolutionParameters;
import org.jake.depmanagement.JakeScope;
import org.jake.depmanagement.JakeScopeMapping;
import org.jake.java.JakeJavadocMaker;
import org.jake.java.JakeResourceProcessor;
import org.jake.java.JakeUtilsJdk;
import org.jake.java.testing.junit.JakeUnit;
import org.jake.java.testing.junit.JakeUnit.JunitReportDetail;
import org.jake.publishing.JakeIvyPublication;
import org.jake.publishing.JakeMavenPublication;
import org.jake.publishing.JakePublisher;
import org.jake.utils.JakeUtilsFile;

/**
 * Template class to define build on Java project.
 * This template is flexible enough to handle exotic project structure as it proposes
 * to override default setting at different level of granularity.<b/>
 * Beside this template define a set of "standard" scope to define dependencies.
 * You are not forced to use it strictly but it can simplify dependency management to follow a given standard.
 * 
 * @author Jerome Angibaud
 */
public class JakeJavaBuild extends JakeBuild {

	public static final JakeScope PROVIDED = JakeScope.of("provided").transitive(false)
			.descr("Dependencies to compile the project but that should not be embedded in produced artifacts.");

	public static final JakeScope COMPILE = JakeScope.of("compile")
			.descr("Dependencies to compile the project.");

	public static final JakeScope RUNTIME = JakeScope.of("runtime").extending(COMPILE)
			.descr("Dependencies to embed in produced artifacts (as war or fat jar * files).");

	public static final JakeScope TEST = JakeScope.of("test").extending(RUNTIME, PROVIDED)
			.descr("Dependencies necessary to compile and run tests.");

	public static final JakeScope SOURCES = JakeScope.of("sources")
			.descr("Contains the source artefacts");

	public static final JakeScope JAVADOC = JakeScope.of("javadoc")
			.descr("Contains the javadoc of this project");

	private static final JakeScopeMapping SCOPE_MAPPING = JakeScopeMapping
			.of(COMPILE).to("archives(master)", COMPILE.name())
			.and(PROVIDED).to("archives(master)", COMPILE.name())
			.and(RUNTIME).to("archives(master)", RUNTIME.name())
			.and(TEST).to("archives(master)", TEST.name());

	/**
	 * Default path for the non managed dependencies. This path is relative to {@link #baseDir()}.
	 */
	protected static final String STD_LIB_PATH = "build/libs";

	/**
	 * Filter to excludes everything in a java source directory which are not resources.
	 */
	protected static final JakeFileFilter RESOURCE_FILTER = JakeFileFilter
			.exclude("**/*.java").andExclude("**/package.html")
			.andExclude("**/doc-files");

	private final JakePlugins<JakeJavaBuildPlugin> plugins = JakePlugins.of(JakeJavaBuildPlugin.class);

	private final JakePlugins<JakeUnitPlugin> jakeUnitPlugins = JakePlugins.of(JakeUnitPlugin.class);

	@JakeOption({
		"Mention if you want to add extra lib in your 'compile' scope but not in your 'runtime' scope. It can be absolute or relative to the project base dir.",
		"These libs will be added to the compile path but won't be embedded in war files or fat jars.",
	"Example : -extraProvidedPath=C:\\libs\\mylib.jar;libs/others/**/*.jar" })
	private String extraProvidedPath;

	@JakeOption({
		"Mention if you want to add extra lib in your 'runtime' scope path. It can be absolute or relative to the project base dir.",
		"These libs will be added to the runtime path.",
	"Example : -extraRuntimePath=C:\\libs\\mylib.jar;libs/others/**/*.jar" })
	private String extraRuntimePath;

	@JakeOption({
		"Mention if you want to add extra lib in your 'compile' scope path. It can be absolute or relative to the project base dir.",
		"These libs will be added to the compile and runtime path.",
	"Example : -extraCompilePath=C:\\libs\\mylib.jar;libs/others/**/*.jar" })
	private String extraCompilePath;

	@JakeOption({
		"Mention if you want to add extra lib in your 'test' scope path. It can be absolute or relative to the project base dir.",
		"These libs will be added to the compile and runtime path.",
	"Example : -extraTestPath=C:\\libs\\mylib.jar;libs/others/**/*.jar" })
	private String extraTestPath;

	@JakeOption("Turn it on to skip tests.")
	protected boolean skipTests;

	public boolean skipTests() {
		return skipTests;
	}

	@JakeOption({"The more details the longer tests take to be processed.",
		"BASIC mention the total time elapsed along detail on failed tests.",
		"FULL detailed report displays additionally the time to run each tests.",
	"Example : -junitReportDetail=NONE"})
	protected JunitReportDetail junitReportDetail = JunitReportDetail.BASIC;

	// A cache for dependency resolver
	private JakeDependencyResolver cachedResolver;

	// A cache for artifact publisher
	private JakePublisher cachedPublisher;

	@Override
	public void setBaseDir(File baseDir) {
		super.setBaseDir(baseDir);
		this.plugins.configureAll(new JakePluginConfigurer<JakeJavaBuildPlugin>() {

			@Override
			public JakeJavaBuildPlugin configure(JakeJavaBuildPlugin plugin) {
				return plugin.configure(JakeJavaBuild.this);
			}

		});
	}


	// --------------------------- Project settings -----------------------

	/**
	 * Returns the encoding of source files for the compiler.
	 */
	public String sourceEncoding() {
		return "UTF-8";
	}

	/**
	 * Returns the level of detail, junit report is supposed produced.
	 * This level is set using by changing the junitReportDetail property.
	 */
	public final JunitReportDetail junitReportDetail() {
		return junitReportDetail;
	}

	/**
	 * Returns the Java source version for the compiler (as "1.4", 1.6", "7", ...).
	 */
	public String sourceJavaVersion() {
		return JakeUtilsJdk.runningJavaVersion();
	}

	/**
	 * Returns the Java target version for the compiler (as "1.4", 1.6", "7", ...).
	 */
	public String targetJavaVersion() {
		return sourceJavaVersion();
	}

	/**
	 * Returns the location of production source code that has not been edited manually (not generated).
	 */
	public JakeDirSet editedSourceDirs() {
		return JakeDirSet.of(baseDir("src/main/java"));
	}

	/**
	 * Returns location of production source code.
	 */
	public JakeDirSet sourceDirs() {
		return editedSourceDirs().and(generatedSourceDir());
	}

	/**
	 * Returns location of production resources.
	 */
	public JakeDirSet resourceDirs() {
		return sourceDirs().andFilter(RESOURCE_FILTER).and(
				baseDir("src/main/resources")).and(generatedResourceDir());
	}

	/**
	 * Returns location of test source code.
	 */
	public JakeDirSet testSourceDirs() {
		return JakeDirSet.of(baseDir().sub("src/test/java"));
	}

	/**
	 * Returns location of test resources.
	 */
	public JakeDirSet testResourceDirs() {
		return JakeDirSet.of(baseDir("src/test/resources")).and(
				testSourceDirs().andFilter(RESOURCE_FILTER));
	}

	/**
	 * Returns location of generated sources.
	 */
	public File generatedSourceDir() {
		return ouputDir("generated-sources/java");
	}

	/**
	 * Returns location of generated resources.
	 */
	public File generatedResourceDir() {
		return ouputDir("generated-resources");
	}

	/**
	 * Returns location of generated resources for tests.
	 */
	public File generatedTestResourceDir() {
		return ouputDir("generated-test-resources");
	}

	/**
	 * Returns location where the java production classes are compiled.
	 */
	public File classDir() {
		return ouputDir().sub("classes").createIfNotExist().root();
	}

	/**
	 * Returns location where the test reports are written.
	 */
	public File testReportDir() {
		return ouputDir("test-reports");
	}

	/**
	 * Returns location where the java production classes are compiled.
	 */
	public File testClassDir() {
		return ouputDir().sub("testClasses").createIfNotExist().root();
	}

	// --------------------------- Configurer -----------------------------

	public JakeJavaCompiler productionCompiler() {
		return JakeJavaCompiler.ofOutput(classDir())
				.andSources(sourceDirs())
				.withClasspath(depsFor(COMPILE).and(depsFor(PROVIDED)))
				.withSourceVersion(this.sourceJavaVersion())
				.withTargetVersion(this.targetJavaVersion());
	}

	public JakeJavaCompiler unitTestCompiler() {
		return JakeJavaCompiler.ofOutput(testClassDir())
				.andSources(testSourceDirs())
				.withClasspath(this.depsFor(TEST).andHead(classDir()))
				.withSourceVersion(this.sourceJavaVersion())
				.withTargetVersion(this.targetJavaVersion());
	}

	public JakeUnit unitTester() {
		final JakeClasspath classpath = JakeClasspath.of(this.testClassDir(), this.classDir()).and(this.depsFor(TEST));
		final File junitReport = new File(this.testReportDir(), "junit");
		return JakeUnit.of(classpath)
				.withReportDir(junitReport)
				.withReport(this.junitReportDetail)
				.withClassesToTest(this.testClassDir());
	}

	public JakeJavadocMaker javadocMaker() {
		final File outputDir = ouputDir(projectName() + "-javadoc");
		final File zip =  ouputDir(projectName() + "-javadoc.zip");
		return JakeJavadocMaker.of(sourceDirs(), outputDir, zip)
				.withClasspath(depsFor(COMPILE).and(depsFor(PROVIDED)));
	}

	public JakeJavaPacker packer() {
		return JakeJavaPacker.of(this);
	}

	protected JakeResourceProcessor resourceProcessor() {
		return JakeResourceProcessor.of(resourceDirs());
	}

	// --------------------------- Callable Methods -----------------------

	@JakeDoc("Generate sources and resources, compile production sources and process production resources to the classes directory.")
	public void compile() {
		JakeLog.startln("Processing production code and resources");
		generateSources();
		productionCompiler().compile();
		generateResources();
		processResources();
		JakeLog.done();
	}

	@JakeDoc("Compile and run all unit tests.")
	public void unitTest() {
		if (!checkProcessTests(testSourceDirs())) {
			return;
		}
		JakeLog.startln("Process unit tests");
		unitTestCompiler().compile();
		processUnitTestResources();
		unitTester().run();
		JakeLog.done();
	}

	@JakeDoc("Produce documents for this project (javadoc, Html site, ...)")
	public void doc() {
		javadocMaker().process();
	}

	@JakeDoc({	"Create many jar files containing respectively binaries, sources, test binaries and test sources.",
	"The jar containing the binary is the one that will be used as a depe,dence for other project."})
	public void pack() {
		packer().pack();
	}

	@JakeDoc("Compile production code and resources, compile test code and resources then launch the unit tests.")
	@Override
	public void base() {
		super.base();
		compile();
		unitTest();
	}

	@JakeDoc({"Publish the produced artifact to the defined repositories. ",
	"This can work only if a 'publishable' repository has been defined and the artifact has been generated (pack method)."})
	public void publish() {
		final Date date = this.buildTime();
		if (this.publisher().hasMavenPublishRepo()) {
			this.publisher().publishMaven(module(), mavenPublication(), dependencies(), date);
		}
		if (this.publisher().hasIvyPublishRepo()) {
			this.publisher().publishIvy(module(), ivyPublication(), dependencies(), COMPILE, SCOPE_MAPPING, date);
		}
	}



	// ----------------------- Overridable sub-methods ---------------------


	/**
	 * Returns the base dependency resolver.
	 * 
	 * @see #dependencyResolver().
	 */
	protected JakeDependencyResolver baseDependencyResolver() {
		final JakeDependencies dependencies = dependencies().and(extraCommandLineDeps());
		if (dependencies.containsExternalModule()) {
			return JakeDependencyResolver.managed(jakeIvy(), dependencies, module(),
					JakeResolutionParameters.of(SCOPE_MAPPING));
		}
		return JakeDependencyResolver.unmanaged(dependencies);
	}

	/**
	 * Returns the dependencies of this module. By default it uses unmanaged dependencies stored
	 * locally in the project as described by {@link #defaultUnmanagedDependencies()} method.
	 * If you want to use managed dependencies, you must override this method.
	 */
	protected JakeDependencies dependencies() {
		return defaultUnmanagedDependencies();
	}

	protected JakePublisher publisher() {
		if (cachedPublisher == null) {
			cachedPublisher = JakePublisher.usingIvy(jakeIvy());
		}
		return cachedPublisher;
	}


	/**
	 * Returns the resolved dependencies for the given scope. Depending on the passed
	 * options, it may be augmented with extra-libs mentioned in options <code>extraXxxxPath</code>.
	 */
	public final JakeClasspath depsFor(JakeScope scope) {
		if (cachedResolver == null) {
			JakeLog.startln("Setting dependency resolver ");
			cachedResolver = baseDependencyResolver();
			JakeLog.done("Resolver set " + cachedResolver);
		}
		return JakeClasspath.of(cachedResolver.get(scope));
	}

	/**
	 * Override this method if you need to
	 */
	protected void generateSources() {
		// Do nothing by default
	}

	@JakeDoc("Generate files to be taken as resources.  Do nothing by default.")
	protected void generateResources() {
		// Do nothing by default
	}

	protected void processResources() {
		this.resourceProcessor().generateTo(classDir());
	}

	protected void processUnitTestResources() {
		JakeResourceProcessor.of(testResourceDirs()).andIfExist(generatedTestResourceDir()).generateTo(testClassDir());
	}

	protected boolean checkProcessTests(JakeDirSet testSourceDirs) {
		if (skipTests) {
			return false;
		}
		if (testSourceDirs == null || testSourceDirs.jakeDirs().isEmpty()) {
			JakeLog.info("No test source declared. Skip tests.");
			return false;
		}
		if (!testSourceDirs().allExists()) {
			JakeLog.info("No existing test source directory found : " + testSourceDirs +". Skip tests.");
			return false;
		}
		return true;
	}

	protected JakeMavenPublication mavenPublication(boolean includeTests, boolean includeSources) {
		final JakeJavaPacker packer = packer();
		return JakeMavenPublication.of(this.projectName() ,packer.jarFile())
				.andIf(includeSources, packer.jarSourceFile(), "sources")
				.andOptional(javadocMaker().zipFile(), "javadoc")
				.andOptionalIf(includeTests, packer.jarTestFile(), "test")
				.andOptionalIf(includeTests && includeSources, packer.jarTestSourceFile(), "testSources");
	}

	protected JakeIvyPublication ivyPublication(boolean includeTests, boolean includeSources) {
		final JakeJavaPacker packer = packer();
		return JakeIvyPublication.of(packer.jarFile(), COMPILE)
				.andIf(includeSources, packer.jarSourceFile(), "source", SOURCES)
				.andOptional(javadocMaker().zipFile(), "javadoc", JAVADOC)
				.andOptionalIf(includeTests, packer.jarTestFile(), "jar", TEST)
				.andOptionalIf(includeTests, packer.jarTestSourceFile(), "source", SOURCES);
	}

	protected JakeIvyPublication ivyPublication() {
		return ivyPublication(includeTestsInPublication(), includeSourcesInPublication());
	}

	protected JakeMavenPublication mavenPublication() {
		return mavenPublication(includeTestsInPublication(), includeSourcesInPublication());
	}

	protected boolean includeTestsInPublication() {
		return false;
	}

	protected boolean includeSourcesInPublication() {
		return true;
	}

	// ------------------------------------

	public static void main(String[] args) {
		new JakeJavaBuild().base();
	}

	private JakeDependencies extraCommandLineDeps() {
		return JakeDependencies.builder()
				.usingDefaultScopes(COMPILE).onFiles(toPath(extraCompilePath))
				.usingDefaultScopes(RUNTIME).onFiles(toPath(extraRuntimePath))
				.usingDefaultScopes(TEST).onFiles(toPath(extraTestPath))
				.usingDefaultScopes(PROVIDED).onFiles(toPath(extraProvidedPath)).build();
	}

	private final JakeClasspath toPath(String pathAsString) {
		if (pathAsString == null) {
			return JakeClasspath.of();
		}
		return JakeClasspath.of(JakeUtilsFile.toPath(pathAsString, ";", baseDir().root()));
	}

	protected JakeDependencies defaultUnmanagedDependencies() {
		final JakeDir libDir = JakeDir.of(baseDir(STD_LIB_PATH));
		return JakeDependencies.builder()
				.usingDefaultScopes(COMPILE).on(JakeDependency.of(libDir.include("*.jar", "compile/*.jar")))
				.usingDefaultScopes(PROVIDED).on(JakeDependency.of(libDir.include("*.jar", "provided/*.jar")))
				.usingDefaultScopes(RUNTIME).on(JakeDependency.of(libDir.include("*.jar", "runtime/*.jar")))
				.usingDefaultScopes(TEST).on(JakeDependency.of(libDir.include("*.jar", "test/*.jar"))).build();
	}


}
