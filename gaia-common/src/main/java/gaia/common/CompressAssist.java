package gaia.common;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.deflate.DeflateCompressorInputStream;
import org.apache.commons.compress.compressors.deflate.DeflateCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import lombok.Getter;

import gaia.common.ConcurrentAssist.SuppliedReference;

public interface CompressAssist {
	Logger log = LoggerFactory.getLogger(CompressAssist.class);

	interface CompressHandler {
		boolean isCompressed(byte[] source);

		byte[] decompress(byte[] compressed) throws IOException;

		byte[] compress(byte[] raw) throws IOException;
	}

	enum CompressHandlers
			implements CompressHandler {
		gzip {
			@Override
			public boolean isCompressed(byte[] source) {
				return GzipCompressorInputStream.matches(source,
				                                         source == null
				                                         ? 0
				                                         : source.length);
			}

			@Override
			InputStream inputStream(byte[] compressed) throws IOException {
				return new GzipCompressorInputStream(new BufferedInputStream(new ByteArrayInputStream(compressed)));
			}

			@Override
			OutputStream outputStream(OutputStream source) throws IOException {
				return new GzipCompressorOutputStream(source);
			}
		},
		bzip2 {
			@Override
			public boolean isCompressed(byte[] source) {
				return BZip2CompressorInputStream.matches(source,
				                                          source == null
				                                          ? 0
				                                          : source.length);
			}

			@Override
			InputStream inputStream(byte[] compressed) throws IOException {
				return new BZip2CompressorInputStream(new BufferedInputStream(new ByteArrayInputStream(compressed)));
			}

			@Override
			OutputStream outputStream(OutputStream source) throws IOException {
				return new BZip2CompressorOutputStream(source);
			}
		},
		deflate {
			@Override
			public boolean isCompressed(byte[] source) {
				return DeflateCompressorInputStream.matches(source,
				                                            source == null
				                                            ? 0
				                                            : source.length);
			}

			@Override
			InputStream inputStream(byte[] compressed) {
				return new DeflateCompressorInputStream(new BufferedInputStream(new ByteArrayInputStream(compressed)));
			}

			@Override
			OutputStream outputStream(OutputStream source) throws IOException {
				return new DeflateCompressorOutputStream(source);
			}
		},
		direct {
			@Override
			public boolean isCompressed(byte[] source) {
				return true;
			}

			@Override
			public byte[] decompress(byte[] compressed) {
				return compressed;
			}

			@Override
			public byte[] compress(byte[] raw) {
				return raw;
			}
		};

		InputStream inputStream(byte[] compressed) throws IOException {
			throw new UnsupportedOperationException("");
		}

		OutputStream outputStream(OutputStream source) throws IOException {
			throw new UnsupportedOperationException("");
		}

		@Override
		public byte[] decompress(byte[] compressed) throws IOException {
			if (!this.isCompressed(compressed)) {
				throw new IllegalArgumentException("unknown compress type");
			}
			ByteArrayOutputStream outStream = new ByteArrayOutputStream();

			InputStream gzipInStream = this.inputStream(compressed);


			StreamAssist.copy(gzipInStream,
			                  outStream);

			outStream.flush();
			outStream.close();

			return outStream.toByteArray();
		}

		@Override
		public byte[] compress(byte[] raw) throws IOException {
			ByteArrayOutputStream obj = new ByteArrayOutputStream();
			OutputStream gzip = this.outputStream(obj);
			gzip.write(raw);
			gzip.flush();
			gzip.close();
			return obj.toByteArray();
		}
	}

	static boolean isCompressed(final byte[] compressed) {
		return (compressed[0] == (byte) (GZIPInputStream.GZIP_MAGIC)) && (compressed[1] == (byte) (GZIPInputStream.GZIP_MAGIC >> 8));
	}

	abstract class ArchiveElement<O extends Closeable, F> {
		final O stream;
		@Getter
		final F entry;
		final SuppliedReference<byte[]> contentReference;

		ArchiveElement(O stream,
		               F entry) {
			this.stream = stream;
			this.entry = entry;
			this.contentReference = new SuppliedReference<>(() -> {
				try (InputStream in = this.openStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
					StreamAssist.copy(in,
					                  out,
					                  -1);

					return out.toByteArray();
				}
				catch (Throwable throwable) {
					throw ExceptionAssist.safe(throwable);
				}
			});
		}

