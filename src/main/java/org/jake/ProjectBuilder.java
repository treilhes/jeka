package org.jake;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.jake.file.JakeDir;
import org.jake.java.JakeBuildJar;
import org.jake.java.JakeClassLoader;
import org.jake.java.JakeClasspath;
import org.jake.java.JakeJavaCompiler;
import org.jake.java.eclipse.JakeBuildEclipseProject;
import org.jake.utils.JakeUtilsIterable;
import org.jake.utils.JakeUtilsReflect;
import org.jake.utils.JakeUtilsString;
import org.jake.utils.JakeUtilsTime;

class ProjectBuilder {

	//private static final String DEFAULT_METHOD_NAME = "base";

	private static final String BUILD_SOURCE_DIR = "build/spec";

	private static final String BUILD_LIB_DIR = "build/libs/build";

	private static final String BUILD_BIN_DIR = "build/output/build-bin";

	private static final String DEFAULT_JAVA_SOURCE = "src/main/java";

	private final File moduleBaseDir;


	public ProjectBuilder(File moduleBaseDir) {
		super();
		this.moduleBaseDir = moduleBaseDir;
	}



	public boolean build(Iterable<String> methods) {
		final long start = System.nanoTime();

		final String pattern = "-";
		final String intro = "Building module : " + moduleBaseDir.getAbsolutePath();
		JakeLog.info(JakeUtilsString.repeat(pattern, intro.length() ));
		JakeLog.info(intro);
		JakeLog.info(JakeUtilsString.repeat(pattern, intro.length() ));
		JakeLog.nextLine();

		final Iterable<File> buildClasspath = this
				.resolveBuildCompileClasspath();
		final boolean result = this.launch(buildClasspath, methods);

		final float duration = JakeUtilsTime.durationInSeconds(start);
		if (result) {
			JakeLog.info("--> Module " + moduleBaseDir.getAbsolutePath() + " processed with success in " + duration + " seconds.");
		} else {
			JakeLog.info("--> Module " + moduleBaseDir.getAbsolutePath() + " failed after " + duration + " seconds.");
		}
		return result;
	}

	private boolean hasBuildSource() {
		final File buildSource = new File(moduleBaseDir, BUILD_SOURCE_DIR);
		if (!buildSource.exists()) {
			return false;
		}
		return true;
	}

	private void compileBuild(Iterable<File> classpath) {
		final JakeDir buildSource = JakeDir.of(new File(moduleBaseDir,
				BUILD_SOURCE_DIR));
		if (!buildSource.exists()) {
			throw new IllegalStateException(
					BUILD_SOURCE_DIR
					+ " directory has not been found in this project. "
					+ " This directory is supposed to contains build scripts (as form of java source");
		}
		final File buildBinDir = new File(moduleBaseDir, BUILD_BIN_DIR);
		if (!buildBinDir.exists()) {
			buildBinDir.mkdirs();
		}
		JakeLog.info("Creating build binaries ...");
		final long start = System.nanoTime();
		JakeJavaCompiler.ofOutput(buildBinDir)
		.addSourceFiles(buildSource)
		.setClasspath(classpath)
		.compileOrFail();
		JakeLog.info("Done in " + JakeUtilsTime.durationInSeconds(start) + " seconds.", "");
	}

