package gaia.common;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.security.auth.Destroyable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import gaia.ConstValues;
import gaia.common.CharsetSpec.ByteArrayOutputStreamExt;
import gaia.common.CharsetSpec.CharSets;
import gaia.common.ConcurrentAssist.ConditionedLock;
import gaia.common.MimeSpecification.ByteArrayMapper;
import gaia.common.RandomAssist.RandomType;
import gaia.common.ResourceAssist.ResourceContainer;

/**
 * @author dragon
 * @since 2020. 12. 3.
 */
public interface IOAssist {
	Logger log = LoggerFactory.getLogger(IOAssist.class);

	class ByteArray
			implements Closeable {
		final AtomicReference<byte[]> reference;
		@Getter
		final int bufferLength;
		@Setter
		@Getter
		int contentLength = 0;
		@Getter
		transient boolean destroyed = false;

		ByteArray(int length) {
			this.reference = new AtomicReference<>(new byte[length]);
			this.bufferLength = length;
		}

		public final byte[] get() {
			return this.reference.get();
		}

		@Override
		public void close() {
			if (this.destroyed) {
				log.warn("destroy twice!");
			}

			this.reference.set(null);

			this.destroyed = true;
		}
	}

	class ByteArrayBuffer
			extends ResourceContainer<ByteArray> {
		@Getter
		final int bufferSize;

		public ByteArrayBuffer(int cap,
		                       int bufferSize) {
			super(cap);
			this.bufferSize = bufferSize;
		}

		@Override
		protected ByteArray create() {
			return new ByteArrayImpl();
		}

		final class ByteArrayImpl
				extends ByteArray {
			/**
			 *
			 */
			ByteArrayImpl() {
				super(ByteArrayBuffer.this.bufferSize);
			}

			@Override
			public void close() {
				ByteArrayBuffer.this.recycle(this);
			}
		}
	}

	@Data
	class CheckPositionResult {
		final long position;

		final int readLength;

		final long nextPosition;
	}

	// ---------------------------------------------------------------------
	// Section adaptive stream
	// ---------------------------------------------------------------------
	interface AdaptiveStream
			extends Closeable,
			        Destroyable {
		/**
		 * @return store mode
		 */
		StoreMode getMode();

		/**
		 * @return id
		 */
		long getId();

		/**
		 * reset stream
		 */
		void reset();

		/**
		 * @return current read / write position
		 */
		long mark();

		/**
		 * set read / write position
		 *
		 * @param value mark point
		 */
		void mark(long value);

		/**
		 * add delta to mark
		 *
		 * @param delta rewind offset
		 */
		long rewind(long delta);

		/**
		 * @return limit of this stream
		 */
		long limit();

		/**
		 * return readable bytes
		 *
		 * @return readable bytes of this stream
		 */
		long remain();

		/**
		 * set read / write limit
		 *
		 * @param value value
		 */
		void limit(long value);

		/**
		 * @return size of this stream
		 */
		long size();

		/**
		 * shrink front when range in memory range
		 */
		void compact();

		/**
		 * shrink front at mark
		 */
		void shrinkFront();

		/**
		 * cut off front amount of bytes
		 *
		 * @param amount shrink amount of bytes
		 */
		void shrinkFront(long amount);

		/**
		 * set append position effect cut off after position
		 *
		 * @param position cut off position
		 */
		void shrink(long position);

		/**
		 * write
		 *
		 * @param b write value
		 */
		void write(int b);

		/**
		 * write byte array
		 *
		 * @param b write value
		 */
		void write(byte[] b);

		/**
		 *
		 */
		void write(byte[] b,
		           int off,
		           int len);

		/**
		 * @param position
		 * @param b
		 */
		void write(long position,
		           byte b);

		/**
		 * @param position
		 * @param b
		 */
		void write(long position,
		           byte[] b);

		/**
		 * @param position
		 * @param b
		 * @param off
		 * @param len
		 */
		void write(long position,
		           byte[] b,
		           int off,
		           int len);

		/**
		 * @param stream
		 */
		void load(InputStream stream);

		/**
		 * @param oldByte
		 * @param newByte
		 * @return
		 */
		AdaptiveStream replace(byte oldByte,
		                       byte newByte);

		/**
		 * @param oldByte
		 * @param newByte
		 * @param offset
		 * @param length
		 * @return
		 */
		AdaptiveStream replace(byte oldByte,
		                       byte newByte,
		                       long offset,
		                       long length);

		/**
		 * @param mark
		 * @param limit
		 */
		void setInputStreamRange(long mark,
		                         long limit) throws IOException;

		/**
		 * open input stream at current mark to end of stream
		 *
		 * @return
		 */
		InputStream openInputStream();

		InputStream openInputStream(long mark,
		                            long limit) throws IOException;

		/**
		 * @return
		 */
		OutputStream openOutputStream();

		/**
		 * @return
		 */
		int read();

		/**
		 * read dynamic length
		 *
		 * @param b
		 * @return
		 */
		int read(byte[] b);

		/**
		 * read fixed length
		 *
		 * @param b
		 * @param off
		 * @param len
		 * @return
		 */
		int read(byte[] b,
		         int off,
		         int len);

		/**
		 * @param position
		 * @return
		 */
		long readLongAt(long position);

		/**
		 * @param charset
		 * @return
		 */
		String readFully(CharSets charset);

		/**
		 * read next length of bytes
		 *
		 * @param length
		 * @param charset
		 * @return
		 */
		String read(int length,
		            CharSets charset);

		/**
		 * @param position
		 * @param length
		 * @param charset
		 * @return
		 */
		String read(long position,
		            int length,
		            CharSets charset);

		/**
		 * @param array
		 * @return
		 */
		int read(ByteArray array);

		/**
		 * @param array
		 * @param length
		 * @return
		 */
		int read(ByteArray array,
		         int length);

		/**
		 * @param position
		 * @param b
		 * @param off
		 * @param len
		 */
		void read(long position,
		          byte[] b,
		          int off,
		          int len);

		/**
		 * @param position
		 * @param buffer
		 */
		int read(long position,
		         ByteBuffer buffer);

		/**
		 * @param position
		 * @param buffer
		 * @param length
		 * @return
		 */
		int read(long position,
		         ByteBuffer buffer,
		         int length);

		/**
		 * copy given length to output stream
		 *
		 * @param buffer
		 * @param length
		 * @return
		 */
		int read(OutputStream buffer,
		         int length);

		/**
		 * @param position
		 * @return
		 */
		byte read(long position);

		/**
		 * @param charset
		 * @return
		 */
		String toString(CharSets charset);

		/**
		 * @param charset
		 * @param offset
		 * @param length
		 * @return
		 */
		String toString(CharSets charset,
		                long offset,
		                int length);

		/**
		 * @param out
		 */
		void writeTo(OutputStream out);

		/**
		 * @param out
		 * @param offset
		 * @param length
		 */
		void writeTo(OutputStream out,
		             long offset,
		             long length);

		/**
		 * @return
		 */
		String toString(ByteArrayMapper mapper,
		                Object option,
		                CharSets cs);

		/**
		 * @param offset
		 * @param length
		 * @return
		 */
		String toString(ByteArrayMapper mapper,
		                Object option,
		                CharSets cs,
		                int offset,
		                int length);

		/**
		 * @param delim
		 * @return
		 */
		long indexOf(byte delim);

		/**
		 * @param offset
		 * @param delim
		 * @return
		 */
		long indexOf(long offset,
		             byte delim);

		/**
		 * @return
		 */
		byte[] getAsBytes();

		/**
		 * @return
		 */
		byte[] extractBuffer();

		/**
		 * @return
		 */
		File extractTempStore();

		/**
		 * @return
		 */
		AdaptiveStream newInstance();

		/**
		 * @return
		 */
		AdaptiveStream copyInstance();

		/**
		 * @return
		 */
		boolean isShrinkWhenInputClose();

		/**
		 * @param shrinkWhenInputClose
		 */
		void setShrinkWhenInputClose(boolean shrinkWhenInputClose);

		/**
		 * @author dragon
		 */
		enum StoreMode {
			/**
			 *
			 */
			memory,
			/**
			 *
			 */
			file
		}
	}

