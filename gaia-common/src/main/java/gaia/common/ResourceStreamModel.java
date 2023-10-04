package gaia.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import lombok.Getter;

import gaia.common.CompressAssist.ArchiveElement;
import gaia.common.GWCommon.EntryNotFoundException;

public interface ResourceStreamModel {
	interface ResourceStream {
		boolean exists();

		@SuppressWarnings("SameReturnValue")
		URL getUrl();

		@SuppressWarnings("SameReturnValue")
		URI getUri();

		default long contentLength() {
			return content().length;
		}

		String getName();

		default String getDescription() {
			return Optional.ofNullable(getUri())
			               .map(Object::toString)
			               .orElseGet(this::getName);
		}

		default byte[] content() {
			try (InputStream in = this.openInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				StreamAssist.copy(in,
				                  out,
				                  -1);

				return out.toByteArray();
			}
			catch (Throwable throwable) {
				throw ExceptionAssist.safe(throwable);
			}
		}

		boolean isReadable();

		InputStream openInputStream() throws IOException;

		default boolean isWritable() {
			return false;
		}

		default OutputStream openOutputStream() throws IOException {
			throw new UnsupportedOperationException("output stream not supported!");
		}
	}

	abstract class LocationResourceStream
			implements ResourceStream {
		@Getter
		final URL url;
		@Getter
		final URI uri;

		public LocationResourceStream(final URL url) {
			this.url = url;
			this.uri = url == null
			           ? null
			           : FunctionAssist.supply(url::toURI,
			                                   throwable -> null);
		}

		public LocationResourceStream(final URI uri) {
			this.uri = uri;
			this.url = uri == null
			           ? null
			           : FunctionAssist.supply(uri::toURL,
			                                   throwable -> null);
		}

		@Override
		public boolean exists() {
			return null != this.url && null != this.uri;
		}

		@Override
		public String toString() {
			return StringAssist.format("%s(%s)",
			                           this.getClass()
			                               .getSimpleName(),
			                           this.uri.toString());
		}
	}

	class URIResourceStream
			extends LocationResourceStream {
		public URIResourceStream(URI uri) {
			super(uri);
		}

		@Override
		public boolean exists() {
			return false;
		}

		@Override
		public String getName() {
			return null;
		}

		@Override
		public boolean isReadable() {
			return false;
		}

		@Override
		public InputStream openInputStream() {
			return null;
		}
	}

	class URLResourceStream
			extends LocationResourceStream {
		public URLResourceStream(URL url) {
			super(url);
		}

		@Override
		public boolean exists() {
			return true;
		}

		@Override
		public String getName() {
			return this.url.getFile();
		}

		@Override
		public boolean isReadable() {
			return true;
		}

		@Override
		public InputStream openInputStream() {
			return FunctionAssist.supply(this.url::openStream);
		}
	}

	abstract class ClassPathResourceStream
			extends LocationResourceStream {
		final String path;
		@Getter
		final String name;

		protected ClassPathResourceStream(final String path) {
			this(path,
			     FunctionAssist.supply(() -> {
				     final int index = path.lastIndexOf('/');
				     return index < 0
				            ? path
				            : path.substring(index + 1);
			     }));
		}

		protected ClassPathResourceStream(final String path,
		                                  final String name) {
			super(FunctionAssist.supply(() -> {
				// target class loader
				URL stream = Thread.currentThread()
				                   .getContextClassLoader()
				                   .getResource(path);

				if (stream != null) {
					return stream;
				}
				//second context loader
				stream = ClassPathResourceStream.class.getClassLoader()
				                                      .getResource(path);
				if (stream != null) {
					return stream;
				}

				return ClassLoader.getSystemResource(path);
			}));
			this.path = path.replace('\\',
			                         '/');
			this.name = null == name
			            ? FunctionAssist.supply(() -> {
				final int index = this.path.lastIndexOf('/');
				return index < 0
				       ? this.path
				       : this.path.substring(index + 1);
			})
			            : name;
		}

		@Override
		public boolean exists() {
			return null != this.url;
		}

		@Override
		public boolean isReadable() {
			return this.exists();
		}

		@Override
		public InputStream openInputStream() throws IOException {
			if (!exists()) {
				throw new EntryNotFoundException(StringAssist.format("[%s] cannot find",
				                                                     this.path),
				                                 "classpath resource context");
			}
			return this.url.openStream();
		}
	}