	private boolean launch(Iterable<File> buildClasspath,
			Iterable<String> methods) {
		final Class<? extends JakeBuildBase> buildClass = getBuildClass(buildClasspath);
		if (buildClass == null) {
			throw new IllegalStateException(
					"No build class found for this project and cannot find default build class that fits.");
		}
		final JakeBuildBase build = JakeUtilsReflect.newInstance(buildClass);

		JakeLog.info("Use build class '" + buildClass.getCanonicalName()
				+ "' with methods : "
				+ JakeUtilsString.toString(methods, ", ") + ".");
		JakeLog.info("Using classpath : " + buildClasspath);
		if (JakeOptions.hasFieldOptions(buildClass)) {
			JakeLog.info("With options : " + JakeOptions.fieldOptionsToString(build));
		}
		JakeLog.nextLine();

		for (final String methodName : methods) {
			final Method method;
			final String actionIntro = "Method : " + methodName;
			JakeLog.info(actionIntro);
			JakeLog.info(JakeUtilsString.repeat("-", actionIntro.length()));
			try {
				method = build.getClass().getMethod(methodName);
			} catch (final NoSuchMethodException e) {
				JakeLog.warn("No zero-arg method '" + methodName
						+ "' found in class '" + buildClass.getCanonicalName() + "'. Skip.");
				continue;
			}
			final long start = System.nanoTime();
			try {
				method.invoke(build);
			} catch (final SecurityException e) {
				throw new RuntimeException(e);
			} catch (final IllegalAccessException e) {
				throw new RuntimeException(e);
			} catch (final InvocationTargetException e) {
				final Throwable target = e.getTargetException();
				if (target instanceof JakeException) {
					JakeLog.error(target.getMessage());
					return false;
				} else if (target instanceof RuntimeException) {
					throw (RuntimeException) target;
				} else {
					throw new RuntimeException(target);
				}
			} finally {
				JakeLog.nextLine();
				JakeLog.info("-> Method " + methodName + " executed in " + JakeUtilsTime.durationInSeconds(start) + " seconds.");
			}
			JakeLog.flush();
			JakeLog.nextLine();
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	private Class<? extends JakeBuildBase> getBuildClass(Iterable<File> buildClasspath) {
		final String buildClassName = resolveBuildClassName(buildClasspath);
		if (buildClassName == null) {
			return null;
		}
		final File buildBin = new File(moduleBaseDir, BUILD_BIN_DIR);
		final Iterable<File> runtimeClassPath = JakeUtilsIterable.concatToList(
				buildBin, buildClasspath);
		final JakeClassLoader classLoader = JakeClassLoader.current().createChild(runtimeClassPath);
		final Class<? extends JakeBuildBase> result = classLoader.load(buildClassName);
		return result;
	}

	private String resolveBuildClassName(Iterable<File> buildClasspath) {
		if (this.hasBuildSource()) {
			this.compileBuild(buildClasspath);
			final File buildBin = new File(moduleBaseDir, BUILD_BIN_DIR);
			final List<String> buildClassNames = getBuildClassNames(JakeClasspath.of(buildBin));
			if (!buildClassNames.isEmpty()) {
				return buildClassNames.get(0);
			} else {
				JakeLog.warn("No Build class found on " + BUILD_SOURCE_DIR + " ... try to use a default one.");
			}
		} else {
			JakeLog.info("No build provided...try to use a default one.");
		}
		return defaultBuildClassName();
	}

	private JakeClasspath resolveBuildCompileClasspath() {
		JakeClasspath result = JakeClassLoader.current().childClasspath();
		final File buildLibDir = new File(moduleBaseDir, BUILD_LIB_DIR);
		if (buildLibDir.exists() && buildLibDir.isDirectory()) {
			result = result.and(JakeDir.of(buildLibDir).include("**/*.jar"));
		}
		final File extLibDir = new File(JakeLocator.jakeHome(), "ext");
		if (extLibDir.exists() && extLibDir.isDirectory()) {
			result = result.and(JakeDir.of(extLibDir).include("**/*.jar"));
		}
		result = result.and(JakeOptions.extraJakeClasspath());
		return result;
	}

	@SuppressWarnings("rawtypes")
	private static List<String> getBuildClassNames(Iterable<File> classpath) {

		final JakeClassLoader classLoader = JakeClassLoader.current().createChild(classpath);

		// Find the Build class
		// TODO Not optimized. Reduce the search path to load less classes.
		final Set<Class<?>> classes = classLoader.getAllTopLevelClasses(null);
		final List<String> buildClasses = new LinkedList<String>();
		for (final Class clazz : classes) {
			final boolean isAbstract = Modifier
					.isAbstract(clazz.getModifiers());
			if (!isAbstract && JakeBuildBase.class.isAssignableFrom(clazz)) {
				buildClasses.add(clazz.getName());
			}
		}
		return buildClasses;
	}

	private String defaultBuildClassName() {
		if (new File(moduleBaseDir, DEFAULT_JAVA_SOURCE).exists()
				&& new File(moduleBaseDir, BUILD_LIB_DIR ).exists()) {
			return JakeBuildJar.class.getName();
		}
		if (JakeBuildEclipseProject.candidate(moduleBaseDir)) {
			return JakeBuildEclipseProject.class.getName();
		}
		return null;
	}

}