	interface AdaptiveStreamProvider {
		/**
		 * @return
		 */
		AdaptiveStream provide();
	}

	interface TempDirectory
			extends Closeable {
		File getRoot();

		TempFile createFile(String name);

		void clearFiles();

	}

	interface TempFile
			extends Closeable {
		/**
		 * @return
		 */
		TempFileProvider provider();

		/**
		 * @return
		 */
		boolean deleteOnExit();

		/**
		 * @return
		 */
		File getFile();

		/**
		 * @return
		 */
		File exportFile();

		/**
		 * @return
		 */
		TempFile copy();

		/**
		 * @return
		 */
		OutputStream openOutputStream() throws IOException;

		/**
		 * @return
		 */
		InputStream openInputStream() throws IOException;

		/**
		 * @return
		 * @throws IOException
		 */
		RandomAccessFile openRandomAccessFile(String mode) throws IOException;
	}

	interface TempFileProvider
			extends AutoCloseable {
		/**
		 * @return
		 */
		String getName();

		/**
		 * @return
		 */
		File getWorkingRoot();

		/**
		 * @param other
		 * @return
		 */
		TempFile copyTempFile(final TempFile other);

		/**
		 * @param name
		 * @return
		 */
		TempFile createTempFile(final String name);

		/**
		 * @param deleteOnExit
		 * @return
		 */
		TempFile createTempFile(final boolean deleteOnExit);

		/**
		 * @param prefix
		 * @param suffix
		 * @return
		 */
		TempFile createTempFile(final String prefix,
		                        final String suffix);

		/**
		 * @param prefix
		 * @param suffix
		 * @param deleteOnExit
		 * @return
		 */
		TempFile createTempFile(final String prefix,
		                        final String suffix,
		                        final boolean deleteOnExit);

		/**
		 * @param in
		 * @return
		 * @throws IOException
		 */
		File wrapInputStreamToFile(final InputStream in) throws IOException;

		/**
		 * @param prefix
		 * @param suffix
		 * @param in
		 * @return
		 * @throws IOException
		 */
		File wrapInputStreamToFile(final String prefix,
		                           final String suffix,
		                           final InputStream in) throws IOException;

		/**
		 * @return
		 */
		TempDirectory createTempDirectory();

		/**
		 * @param prefix
		 * @param suffix
		 * @return
		 */
		TempDirectory createTempDirectory(final String prefix,
		                                  final String suffix);

		/**
		 * @param name
		 * @return
		 */
		TempDirectory createTempDirectory(final String name);
	}

	final class CloseSkipOutputStream
			extends FilterOutputStream {

		public CloseSkipOutputStream(OutputStream out) {
			super(out);
		}

		@Override
		public void close() throws IOException {
			// skip
			this.flush();
		}
	}

	final class CloseSkipInputStream
			extends FilterInputStream {

		public CloseSkipInputStream(InputStream in) {
			super(in);
		}

		@Override
		public void close() {
			//
		}
	}

	class WriteCountOutputStream
			extends OutputStream {
		/**
		 *
		 */
		final AtomicLong counter;

		/**
		 *
		 */
		public WriteCountOutputStream() {
			this(new AtomicLong());
		}

		/**
		 * @param counter
		 */
		public WriteCountOutputStream(AtomicLong counter) {
			super();
			this.counter = counter;
		}

		/**
		 *
		 */
		@Override
		public void write(int b) {
			this.counter.getAndAdd(1L);
		}

		/**
		 *
		 */
		@Override
		public void write(byte[] b) {
			if (b == null) {
				return;
			}
			this.counter.getAndAdd(b.length);
		}

		/**
		 *
		 */
		@Override
		public void write(byte[] b,
		                  int off,
		                  int len) {
			if (checkRange(b,
			               off,
			               len)) {
				this.counter.getAndAdd(len);
			}
		}
	}

	class JarArchiveEntry {
		/**
		 *
		 */
		@Getter
		final JarFile jarFile;

		/**
		 *
		 */
		@Getter
		final JarEntry jarEntry;

		/**
		 * @param jarFile
		 * @param jarEntry
		 */
		public JarArchiveEntry(JarFile jarFile,
		                       JarEntry jarEntry) {
			super();
			this.jarFile = jarFile;
			this.jarEntry = jarEntry;
		}

		/**
		 * @return
		 * @throws IOException
		 */
		public InputStream open() throws IOException {
			return this.jarFile.getInputStream(this.jarEntry);
		}
	}