	class JavaClassResourceStream
			extends ClassPathResourceStream {
		final Class<?> clazz;

		public JavaClassResourceStream(Class<?> clazz) {
			super(clazz.getName()
			           .replace('.',
			                    '/')
			           .concat(".class"),
			      clazz.getSimpleName());
			this.clazz = clazz;
		}
	}

	class JavaResourcePathStream
			extends ClassPathResourceStream {
		public JavaResourcePathStream(String path) {
			super(path);
		}
	}

	class JavaFileResourceStream
			extends LocationResourceStream {
		final File file;

		public JavaFileResourceStream(File file) {
			super(file.toURI());
			this.file = file;
		}

		@Override
		public boolean exists() {
			return this.file.exists();
		}

		@Override
		public String getName() {
			return this.file.getName();
		}

		@Override
		public long contentLength() {
			return this.file.length();
		}

		@Override
		public byte[] content() {
			return FunctionAssist.supply(() -> FileAssist.read(FileAsserts.readable(this.file)));
		}

		@Override
		public boolean isReadable() {
			return this.file.canRead();
		}

		@Override
		public boolean isWritable() {
			return this.file.canWrite();
		}

		@Override
		public OutputStream openOutputStream() throws IOException {
			return new FileOutputStream(this.file);
		}

		@Override
		public InputStream openInputStream() throws IOException {
			return new FileInputStream(this.file);
		}
	}

	class ZipEntryResourceStream
			implements ResourceStream {
		final ZipFile zipFile;
		final ZipEntry zipEntry;
		final String description;

		public ZipEntryResourceStream(ZipFile zipFile,
		                              ZipEntry zipEntry,
		                              String description) {
			this.zipFile = zipFile;
			this.zipEntry = zipEntry;
			this.description = description;
		}

		@Override
		public boolean exists() {
			return true;
		}

		@Override
		public URL getUrl() {
			return null;
		}

		@Override
		public URI getUri() {
			return null;
		}

		@Override
		public String getName() {
			return this.zipEntry.getName();
		}

		@Override
		public boolean isReadable() {
			return true;
		}

		@Override
		public InputStream openInputStream() throws IOException {
			return this.zipFile.getInputStream(this.zipEntry);
		}

		@Override
		public String toString() {
			return StringAssist.format("%s[%s@%s]",
			                           this.getClass()
			                               .getSimpleName(),
			                           this.zipEntry.getName(),
			                           this.description);
		}
	}

	class ArchiveElementResourceStream
			implements ResourceStream {
		static final URI uri = FunctionAssist.supply(() -> new URI("archive:prebuild"));
		final ArchiveElement<?, ?> element;

		public ArchiveElementResourceStream(ArchiveElement<?, ?> element) {
			this.element = element;
		}

		@Override
		public long contentLength() {
			return this.element.size();
		}

		@Override
		public boolean exists() {
			return true;
		}

		@Override
		public String getName() {
			return this.element.name();
		}

		@Override
		public byte[] content() {
			return this.element.content();
		}

		@Override
		public boolean isReadable() {
			return true;
		}

		@Override
		public InputStream openInputStream() throws IOException {
			return this.element.openStream();
		}

		@Override
		public URL getUrl() {
			return null;
		}

		@Override
		public URI getUri() {
			return uri;
		}
	}

	class NamedStreamResource
			implements ResourceStream {
		static final URI default_uri = FunctionAssist.supply(() -> new URI("bytearray:prebuild"));

		final URI uri;
		final byte[] content;

		public NamedStreamResource(byte[] content) {
			this.uri = default_uri;
			this.content = content;
		}

		public NamedStreamResource(String name,
		                           InputStream stream) {
			this.uri = FunctionAssist.supply(() -> new URI("bytearray:".concat(name)));
			this.content = FunctionAssist.supply(() -> StreamAssist.read(stream));
		}

		@Override
		public boolean exists() {
			return true;
		}

		@Override
		public String getName() {
			return "bytearray";
		}

		@Override
		public byte[] content() {
			return this.content;
		}

		@Override
		public boolean isReadable() {
			return true;
		}

		@Override
		public InputStream openInputStream() {
			return new ByteArrayInputStream(this.content);
		}

		@Override
		public URL getUrl() {
			return null;
		}

		@Override
		public URI getUri() {
			return default_uri;
		}
	}
}