		public void copy(OutputStream out) throws IOException {
			out.write(content());
		}

		public final byte[] content() {
			return this.contentReference.supplied();
		}

		public abstract InputStream openStream() throws IOException;

		public abstract String name();

		public abstract long size();

		public abstract boolean directory();
	}

	interface ArchiveHandler {
		default void stream(InputStream in,
		                    Consumer<Stream<ArchiveElement<?, ?>>> consumer) throws IOException {
			this.stream(in,
			            iterator -> true,
			            consumer);
		}

		void stream(InputStream in,
		            Predicate<ArchiveEntryIterator<?, ?>> checker,
		            Consumer<Stream<ArchiveElement<?, ?>>> consumer) throws IOException;

		default void stream(File file,
		                    Consumer<Stream<ArchiveElement<?, ?>>> consumer) throws IOException {
			this.stream(file,
			            iterator -> true,
			            consumer);
		}

		void stream(File file,
		            Predicate<ArchiveEntryIterator<?, ?>> checker,
		            Consumer<Stream<ArchiveElement<?, ?>>> consumer) throws IOException;

		static boolean isArchive(final String name) {
			final String ext = FileAssist.getExtension(name);

			switch (ext) {
				case "zip":
				case "jar":
				case "tar": {
					return true;
				}

				default: {
					return false;
				}
			}
		}
	}

	abstract class ArchiveEntryIterator<O extends Closeable, F>
			implements Iterator<ArchiveElement<O, F>>,
			           Closeable {
		final O stream;
		final Enumeration<F> enumeration;

		ArchiveEntryIterator(O stream) {
			this.stream = stream;
			this.enumeration = enumeration(stream);
		}

		protected abstract Enumeration<F> enumeration(O stream);

		public abstract boolean hasEntry(String name);

		@Override
		public boolean hasNext() {
			return enumeration.hasMoreElements();
		}

		@Override
		public void close() {
			ObjectFinalizer.close(this.stream);
		}
	}

	class ZipStreamIterator
			extends ArchiveEntryIterator<ZipFile, ZipArchiveEntry> {

		ZipStreamIterator(InputStream in) throws IOException {
			super(new ZipFile(new SeekableInMemoryByteChannel(IOUtils.toByteArray(in))));
		}

		@Override
		protected Enumeration<ZipArchiveEntry> enumeration(ZipFile stream) {
			return this.stream.getEntries();
		}

		@Override
		public ArchiveElement<ZipFile, ZipArchiveEntry> next() {
			return new ZipArchiveElement(this.stream,
			                             this.enumeration.nextElement());
		}

		@Override
		public boolean hasEntry(String name) {
			return this.stream.getEntry(name) != null;
		}
	}

	class ZipArchiveElement
			extends ArchiveElement<ZipFile, ZipArchiveEntry> {
		ZipArchiveElement(ZipFile stream,
		                  ZipArchiveEntry entry) {
			super(stream,
			      entry);
		}

		@Override
		public InputStream openStream() throws IOException {
			return this.stream.getInputStream(this.entry);
		}

		@Override
		public String name() {
			return this.entry.getName();
		}

		@Override
		public long size() {
			return this.entry.getSize();
		}

		@Override
		public boolean directory() {
			return this.entry.isDirectory();
		}
	}

	class ZipFileIterator
			extends ArchiveEntryIterator<java.util.zip.ZipFile, java.util.zip.ZipEntry> {

		ZipFileIterator(File file) throws IOException {
			super(new java.util.zip.ZipFile(file));
		}

		@Override
		@SuppressWarnings("unchecked")
		protected Enumeration<java.util.zip.ZipEntry> enumeration(java.util.zip.ZipFile stream) {
			return (Enumeration<java.util.zip.ZipEntry>) this.stream.entries();
		}

		@Override
		public ArchiveElement<java.util.zip.ZipFile, java.util.zip.ZipEntry> next() {
			return new ZipFileArchiveElement(this.stream,
			                                 this.enumeration.nextElement());
		}

		@Override
		public boolean hasEntry(String name) {
			return this.stream.getEntry(name) != null;
		}
	}