	@Slf4j
	class AdaptiveStreamImpl
			extends OutputStream
			implements AdaptiveStream {
		final AtomicLong mod = new AtomicLong(0);
		final ConditionedLock lock = new ConditionedLock();
		@Getter
		final int initialSize;
		final byte[] buf;
		final AtomicLong count = new AtomicLong();
		final AtomicLong mark = new AtomicLong(0);
		final OutputStream outputStream;
		final InputStream exportInputStream;
		final AtomicBoolean flagInput = new AtomicBoolean(false);
		final AtomicLong limit = new AtomicLong(-1);
		@Getter
		private final long id = RandomAssist.getNextNumber();
		TempFile store = null;
		RandomAccessFile access = null;
		@Setter
		AdaptiveStreamProvider provider = null;
		@Setter
		TempFileProvider fileProvider = null;
		@Getter
		transient boolean destroyed = false;
		@Setter
		@Getter
		transient boolean shrinkWhenInputClose = true;
		Throwable destroyStack = null;
		@Getter
		private StoreMode mode = StoreMode.memory;

		public AdaptiveStreamImpl(int size) {
			if (size < 0) {
				throw new IllegalArgumentException("Negative initial size: " + size);
			}
			this.buf = new byte[size];
			this.initialSize = size;
			this.outputStream = new CloseSkipOutputStream(this);
			this.exportInputStream = new ExportInputStream();
		}

		@Override
		public String toString() {
			return StringAssist.format("(stream:%s) %s / %s",
			                           this.id,
			                           this.count.get(),
			                           this.mark.get());
		}

		private void closeRandom() {
			ObjectFinalizer.close(this.access);
			ObjectFinalizer.close(this.store);
			this.access = null;
			this.store = null;
			this.mode = StoreMode.memory;
		}

		private void closeInputStream() {
			if (this.flagInput.compareAndSet(true,
			                                 false)) {
				if (this.shrinkWhenInputClose) {
					this.shrinkFront();
				}
				return;
			}

			throw new IllegalStateException("input stream not opened!");
		}

		@Override
		public final void write(int b) {
			this.checkDestroy();
			final AtomicLong count = this.count;

			this.lock.execute(() -> {
				this.ensureCapacity(count.get() + 1);

				switch (this.mode) {
					case memory: {
						this.buf[BitsAssist.getInt(count.get())] = (byte) b;
						break;
					}

					case file: {
						this.access.seek(count.get());
						this.access.write(b);
						break;
					}
				}
				count.addAndGet(1);
				this.mod.getAndAdd(1L);
			});
		}

		@Override
		public void write(byte[] b) {
			this.checkDestroy();
			this.write(b,
			           0,
			           b.length);
		}

		@Override
		public final void write(byte[] b,
		                        int off,
		                        int len) {
			this.checkDestroy();

			if (off < 0 || off > b.length || len < 0 || off + len - b.length > 0) {
				throw new IndexOutOfBoundsException();
			}

			final AtomicLong count = this.count;

			this.lock.execute(() -> {
				this.ensureCapacity(count.get() + len);

				switch (this.mode) {
					case memory: {
						System.arraycopy(b,
						                 off,
						                 this.buf,
						                 BitsAssist.getInt(count.get()),
						                 len);
						break;
					}

					case file: {
						this.access.seek(count.get());
						this.access.write(b,
						                  off,
						                  len);
						break;
					}
				}
				count.getAndAdd(len);
				this.mod.getAndAdd(1L);
			});
		}

		@Override
		public void close() throws IOException {
			this.reset();
		}

		@Override
		public final void reset() {
			this.lock.execute(() -> {
				this.closeRandom();
				this.count.set(0);
				this.mark.set(0);
				this.mod.getAndAdd(1L);
			});

			this.checkDestroy();
		}

		@Override
		public final long mark() {
			return this.mark.get();
		}

		@Override
		public final void mark(long value) {
			if (this.count.get() < value) {
				throw new Error("exceed mark range");
			}
			this.mark.set(value);
		}

		@Override
		public final long rewind(long delta) {
			return this.lock.tran(() -> {
				final long newMark = this.mark.get() + delta;

				if (this.count.get() < newMark) {
					throw new Error("exceed mark range");
				}

				this.mark.set(newMark);

				return newMark;
			});
		}

		@Override
		public final long limit() {
			return this.limit.get();
		}

		@Override
		public final long remain() {
			return this.lock.tran(() -> {
				final long position = this.mark.get();

				return this.limit.get() > 0
				       ? this.limit.get() - position
				       : this.count.get() - position;
			});
		}

		@Override
		public final void limit(long value) {
			this.limit.set(value);
		}

		@Override
		public long size() {
			return this.count.get() - this.mark.get();
		}

		@Override
		public final void compact() {
			if (this.count.get() - this.mark.get() < this.initialSize) {
				this.shrinkFront();
			}
		}

		@Override
		public final void shrinkFront() {
			this.shrinkFront(this.mark.get());
		}

		@Override
		public final void shrinkFront(final long pos) {
			if (pos < 0) {
				throw new Error("negative pos?");
			}

			final long newCount = this.count.get() - pos;

			if (newCount < 0) { //
				throw new Error(StringAssist.format("count[%s] to pos [%s]",
				                                    this.count.get(),
				                                    pos));
			}

			final byte[] buffer = this.buf;

			this.lock.execute(() -> {
				switch (this.mode) {
					case memory: {
						if (newCount == 0) {
							this.mod.getAndAdd(1L);
							this.count.set(0);
						} else {
							System.arraycopy(buffer,
							                 BitsAssist.getInt(pos),
							                 buffer,
							                 0,
							                 BitsAssist.getInt(newCount));
						}
						break;
					}

					case file: {
						final RandomAccessFile access = this.access;
						if (this.initialSize > newCount) {
							// switch to memory
							access.seek(pos);
							access.read(buffer,
							            0,
							            BitsAssist.getInt(newCount));
							this.closeRandom();
						} else {
							// file shrink
							long remain = newCount;
							long index = 0;
							while (remain > 0) {
								access.seek(pos + index);
								final int read = access.read(buffer);

								if (read < 0) {
									throw new IOException("EOF");
								}

								access.seek(index);
								access.write(buffer,
								             0,
								             read);

								index += read;
								remain -= read;
							}
						}
						break;
					}
				}
				//
				this.count.set(newCount);

				// mark changed
				final long newMark = this.mark.get() - pos;

				this.mark.set(newMark > 0
				              ? newMark
				              : 0);
				// limit change
				final long newLimit = this.limit.get() - pos;

				this.limit.set(newLimit > 0
				               ? newLimit
				               : 0);

				this.mod.getAndAdd(1L);
			});
		}

		@Override
		public final void shrink(final long pos) {
			this.lock.execute(() -> {
				if (this.count.get() < pos) {
					throw new IOException(StringAssist.format("cannot shrint negative current[%s] -> to[%s]",
					                                          this.count.get(),
					                                          pos));
				}
				switch (this.mode) {
					case memory: {
						break;
					}

					case file: {
						final RandomAccessFile access = this.access;
						if (this.initialSize > pos) {
							// switch to memory
							access.seek(0);
							access.read(this.buf,
							            0,
							            BitsAssist.getInt(pos));
							this.closeRandom();
						}
						break;
					}
				}
				this.count.set(pos);

				// mod pos
				if (this.mark.get() > pos) {
					this.mark.set(pos);
				}
				if (this.limit.get() > pos) {
					this.limit.set(pos);
				}

				this.mod.getAndAdd(1L);
			});
		}

		@Override
		public final void write(long position,
		                        byte b) {
			this.write(position,
			           new byte[]{b});
		}

		@Override
		public final void write(long position,
		                        byte[] b) {
			this.write(position,
			           b,
			           0,
			           b.length);
		}

		@Override
		public final void write(long position,
		                        byte[] b,
		                        int off,
		                        int len) {
			this.checkDestroy();

			if (off < 0 || off > b.length || len < 0 || off + len - b.length > 0) {
				throw new IndexOutOfBoundsException();
			}

			this.lock.execute(() -> {
				this.ensureCapacity(position + len);

				switch (this.mode) {
					case memory: {
						System.arraycopy(b,
						                 off,
						                 this.buf,
						                 BitsAssist.getInt(position),
						                 len);
						break;
					}

					case file: {
						final RandomAccessFile access = this.access;

						// final long pointer = access.getFilePointer();
						access.seek(position);
						access.write(b,
						             off,
						             len);

						break;
					}
				}
				final AtomicLong count = this.count;

				if (position + len > count.get()) {
					count.set(position + len);
				}

				this.mod.getAndAdd(1L);
			});
		}

		@Override
		public void load(final InputStream stream) {
			StreamAssist.arrayAccept(buffer -> {
				int size;

				while ((size = stream.read(buffer)) > 0) {
					this.write(buffer,
					           0,
					           size);
				}
			});
		}

		/**
		 *
		 */
		@Override
		public AdaptiveStream replace(byte oldByte,
		                              byte newByte) {
			return this.replace(oldByte,
			                    newByte,
			                    0L,
			                    this.count.get());
		}

		@Override
		public final AdaptiveStream replace(byte oldByte,
		                                    byte newByte,
		                                    final long offset,
		                                    final long length) {
			this.checkDestroy();
			final byte[] buffer = this.buf;
			final long end = Math.min(this.count.get() - offset,
			                          length);

			this.lock.execute(() -> {
				switch (this.mode) {
					case memory: {
						ArrayAssist.replace(buffer,
						                    BitsAssist.getInt(offset),
						                    BitsAssist.getInt(end),
						                    oldByte,
						                    newByte);
						break;
					}

					case file: {
						final RandomAccessFile access = this.access;

						final long pointer = access.getFilePointer();
						try {
							access.seek(offset);

							long remain = length;

							while (remain > 0) {
								final long current = access.getFilePointer();

								int read = access.read(buffer);

								if (read < 0) {
									throw new IOException("EOF");
								}

								ArrayAssist.replace(buffer,
								                    0,
								                    read,
								                    oldByte,
								                    newByte);

								access.seek(current);
								access.write(buffer,
								             0,
								             read);

								remain -= read;
							}
						}
						finally {
							access.seek(pointer);
						}
						break;
					}
				}

				this.mod.getAndAdd(1L);
			});

			return this;
		}

		@Override
		public final void setInputStreamRange(long mark,
		                                      long limit) throws IOException {
			// check input
			if (limit > 0 && mark > limit) {
				throw new IOException("mark exceed limit?");
			}

			this.lock.execute(() -> {
				if (this.count.get() < mark) {
					throw new IOException("mark exceed max length");
				}
				if (this.count.get() < limit) {
					throw new IOException("mark exceed max length");
				}

				this.mark.set(mark);
				this.limit.set(limit);
			});
		}

		@Override
		public final InputStream openInputStream() {
			if (this.flagInput.compareAndSet(false,
			                                 true)) {
				return this.exportInputStream;
			}

			throw new IllegalStateException("input stream already opened");
		}

		@Override
		public InputStream openInputStream(long mark,
		                                   long limit) throws IOException {
			this.setInputStreamRange(mark,
			                         limit);
			return this.openInputStream();
		}

		/**
		 *
		 */
		@Override
		public final OutputStream openOutputStream() {
			return this.outputStream;
		}

		@Override
		public int read() {

			return this.lock.tran(() -> {
				int val = -1;
				// eof
				if (this.limit.get() > 0 && this.mark.get() >= this.limit.get()) {
					return val;
				}

				final long position = this.mark.getAndIncrement();

				switch (this.mode) {
					case memory: {
						val = this.buf[BitsAssist.getInt(position)] & 0xFF;
						break;
					}

					case file: {
						final RandomAccessFile access = this.access;

						access.seek(position);

						val = access.read();

						break;
					}
				}
				return val;
			});
		}

		@Override
		public final int read(byte[] b) {
			return this.read(b,
			                 0,
			                 b.length);
		}

		@Override
		public final int read(final byte[] b,
		                      final int off,
		                      final int len) {
			if (off < 0 || off > b.length || len < 0 || off + len > b.length) {
				throw new IndexOutOfBoundsException();
			}

			return this.lock.tran(() -> this.read0(b,
			                                       off,
			                                       len));
		}

		private int read0(final byte[] b,
		                  final int off,
		                  final int len) throws IOException {

			CheckPositionResult positionResult = this.checkNextPosition(len);

			if (null == positionResult) {
				return -1;
			}

			switch (this.mode) {
				case memory: {
					System.arraycopy(this.buf,
					                 BitsAssist.getInt(positionResult.getPosition()),
					                 b,
					                 off,
					                 positionResult.getReadLength());
					break;
				}

				case file: {
					final RandomAccessFile access = this.access;
					access.seek(positionResult.getPosition());

					access.readFully(b,
					                 off,
					                 positionResult.getReadLength());

					break;
				}
			}

			this.mark.set(positionResult.getNextPosition());

			return positionResult.getReadLength();

		}

		@Override
		public long readLongAt(long position) {
			final int len = 8;

			return this.lock.tran(() -> {
				this.checkPosition(position + len);

				switch (this.mode) {
					case memory: {
						return BitsAssist.getLong(this.buf,
						                          BitsAssist.getInt(position));
					}

					case file: {
						final RandomAccessFile access = this.access;
						final long current = access.getFilePointer();
						final byte[] b = this.buf;
						try {
							access.seek(position);

							access.readFully(b,
							                 0,
							                 len);
							return BitsAssist.getLong(b,
							                          0);

						}
						finally {
							access.seek(current);
						}
					}
				}
				throw new Error("unknown mode?");
			});
		}

		@Override
		public String readFully(CharSets charset) {
			return this.read(-1,
			                 charset);
		}

		@Override
		public String read(int length,
		                   CharSets charset) {
			return this.lock.tran(() -> {
				final long position = this.mark.get();
				final long nextPosition = length < 0
				                          ? this.count.get()
				                          : position + length;
				this.checkPosition(nextPosition);

				this.mark.set(nextPosition);

				return this.read(position,
				                 BitsAssist.getInt(nextPosition - position),
				                 charset);
			});
		}

		@Override
		public String read(final long position,
		                   final int length,
		                   final CharSets charset) {
			return this.lock.tran(() -> this.read0(position,
			                                       length,
			                                       charset));

		}

		private String read0(final long position,
		                     final int length,
		                     final CharSets charset) throws IOException {
			this.checkRange(position,
			                length);

			switch (this.mode) {
				case memory: {
					int off = BitsAssist.getInt(position);
					return charset.toString(this.buf,
					                        off,
					                        length);
				}

				case file: {
					final RandomAccessFile access = this.access;
					final long current = access.getFilePointer();
					try {
						access.seek(position);

						final byte[] readBuffer = new byte[length];

						access.readFully(readBuffer,
						                 0,
						                 length);

						return charset.toString(readBuffer);
					}
					finally {
						access.seek(current);
					}
				}
			}

			throw new Error("crash!");
		}

		@Override
		public int read(ByteArray array) {
			return this.read(array,
			                 array.getBufferLength());
		}

		@Override
		public int read(ByteArray array,
		                int length) {
			final int read = this.read(array.get(),
			                           0,
			                           length);

			array.setContentLength(read);

			return read;
		}

		@Override
		public final void read(final long position,
		                       final byte[] b,
		                       final int off,
		                       final int len) {
			if (off < 0 || off > b.length || len < 0 || position + len > this.count.get()) {
				throw new IndexOutOfBoundsException();
			}

			this.lock.execute(() -> {
				this.checkPosition(position + len);

				switch (this.mode) {
					case memory: {
						System.arraycopy(this.buf,
						                 BitsAssist.getInt(position),
						                 b,
						                 off,
						                 len);
						break;
					}

					case file: {
						final RandomAccessFile access = this.access;
						final long current = access.getFilePointer();
						try {
							access.seek(position);

							access.readFully(b,
							                 off,
							                 len);

						}
						finally {
							access.seek(current);
						}
						break;
					}
				}
			});
		}

		@Override
		public int read(long position,
		                ByteBuffer buffer) {
			return this.read(position,
			                 buffer,
			                 -1);
		}

		@Override
		public int read(final long position,
		                final ByteBuffer buffer,
		                final int length) {
			return this.lock.tran(() -> {
				this.checkPosition(position);

				// how many bytes read
				final int maxLength = Math.min(buffer.limit() - buffer.position(),
				                               BitsAssist.getInt(this.count.get() - position));
				final int readSize = length < 0
				                     ? maxLength
				                     : Math.min(length,
				                                maxLength);

				switch (this.mode) {
					case memory: {
						buffer.put(this.buf,
						           BitsAssist.getInt(position),
						           readSize);
						break;
					}

					case file: {
						final RandomAccessFile access = this.access;
						final long current = access.getFilePointer();
						try {
							access.seek(position);

							access.readFully(this.buf,
							                 0,
							                 readSize);

							buffer.put(this.buf,
							           0,
							           readSize);
						}
						finally {
							access.seek(current);
						}
						break;
					}
				}

				buffer.flip();
				return readSize;
			});
		}

		private CheckPositionResult checkNextPosition(final int length) {
			final long position = this.mark.get();

			final long max = this.limit.get() > 0
			                 ? this.limit.get() - position
			                 : this.count.get() - position;

			if (max == 0) {
				return null;
			}

			final int readLength = max > length
			                       ? length
			                       : BitsAssist.getInt(max);

			final long nextPosition = position + readLength;

			this.checkPosition(nextPosition);

			return new CheckPositionResult(position,
			                               readLength,
			                               nextPosition);
		}

		@Override
		public final int read(final OutputStream buffer,
		                      final int length) {
			return this.lock.tran(() -> {
				CheckPositionResult positionResult = this.checkNextPosition(length);

				if (null == positionResult) {
					return -1;
				}

				switch (this.mode) {
					case memory: {
						buffer.write(this.buf,
						             BitsAssist.getInt(positionResult.position),
						             positionResult.readLength);
						break;
					}

					case file: {
						final byte[] b = new byte[positionResult.readLength];
						final RandomAccessFile access = this.access;
						access.seek(positionResult.position);

						access.readFully(b,
						                 0,
						                 positionResult.readLength);
						buffer.write(b);
						break;
					}
				}

				this.mark.set(positionResult.nextPosition);

				return positionResult.readLength;
			});
		}

		@Override
		public final byte read(final long position) {
			return this.lock.tran(() -> {

				this.checkPosition(position);

				byte val = 0;

				switch (this.mode) {
					case memory: {
						val = this.buf[BitsAssist.getInt(position)];
						break;
					}

					case file: {
						final RandomAccessFile access = this.access;
						final long current = access.getFilePointer();
						try {
							access.seek(position);

							int read = access.read();

							if (read < 0) {
								throw new IOException("EOF");
							}
							val = (byte) (read & 0xFF);
						}
						finally {
							access.seek(current);
						}
						break;
					}
				}

				return val;
			});
		}

		/**
		 *
		 */
		@Override
		public final String toString(CharSets charset) {
			return this.toString(charset,
			                     0,
			                     BitsAssist.getInt(this.count.get()));
		}

		/**
		 *
		 */
		@Override
		public final String toString(CharSets charset,
		                             final long offset,
		                             final int length) {
			return this.lock.tran(() -> {
				                      this.checkRange(offset,
				                                      length);
				                      String val = null;

				                      switch (this.mode) {
					                      case memory: {
						                      val = charset.toString(this.buf,
						                                             BitsAssist.getInt(offset),
						                                             length);
						                      break;
					                      }

					                      case file: {
						                      final RandomAccessFile access = this.access;
						                      final long current = access.getFilePointer();

						                      try {
							                      byte[] buffer = new byte[length];
							                      access.seek(offset);

							                      access.readFully(buffer,
							                                       0,
							                                       length);

							                      val = charset.toString(buffer);
						                      }
						                      finally {
							                      access.seek(current);
						                      }
						                      break;
					                      }
				                      }

				                      this.mod.getAndAdd(1L);

				                      return val;
			                      },
			                      thw -> StringAssist.format("cannot toString stream because [%s]",
			                                                 ExceptionAssist.getStackMessage(thw)));
		}

		@Override
		public void writeTo(OutputStream out) {
			this.writeTo(out,
			             0,
			             this.count.get());
		}

		@Override
		public final void writeTo(final OutputStream out,
		                          final long offset,
		                          final long length) {
			this.lock.execute(() -> {
				this.checkRange(offset,
				                length);
				final byte[] buffer = this.buf;

				switch (this.mode) {
					case memory: {
						out.write(buffer,
						          BitsAssist.getInt(offset),
						          BitsAssist.getInt(length));
						break;
					}

					case file: {
						final RandomAccessFile access = this.access;
						final long current = access.getFilePointer();

						try {
							long remain = length;
							access.seek(offset);
							while (remain > 0) {
								int read = access.read(buffer,
								                       0,
								                       Math.min(BitsAssist.getInt(remain),
								                                buffer.length));

								if (read < 0) {
									throw new IOException("EOF");
								}

								out.write(buffer,
								          0,
								          read);

								remain -= read;
							}
						}
						finally {
							access.seek(current);
						}
						break;
					}
				}

				this.mod.getAndAdd(1L);
			});
		}

		@Override
		public final String toString(ByteArrayMapper mapper,
		                             Object option,
		                             CharSets cs) {
			return this.toString(mapper,
			                     option,
			                     cs,
			                     0,
			                     BitsAssist.getInt(this.count.get()));
		}

		/**
		 *
		 */
		@Override
		public final String toString(ByteArrayMapper mapper,
		                             Object option,
		                             CharSets cs,
		                             int offset,
		                             int length) {
			return this.lock.tran(() -> {
				this.checkRange(offset,
				                length);
				String val = null;

				switch (this.mode) {
					case memory: {
						val = cs.toString(mapper.encode(this.buf,
						                                offset,
						                                length,
						                                option));
						break;
					}

					case file: {
						final RandomAccessFile access = this.access;
						final long current = access.getFilePointer();

						try {
							access.seek(offset);

							final byte[] readBuffer = new byte[length];

							access.readFully(readBuffer,
							                 0,
							                 length);

							val = cs.toString(mapper.encode(readBuffer,
							                                0,
							                                length,
							                                option));
						}
						finally {
							access.seek(current);
						}
						break;
					}
				}
				return val;
			});
		}

		@Override
		public long indexOf(byte delim) {
			return this.indexOf(this.mark.get(),
			                    delim);
		}

		@Override
		public long indexOf(long offset,
		                    byte delim) {
			return this.lock.tran(() -> {
				this.checkDestroy();

				final byte[] buffer = this.buf;

				switch (this.mode) {
					case memory: {
						final int base = BitsAssist.getInt(offset);
						final int cnt = BitsAssist.getInt(this.count.get());
						for (int i = base; i < cnt; i++) {
							if (buffer[i] == delim) {
								return (long) (i - base);
							}
						}
						break;
					}

					case file: {
						final RandomAccessFile access = this.access;

						final long pointer = access.getFilePointer();

						try {
							access.seek(offset);

							long remain = this.count.get();

							while (remain > 0) {
								final long current = access.getFilePointer();

								int read = access.read(buffer);

								if (read < 0) {
									throw new IOException("EOF");
								}

								for (int i = 0; i < read; i++) {
									if (buffer[i] == delim) {
										return current + i - offset;
									}
								}

								remain -= read;
							}
						}
						finally {
							access.seek(pointer);
						}
						break;
					}
				}

				return -1L;
			});
		}

		@Override
		public final byte[] getAsBytes() {
			return this.lock.tran(() -> this.getAsBytes0(false));
		}

		/**
		 *
		 */

		private byte[] getAsBytes0(boolean reset) throws IOException {
			final long size = this.count.get();

			if (size > Integer.MAX_VALUE) {
				throw new IOException(StringAssist.format("cannot convert byte array size exceed(%s > %s)",
				                                          size,
				                                          Integer.MAX_VALUE));
			}

			final int convertLength = BitsAssist.getInt(size);

			final byte[] bytes = new byte[convertLength];

			switch (this.mode) {
				case memory: {
					System.arraycopy(this.buf,
					                 0,
					                 bytes,
					                 0,
					                 convertLength);
					break;
				}

				case file: {
					final RandomAccessFile access = this.access;
					final long current = access.getFilePointer();

					try {
						access.seek(0);

						access.readFully(bytes,
						                 0,
						                 convertLength);
					}
					finally {
						access.seek(current);
					}
					break;
				}
			}

			if (reset) {
				this.reset();

				this.mod.getAndAdd(1L);
			}
			return bytes;
		}

		/**
		 *
		 */
		@Override
		public final byte[] extractBuffer() {
			return this.lock.tran(() -> this.getAsBytes0(true));
		}

		/**
		 *
		 */
		@Override
		public File extractTempStore() {
			final TempFile ret = this.store;
			return this.lock.tran(() -> {
				switch (this.mode) {
					case memory: {
						throw new Error("Memory mod cannot export temp store");
					}

					case file: {
						if (ret == null) {
							throw new Error("temp store file is null!");
						}
						File file = ret.exportFile();

						if (!file.exists()) {
							throw new Error("temp store file is not exist!");
						}

						this.closeRandom();

						this.count.set(0);
						this.mod.getAndAdd(1);

						return file;
					}
				}
				throw new Error("unknown store mode?");
			});
		}

		@Override
		public AdaptiveStream newInstance() {
			return new AdaptiveStreamImpl(this.initialSize);
		}

		@Override
		public final AdaptiveStream copyInstance() {
			AdaptiveStreamImpl clone = this.provider == null
			                           ? new AdaptiveStreamImpl(this.initialSize)
			                           : (AdaptiveStreamImpl) this.provider.provide();

			final byte[] buffer = this.buf;

			this.lock.execute(() -> {
				clone.count.set(this.count.get());
				clone.mode = this.mode;
				switch (this.mode) {
					case memory: {
						final int convertLength = BitsAssist.getInt(this.count.get());
						System.arraycopy(buffer,
						                 0,
						                 clone.buf,
						                 0,
						                 convertLength);
						break;
					}

					case file: {
						if (this.fileProvider == null) {
							throw new NullPointerException("fileProvider is null!");
						}

						final TempFileProvider fileProvider = this.fileProvider;

						// swap file
						clone.store = fileProvider.createTempFile("extend_output.",
						                                          ".store");
						clone.access = this.store.openRandomAccessFile("rw");

						final long copied = FileAssist.copy(this.access,
						                                    clone.access);
						if (log.isTraceEnabled()) {
							log.trace(StringAssist.format("[%s bytes] copied",
							                              copied));
						}
						break;
					}
				}
			});

			return clone;
		}

		/**
		 * @param position
		 */
		private void checkPosition(final long position) {
			this.checkRange(position,
			                0);
		}

		/**
		 * @param offset
		 * @param length
		 */
		private void checkRange(final long offset,
		                        final long length) {
			if (this.count.get() < offset + length) {
				throw new IllegalArgumentException(StringAssist.format("array index out of bound! [%s] < ([%s][%s])",
				                                                       this.count,
				                                                       offset,
				                                                       length));
			}
		}

		/**
		 *
		 */
		private void checkDestroy() {
			if (this.destroyed) {
				if (log.isTraceEnabled()) {
					log.trace(StringAssist.format("already destroy (stream:%s)",
					                              this.id),
					          this.destroyStack);
				}
				throw new IllegalStateException(StringAssist.format("already destroy (stream:%s)",
				                                                    this.id));
			}
		}

		/**
		 * @param minCapacity
		 */
		private void ensureCapacity(long minCapacity) throws IOException {
			switch (this.mode) {
				case file: {
					return;
				}

				case memory: {
					if (minCapacity - this.buf.length > 0) {
						if (this.fileProvider == null) {
							throw new NullPointerException("fileProvider is null!");
						}

						final TempFileProvider fileProvider = this.fileProvider;

						// swap file
						this.store = fileProvider.createTempFile("extend_output.",
						                                         ".store");
						this.access = this.store.openRandomAccessFile("rw");

						this.access.write(this.buf,
						                  0,
						                  BitsAssist.getInt(this.count.get()));

						this.mode = StoreMode.file;
					}
					break;
				}
			}
		}

		/**
		 *
		 */
		@Override
		public void destroy() {
			if (this.destroyed) {
				log.warn(StringAssist.format("destroy-twice:(stream:%s) by [%s]",
				                             this.id,
				                             Thread.currentThread()
				                                   .getName()));
			} else {
				this.reset();
			}

			this.destroyed = true;

			if (log.isTraceEnabled()) {
				this.destroyStack = new Throwable(StringAssist.format("destroy-stack:(stream:%s) by [%s]",
				                                                      this.id,
				                                                      Thread.currentThread()
				                                                            .getName()));
			}
		}

		/**
		 * @author 이상근
		 */
		final class ExportInputStream
				extends InputStream {
			/**
			 *
			 */
			@Override
			public int read() {
				return AdaptiveStreamImpl.this.read();
			}

			/**
			 *
			 */
			@Override
			public int read(byte[] b) {
				return AdaptiveStreamImpl.this.read(b);
			}

			/**
			 *
			 */
			@Override
			public int read(byte[] b,
			                int off,
			                int len) {
				return AdaptiveStreamImpl.this.read(b,
				                                    off,
				                                    len);
			}

			/**
			 *
			 */
			@Override
			public void close() throws IOException {
				super.close();

				AdaptiveStreamImpl.this.closeInputStream();
			}
		}
	}

