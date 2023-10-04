package gaia.common;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.BitSet;

import gaia.ConstValues;


/**
 * @author dragon
 * @since 2020. 11. 30.
 */
public interface MimeSpecification {
	enum MimeCodec
			implements StreamMapper,
			           ByteArrayMapper {
		/**
		 *
		 */
		BASE64(Base64CodecImpl.class),
		/**
		 * Quoted-printable
		 */
		QP(QPCodecImpl.class),
		/**
		 *
		 */
		BIT8(null);

		/**
		 *
		 */
		final StreamMapper impl;

		/**
		 * @param type type of mapper
		 */
		MimeCodec(Class<? extends StreamMapper> type) {
			try {
				this.impl = type == null
				            ? null
				            : type.getDeclaredConstructor()
				                  .newInstance();
			}
			catch (Exception e) {
				throw new Error(e);
			}
		}

		/**
		 *
		 */
		public String encodeString(byte[] bytes,
		                           Object option) {
			return new String(this.encode(bytes,
			                              option));
		}

		/**
		 *
		 */
		@Override
		public void encode(InputStream in,
		                   OutputStream out,
		                   Object option) throws IOException {
			if (this.impl == null) {
				StreamAssist.copy(in,
				                  out,
				                  -1);
				return;
			}
			this.impl.encode(in,
			                 out,
			                 option);
		}

		@Override
		public OutputStream wrapEncodeStream(OutputStream out,
		                                     Object option) {
			if (this.impl == null) {
				return out;
			}
			return this.impl.wrapEncodeStream(out,
			                                  option);
		}

		@Override
		public void decode(InputStream in,
		                   OutputStream out,
		                   Object option) throws IOException {
			if (this.impl == null) {
				StreamAssist.copy(in,
				                  out,
				                  -1);
				return;
			}
			this.impl.decode(in,
			                 out,
			                 option);
		}

		/**
		 *
		 */
		@Override
		public OutputStream wrapDecodeStream(OutputStream out,
		                                     Object option) {
			if (this.impl == null) {
				return out;
			}
			return this.impl.wrapDecodeStream(out,
			                                  option);
		}

		@Override
		public byte[] encode(byte[] bytes) {
			return this.encode(bytes,
			                   null);
		}

		@Override
		public byte[] encode(byte[] bytes,
		                     Object option) {
			if (bytes == null) {
				return null;
			}
			return this.encode(bytes,
			                   0,
			                   bytes.length,
			                   option);
		}

		@Override
		public byte[] encode(byte[] bytes,
		                     int offset,
		                     int length) {
			return this.encode(bytes,
			                   offset,
			                   length,
			                   null);
		}

		@Override
		public byte[] encode(byte[] bytes,
		                     int offset,
		                     int length,
		                     Object option) {
			if (bytes == null) {
				return null;
			}

			ByteArrayInputStream in = null;
			ByteArrayOutputStream out = null;
			try {
				in = new ByteArrayInputStream(bytes,
				                              offset,
				                              length);
				out = new ByteArrayOutputStream();

				this.encode(in,
				            out,
				            option);

				return out.toByteArray();
			}
			catch (Exception thw) {
				throw new Error("base64 decode error?",
				                thw);
			}
			finally {
				ObjectFinalizer.close(out);
			}
		}

		@Override
		public byte[] decode(byte[] bytes) {
			return this.decode(bytes,
			                   null);
		}

		@Override
		public byte[] decode(byte[] bytes,
		                     Object option) {
			if (bytes == null) {
				return null;
			}

			return this.decode(bytes,
			                   0,
			                   bytes.length,
			                   option);
		}

		@Override
		public byte[] decode(byte[] bytes,
		                     int offset,
		                     int length) {
			return this.decode(bytes,
			                   offset,
			                   length,
			                   null);
		}

		@Override
		public byte[] decode(byte[] bytes,
		                     int offset,
		                     int length,
		                     Object option) {
			ByteArrayInputStream in = null;
			ByteArrayOutputStream out = null;
			try {
				in = new ByteArrayInputStream(bytes,
				                              offset,
				                              length);
				out = new ByteArrayOutputStream();

				this.decode(in,
				            out,
				            option);

				return out.toByteArray();
			}
			catch (ArrayIndexOutOfBoundsException a) {
				throw new MimeCodecException("invalid input array",
				                             a);
			}
			catch (Exception thw) {
				throw new Error("base64 decode error?",
				                thw);
			}
			finally {
				ObjectFinalizer.close(out);
			}
		}

	}

	interface StreamMapper {
		/**
		 *
		 */
		void encode(InputStream in,
		            OutputStream out,
		            Object option) throws IOException;

