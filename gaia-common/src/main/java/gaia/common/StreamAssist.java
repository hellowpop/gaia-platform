package gaia.common;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gaia.common.CharsetSpec.ByteArrayOutputStreamExt;
import gaia.common.CharsetSpec.CharSets;
import gaia.common.FunctionAssist.ErrorHandleConsumer;
import gaia.common.FunctionAssist.ErrorHandleFunction;
import gaia.common.FunctionAssist.ErrorHandleSupplier;
import gaia.common.ResourceAssist.ResourceContainer;


public interface StreamAssist {
	Logger log = LoggerFactory.getLogger(StreamAssist.class);

	interface StreamBlockInfo {
		/**
		 * @param sequence sequence
		 * @param block    block
		 * @param length   length
		 * @return true : indicate iterate stop or else continue scan
		 */
		boolean arrive(int sequence,
		               byte[] block,
		               int length);
	}

	class CountOutputStream
			extends OutputStream {
		final AtomicLong counter = new AtomicLong();

		public final long length() {
			return this.counter.get();
		}

		@Override
		public void write(int b) {
			this.counter.getAndIncrement();
		}

		@Override
		public void write(byte[] b,
		                  int off,
		                  int len) {
			checkRange(b,
			           off,
			           len);

			this.counter.getAndAdd(len);
		}
	}