	@Slf4j
	class AdaptiveStreamPool
			extends ResourceContainer<AdaptiveStream>
			implements AdaptiveStreamProvider {
		transient int initialSize = 4096;
		transient boolean closing = false;
		@Setter
		TempFileProvider fileProvider = null;

		public AdaptiveStreamPool() {
			this(GWEnvironment.getInteger("adaptive.stream.pool.size",
			                              10),
			     GWEnvironment.getInteger("adaptive.stream.initial.size",
			                              4096));
		}

		public AdaptiveStreamPool(int poolSize,
		                          int bufferSize) {
			super(poolSize);
			this.initialSize = bufferSize;
		}

		/**
		 * @param size
		 */
		public final void setInitialSize(int size) {
			this.initialSize = size;
		}

		/**
		 *
		 */
		private void onPostConstruct() {
			if (this.fileProvider == null) {
				this.fileProvider = new TempFileLocalProvider();
			}
		}

		@Override
		protected AdaptiveStream create() {
			PooledAdaptiveOutputStream pas = new PooledAdaptiveOutputStream();

			pas.setProvider(this);
			pas.setFileProvider(this.fileProvider);

			if (log.isTraceEnabled()) {
				log.trace(StringAssist.format("%s created",
				                              pas.toString()));
			}

			return pas;
		}

		@Override
		public void close() throws Exception {
			if (this.closing) {
				return;
			}

			this.closing = true;

			super.close();
		}

		@Override
		public AdaptiveStream provide() {
			return this.getResource();
		}

		/**
		 * @author 이상근
		 */
		final class PooledAdaptiveOutputStream
				extends AdaptiveStreamImpl {
			public PooledAdaptiveOutputStream() {
				super(AdaptiveStreamPool.this.initialSize);
			}

			@Override
			public void close() throws IOException {
				if (AdaptiveStreamPool.this.closing) {
					super.close();
					return;
				}
				this.reset();
				if (!AdaptiveStreamPool.this.recycle(this)) {
					if (log.isTraceEnabled()) {
						log.trace(StringAssist.format("(stream:%s) rejected",
						                              this.getId()));
					}
				}
			}

			@Override
			public AdaptiveStream newInstance() {
				return AdaptiveStreamPool.this.getResource();
			}
		}
	}

