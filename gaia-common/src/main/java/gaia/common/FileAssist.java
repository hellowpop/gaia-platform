package gaia.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gaia.common.CharsetSpec.CharSets;
import gaia.common.FunctionAssist.ErrorHandleConsumer;


public interface FileAssist {
	Logger log = LoggerFactory.getLogger(FileAssist.class);

	Comparator<File> filenameComparator = Comparator.comparing(File::getName);
	File TEMP = FunctionAssist.supply(() -> {
		final File file = new File(GWEnvironment.getProperty("java.io.tmpdir"));

		if (!file.exists()) {
			log.error(StringAssist.format("[%s] is not exists so create result is [%s]",
			                              file.getAbsolutePath(),
			                              file.mkdirs()));
		}

		return file;
	});

	static File createTemporal() {
		return new File(TEMP,
		                RandomAssist.getUUID());
	}

	/**
	 * @param name
	 * @param create
	 * @return
	 */
	static File getTempDirectory(String name,
	                             boolean create) {
		final File targetDirectory = new File(TEMP,
		                                      name);

		if (targetDirectory.exists()) {
			if (targetDirectory.isFile()) {
				throw new IllegalStateException(StringAssist.format("%s is already exist. but file instance!",
				                                                    name));
			}
			return targetDirectory;
		}

		if (create) {
			if (targetDirectory.mkdir()) {
				return targetDirectory;
			}
			throw new Error(StringAssist.format("[%s] make temporal directory fail!",
			                                    name));
		}

		throw new Error(StringAssist.format("[%s] create fail!",
		                                    name));
	}

	/**
	 * @param file
	 * @return
	 */
	static String getFileSize(File file) {
		long size = file.length();
		if (size < 1024) {
			return size + "Byte";
		} else if (size / 1024 < 1024) {
			return size / 1024 + "KB";
		} else if (size / 1024 / 1024 < 1024) {
			return size / 1024 / 1024 + "MB";
		} else {
			return size + "Byte";
		}
	}

	// ---------------------------------------------------------------------
	// Section copy
	// ---------------------------------------------------------------------
	static void copy(final File source,
	                 final File target) throws IOException {
		FileAsserts.readable(source);
		FileAsserts.writable(target);
		Files.copy(source.toPath(),
		           target.toPath());
	}

	static long copy(final RandomAccessFile from,
	                 final RandomAccessFile to) {
		return StreamAssist.copy(from.getChannel(),
		                         to.getChannel());
	}

	// ---------------------------------------------------------------------
	// Section iterate / navigate directory
	// ---------------------------------------------------------------------
	class InnerIterateTool {
		private InnerIterateTool() {
		}

		void iterateDirectory(final File root,
		                      final Consumer<File> consumer) {
			Optional.ofNullable(root.listFiles())
			        .ifPresent(files -> {
				        for (File file : files) {
					        if (file.isFile()) {
						        consumer.accept(file);
					        } else if (file.isDirectory()) {
						        this.iterateDirectory(file,
						                              consumer);
					        }
				        }
			        });
		}
	}

	InnerIterateTool iterateTool = new InnerIterateTool();

	static void iterate(final File root,
	                    final Consumer<File> consumer) {
		if (root.isFile()) {
			consumer.accept(root);
		}

		if (root.isDirectory()) {
			iterateTool.iterateDirectory(root,
			                             consumer);
		}
	}

	// ---------------------------------------------------------------------
	// Section read
	// ---------------------------------------------------------------------
	static String read(final Path path,
	                   final CharSets cs) throws IOException {
		return cs.toString(read(path));
	}

	static byte[] read(final Path path) throws IOException {
		return Files.readAllBytes(path);
	}

	static String read(final String path,
	                   final CharSets cs) {
		return FunctionAssist.supply(() -> cs.toString(Files.readAllBytes(new File(path).toPath())));
	}

	static byte[] read(final File file) throws IOException {
		return Files.readAllBytes(FileAsserts.readable(file)
		                                     .toPath());
	}

	static String read(File file,
	                   CharSets cs) throws IOException {
		return cs.toString(read(file));
	}

	static String readSafe(File file,
	                       CharSets cs) {
		try {
			return read(file,
			            cs);
		}
		catch (IOException ioe) {
			throw new RuntimeException(StringAssist.format("[%s] read error",
			                                               file.getAbsolutePath()),
			                           ioe);
		}
	}