		/**
		 *
		 */
		OutputStream wrapEncodeStream(OutputStream out,
		                              Object option);

		void decode(InputStream in,
		            OutputStream out,
		            Object option) throws IOException;

		/**
		 *
		 */
		OutputStream wrapDecodeStream(OutputStream out,
		                              Object option);
	}

	interface ByteArrayMapper {
		/**
		 *
		 */
		byte[] encode(byte[] bytes);

		/**
		 *
		 */
		byte[] encode(byte[] bytes,
		              Object option);

		/**
		 *
		 */
		byte[] encode(byte[] bytes,
		              int offset,
		              int length);

		/**
		 *
		 */
		byte[] encode(byte[] bytes,
		              int offset,
		              int length,
		              Object option);

		/**
		 *
		 */
		byte[] decode(byte[] bytes);

		/**
		 *
		 */
		byte[] decode(byte[] bytes,
		              Object option);

		/**
		 *
		 */
		byte[] decode(byte[] bytes,
		              int offset,
		              int length);

		/**
		 *
		 */
		byte[] decode(byte[] bytes,
		              int offset,
		              int length,
		              Object option);
	}

	byte BASE64_PAD = (byte) '=';

	char[] Base64Table = FunctionAssist.supply("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"::toCharArray,
	                                           thw -> {
		                                           throw new Error("");
	                                           });

	byte[] Base64LookupTable = FunctionAssist.supply(() -> {
		                                                 byte[] array = new byte[256];
		                                                 for (int i = 0; i < 255; i++) {
			                                                 array[i] = -1;
		                                                 }

		                                                 for (int j = 0; j < Base64Table.length; j++) {
			                                                 array[Base64Table[j]] = (byte) j;
		                                                 }
		                                                 return array;
	                                                 },
	                                                 thw -> {
		                                                 throw new Error("");
	                                                 });

	static boolean canDecodeBase64(byte octect) {
		if (octect == BASE64_PAD) {
			return true;
		} else return Base64LookupTable[octect] != -1;
	}

	static int decode_base64_sep(byte[] source,
	                             byte[] target) {
		if (source.length != 4) {
			throw new RuntimeException("INVAILD SIZE");
		}

		byte L1 = 0;
		byte L2 = 0;

		byte byte0 = Base64LookupTable[source[0] & 0xff];
		byte byte1 = Base64LookupTable[source[1] & 0xff];
		L1 = (byte) (byte0 << 2 & 0xfc | byte1 >>> 4 & 0x3);
		if (source[2] == 61) {
			target[0] = L1;
			return 1;
		}

		byte0 = byte1;
		byte1 = Base64LookupTable[source[2] & 0xff];
		L2 = (byte) (byte0 << 4 & 0xf0 | byte1 >>> 2 & 0xf);
		if (source[3] == 61) {
			target[0] = L1;
			target[1] = L2;
			return 2;
		}

		target[0] = L1;
		target[1] = L2;

		byte0 = byte1;
		byte1 = Base64LookupTable[source[3] & 0xff];
		target[2] = (byte) (byte0 << 6 & 0xc0 | byte1 & 0x3f);

		return 3;
	}

	final class Base64CodecImpl
			implements StreamMapper {
		/**
		 *
		 */
		@Override
		public void encode(InputStream in,
		                   OutputStream out,
		                   Object option) {
			final int limit = PrimitiveTypes.parseInteger(option,
			                                              -1);
			Base64EncodeStream stream = new Base64EncodeStream(out,
			                                                   limit);
			try {
				StreamAssist.copy(in,
				                  stream,
				                  -1);
			}
			finally {
				ObjectFinalizer.close(stream);
			}
		}

		@Override
		public OutputStream wrapEncodeStream(final OutputStream out,
		                                     final Object option) {
			final int limit = null == option
			                  ? -1
			                  : PrimitiveTypes.parseInteger(option,
			                                                -1);
			return new Base64EncodeStream(out,
			                              limit);
		}

		@Override
		public void decode(InputStream in,
		                   OutputStream out,
		                   Object option) {
			Base64DecodeStream stream = new Base64DecodeStream(out);
			try {
				StreamAssist.copy(in,
				                  stream,
				                  -1);
			}
			finally {
				ObjectFinalizer.close(stream);
			}
		}

		@Override
		public OutputStream wrapDecodeStream(OutputStream out,
		                                     Object option) {
			return new Base64DecodeStream(out);
		}
	}