	@Slf4j
	final class TempDirectoryLocal
			implements TempDirectory {
		/**
		 *
		 */
		final TempFileProvider provider;

		/**
		 *
		 */
		final AtomicReference<File> file = new AtomicReference<>();

		/**
		 * @param file
		 */
		public TempDirectoryLocal(final TempFileProvider provider,
		                          final File file) {
			super();
			this.provider = provider;
			this.file.set(file);
		}

		/**
		 *
		 */
		@Override
		public void close() {
			Optional.ofNullable(this.file.getAndSet(null))
			        .ifPresent(ObjectFinalizer::close);
		}

		/**
		 *
		 */
		@Override
		public File getRoot() {
			final File root = this.file.get();

			if (root == null) {
				throw new Error("Already closed temp directory");
			}
			return root;
		}

		/**
		 *
		 */
		@Override
		public TempFile createFile(String name) {
			final File create = new File(this.getRoot(),
			                             name);

			return new TempFileLocal(this.provider,
			                         create);
		}

		/**
		 *
		 */
		@Override
		public void clearFiles() {
			Optional.ofNullable(this.getRoot()
			                        .listFiles())
			        .ifPresent(files -> {
				        for (final File file : files) {
					        ObjectFinalizer.close(file);
				        }
			        });
		}
	}