	static String read(File file,
	                   String cs) throws IOException {
		return read(file,
		            CharSets.of(cs));
	}

	static <T> T read(String fileName,
	                  Function<FileInputStream, T> consumer) {
		return read(new File(fileName),
		            consumer);
	}

	static <T> T read(File file,
	                  Function<FileInputStream, T> consumer) {
		FileInputStream stream = null;
		try {
			stream = new FileInputStream(file);
			return consumer.apply(stream);
		}
		catch (Exception exception) {
			throw ExceptionAssist.wrap("",
			                           exception);
		}
		finally {
			ObjectFinalizer.close(stream);
		}
	}
	// ---------------------------------------------------------------------
	// Section write
	// ---------------------------------------------------------------------

	static void write(String fileName,
	                  ErrorHandleConsumer<FileOutputStream> consumer) {
		write(new File(fileName),
		      consumer);
	}

	static void write(File file,
	                  ErrorHandleConsumer<FileOutputStream> consumer) {
		FileOutputStream stream = null;
		try {
			stream = FunctionAssist.supply(() -> new FileOutputStream(file));
			FunctionAssist.consume(stream,
			                       consumer);
		}
		finally {
			ObjectFinalizer.close(stream);
		}
	}

	static void write(final String target,
	                  final String source,
	                  final CharSets charset) {
		write(new File(target),
		      charset.getBytes(source));
	}

	/**
	 * @param target
	 * @param source
	 */
	static void write(final File target,
	                  final byte[] source) {
		FunctionAssist.execute(() -> Files.write(target.toPath(),
		                                         source));
	}

	/**
	 * @param file
	 * @param message
	 * @param cs
	 */
	static void write(File file,
	                  String message,
	                  CharSets cs) {
		write(file,
		      cs.getBytes(message));
	}

	// ---------------------------------------------------------------------
	// Section zip packing
	// ---------------------------------------------------------------------
	static void pack(String sourceDirPath,
	                 String zipFilePath) throws IOException {
		pack(FileAsserts.exists(new File(sourceDirPath)),
		     FileAsserts.writable(zipFilePath));
	}

	static void pack(File source,
	                 File target) throws IOException {
		pack(source.toPath(),
		     target.toPath());
	}

	static void pack(Path source,
	                 Path zip) throws IOException {
		pack(source,
		     Files.newOutputStream(zip));
	}

	static void pack(Path source,
	                 OutputStream out) throws IOException {
		try (ZipOutputStream zs = new ZipOutputStream(out)) {
			try (Stream<Path> paths = Files.walk(source)) {
				paths.filter(path -> !Files.isDirectory(path))
				     .forEach(path -> {
					     ZipEntry zipEntry = new ZipEntry(source.relativize(path)
					                                            .toString());
					     try {
						     zs.putNextEntry(zipEntry);
						     Files.copy(path,
						                zs);
						     zs.closeEntry();
					     }
					     catch (IOException e) {
						     log.error("pack archive error",
						               e);
					     }
				     });
			}
		}
	}

	static void unpack(String sourceFilePath,
	                   String unzipDirPath) {
		unpack(FileAsserts.readable(sourceFilePath),
		       FileAsserts.validateDirectory(unzipDirPath));
	}

	static void unpack(File source,
	                   File target) {
		unpack(source.toPath(),
		       target.toPath());
	}