	class ZipFileArchiveElement
			extends ArchiveElement<java.util.zip.ZipFile, java.util.zip.ZipEntry> {
		ZipFileArchiveElement(java.util.zip.ZipFile stream,
		                      java.util.zip.ZipEntry entry) {
			super(stream,
			      entry);
		}

		@Override
		public InputStream openStream() throws IOException {
			return this.stream.getInputStream(this.entry);
		}

		@Override
		public String name() {
			return this.entry.getName();
		}

		@Override
		public long size() {
			return this.entry.getSize();
		}

		@Override
		public boolean directory() {
			return this.entry.isDirectory();
		}
	}

	class TarArchiveEntryIterator
			extends ArchiveEntryIterator<TarFile, TarArchiveEntry> {
		TarArchiveEntryIterator(InputStream in) throws IOException {
			super(new TarFile(new SeekableInMemoryByteChannel(IOUtils.toByteArray(in))));
		}

		TarArchiveEntryIterator(File file) throws IOException {
			super(new TarFile(file));
		}

		@Override
		protected Enumeration<TarArchiveEntry> enumeration(TarFile stream) {
			return Collections.enumeration(this.stream.getEntries());
		}

		@Override
		public ArchiveElement<TarFile, TarArchiveEntry> next() {
			return new TarArchiveElement(this.stream,
			                             this.enumeration.nextElement());
		}

		@Override
		public boolean hasEntry(String name) {
			return this.stream.getEntries()
			                  .stream()
			                  .anyMatch(entry -> StringUtils.equals(entry.getName(),
			                                                        name));
		}
	}

	class TarArchiveElement
			extends ArchiveElement<TarFile, TarArchiveEntry> {
		TarArchiveElement(TarFile stream,
		                  TarArchiveEntry entry) {
			super(stream,
			      entry);
		}

		@Override
		public InputStream openStream() throws IOException {
			return this.stream.getInputStream(this.entry);
		}

		@Override
		public String name() {
			return this.entry.getName();
		}

		@Override
		public long size() {
			return this.entry.getSize();
		}

		@Override
		public boolean directory() {
			return this.entry.isDirectory();
		}
	}

	enum ArchiveHandlers
			implements ArchiveHandler {
		zip {
			@Override
			ArchiveEntryIterator<?, ?> openIterator(InputStream in) throws IOException {
				return new ZipStreamIterator(in);
			}

			@Override
			ArchiveEntryIterator<?, ?> openIterator(File file) throws IOException {
				return new ZipFileIterator(file);
			}
		},
		jar {
			@Override
			ArchiveEntryIterator<?, ?> openIterator(InputStream in) throws IOException {
				return new ZipStreamIterator(in);
			}

			@Override
			ArchiveEntryIterator<?, ?> openIterator(File file) throws IOException {
				return new ZipFileIterator(file);
			}
		},
		tar {
			@Override
			ArchiveEntryIterator<?, ?> openIterator(InputStream in) throws IOException {
				return new TarArchiveEntryIterator(in);
			}

			@Override
			ArchiveEntryIterator<?, ?> openIterator(File file) throws IOException {
				return new TarArchiveEntryIterator(file);
			}
		};


		@Override
		public final void stream(final InputStream in,
		                         final Predicate<ArchiveEntryIterator<?, ?>> predicate,
		                         final Consumer<Stream<ArchiveElement<?, ?>>> consumer) throws IOException {
			try (final ArchiveEntryIterator<?, ?> iterator = this.openIterator(in)) {
				if (predicate.test(iterator)) {
					consumer.accept(StreamSupport.stream(Spliterators.spliterator(iterator,
					                                                              Long.MAX_VALUE,
					                                                              0),
					                                     false));
				}
			}
		}

		@Override
		public final void stream(final File file,
		                         final Predicate<ArchiveEntryIterator<?, ?>> predicate,
		                         final Consumer<Stream<ArchiveElement<?, ?>>> consumer) throws IOException {
			try (final ArchiveEntryIterator<?, ?> iterator = this.openIterator(file)) {
				if (predicate.test(iterator)) {
					consumer.accept(StreamSupport.stream(Spliterators.spliterator(iterator,
					                                                              Long.MAX_VALUE,
					                                                              0),
					                                     false));
				}
			}
		}


		abstract ArchiveEntryIterator<?, ?> openIterator(InputStream in) throws IOException;

		abstract ArchiveEntryIterator<?, ?> openIterator(File file) throws IOException;
	}
}