	@Slf4j
	final class TempFileLocal
			implements TempFile {
		/**
		 *
		 */
		final TempFileProvider provider;

		/**
		 *
		 */
		final AtomicReference<File> file;

		/**
		 *
		 */
		final AtomicReference<InnerFileOutputStream> out;

		/**
		 *
		 */
		final AtomicReference<InnerFileInputStream> in;

		/**
		 *
		 */
		final ConcurrentHashMap<String, InnerRandomAccessFile> random;

		/**
		 *
		 */
		final boolean deleteOnExit;

		/**
		 * @param file
		 */
		public TempFileLocal(final TempFileProvider provider,
		                     final File file) {
			this(provider,
			     file,
			     false);
		}

		/**
		 * @param file
		 * @param deleteOnExit
		 */
		public TempFileLocal(final TempFileProvider provider,
		                     final File file,
		                     final boolean deleteOnExit) {
			super();
			this.provider = provider;
			this.deleteOnExit = deleteOnExit;
			this.file = new AtomicReference<>(file);
			this.out = new AtomicReference<>();
			this.in = new AtomicReference<>();
			this.random = new ConcurrentHashMap<>();

		}

		@Override
		public TempFileProvider provider() {
			return this.provider;
		}

		@Override
		public boolean deleteOnExit() {
			return this.deleteOnExit;
		}

		/**
		 * @return
		 */
		@Override
		public File getFile() {
			return Optional.ofNullable(this.file.get())
			               .orElseThrow(() -> new Error("Already close Temp File"));
		}

		/**
		 * @return
		 */
		@Override
		public File exportFile() {
			return Optional.ofNullable(this.file.getAndSet(null))
			               .orElseThrow(() -> new Error("Already close Temp File"));
		}

		@Override
		public TempFile copy() {
			return this.provider.copyTempFile(this);
		}

		/**
		 *
		 */
		@Override
		public OutputStream openOutputStream() throws IOException {
			InnerFileOutputStream stream = this.out.get();

			if (stream == null || stream.isClosed()) {
				stream = new InnerFileOutputStream(this.getFile());
				this.out.set(stream);
				return stream;
			}

			throw new IOException("Current output stream is not closed!");
		}

		/**
		 *
		 */
		@Override
		public InputStream openInputStream() throws IOException {
			InnerFileInputStream stream = this.in.get();

			if (stream == null || stream.isClosed()) {
				stream = new InnerFileInputStream(this.getFile());
				this.in.set(stream);
				return stream;
			}

			throw new IOException("Current input stream is not closed!");
		}

		/**
		 *
		 */
		@Override
		public RandomAccessFile openRandomAccessFile(String mode) throws IOException {
			InnerRandomAccessFile stream = this.random.get(mode);

			if (stream == null || stream.isClosed()) {
				stream = new InnerRandomAccessFile(this.getFile(),
				                                   mode);
				this.random.put(mode,
				                stream);
				return stream;
			}

			return stream;
		}

		/**
		 *
		 */
		@Override
		public void close() {
			final File file = this.file.getAndSet(null);

			final InnerFileInputStream in = this.in.getAndSet(null);

			if (in != null && !in.isClosed()) {
				log.warn(StringAssist.format("unclosed temp input stream found [%s]",
				                             file));
			}

			ObjectFinalizer.close(in);

			final InnerFileOutputStream out = this.out.getAndSet(null);
			if (out != null && !out.isClosed()) {
				log.warn(StringAssist.format("unclosed temp output stream found [%s]",
				                             file));
			}
			ObjectFinalizer.close(out);

			List<InnerRandomAccessFile> randoms = new ArrayList<>(this.random.values());
			for (InnerRandomAccessFile rd : randoms) {
				if (!rd.isClosed()) {
					log.warn(StringAssist.format("unclosed temp random stream found [%s]",
					                             file),
					         rd.create);
				}
				ObjectFinalizer.close(rd);
			}
			this.random.clear();

			ObjectFinalizer.close(file);
		}

		/**
		 * @author dragon
		 */
		static final class InnerFileInputStream
				extends FileInputStream {
			final AtomicBoolean closed = new AtomicBoolean(false);

			/**
			 * @param file
			 * @throws FileNotFoundException
			 */
			public InnerFileInputStream(File file) throws FileNotFoundException {
				super(file);
			}

			/**
			 * @return
			 */
			boolean isClosed() {
				return this.closed.get();
			}

			/**
			 *
			 */
			@Override
			public void close() throws IOException {
				this.closed.set(true);
				super.close();
			}
		}

		/**
		 * @author dragon
		 */
		static final class InnerFileOutputStream
				extends FileOutputStream {
			final AtomicBoolean closed = new AtomicBoolean(false);

			/**
			 * @param file
			 */
			InnerFileOutputStream(File file) throws FileNotFoundException {
				super(file);
			}

			/**
			 * @return
			 */
			boolean isClosed() {
				return this.closed.get();
			}

			/**
			 *
			 */
			@Override
			public void close() throws IOException {
				this.closed.set(true);
				super.close();
			}
		}

		/**
		 * @author dragon
		 */
		static final class InnerRandomAccessFile
				extends RandomAccessFile {
			final Throwable create;
			final AtomicBoolean closed = new AtomicBoolean(false);

			/**
			 * @param file
			 * @param mode
			 * @throws FileNotFoundException
			 */
			public InnerRandomAccessFile(File file,
			                             String mode) throws FileNotFoundException {
				super(file,
				      mode);
				this.create = new Throwable("CREATE");
			}

			/**
			 * @return
			 */
			boolean isClosed() {
				return this.closed.get();
			}

			/**
			 *
			 */
			@Override
			public void close() throws IOException {
				this.closed.set(true);
				super.close();
			}
		}
	}