	/**
	 * @author dragon
	 */
	class Base64DecodeStream
			extends FilterOutputStream {
		/**
		 *
		 */
		private final byte[] bufferSource = new byte[4];

		/**
		 *
		 */
		private final byte[] bufferTarget = new byte[3];

		/**
		 *
		 */
		private int sourceIndex;

		/**
		 *
		 */
		public Base64DecodeStream(OutputStream out) {
			super(wrap(out));
		}

		/**
		 *
		 */
		@Override
		public void write(int b) throws IOException {
			if (canDecodeBase64((byte) b)) {
				this.bufferSource[this.sourceIndex++] = (byte) b;
				this.decode();
			}
		}

		/**
		 *
		 */
		@Override
		public void flush() throws IOException {
			this.decode();
			super.flush();
		}

		/**
		 *
		 */
		@Override
		public void close() throws IOException {
			if (this.sourceIndex > 0) {
				throw new IllegalStateException(StringAssist.format("undecoded bytes remain! [%s]",
				                                                    this.sourceIndex));
			}
			super.flush();
		}

		/**
		 *
		 */
		private void decode() throws IOException {
			if (this.sourceIndex < 4) {
				return;
			}
			final int count = decode_base64_sep(this.bufferSource,
			                                    this.bufferTarget);
			for (int i = 0; i < count; i++) {
				super.write(this.bufferTarget[i]);
			}
			this.sourceIndex = 0;
		}
	}

	/**
	 * @author dragon
	 */
	class Base64EncodeStream
			extends FilterOutputStream {
		/**
		 *
		 */
		private final byte[] bufferSource = new byte[3];

		/**
		 *
		 */
		private final byte[] bufferTarget = new byte[4];

		/**
		 *
		 */
		private final int limit;

		/**
		 *
		 */
		private int sourceIndex;

		/**
		 *
		 */
		private int writeCount;

		/**
		 *
		 */
		public Base64EncodeStream(OutputStream out,
		                          int limit) {
			super(wrap(out));
			this.limit = limit;
		}

		/**
		 *
		 */
		@Override
		public void write(int b) throws IOException {
			this.bufferSource[this.sourceIndex++] = (byte) b;
			if (this.sourceIndex == 3) {
				this.encode();
				this.sourceIndex = 0;
			}
		}

		/**
		 *
		 */
		@Override
		public void flush() throws IOException {
			if (this.sourceIndex > 0) {
				this.encode();
				this.sourceIndex = 0;
			}

			super.flush();
		}

		/**
		 *
		 */
		@Override
		public void close() throws IOException {
			this.flush();
		}

		/**
		 *
		 */
		private void encode() throws IOException {
			if (this.limit > 0 && this.writeCount + 4 > this.limit) {
				super.write(13);
				super.write(10);
				this.writeCount = 0;
			}

			encode_base64_sep(this.bufferSource,
			                  this.sourceIndex,
			                  this.bufferTarget);

			for (int i = 0; i < 4; i++) {
				super.write(this.bufferTarget[i]);
			}

			this.writeCount += 4;
		}
	}

	char[] QP_hex = "0123456789ABCDEF".toCharArray();

	int[] convert = FunctionAssist.supply(() -> {
		final int[] array = new int[256];

		for (int i = 0; i < 256; i++) {
			array[i] = -1;
		}

		for (int i = 0; i < 16; i++) {
			array[QP_hex[i]] = i;
		}

		return array;
	});

	BitSet PRINTABLE_CHARS = FunctionAssist.supply(() -> {
		final BitSet set = new BitSet(256);

		for (int i = 33; i <= 60; i++) {
			set.set(i);
		}
		for (int i = 62; i <= 126; i++) {
			set.set(i);
		}
		set.set(ConstValues.TAB_BYTE);
		set.set(ConstValues.SPACE_BYTE);

		return set;
	});

	final class QPCodecImpl
			implements StreamMapper {

		/**
		 *
		 */
		@Override
		public void encode(final InputStream in,
		                   final OutputStream out,
		                   final Object option) throws IOException {
			final int limit = PrimitiveTypes.parseInteger(option,
			                                              -1);

			OutputStream stream = null;
			try {
				stream = new QPEncodeStream(out,
				                            limit);

				StreamAssist.copy(in,
				                  stream,
				                  -1);

				stream.flush();
			}
			finally {

				ObjectFinalizer.close(stream);
			}
		}

		@Override
		public OutputStream wrapEncodeStream(final OutputStream out,
		                                     final Object option) {
			final int limit = PrimitiveTypes.parseInteger(option,
			                                              -1);
			return new QPEncodeStream(out,
			                          limit);
		}

		@Override
		public void decode(final InputStream in,
		                   final OutputStream out,
		                   final Object option) throws IOException {
			OutputStream stream = null;
			try {
				stream = new QPDecodeStream(out);

				StreamAssist.copy(in,
				                  stream,
				                  -1);

				stream.flush();
			}
			finally {
				ObjectFinalizer.close(stream);
			}
		}

		@Override
		public OutputStream wrapDecodeStream(OutputStream out,
		                                     final Object option) {
			return new QPDecodeStream(out);
		}

	}