	static void checkRange(byte[] b,
	                       int off,
	                       int len) {
		if (b == null) {
			throw new NullPointerException();
		} else if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0)) {
			throw new IndexOutOfBoundsException();
		}
	}

	ResourceContainer<ByteBuffer> byteBufferContainer = new ResourceContainer<ByteBuffer>() {
		@Override
		protected ByteBuffer create() {
			return ByteBuffer.allocate(4096);
		}
	};

	static void acceptByteBuffer(ErrorHandleConsumer<ByteBuffer> consumer) {
		ByteBuffer buffer = null;
		try {
			buffer = byteBufferContainer.getResource();

			FunctionAssist.consume(buffer,
			                       consumer);
		}
		finally {
			byteBufferContainer.recycle(buffer);
		}
	}

	static <T> T functionByteBuffer(ErrorHandleFunction<ByteBuffer, T> function) {
		ByteBuffer buffer = null;
		try {
			buffer = byteBufferContainer.getResource();

			return FunctionAssist.function(buffer,
			                               function);
		}
		finally {
			byteBufferContainer.recycle(buffer);
		}
	}

	/**
	 * check ThreadAttribute
	 */
	ResourceContainer<byte[]> byteArrayContainer = new ResourceContainer<byte[]>() {
		@Override
		protected byte[] create() {
			return new byte[4096];
		}
	};

	static void arrayAccept(ErrorHandleConsumer<byte[]> consumer) {
		byte[] buffer = null;
		try {
			buffer = byteArrayContainer.getResource();

			FunctionAssist.consume(buffer,
			                       consumer);
		}
		finally {
			byteArrayContainer.recycle(buffer);
		}
	}

	static <T> T arrayFunction(ErrorHandleFunction<byte[], T> function) {
		byte[] buffer = null;
		try {
			buffer = byteArrayContainer.getResource();

			return FunctionAssist.function(buffer,
			                               function);
		}
		finally {
			byteArrayContainer.recycle(buffer);
		}
	}

	static ReadableByteChannel wrap(InputStream stream) {
		return null == stream
		       ? null
		       : Channels.newChannel(stream);
	}

	static WritableByteChannel wrap(OutputStream stream) {
		return null == stream
		       ? null
		       : Channels.newChannel(stream);
	}

	// ---------------------------------------------------------------------
	// Section read
	// ---------------------------------------------------------------------
	static long count(final ErrorHandleSupplier<InputStream> supplier) {
		InputStream in = null;
		CountOutputStream out = null;
		try {
			in = FunctionAssist.supply(supplier);

			out = new CountOutputStream();

			copy(in,
			     out,
			     -1);

			return out.length();
		}
		finally {
			ObjectFinalizer.close(in);
		}
	}

	static String read(final InputStream in,
	                   final CharSets charset) {
		ByteArrayOutputStreamExt out = null;
		try {
			out = new ByteArrayOutputStreamExt();

			copy(in,
			     out,
			     -1);

			return out.toString(charset);
		}
		finally {
			ObjectFinalizer.close(out);
		}
	}

	static byte[] read(final ErrorHandleSupplier<InputStream> supplier) {
		InputStream in = null;
		try {
			in = FunctionAssist.supply(supplier);
			return read(in);
		}
		finally {
			ObjectFinalizer.close(in);
		}
	}

	static byte[] read(final InputStream in) {
		ByteArrayOutputStream out = null;
		try {
			out = new ByteArrayOutputStream();

			copy(in,
			     out,
			     -1);

			return out.toByteArray();
		}
		finally {
			ObjectFinalizer.close(out);
		}
	}

	static byte[] read(URL url) throws IOException {
		try (InputStream in = url.openStream()) {
			return read(in);
		}
	}

	static void readFully(final InputStream in,
	                      final byte[] b) throws IOException {
		readFully(in,
		          b,
		          0,
		          b.length);
	}

	static void readFully(final InputStream in,
	                      final byte[] b,
	                      final int off,
	                      final int len) throws IOException {
		int n = 0;
		do {
			int count = in.read(b,
			                    off + n,
			                    len - n);
			if (count < 0) {
				throw new EOFException(StringAssist.format("[%s] require but [%s] end.",
				                                           len,
				                                           n));
			}
			n += count;
		} while (n < len);
	}

	// ---------------------------------------------------------------------
	// Section copy by stream
	// ---------------------------------------------------------------------
	static void transfer(File file,
	                     BiConsumer<byte[], Integer> consumer) {
		try (FileInputStream stream = new FileInputStream(file)) {
			arrayAccept(buffer -> transfer(buffer,
			                               stream,
			                               -1,
			                               consumer));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("UnusedReturnValue")
	static long copy(final InputStream in,
	                 final OutputStream out) {
		return arrayFunction(buffer -> copy(buffer,
		                                    in,
		                                    out,
		                                    -1L));

	}

	static long copy(final InputStream in,
	                 final OutputStream out,
	                 final long length) {
		return arrayFunction(buffer -> copy(buffer,
		                                    in,
		                                    out,
		                                    length));
	}

	static long copy(final byte[] buffer,
	                 final InputStream in,
	                 final OutputStream out,
	                 final long length) throws IOException {
		return transfer(buffer,
		                in,
		                length,
		                (buf, size) -> FunctionAssist.execute(() -> out.write(buf,
		                                                                      0,
		                                                                      size)));
	}

	static long transfer(final byte[] buffer,
	                     final InputStream in,
	                     final long length,
	                     final BiConsumer<byte[], Integer> consumer) throws IOException {
		long remain = length < 0
		              ? Long.MAX_VALUE
		              : length;
		long transfer = 0L;

		while (remain > 0) {
			final int size = in.read(buffer);

			if (size < 0) {
				if (length > 0) {
					throw new EOFException();
				}
				break;
			}

			if (size == 0) {
				continue;
			}

			consumer.accept(buffer,
			                size);

			transfer += size;
			remain -= size;
		}

		return transfer;
	}

	// ---------------------------------------------------------------------
	// Section copy by channel
	// ---------------------------------------------------------------------

	static long copy(final ReadableByteChannel in,
	                 final WritableByteChannel out) {
		return copy(in,
		            out,
		            -1L);
	}

	static long copy(final ReadableByteChannel in,
	                 final WritableByteChannel out,
	                 final long length) {
		return functionByteBuffer(buffer -> copy(buffer,
		                                         in,
		                                         out,
		                                         length));
	}

	static long copy(final ByteBuffer buffer,
	                 final InputStream inputStream,
	                 final OutputStream outputStream) throws IOException {
		return copy(buffer,
		            Channels.newChannel(inputStream),
		            Channels.newChannel(outputStream),
		            -1L);
	}

	static long copy(final ByteBuffer buffer,
	                 final ReadableByteChannel in,
	                 final WritableByteChannel out) throws IOException {
		return copy(buffer,
		            in,
		            out,
		            -1L);
	}

	static long copy(final ByteBuffer buffer,
	                 final ReadableByteChannel in,
	                 final WritableByteChannel out,
	                 final long length) throws IOException {
		long remain = length < 0
		              ? Long.MAX_VALUE
		              : length;
		long transfer = 0L;

		while (remain > 0) {
			final int copyLength = remain > buffer.capacity()
			                       ? buffer.capacity()
			                       : BitsAssist.getInt(remain);
			// clear buffer
			buffer.clear();

			// limit read size
			buffer.limit(copyLength);

			final int size = in.read(buffer);

			if (size < 0) {
				if (length > 0) {
					throw new EOFException();
				}
				break;
			}

			if (size == 0) {
				continue;
			}

			buffer.flip();

			int copied = 0;

			while (buffer.hasRemaining()) {
				copied += out.write(buffer);
			}

			if (copied != size) {
				throw new EOFException(StringAssist.format("read bytes copy different? [copy : %s][ read: %s]",
				                                           copied,
				                                           size));
			}

			transfer += size;
			remain -= size;
		}

		return transfer;
	}

	static void iterateStream(final InputStream in,
	                          final int block,
	                          final StreamBlockInfo consumer) throws IOException {
		final byte[] buffer = new byte[block];

		AtomicInteger sequence = new AtomicInteger(0);

		int length = -1;

		do {
			length = in.read(buffer);

			if (length > 0 && consumer.arrive(sequence.getAndIncrement(),
			                                  buffer,
			                                  length)) {
				// stop when find
				return;
			}
		} while (length > 0);
	}
}