	final class TempFileLocalProvider
			implements TempFileProvider {
		final File root;
		@Setter
		@Getter
		String name = null;

		public TempFileLocalProvider() {
			this(FileAssist.getTempDirectory(".temp.provider",
			                                 true));
		}

		public TempFileLocalProvider(File root) {
			this.name = StringAssist.format("TempFileLocalProvider[%s]",
			                                root.getAbsolutePath());
			if (root.exists() && root.isFile()) {
				throw new Error(StringAssist.format("target is not a directory ! [%s]",
				                                    root.getAbsolutePath()));
			}

			if (!root.exists() && !root.mkdirs()) {
				throw new Error(StringAssist.format("target directory create fail! [%s]",
				                                    root.getAbsolutePath()));
			}

			this.root = root;

			// clear
			File[] files = root.listFiles();

			if (files != null) {
				for (final File file : files) {
					ObjectFinalizer.close(file);
				}
			}
		}

		@Override
		public void close() {
			ObjectFinalizer.close(this.root);
		}

		@Override
		public File getWorkingRoot() {
			return this.root;
		}

		@Override
		public TempFile copyTempFile(TempFile other) {
			try {
				final File file = File.createTempFile("clone_",
				                                      "_other",
				                                      this.root);
				if (other.deleteOnExit()) {
					file.deleteOnExit();
				}

				FileAssist.copy(other.getFile(),
				                file);

				return new TempFileLocal(this,
				                         file,
				                         other.deleteOnExit());
			}
			catch (IOException e) {
				throw new RuntimeException("create temp file fail",
				                           e);
			}
		}

		@Override
		public TempFile createTempFile(final String name) {
			File file = new File(this.root,
			                     name);

			while (file.exists()) {
				File sub = new File(this.root,
				                    RandomAssist.getUniqueString());
				while (sub.exists()) {
					sub = new File(this.root,
					               RandomAssist.getUniqueString());
				}

				sub.mkdir();

				file = new File(sub,
				                name);
			}

			return new TempFileLocal(this,
			                         file);
		}

		/**
		 * @param deleteOnExit
		 * @return
		 */
		@Override
		public TempFile createTempFile(final boolean deleteOnExit) {
			return this.createTempFile("prefix_",
			                           "_suffix",
			                           deleteOnExit);
		}

		/**
		 * @param prefix
		 * @param suffix
		 * @return
		 */
		@Override
		public TempFile createTempFile(final String prefix,
		                               final String suffix) {
			return this.createTempFile(prefix,
			                           suffix,
			                           false);
		}

		/**
		 * @param prefix
		 * @param suffix
		 * @param deleteOnExit
		 * @return
		 */
		@Override
		public TempFile createTempFile(final String prefix,
		                               final String suffix,
		                               final boolean deleteOnExit) {
			try {
				final File file = File.createTempFile(prefix,
				                                      suffix,
				                                      this.root);
				if (deleteOnExit) {
					file.deleteOnExit();
				}

				return new TempFileLocal(this,
				                         file,
				                         deleteOnExit);
			}
			catch (IOException e) {
				throw new RuntimeException("create temp file fail",
				                           e);
			}
		}

		/**
		 * @param in
		 * @return
		 * @throws IOException
		 */
		@Override
		public File wrapInputStreamToFile(final InputStream in) throws IOException {
			return this.wrapInputStreamToFile("prefix_",
			                                  "_suffix",
			                                  in);
		}

		/**
		 * @param in
		 * @return
		 */
		@Override
		public File wrapInputStreamToFile(final String prefix,
		                                  final String suffix,
		                                  final InputStream in) throws IOException {
			File tmpFile = null;
			FileOutputStream fout = null;
			try {
				tmpFile = this.createTempFile(prefix,
				                              suffix)
				              .exportFile();
				fout = new FileOutputStream(tmpFile);


				final long copied = StreamAssist.copy(in,
				                                      fout,
				                                      -1);
				if (log.isTraceEnabled()) {
					log.trace(StringAssist.format("[%s bytes] copied",
					                              copied));
				}
				return tmpFile;
			}
			finally {
				ObjectFinalizer.close(fout);
			}
		}

		/**
		 * @return
		 */
		@Override
		public TempDirectory createTempDirectory() {
			return this.createTempDirectory("_random_",
			                                "_directory_");
		}

		/**
		 * @param prefix
		 * @param suffix
		 * @return
		 */
		@Override
		public TempDirectory createTempDirectory(final String prefix,
		                                         final String suffix) {
			return this.createTempDirectory(StringAssist.format("%s%s%s",
			                                                    prefix,
			                                                    RandomType.specialExcept.random(12),
			                                                    suffix));
		}

		/**
		 * @param name
		 * @return
		 */
		@Override
		public TempDirectory createTempDirectory(final String name) {
			final File file = new File(this.root,
			                           name);

			if (file.exists() && file.isFile()) {
				throw new RuntimeException(StringAssist.format("create temp file fail (already file instance exist) [%s]",
				                                               file.getAbsolutePath()));
			}

			if (!file.exists()) {
				if (!file.mkdir()) {
					throw new RuntimeException(StringAssist.format("create temp file fail (mkdir fail) [%s]",
					                                               file.getAbsolutePath()));
				}
			}

			return new TempDirectoryLocal(this,
			                              file);
		}
	}

