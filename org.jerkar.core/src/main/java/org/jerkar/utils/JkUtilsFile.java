package org.jerkar.utils;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipOutputStream;

/**
 * Utility class for dealing with files.
 * 
 * @author Jerome Angibaud
 */
public final class JkUtilsFile {

	/**
	 * Throws an {@link IllegalArgumentException} if the specified file is not a directory or does not exist.
	 */
	public static void assertDir(File candidate) {
		if (!candidate.exists()) {
			throw new IllegalArgumentException(candidate.getPath()
					+ " does not exist.");
		}
		if (!candidate.isDirectory()) {
			throw new IllegalArgumentException(candidate
					+ " is not a directory.");
		}
	}




	/**
	 * Moves a file to another location.
	 */
	public static void move(File from, File to) {
		if (!from.renameTo(to)) {
			copyFile(from, to);
			if (!from.delete()) {
				if (!to.delete()) {
					throw new RuntimeException("Unable to delete " + to);
				}
				throw new RuntimeException("Unable to delete " + from);
			}
		}
	}


	/**
	 * Returns the relative path of the specified file relative to the specified base directory.
	 * File argument must be a child of the base directory otherwise method throw an {@link IllegalArgumentException}.
	 */
	public static String getRelativePath(File baseDir, File file) {
		final FilePath basePath = FilePath.of(baseDir);
		final FilePath filePath = FilePath.of(file);
		return filePath.relativeTo(basePath).toString();
	}


	/**
	 * Same as {@link #copyDir(File, File, FileFilter, boolean, PrintStream)} without stream report.
	 */
	public static int copyDir(File source, File targetDir, FileFilter filter,
			boolean copyEmptyDir) {
		return copyDir(source, targetDir, filter, copyEmptyDir, null);
	}

	/**
	 * Copies the source directory content to the target directory.
	 * @param source The directory we want copy the content from.
	 * @param targetDir The directory where will be copied the content
	 * @param filter Filter to decide which file should be copied or not. If you want to copy
	 * everything, use {@link FileFilter#} that always return <code>true</code>.
	 * @param copyEmptyDir specify if the empty dirs should be copied as well.
	 * @param reportStream The scream where a copy status can be written. If <code>null</code>,
	 * no status is written.
	 * @return The file copied count.
	 */
	public static int copyDir(File source, File targetDir, FileFilter filter,
			boolean copyEmptyDir, PrintStream reportStream) {
		return copyDirReplacingTokens(source, targetDir, filter, copyEmptyDir, reportStream, null);
	}

	/**
	 * Same as {@link #copyDir(File, File, FileFilter, boolean, PrintStream)} but also replacing all
	 * token as '${key}' by their respecting value. The replacement is done only if the specified tokenValues
	 * map contains the according key.
	 * @param tokenValues a map for replacing token key by value
	 */
	public static int copyDirReplacingTokens(File fromDir, File toDir, FileFilter filterArg,
			boolean copyEmptyDir, PrintStream reportStream, Map<String, String> tokenValues) {
		final FileFilter filter = JkUtilsObject.firstNonNull(filterArg, JkFileFilters.acceptAll());
		assertDir(fromDir);
		if (fromDir.equals(toDir)) {
			throw new IllegalArgumentException(
					"Base and destination directory can't be the same : "
							+ fromDir.getPath());
		}
		if (isAncestor(fromDir, toDir) && filter.accept(toDir)) {
			throw new IllegalArgumentException("Base filtered directory "
					+ fromDir.getPath() + ":(" + filter
					+ ") cannot contain destination directory "
					+ toDir.getPath()
					+ ". Narrow filter or change the target directory.");
		}
		if (toDir.isFile()) {
			throw new IllegalArgumentException(toDir.getPath()
					+ " is file. Should be directory");
		}

		if (reportStream != null) {
			reportStream.append("Coping content of " + fromDir.getPath());
		}
		final File[] children = fromDir.listFiles();
		int count = 0;
		for (final File child : children) {
			if (child.isFile()) {
				if (filter.accept(child)) {
					final File targetFile = new File(toDir, child.getName());
					if (tokenValues == null || tokenValues.isEmpty()) {
						copyFile(child, targetFile, reportStream);
					} else {
						final File toFile = new File(toDir, targetFile.getName());
						copyFileReplacingTokens(child, toFile, tokenValues, reportStream);
					}

					count++;
				}
			} else {
				final File subdir = new File(toDir, child.getName());
				if (filter.accept(child) && copyEmptyDir) {
					subdir.mkdirs();
				}
				final int subCount = copyDirReplacingTokens(child, subdir, filter,
						copyEmptyDir, reportStream, tokenValues);
				count = count + subCount;
			}

		}
		return count;
	}

