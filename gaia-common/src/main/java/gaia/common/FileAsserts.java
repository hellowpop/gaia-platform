package gaia.common;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.StringUtils;

import gaia.ConstValues.CrashedException;
import gaia.common.FunctionAssist.ErrorHandleConsumer;
import gaia.common.FunctionAssist.ErrorHandleFunction;


public interface FileAsserts {
	class FileAsserter {
		final AtomicReference<File> ref = new AtomicReference<>();

		FileAsserter(File file) {
			this.ref.set(file);
		}

		/**
		 * change assert target file instance use function
		 *
		 * @param function file transfer function
		 * @return asserter self
		 */
		public FileAsserter map(ErrorHandleFunction<File, File> function) {
			this.ref.set(FunctionAssist.function(this.ref.get(),
			                                     function));
			return this;
		}

		/**
		 * do something with assert target file instance
		 *
		 * @param consumer file consumer
		 * @return asserter self
		 */
		public FileAsserter execute(ErrorHandleConsumer<File> consumer) {
			FunctionAssist.consume(this.ref.get(),
			                       consumer);
			return this;
		}

		/**
		 * change assert target file instance use other file instance
		 *
		 * @param other file instance
		 * @return asserter self
		 */
		public FileAsserter map(File other) {
			this.ref.set(other);
			return this;
		}

		/**
		 * switch to parent file
		 *
		 * @return asserter self
		 */
		public FileAsserter toParent() {
			this.ref.set(this.ref.get()
			                     .getParentFile());
			return this;
		}

		/**
		 * check readable
		 *
		 * @return asserter self
		 */
		public FileAsserter readable() {
			FileAsserts.readable(this.ref.get());
			return this;
		}

		/**
		 * check writable
		 *
		 * @return asserter self
		 */
		public FileAsserter writable() {
			FileAsserts.writable(this.ref.get());
			return this;
		}

		/**
		 * check directory structure
		 *
		 * @return asserter self
		 */
		public FileAsserter validateDirectory() {
			FileAsserts.validateDirectory(this.ref.get());
			return this;
		}

		/**
		 * check exist
		 *
		 * @return asserter self
		 */
		public FileAsserter exists() {
			FileAsserts.exists(this.ref.get());
			return this;
		}

		/**
		 * check file instance
		 *
		 * @return asserter self
		 */
		public FileAsserter mustFile() {
			FileAsserts.mustFile(this.ref.get());
			return this;
		}

	}

	static FileAsserter of(final File file) {
		return new FileAsserter(file);
	}

	static FileAsserter of(final String filename) {
		return new FileAsserter(new File(filename));
	}

	static File exists(String path) {
		final File file = new File(path);

		return exists(file);
	}

	static File exists(File file) {
		if (!file.exists()) {
			throw new ExistViolate(StringAssist.format("[%s] is not exist!",
			                                           file.getPath()));
		}
		return file;
	}

	static File existDirectory(String path) {
		final File file = new File(path);

		return existDirectory(file);
	}

	static File existDirectory(File file) {
		exists(file);

		if (!file.isDirectory())
			throw new ExistViolate(StringAssist.format("[%s] is not a directory",
			                                           file.getPath()));

		return file;
	}

	File[] EMPTY_FILES = new File[]{};

	static File[] listFiles(String path) {
		final File file = new File(path);

		return listFiles(file);
	}

	static File[] listFiles(File file) {
		exists(file);

		if (!file.isDirectory())
			throw new FileInstanceViolate(StringAssist.format("[%s] is not a directory",
			                                                  file.getPath()));

		return Optional.ofNullable(file.listFiles())
		               .orElse(EMPTY_FILES);
	}

	@SuppressWarnings("UnusedReturnValue")
	static File mustFile(File file) {
		if (file.exists() && file.isDirectory()) {
			throw new DirectoryInstanceViolate(StringAssist.format("[%s] is directory!",
			                                                       file.getPath()));
		}
		return file;
	}

	static File readable(String filename) {
		return readable(new File(filename));
	}

	static File readable(File file) {
		// check exist
		exists(file);

		// check file
		mustFile(file);

		// check writable
		if (!file.canRead()) {
			throw new ReadableViolate(StringAssist.format("[%s] is not readable state!",
			                                              file.getPath()));
		}
		return file;
	}

	static File writable(String filename) {
		return writable(new File(filename));
	}

	static File writable(File file) {
		// check path
		validateDirectory(file.getParentFile());
		// check file
		mustFile(file);
		// check writable
		if (file.exists() && !file.canWrite()) {
			throw new WritableViolate(StringAssist.format("[%s] is not writable state!",
			                                              file.getPath()));
		}
		return file;
	}

	static File validateDirectory(String fileName) {
		if (StringUtils.isEmpty(fileName)) {
			throw new CrashedException("file name must define");
		}
		return validateDirectory(new File(fileName));
	}

	static File validateDirectory(File file) {
		if (file.exists() && file.isFile()) {
			throw new FileInstanceViolate(StringAssist.format("[%s] is file instance",
			                                                  file.getPath()));
		}
		if (!file.exists() && !file.mkdirs()) {
			throw new DirectoryCreateViolate(StringAssist.format("[%s] directory make fail",
			                                                     file.getPath()));
		}

		return file;
	}

	// ---------------------------------------------------------------------
	// Section exception define
	// ---------------------------------------------------------------------
	abstract class FileAssertException
			extends
			RuntimeException {
		private static final long serialVersionUID = -1325372637163368662L;

		FileAssertException(String message) {
			super(message);
		}
	}

	class ReadableViolate
			extends
			FileAssertException {
		private static final long serialVersionUID = 9211124808321736165L;

		ReadableViolate(String message) {
			super(message);
		}
	}

	class WritableViolate
			extends
			FileAssertException {
		private static final long serialVersionUID = 3869122916259819522L;

		WritableViolate(String message) {
			super(message);
		}
	}

	class DirectoryCreateViolate
			extends
			FileAssertException {
		private static final long serialVersionUID = 7913770588278961815L;

		DirectoryCreateViolate(String message) {
			super(message);
		}
	}

	class DirectoryInstanceViolate
			extends
			FileAssertException {
		private static final long serialVersionUID = -6346789682947420997L;

		DirectoryInstanceViolate(String message) {
			super(message);
		}
	}

	class FileInstanceViolate
			extends
			FileAssertException {
		private static final long serialVersionUID = -906957205610133012L;

		FileInstanceViolate(String message) {
			super(message);
		}
	}

	class ExistViolate
			extends
			FileAssertException {
		private static final long serialVersionUID = 7992223204409727942L;

		ExistViolate(String message) {
			super(message);
		}
	}
}