	// ---------------------------------------------------------------------
	// Section
	// ---------------------------------------------------------------------
	static boolean checkRange(byte[] b,
	                          int off,
	                          int len) {
		if (b == null) {
			throw new NullPointerException();
		} else if (off < 0 || off > b.length || len < 0 || off + len > b.length || off + len < 0) {
			throw new IndexOutOfBoundsException();
		} else return len != 0;
	}

	/**
	 * @param file
	 * @param charset
	 * @return
	 * @throws IOException
	 */
	static BufferedReader openFileReader(final File file,
	                                     final CharSets charset) throws IOException {
		// check exist and writable
		if (!file.exists() || !file.canRead()) {
			throw new IOException(StringAssist.format("[%s] cannot read file!",
			                                          file.getAbsolutePath()));
		}
		return new BufferedReader(new InputStreamReader(new FileInputStream(file),
		                                                charset.getCharset()));
	}

	/**
	 * @param file
	 * @param charset
	 * @return
	 * @throws IOException
	 */
	static BufferedWriter openFileWriter(final File file,
	                                     final CharSets charset) throws IOException {
		// check exist and writable
		if (file.exists() && !file.canWrite()) {
			throw new IOException(StringAssist.format("[%s] cannot write!",
			                                          file.getAbsolutePath()));
		}
		return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file),
		                                                 charset.getCharset()));
	}

	static InputStream openResourceStream(String name) {
		if (StringUtils.isEmpty(name)) {
			throw new IllegalArgumentException("name is empty!");
		}
		InputStream in = null;
		try {
			in = Thread.currentThread()
			           .getContextClassLoader()
			           .getResourceAsStream(name);

			if (in == null) {
				in = ClassLoader.getSystemResourceAsStream(name);
			}

			if (in == null) {
				File file = new File(name);
				in = file.exists() && file.canRead()
				     ? new FileInputStream(file)
				     : null;
			}

			if (in == null) {
				throw new IOException(StringAssist.format("[%s] not found",
				                                          name));
			}

			return in;
		}
		catch (IOException ioe) {
			throw new IllegalStateException("read resource fail!",
			                                ioe);
		}
	}

	/**
	 * @param file
	 * @return
	 */
	static String getExtension(final File file) {
		return getExtension(file.getName());
	}

	/**
	 * @param path
	 * @return
	 */
	static String getExtension(final String path) {
		String[] elms = StringAssist.split(path,
		                                   '.',
		                                   true,
		                                   true);

		if (elms.length == 0) {
			return ConstValues.BLANK_STRING;
		}
		return elms[elms.length - 1];
	}

	static String getName(final File file) {
		return getName(file.getName());
	}

	/**
	 * @param path
	 * @return
	 */
	static String getName(final String path) {
		final int index = path.lastIndexOf('.');

		return index < 0
		       ? path
		       : path.substring(0,
		                        index);
	}

	static File of(File root,
	               String name) {
		return root == null
		       ? new File(name)
		       : new File(root,
		                  name);
	}

	static int getPort(URL url) {
		return url.getPort() > 0
		       ? url.getPort()
		       : url.getDefaultPort();
	}

	static byte[] readResourceStream(final String name) {
		InputStream in = null;
		try {
			in = openResourceStream(name);

			return StreamAssist.read(in);
		}
		finally {
			ObjectFinalizer.close(in);
		}
	}

	/**
	 * @param name
	 * @param charset
	 * @return
	 * @throws IOException
	 */
	static String readResourceStream(final String name,
	                                 final CharSets charset) {
		InputStream in = null;
		try {
			in = openResourceStream(name);

			return readStream(in,
			                  charset);
		}
		finally {
			ObjectFinalizer.close(in);
		}
	}

	/**
	 * @param in
	 * @param charset
	 * @return
	 */
	static String readStream(final InputStream in,
	                         final CharSets charset) {
		ByteArrayOutputStreamExt out = null;
		try {
			out = new ByteArrayOutputStreamExt();

			StreamAssist.copy(in,
			                  out,
			                  -1);

			return out.toString(charset);
		}
		finally {
			ObjectFinalizer.close(out);
		}
	}

	/**
	 * @param clazz
	 * @param name
	 * @param charset
	 * @return
	 */
	static String readResourceStream(final Class<?> clazz,
	                                 final String name,
	                                 final CharSets charset) {
		if (StringUtils.isEmpty(name)) {
			throw new IllegalArgumentException("name is empty!");
		}
		InputStream in = null;
		try {
			in = clazz.getResourceAsStream(name);

			if (in == null) {
				return null;
			}

			return readStream(in,
			                  charset);
		}
		finally {
			ObjectFinalizer.close(in);
		}
	}

	@Deprecated
	static void readFully(final InputStream in,
	                      final byte[] b) throws IOException {
		StreamAssist.readFully(in,
		                       b,
		                       0,
		                       b.length);
	}

	@Deprecated
	static void readFully(final InputStream in,
	                      final byte[] b,
	                      final int off,
	                      final int len) throws IOException {
		StreamAssist.readFully(in,
		                       b,
		                       off,
		                       len);
	}
}