	/**
	 * Returns the content of the specified property file as a {@link Properties} object.
	 */
	public static Properties readPropertyFile(File propertyfile)  {
		final Properties props = new Properties();
		FileInputStream fileInputStream = null;
		try {
			fileInputStream = new FileInputStream(propertyfile);
			props.load(fileInputStream);
		} catch (final Exception e) {
			throw new RuntimeException(e);
		} finally {
			JkUtilsIO.closeQuietly(fileInputStream);
		}
		return props;
	}

	/**
	 * Returns the content of the specified property file as a {@link Map} object.
	 */
	public static Map<String, String> readPropertyFileAsMap(File propertyfile) {
		final Properties properties = readPropertyFile(propertyfile);
		return JkUtilsIterable.propertiesToMap(properties);
	}

	/**
	 * Returns the content of the specified file as a string.
	 */
	public static String read(File file) {
		final FileInputStream fileInputStream = JkUtilsIO.inputStream(file);
		final String result = JkUtilsIO.readAsString(fileInputStream);
		JkUtilsIO.closeQuietly(fileInputStream);
		return result;
	}

	/**
	 * Copies the given file to the specified directory, writting status on the provided reportStream.
	 */
	public static void copyFileToDir(File from, File toDir, PrintStream reportStream) {
		final File to = new File(toDir, from.getName());
		copyFile(from, to, reportStream);
	}

	/**
	 * Copies the given file to the specified directory.
	 */
	public static void copyFile(File from, File toFile) {
		copyFile(from, toFile, null);
	}

	public static void copyFile(File from, File toFile, PrintStream reportStream) {
		if (reportStream != null) {
			reportStream.println("Coping file " + from.getAbsolutePath() + " to " + toFile.getAbsolutePath());
		}
		if (!from.exists()) {
			throw new IllegalArgumentException("File " + from.getPath()
					+ " does not exist.");
		}
		if (from.isDirectory()) {
			throw new IllegalArgumentException(from.getPath()
					+ " is a directory. Should be a file.");
		}
		try {
			final InputStream in = new FileInputStream(from);
			if (!toFile.getParentFile().exists()) {
				toFile.getParentFile().mkdirs();
			}
			if (!toFile.exists()) {
				toFile.createNewFile();
			}
			final OutputStream out = new FileOutputStream(toFile);

			final byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			in.close();
			out.close();
		} catch (final IOException e) {
			throw new RuntimeException(
					"IO exception occured while copying file " + from.getPath()
					+ " to " + toFile.getPath(), e);
		}

	}

	/**
	 * Fully delete the content of he specified directory.
	 */
	public static void deleteDirContent(File dir) {
		final File[] files = dir.listFiles();
		if (files != null) {
			for (final File file : files) {
				if (file.isDirectory()) {
					deleteDirContent(file);
				}
				file.delete();
			}
		}
	}

