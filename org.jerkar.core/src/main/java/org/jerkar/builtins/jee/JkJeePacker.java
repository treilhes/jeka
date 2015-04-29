package org.jerkar.builtins.jee;

import java.io.File;

import org.jerkar.JkDir;
import org.jerkar.JkException;
import org.jerkar.JkPath;
import org.jerkar.builtins.javabuild.JkJavaBuild;

/**
 * War and Ear maker for {@link JkJavaBuild}. This maker will get information from supplied java builder
 * to create relevant jars.
 * 
 * @author Jerome Angibaud
 */
public class JkJeePacker {

	public static JkJeePacker of(JkJavaBuild build) {
		return new JkJeePacker(build);
	}

	private final JkJavaBuild build;

	private JkJeePacker(JkJavaBuild build) {
		super();
		this.build = build;
	}

	public void war(File webappSrc, File warDirDest, File warFileDest) {
		if (! new File(webappSrc, "WEB-INF/web.xml").exists()) {
			throw new JkException("The directory " + webappSrc.getPath()
					+ " does not contains WEB-INF" + File.separator + "web.xml file");
		}
		final JkPath path = build.depsFor(JkJavaBuild.RUNTIME);
		final JkDir dir = JkDir.of(warDirDest).importDirContent(webappSrc)
				.sub("WEB-INF/classes").importDirContent(build.classDir())
				.sub("../lib").copyInFiles(path);
		dir.zip().to(warFileDest);
	}

	public void ear(Iterable<File> warFiles, File earSrc, File destDir, File destFile) {
		JkDir.of(destDir).importDirContent(earSrc).copyInFiles(warFiles).zip().to(destFile);
	}

}