	static void unpack(Path sourceZip,
	                   Path targetDir) {
		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(sourceZip.toFile()))) {
			// list files in zip
			ZipEntry zipEntry = zis.getNextEntry();
			while (zipEntry != null) {

				boolean isDirectory = zipEntry.getName()
				                              .endsWith(File.separator);

				Path newPath = zipSlipProtect(zipEntry,
				                              targetDir);
				if (isDirectory) {
					Files.createDirectories(newPath);
				} else {
					if (newPath.getParent() != null) {
						if (Files.notExists(newPath.getParent())) {
							Files.createDirectories(newPath.getParent());
						}
					}
					// copy files
					Files.copy(zis,
					           newPath,
					           StandardCopyOption.REPLACE_EXISTING);
				}

				zipEntry = zis.getNextEntry();
			}
			zis.closeEntry();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	static Path zipSlipProtect(ZipEntry zipEntry,
	                           Path targetDir) throws IOException {

		// test zip slip vulnerability
		Path targetDirResolved = targetDir.resolve(zipEntry.getName());

		// make sure normalized file still has targetDir as its prefix
		// else throws exception
		Path normalizePath = targetDirResolved.normalize();
		if (!normalizePath.startsWith(targetDir)) {
			throw new IOException("Bad zip entry: " + zipEntry.getName());
		}
		return normalizePath;
	}

	int NOT_FOUND = -1;

	String EMPTY_STRING = "";

	char EXTENSION_SEPARATOR = '.';

	char SYSTEM_SEPARATOR = File.separatorChar;

	/**
	 * The Unix separator character.
	 */
	char UNIX_SEPARATOR = '/';

	/**
	 * The Windows separator character.
	 */
	char WINDOWS_SEPARATOR = '\\';

	char OTHER_SEPARATOR = FunctionAssist.supply(() -> File.separatorChar == WINDOWS_SEPARATOR
	                                                   ? UNIX_SEPARATOR
	                                                   : WINDOWS_SEPARATOR);

	static String getFileName(final String path) {
		if (StringUtils.isEmpty(path)) {
			return EMPTY_STRING;
		}

		final String regulated = path.replace('\\',
		                                      '/');

		final int index = regulated.lastIndexOf('/');

		return index < 0
		       ? regulated
		       : regulated.substring(index + 1);

	}

	static String getExtension(final String fileName) throws IllegalArgumentException {
		if (fileName == null) {
			return EMPTY_STRING;
		}
		final int index = indexOfExtension(fileName);
		if (index == NOT_FOUND) {
			return EMPTY_STRING;
		}
		return fileName.substring(index + 1);
	}

	static int indexOfExtension(final String fileName) throws IllegalArgumentException {
		if (fileName == null) {
			return NOT_FOUND;
		}
		if (isSystemWindows()) {
			// Special handling for NTFS ADS: Don't accept colon in the fileName.
			final int offset = fileName.indexOf(':',
			                                    getAdsCriticalOffset(fileName));
			if (offset != -1) {
				throw new IllegalArgumentException("NTFS ADS separator (':') in file name is forbidden.");
			}
		}
		final int extensionPos = fileName.lastIndexOf(EXTENSION_SEPARATOR);
		final int lastSeparator = indexOfLastSeparator(fileName);
		return lastSeparator > extensionPos
		       ? NOT_FOUND
		       : extensionPos;
	}

	static boolean isSystemWindows() {
		return SYSTEM_SEPARATOR == WINDOWS_SEPARATOR;
	}

	static int getAdsCriticalOffset(final String fileName) {
		// Step 1: Remove leading path segments.
		final int offset1 = fileName.lastIndexOf(SYSTEM_SEPARATOR);
		final int offset2 = fileName.lastIndexOf(OTHER_SEPARATOR);
		if (offset1 == -1) {
			if (offset2 == -1) {
				return 0;
			}
			return offset2 + 1;
		}
		if (offset2 == -1) {
			return offset1 + 1;
		}
		return Math.max(offset1,
		                offset2) + 1;
	}

	static int indexOfLastSeparator(final String fileName) {
		if (fileName == null) {
			return NOT_FOUND;
		}
		final int lastUnixPos = fileName.lastIndexOf(UNIX_SEPARATOR);
		final int lastWindowsPos = fileName.lastIndexOf(WINDOWS_SEPARATOR);
		return Math.max(lastUnixPos,
		                lastWindowsPos);
	}

	static boolean compareByMemoryMappedFiles(final File source,
	                                          final File target) throws IOException {
		if (source.length() != target.length()) {
			return false;
		}

		final byte[] buffer1 = new byte[8096];
		final byte[] buffer2 = new byte[8096];

		try (FileInputStream in1 = new FileInputStream(source); FileInputStream in2 = new FileInputStream(target)) {
			while (true) {
				int read1 = in1.read(buffer1);
				int read2 = in2.read(buffer2);

				if (read1 != read2) {
					return false;
				}

				if (read1 < 0) {
					return true;
				}

				if (!ArrayAssist.compare(buffer1,
				                         buffer2,
				                         read1)) {
					return false;
				}
			}
		}
	}

	static String normalizePath(final File file) {
		return file.getAbsolutePath()
		           .replace('\\',
		                    '/');
	}
}