	/**
	 * Get the url from the specified file.
	 */
	public static URL toUrl(File file) {
		try {
			return file.toURI().toURL();
		} catch (final MalformedURLException e) {
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Get the file from the specified url.
	 */
	public static File fromUrl(URL url) {
		File result;
		try {
			result = new File(url.toURI());
		} catch(final URISyntaxException e) {
			result = new File(url.getPath());
		} catch(final IllegalArgumentException e) {
			throw new IllegalArgumentException(url + " : " + e.getMessage(), e);
		}
		return result;
	}


	/**
	 * Returns <code>true</code> if the ancestorCandidate file is an ancestor of the specified childCandidate.
	 */
	public static boolean isAncestor(File ancestorCandidate,
			File childCandidtate) {
		File parent = childCandidtate;
		while (true) {
			parent = parent.getParentFile();
			if (parent == null) {
				return false;
			}
			if (isSame(parent, ancestorCandidate)) {
				return true;
			}
		}
	}

	/**
	 * Returns <code>true</code> if the canonical files passed as arguments have the same canonical file.
	 */
	public static boolean isSame(File file1, File file2) {
		return canonicalFile(file1).equals(canonicalFile(file2));
	}

	/**
	 * A 'checked exception free' version of {@link File#getCanonicalPath()}.
	 */
	public static String canonicalPath(File file) {
		try {
			return file.getCanonicalPath();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * A 'checked exception free' version of {@link File#getCanonicalFile()}.
	 */
	public static File canonicalFile(File file) {
		try {
			return file.getCanonicalFile();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @see #zipDir(File, int, Iterable)
	 */
	public static void zipDir(File zipFile, int zipLevel, File... dirs) {
		zipDir(zipFile, zipLevel, Arrays.asList(dirs));
	}

	/**
	 * Zips the content of the specified directories into the specified zipFile.
	 * If the specified zip file does not exist, the method will create it.
	 * 
	 * @param zipLevel
	 *            the compression level (0-9) as specified in
	 *            {@link ZipOutputStream#setLevel(int)}.
	 */
	public static void zipDir(File zipFile, int zipLevel, Iterable<File> dirs) {

		final ZipOutputStream zos = JkUtilsIO.createZipOutputStream(zipFile, zipLevel);
		try {
			for (final File dir : dirs) {
				JkUtilsIO.addZipEntry(zos, dir, dir);
			}
			zos.close();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

	}

	/**
	 * Returns all files contained recursively in the specified directory.
	 */
	public static List<File> filesOf(File dir, boolean includeFolder) {
		return filesOf(dir, JkFileFilters.acceptAll(), includeFolder);
	}

	/**
	 * Returns all files contained recursively in the specified directory.
	 */
	public static List<File> filesOf(File dir, FileFilter fileFilter,
			boolean includeFolders) {
		assertDir(dir);
		final List<File> result = new LinkedList<File>();
		for (final File file : dir.listFiles()) {
			if (file.isFile() && !fileFilter.accept(file)) {
				continue;
			}
			if (file.isDirectory()) {
				if (includeFolders && fileFilter.accept(file)) {
					result.add(file);
				}
				result.addAll(filesOf(file, fileFilter, includeFolders));
			} else {
				result.add(file);
			}
		}
		return result;
	}

	public static boolean isEmpty(File dir, boolean countFolders) {
		return count(dir, JkFileFilters.acceptAll(), countFolders) == 0;
	}

	/**
	 * Returns count of files contained recursively in the specified directory.
	 * If the dir does not exist then it returns 0.
	 */
	public static int count(File dir, FileFilter fileFilter,
			boolean includeFolders) {
		int result = 0;
		if (!dir.exists()) {
			return 0;
		}
		for (final File file : dir.listFiles()) {
			if (file.isFile() && !fileFilter.accept(file)) {
				continue;
			}
			if (file.isDirectory()) {
				if (includeFolders) {
					result++;
				}
				result = result + count(file, fileFilter, includeFolders);
			} else {
				result++;
			}
		}
		return result;
	}

	public static File workingDir() {
		return JkUtilsFile.canonicalFile(new File("."));
	}

	public static File tempDir() {
		return new File(System.getProperty("java.io.tmpdir"));
	}

	public static void writeString(File file, String content, boolean append) {
		try {
			if (!file.exists()) {
				file.getParentFile().mkdirs();
				file.createNewFile();
			}
			final FileWriter fileWriter = new FileWriter(file, append);
			fileWriter.append(content);
			fileWriter.close();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}


	public static String md5Checksum(File file) {
		InputStream fis;
		try {
			fis = new FileInputStream(file);
		} catch (final FileNotFoundException e) {
			throw new IllegalArgumentException(file.getPath() + " not found.", e);
		}

		final byte[] buffer = new byte[1024];
		MessageDigest complete;
		try {
			complete = MessageDigest.getInstance("MD5");
		} catch (final NoSuchAlgorithmException e) {
			JkUtilsIO.closeQuietly(fis);
			throw new RuntimeException(e);
		}
		int numRead;
		do {
			numRead = JkUtilsIO.read(fis);
			if (numRead > 0) {
				complete.update(buffer, 0, numRead);
			}
		} while (numRead != -1);
		JkUtilsIO.closeQuietly(fis);
		final byte[] bytes = complete.digest();
		String result = "";
		for (final byte element : bytes) {
			result += Integer.toString((element & 0xff) + 0x100, 16).substring(
					1);
		}
		return result;
	}

	/**
	 * Same as {@link File#createTempFile(String, String) but throwing only unchecked exceptions.
	 */
	public static File createTempFile(String prefix, String suffix) {
		try {
			return File.createTempFile(prefix, suffix);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates the specified file on the File system if not exist.
	 */
	public static File createFileIfNotExist(File file) {
		try {
			if (!file.exists()) {
				file.createNewFile();
			}
			return file;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}


	/**
	 * Deletes the specified test, throwing a {@link RuntimeException} if the delete fails.
	 */
	public static void delete(File file) {
		if (!file.delete()) {
			throw new RuntimeException("File " + file.getAbsolutePath()  + " can't be deleted.");
		}
	}

	/**
	 * Copies the content of the specified in file into the specified out file. While coping token ${key}
	 * are replaced by the value found in the specified replacements map.
	 * @see #copyDirReplacingTokens(File, File, FileFilter, boolean, PrintStream, Map)
	 */
	public static void copyFileReplacingTokens(File in, File out, Map<String, String> replacements) {
		copyFileReplacingTokens(in, out, replacements, null);
	}

	/**
	 * Same as {@link #copyFileReplacingTokens(File, File, Map, PrintStream)} but writtying the status in the specified
	 * reportStream.
	 */
	public static void copyFileReplacingTokens(File from, File toFile, Map<String, String> replacements, PrintStream reportStream) {
		if (!from.exists()) {
			throw new IllegalArgumentException("File " + from.getPath()
					+ " does not exist.");
		}
		if (from.isDirectory()) {
			throw new IllegalArgumentException(from.getPath()
					+ " is a directory. Should be a file.");
		}
		final TokenReplacingReader replacingReader = new TokenReplacingReader(from, replacements);
		if (!toFile.exists()) {
			try {
				toFile.createNewFile();
			} catch (final IOException e) {
				JkUtilsIO.closeQuietly(replacingReader);
				throw new RuntimeException(e);
			}
		}
		final Writer writer;
		try {
			writer = new FileWriter(toFile);
		} catch (final IOException e) {
			JkUtilsIO.closeQuietly(replacingReader);
			throw new RuntimeException(e);
		}
		if (reportStream != null) {
			reportStream.println("Coping and replacing token from file "
					+ from.getAbsolutePath() + " to " + toFile.getAbsolutePath());
		}
		final char[] buf = new char[1024];
		int len;
		try {
			while ((len = replacingReader.read(buf)) > 0) {
				writer.write(buf, 0, len);
			}
		} catch (final IOException e) {
			throw new RuntimeException(e);
		} finally {
			JkUtilsIO.closeQuietly(writer);
			JkUtilsIO.closeQuietly(replacingReader);
		}
	}

	/**
	 * Copies the content of the specified url to the specified file. While coping token ${key}
	 * are replaced by the value found in the specified replacements map.
	 * @see #copyFileReplacingTokens(File, File, Map)
	 */
	public static void copyUrlReplacingTokens(URL url,  File toFile, Map<String, String> replacements, PrintStream reportStream) {
		final InputStream is;
		try {
			is = url.openStream();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
		copyStreamReplacingTokens(is, toFile, replacements, reportStream);
		JkUtilsIO.closeQuietly(is);
	}

	/**
	 * Copies the content of the specified input Stream to the specified file. While coping token ${key}
	 * are replaced by the value found in the specified replacements map.
	 * @see #copyFileReplacingTokens(File, File, Map)
	 */
	public static void copyStreamReplacingTokens(InputStream inputStream, File toFile, Map<String, String> replacements, PrintStream reportStream) {
		final TokenReplacingReader replacingReader = new TokenReplacingReader(new InputStreamReader(inputStream), replacements);
		if (!toFile.exists()) {
			try {
				toFile.createNewFile();
			} catch (final IOException e) {
				JkUtilsIO.closeQuietly(replacingReader);
				throw new RuntimeException(e);
			}
		}
		final Writer writer;
		try {
			writer = new FileWriter(toFile);
		} catch (final IOException e) {
			JkUtilsIO.closeQuietly(replacingReader);
			throw new RuntimeException(e);
		}
		final char[] buf = new char[1024];
		int len;
		try {
			while ((len = replacingReader.read(buf)) > 0) {
				writer.write(buf, 0, len);
			}
		} catch (final IOException e) {
			throw new RuntimeException(e);
		} finally {
			JkUtilsIO.closeQuietly(writer);
			JkUtilsIO.closeQuietly(replacingReader);
		}
	}


	private static class FilePath {

		public static FilePath of(File file) {
			final List<String> elements = new ArrayList<String>();
			for (File indexFile = file; indexFile != null; indexFile = indexFile.getParentFile()) {
				if (indexFile.getParent() == null) {
					elements.add(JkUtilsString.substringBeforeLast(indexFile.getPath(), File.separator));
				} else {
					elements.add(indexFile.getName());
				}
			}
			Collections.reverse(elements);
			return new FilePath(elements);
		}

		private final List<String> elements;

		private FilePath(List<String> elements) {
			super();
			this.elements = Collections.unmodifiableList(elements);
		}

		private FilePath common(FilePath other) {
			final List<String> result = new LinkedList<String>();
			for (int i = 0; i<elements.size(); i++) {
				if (i>= other.elements.size()) {
					break;
				}
				final String thisElement = this.elements.get(i);
				final String otherElement = other.elements.get(i);
				if (thisElement.equals(otherElement)) {
					result.add(thisElement);
				} else {
					break;
				}
			}
			return new FilePath(result);
		}

		public FilePath relativeTo(FilePath otherFolder) {
			final FilePath common = this.common(otherFolder);

			// this path is a sub past of the other
			if (common.equals(otherFolder)) {
				List<String> result = new ArrayList<String>(this.elements);
				result = result.subList(common.elements.size(), this.elements.size());
				return new FilePath(result);
			}


			final List<String> result = new ArrayList<String>();
			for (int i = common.elements.size(); i < otherFolder.elements.size(); i++) {
				result.add("..");
			}
			result.addAll(this.relativeTo(common).elements);
			return new FilePath(result);
		}

		@Override
		public String toString() {
			final StringBuilder builder = new StringBuilder();
			final Iterator<String> it = this.elements.iterator();
			while(it.hasNext()) {
				builder.append(it.next());
				if (it.hasNext()) {
					builder.append(File.separator);
				}
			}
			return builder.toString();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((elements == null) ? 0 : elements.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final FilePath other = (FilePath) obj;
			if (elements == null) {
				if (other.elements != null) {
					return false;
				}
			} else if (!elements.equals(other.elements)) {
				return false;
			}
			return true;
		}

	}


}
