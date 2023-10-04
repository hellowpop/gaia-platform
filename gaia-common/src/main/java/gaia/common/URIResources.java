package gaia.common;


import static gaia.common.FunctionAssist.supply;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import gaia.common.ResourceStreamModel.ClassPathResourceStream;
import gaia.common.URIHandleComponent.URIResourceAdapter;
import gaia.common.URIHandleSpec.URIContentType;
import gaia.common.URIHandleSpec.URIScanHandler;
import gaia.common.URIHandlers.FileURIHandler;


public interface URIResources {
	class ZipEntryResource
			extends URIResourceAdapter {
		final ZipFile zipFile;
		final ZipEntry zipEntry;

		public ZipEntryResource(final URIScanHandler handler,
		                        final URI uri,
		                        final ZipFile zipFile,
		                        final ZipEntry zipEntry) {
			super(handler,
			      supply(() -> {
				      final String path = uri.toString();
				      final int index = path.lastIndexOf("!/");

				      return URIAssist.uri(index < 0
				                           ? path.concat("!/")
				                                 .concat(zipEntry.getName())
				                           : path.substring(0,
				                                            index + 2)
				                                 .concat(zipEntry.getName()));
			      }));
			this.zipFile = zipFile;
			this.zipEntry = zipEntry;
		}

		@Override
		public URIContentType getType() {
			return URIContentType.stream;
		}

		@Override
		public InputStream openInputStream() throws IOException {
			return this.zipFile.getInputStream(this.zipEntry);
		}
	}

	class URLResource
			extends URIResourceAdapter {
		final URL url;

		public URLResource(URL url) {
			this(null,
			     url);
		}

		public URLResource(URIScanHandler handler,
		                   URL url) {
			super(handler,
			      url == null
			      ? null
			      : supply(url::toURI));
			this.url = url;

		}

		public URLResource(URI uri) {
			super(uri);
			this.url = supply(uri::toURL);

		}

		@Override
		public URIContentType getType() {
			return URIContentType.stream;
		}

		@Override
		public InputStream openInputStream() throws IOException {
			if (null != this.url) {
				return this.url.openStream();
			}
			throw new IOException("invalid file status");
		}
	}

	class FileResource
			extends URIResourceAdapter {
		final File file;

		public FileResource(final FileURIHandler handler,
		                    final File file) {
			super(handler,
			      file.toURI());
			this.file = file;
		}

		public FileResource(final FileURIHandler handler,
		                    final URI uri) {
			super(handler,
			      uri);
			this.file = new File(URIAssist.pathOf(uri));
		}

		@Override
		public boolean exists() {
			return this.file.exists();
		}

		@Override
		public URIContentType getType() {
			return URIContentType.stream;
		}

		@Override
		public InputStream openInputStream() throws IOException {
			FileAsserts.readable(this.file);
			if (this.file.exists() && this.file.canRead()) {
				return Files.newInputStream(this.file.toPath());
			}
			throw new IOException("invalid file status");
		}

		@Override
		public OutputStream openOutputStream() throws IOException {
			FileAsserts.writable(this.file);
			if (this.file.canWrite()) {
				return Files.newOutputStream(this.file.toPath());
			}
			throw new IOException("invalid file status");
		}
	}

	abstract class ClasspathResource
			extends URLResource {

		protected ClasspathResource(final URIScanHandler handler,
		                            final URI uri) {
			this(handler,
			     URIAssist.pathOf(uri));
		}

		protected ClasspathResource(final URIScanHandler handler,
		                            final String path) {
			super(handler,
			      supply(() -> {
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

		}
	}

	class JavaClassResource
			extends ClasspathResource {
		public JavaClassResource(URIScanHandler handler,
		                         Class<?> clazz) {
			super(handler,
			      clazz.getName()
			           .replace('.',
			                    '/')
			           .concat(".class"));
		}

		public JavaClassResource(URIScanHandler handler,
		                         String className) {
			super(handler,
			      className.replace('.',
			                        '/'));
		}
	}

	class JavaPathResource
			extends ClasspathResource {
		public JavaPathResource(URIScanHandler handler,
		                        URI uri) {
			super(handler,
			      uri);
		}

		public JavaPathResource(URIScanHandler handler,
		                        String path) {
			super(handler,
			      path.replace('\\',
			                   '/'));
		}
	}
}