	/**
	 * @author dragon
	 */
	class QPDecodeStream
			extends FilterOutputStream {
		/**
		 *
		 */
		private final int[] bufferSource = new int[2];

		/**
		 *
		 */
		private int sourceIndex;

		/**
		 *
		 */
		private transient boolean escape = false;

		/**
		 *
		 */
		public QPDecodeStream(OutputStream out) {
			super(wrap(out));
		}

		/**
		 *
		 */
		@Override
		public void write(int b) throws IOException {
			switch (b) {
				case ConstValues.BYTE_EQUAL: {
					if (this.escape) {
						throw new IllegalStateException("duplicated escape character!");

					}
					this.escape = true;
					break;
				}

				case ConstValues.BYTE_CR:
				case ConstValues.BYTE_LF: {
					break;
				}

				default: {
					if (this.escape) {
						this.bufferSource[this.sourceIndex++] = b;

						if (this.sourceIndex == 2) {
							final int u = convert[this.bufferSource[0]];
							final int l = convert[this.bufferSource[1]];

							if (u < 0 || l < 0) {
								throw new IllegalStateException("invalid 16 raix char!");
							}

							super.write((char) ((u << 4) + l));
							this.sourceIndex = 0;
							this.escape = false;
						}
					} else {
						super.write(b);
					}
					break;
				}
			}
		}

		/**
		 *
		 */
		@Override
		public void close() throws IOException {
			if (this.escape) {
				throw new IllegalStateException(StringAssist.format("undecoded bytes remain! [%s]",
				                                                    this.sourceIndex));
			}
			super.flush();
		}
	}

	/**
	 * @author dragon
	 */
	class QPEncodeStream
			extends FilterOutputStream {
		/**
		 *
		 */
		private final int limit;

		/**
		 *
		 */
		private int count = 0;

		/**
		 *
		 */
		public QPEncodeStream(OutputStream out,
		                      int limit) {
			super(wrap(out));
			this.limit = limit;
		}

		/**
		 *
		 */
		@Override
		public void write(final int i) throws IOException {
			final int b = i < 0
			              ? i + 256
			              : i;
			this.output(b,
			            PRINTABLE_CHARS.get(b));
		}

		/**
		 *
		 */
		protected void output(int i,
		                      boolean flag) throws IOException {
			if (flag) {
				if (this.limit > 0 && this.count + 1 > this.limit) {
					super.write(13);
					super.write(10);
					super.flush();
					this.count = 0;
				}
				super.write(i);
				this.count++;
				return;
			}

			if (this.limit > 0 && this.count + 3 > this.limit) {
				super.write(13);
				super.write(10);
				super.flush();
				this.count = 0;
			}
			super.write(ConstValues.BYTE_EQUAL);
			super.write(QP_hex[i >> 4]);
			super.write(QP_hex[i & 0xf]);
			this.count += 3;
		}
	}

	class MimeCodecException
			extends RuntimeException {
		public MimeCodecException(String message,
		                          Throwable cause) {
			super(message,
			      cause);
		}
	}

	/**
	 *
	 */
	static OutputStream wrap(OutputStream out) {
		if (out instanceof ByteArrayOutputStream) {
			return out;
		}

		if (out instanceof BufferedOutputStream) {
			return out;
		}
		return new BufferedOutputStream(out);
	}

	static void encode_base64_sep(final byte[] source,
	                              final int length,
	                              final byte[] target) {
		final int calculatedLength = Math.min(length,
		                                      3);

		switch (calculatedLength) {
			case 1: {
				target[0] = (byte) Base64Table[source[0] >>> 2 & 0x3f];
				target[1] = (byte) Base64Table[(source[0] << 4 & 0x30)];
				target[2] = 61;
				target[3] = 61;
				break;
			}
			case 2: {
				target[0] = (byte) Base64Table[source[0] >>> 2 & 0x3f];
				target[1] = (byte) Base64Table[(source[0] << 4 & 0x30) + (source[1] >>> 4 & 0xf)];
				target[2] = (byte) Base64Table[(source[1] << 2 & 0x3c)];
				target[3] = 61;
				break;
			}
			case 3: {
				target[0] = (byte) Base64Table[source[0] >>> 2 & 0x3f];
				target[1] = (byte) Base64Table[(source[0] << 4 & 0x30) + (source[1] >>> 4 & 0xf)];
				target[2] = (byte) Base64Table[(source[1] << 2 & 0x3c) + (source[2] >>> 6 & 0x3)];
				target[3] = (byte) Base64Table[source[2] & 0x3f];
				break;
			}
		}
	}
}
