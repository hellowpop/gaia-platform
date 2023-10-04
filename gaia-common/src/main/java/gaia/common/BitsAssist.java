package gaia.common;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import gaia.common.CharsetSpec.ByteArrayOutputStreamExt;
import gaia.common.CharsetSpec.CharSets;


/**
 * @author dragon
 */
public interface BitsAssist {
	int ALL_MASK_1 = 0x000000FF;

	int ALL_REVERT_1 = ~ALL_MASK_1;

	int ALL_MASK_2 = 0x0000FF00;

	int ALL_MASK_3 = 0x00FF0000;

	int ALL_MASK_4 = 0xFF000000;

	long FULL_LONG = ~0L;

	int FULL_INT = ~0;

	int FULL_SHORT = (short) ~0;

	int FULL_BYTE = (byte) ~0;

	char[] digits = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();

	char[] HEXA_DIGIT = "0123456789abcdefABCDEF".toCharArray();

	static void validateArrayLength(byte[] bytes,
	                                int offset,
	                                int check) {
		assert null != bytes
				: "array cannot be null";
		if (bytes.length < offset + check)
			throw new IllegalArgumentException(StringAssist.format("length validate fail [length : %s][offset: %s][length: %s]",
			                                                       bytes.length,
			                                                       offset,
			                                                       check));
	}

	static byte plus(byte a,
	                 byte b) {
		int carry = a & b;
		int result = a ^ b;
		while (carry != 0) {
			int shiftedcarry = carry << 1;
			carry = result & shiftedcarry;
			result ^= shiftedcarry;
		}
		return (byte) (result & 0xFF);
	}

	static boolean compare(final byte[] source,
	                       final byte[] target) {
		if (source == null && target == null) {
			return true;
		}

		if (source == null || target == null) {
			return false;
		}

		if (source.length != target.length) {
			return false;
		}

		final int length = source.length;
		for (int i = 0; i < length; i++) {
			if (source[i] != target[i]) {
				return false;
			}
		}

		return true;
	}

	static byte hexToByte(final char a,
	                      final char b) {
		int first = getHexIndex(a);

		int second = getHexIndex(b);

		return BitsAssist.getByte(first << 4 | second);
	}

	static int getHexIndex(char c) {
		int first = ArrayAssist.findArrayIndex(HEXA_DIGIT,
		                                       c);
		if (first < 0) {
			throw new IllegalArgumentException("invalid.hex.value:" + c);
		}

		if (first > 15) {
			first -= 6;
		}

		return first;
	}

	static byte getByte(int value) {
		return (byte) (value & 0xFF);
	}

	static byte read(InputStream in) throws IOException {
		int read = in.read();

		if (read < 0) {
			throw new EOFException();
		}

		return (byte) (read & 0xFF);
	}

	static long readAsLong(InputStream in) throws IOException {
		int read = in.read();

		if (read < 0) {
			throw new EOFException();
		}

		return read & 0xFFL;
	}

	static String toBinaryString(final int i,
	                             final int length) {
		return toUnsignedString(i,
		                        1,
		                        length);
	}

	// ---------------------------------------------------------------------
	// Section Checksum
	// ---------------------------------------------------------------------
	static String toUnsignedString(final int _i,
	                               final int shift,
	                               final int length) {
		char[] buf = new char[32];
		int charPos = 32;
		int radix = 1 << shift;
		int mask = radix - 1;
		int i = _i;
		int remain = length;
		do {
			buf[--charPos] = digits[i & mask];
			i >>>= shift;
		} while (charPos > 0 && --remain > 0);

		return new String(buf,
		                  charPos,
		                  32 - charPos);
	}

	static String toHexString(final int i,
	                          final int length) {
		return toUnsignedString(i,
		                        4,
		                        length);
	}

	static void toHexString(final StringBuilder buffer,
	                        final byte[] bytes) {
		for (final byte bt : bytes) {
			appendUnsignedString(buffer,
			                     bt,
			                     4,
			                     2);
		}
	}

	// -------------------------------------------------------------------------------------------------------
	static void appendUnsignedString(StringBuilder buffer,
	                                 final byte _i,
	                                 final int shift,
	                                 final int length) {
		int radix = 1 << shift;
		int mask = radix - 1;
		byte i = _i;
		int remain = length;
		do {
			buffer.insert(0,
			              digits[i & mask]);
			i >>>= shift;
		} while (--remain > 0);
	}

	static long genCheckSum(final byte[] buffer,
	                        final int length) {
		return genCheckSum(0L,
		                   buffer,
		                   length);
	}

	static long genCheckSum(final long base,
	                        final byte[] buffer,
	                        final int length) {
		return genCheckSum(base,
		                   buffer,
		                   0,
		                   length);
	}

	static long genCheckSum(final long base,
	                        final byte[] buffer,
	                        final int offset,
	                        final int length) {
		if (buffer.length < offset + length) {
			throw new IllegalArgumentException(StringAssist.format("buffer total [%s] but request off[%s] len[%s]",
			                                                       buffer.length,
			                                                       offset,
			                                                       length));
		}
		byte[] checksum = new byte[8];

		if (base != 0L) {
			putLong(0,
			        checksum,
			        0);
		}

		for (int i = 0; i < length; i++) {
			checksum[i % 8] ^= buffer[offset + i];
		}

		return getLong(checksum,
		               0);
	}

	static void putLong(long val,
	                    byte[] b,
	                    int off) {
		putLong(val,
		        b,
		        off,
		        ENDIAN.BIG);
	}

	static long getLong(byte[] b,
	                    int off) {
		return getLong(b,
		               off,
		               ENDIAN.BIG);
	}

	static void putLong(long val,
	                    byte[] b,
	                    int off,
	                    ENDIAN endian) {
		endian.setLong(val,
		               b,
		               off);
	}

	static long getLong(byte[] b,
	                    int off,
	                    ENDIAN e) {
		return e.getLong(b,
		                 off);

	}

	static long mark(long target,
	                 long mask) {
		return target | mask;
	}

	static long unmark(long target,
	                   long mask) {
		return target & ~mask;
	}

	static boolean isMarked(final long value,
	                        final long mask) {
		return (value & mask) == mask;
	}

	static boolean rangeMatch(final long mask,
	                          final long arg1,
	                          final long arg2) {
		return (mask & arg1) == (mask & arg2);
	}

	static int mark(int target,
	                int mask) {
		return target | mask;
	}

	static int unmark(int target,
	                  int mask) {
		return target & ~mask;
	}

	static boolean rangeMatch(final int mask,
	                          final int arg1,
	                          final int arg2) {
		return (mask & arg1) == (mask & arg2);
	}

	static boolean isMarked(final int value,
	                        final int mask) {
		return (value & mask) == mask;
	}

	static short mark(short target,
	                  short mask) {
		return (short) (target | mask);
	}

	static short unmark(short target,
	                    short mask) {
		return getShort(target & ~mask);
	}

	static short getShort(int value) {
		return (short) (value & 0xffff);
	}

	static boolean isMarked(final short value,
	                        final short mask) {
		return (value & mask) != 0;
	}

	static byte mark(byte target,
	                 byte mask) {
		return (byte) (target | mask);
	}

	static byte unmark(byte target,
	                   byte mask) {
		return getByte(target & ~mask);
	}

	static boolean isMarked(final byte value,
	                        final byte mask) {
		return (value & mask) != 0;
	}

	static boolean getBoolean(InputStream in) throws IOException {
		final int read = in.read();

		if (read < 0) {
			throw new EOFException();
		}

		return read != 0;
	}

	static boolean getBoolean(byte[] b,
	                          int off) {
		return b[off] != 0;
	}

	static void putBoolean(byte[] b,
	                       int off,
	                       boolean val) {
		b[off] = (byte) (val
		                 ? 1
		                 : 0);
	}

	static void putBoolean(boolean val,
	                       OutputStream out) throws IOException {
		putByte((byte) (val
		                ? 1
		                : 0),
		        out);
	}

	static void putByte(byte val,
	                    OutputStream out) throws IOException {
		out.write(val);
	}

	static void putByte(int val,
	                    OutputStream out) throws IOException {
		putByte((byte) (val & 0xFF),
		        out);
	}

	static void putChar(char val,
	                    byte[] b,
	                    int off) {
		putChar(val,
		        b,
		        off,
		        ENDIAN.BIG);
	}

	static void putChar(char val,
	                    byte[] b,
	                    int off,
	                    ENDIAN endian) {
		endian.setChar(val,
		               b,
		               off);
	}

	static void putChar(char val,
	                    OutputStream out) throws IOException {
		putChar(val,
		        out,
		        ENDIAN.BIG);
	}

	static void putChar(char val,
	                    OutputStream out,
	                    ENDIAN endian) throws IOException {
		endian.setChar(val,
		               out);
	}

	static char getChar(byte[] b,
	                    int off) {
		return getChar(b,
		               off,
		               ENDIAN.BIG);
	}

	static char getChar(byte[] b,
	                    int off,
	                    ENDIAN e) {
		return e.getChar(b,
		                 off);
	}

	static char getChar(InputStream in) throws IOException {
		return getChar(in,
		               ENDIAN.BIG);
	}

	static char getChar(InputStream in,
	                    ENDIAN e) throws IOException {
		return e.getChar(in);
	}

	static void putShort(int val,
	                     byte[] b,
	                     int off) {
		putShort((short) (val & 0xFFFF),
		         b,
		         off);
	}

	static void putShort(short val,
	                     byte[] b,
	                     int off) {
		putShort(val,
		         b,
		         off,
		         ENDIAN.BIG);
	}

	static void putShort(short val,
	                     byte[] b,
	                     int off,
	                     ENDIAN endian) {
		endian.setShort(val,
		                b,
		                off);
	}

	static void putUnsignedShort(int val,
	                             OutputStream out) throws IOException {
		putShort((short) (val & 0xFFFF),
		         out);
	}

	static void putShort(short val,
	                     OutputStream out) throws IOException {
		putShort(val,
		         out,
		         ENDIAN.BIG);
	}

	static void putShort(short val,
	                     OutputStream out,
	                     ENDIAN endian) throws IOException {
		endian.putShort(val,
		                out);
	}

	static void putUnsignedShort(int val,
	                             OutputStream out,
	                             ENDIAN endian) throws IOException {
		putShort((short) (val & 0xFFFF),
		         out,
		         endian);
	}

	static int getUnsignedShort(short value) {
		return value & 0xffff;
	}

	static byte[] getShortBytes(short val) {
		byte[] b = new byte[2];
		putShort(val,
		         b,
		         0);
		return b;
	}

	static short getShort(byte[] b,
	                      int off) {
		return getShort(b,
		                off,
		                ENDIAN.BIG);
	}

	static short getShort(byte[] b,
	                      int off,
	                      ENDIAN e) {
		return e.getShort(b,
		                  off);
	}

	static short getShort(InputStream in) throws IOException {
		return getShort(in,
		                ENDIAN.BIG);
	}

	static short getShort(InputStream in,
	                      ENDIAN e) throws IOException {
		return e.getShort(in);
	}

	static short getUnsignedByte(byte b,
	                             int off) {
		return (short) (b & 0xFF);
	}

	static short getUnsignedByte(InputStream in) throws IOException {
		return (short) (in.read() & 0xFF);
	}

	static byte getSignedByte(InputStream in) throws IOException {
		return (byte) (in.read() & 0xFF);
	}

	static int getUnsignedShort(byte[] b,
	                            int off) {
		return getUnsignedShort(b,
		                        off,
		                        ENDIAN.BIG);
	}

	static int getUnsignedShort(byte[] b,
	                            int off,
	                            ENDIAN e) {
		return e.getUnsignedShort(b,
		                          off);
	}

	static int getUnsignedShort(InputStream in) throws IOException {
		return getUnsignedShort(in,
		                        ENDIAN.BIG);
	}

	static int getUnsignedShort(InputStream in,
	                            ENDIAN e) throws IOException {
		return e.getUnsignedShort(in);
	}

	static byte[] getIntBytes(int val) {
		byte[] b = new byte[4];
		putInt(val,
		       b,
		       0);
		return b;
	}

	static void putInt(int val,
	                   byte[] b,
	                   int off) {
		putInt(val,
		       b,
		       off,
		       ENDIAN.BIG);
	}

	static void putInt(int val,
	                   byte[] b,
	                   int off,
	                   ENDIAN endian) {
		endian.setInt(val,
		              b,
		              off);
	}

	static int getInteger(InputStream in) throws IOException {
		return getInteger(in,
		                  ENDIAN.BIG);
	}

	static int getInteger(InputStream in,
	                      ENDIAN e) throws IOException {
		return e.getInt(in);
	}

	static long getUnsignedInt(byte[] b,
	                           int off) {
		return getUnsignedInt(b,
		                      off,
		                      ENDIAN.BIG);
	}

	static long getUnsignedInt(byte[] b,
	                           int off,
	                           ENDIAN e) {
		return e.getUnsignedInt(b,
		                        off);
	}

	static long getUnsignedInt(InputStream in) throws IOException {
		return getUnsignedInt(in,
		                      ENDIAN.BIG);
	}

	static long getUnsignedInt(InputStream in,
	                           ENDIAN e) throws IOException {
		return e.getUnsignedInt(in);
	}

	static void putUnsignedInteger(final long val,
	                               final OutputStream out) throws IOException {
		putUnsignedInteger(val,
		                   out,
		                   ENDIAN.BIG);
	}

	static void putUnsignedInteger(final long val,
	                               final OutputStream out,
	                               final ENDIAN endian) throws IOException {
		endian.setInt((int) (val & 0xFFFFFFFFL),
		              out);
	}

	static void putInteger(final int val,
	                       final OutputStream out) throws IOException {
		putInteger(val,
		           out,
		           ENDIAN.BIG);
	}

	static void putInteger(final int val,
	                       final OutputStream out,
	                       final ENDIAN endian) throws IOException {
		endian.setInt(val,
		              out);
	}

	static void putLong(long val,
	                    OutputStream out) throws IOException {
		putLong(val,
		        out,
		        ENDIAN.BIG);
	}

	static void putLong(long val,
	                    OutputStream out,
	                    ENDIAN endian) throws IOException {
		endian.setLong(val,
		               out);
	}

	static long getLong(InputStream in) throws IOException {
		return getLong(in,
		               ENDIAN.BIG);
	}

	static long getLong(InputStream in,
	                    ENDIAN e) throws IOException {
		return e.getLong(in);
	}

	static float getFloat(byte[] b,
	                      int off) {
		final int i = getInteger(b,
		                         off);
		return Float.intBitsToFloat(i);
	}

	static int getInteger(byte... b) {
		if (b.length < 4)
			throw new IllegalArgumentException("integer array cannot smaller then 4");
		return getInteger(b,
		                  0);
	}

	static int getInteger(byte[] b,
	                      int off) {
		return getInteger(b,
		                  off,
		                  ENDIAN.BIG);
	}

	static int getInteger(byte[] b,
	                      int off,
	                      ENDIAN e) {
		return e.getInt(b,
		                off);
	}

	static float getFloat(InputStream in) throws IOException {
		return getFloat(in,
		                ENDIAN.BIG);
	}

	static float getFloat(InputStream in,
	                      ENDIAN e) throws IOException {
		final int i = getInteger(in,
		                         e);
		return Float.intBitsToFloat(i);
	}

	static void putFloat(float val,
	                     byte[] b,
	                     int off) {
		int i = Float.floatToIntBits(val);
		putInt(i,
		       b,
		       off);
	}

	static void putFloat(final float val,
	                     final OutputStream out) throws IOException {
		putFloat(val,
		         out,
		         ENDIAN.BIG);
	}

	static void putFloat(final float val,
	                     final OutputStream out,
	                     ENDIAN e) throws IOException {
		int i = Float.floatToIntBits(val);
		putInteger(i,
		           out,
		           e);
	}

	static double getDouble(byte[] b,
	                        int off) {
		long j = getLong(b,
		                 off);

		return Double.longBitsToDouble(j);
	}

	static double getDouble(InputStream in) throws IOException {
		return getDouble(in,
		                 ENDIAN.BIG);
	}

	static double getDouble(InputStream in,
	                        ENDIAN e) throws IOException {
		long j = getLong(in,
		                 e);

		return Double.longBitsToDouble(j);
	}

	static void putDouble(double val,
	                      byte[] b,
	                      int off) {
		long j = Double.doubleToLongBits(val);
		putLong(j,
		        b,
		        off);
	}

	static void putDouble(double val,
	                      OutputStream out) throws IOException {
		putDouble(val,
		          out,
		          ENDIAN.BIG);
	}

	static void putDouble(double val,
	                      OutputStream out,
	                      ENDIAN e) throws IOException {
		long j = Double.doubleToLongBits(val);
		putLong(j,
		        out,
		        e);
	}

	static long convertLong(Object obj) {
		if (obj == null) {
			return 0;
		}

		if (obj instanceof Number) {
			return ((Number) obj).longValue();
		}

		try {
			return Long.parseLong(obj.toString());
		}
		catch (Exception thw) {
			return 0;
		}
	}

	static long getLong(int value) {
		return value;
	}

	static int getInt(long value) {
		if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
			throw new IllegalArgumentException("exceed intger range:" + value);
		}
		return (int) (value);
	}

	static int getInt(double value) {
		if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
			throw new IllegalArgumentException("exceed intger range:" + value);
		}
		return Double.valueOf(value)
		             .intValue();
	}

	static Integer getInteger(long value) {
		if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
			return null;
		}
		return (int) (value & 0xFFFFFFFFL);
	}

	static void putString(OutputStream out,
	                      String obj,
	                      final CharSets cs) throws IOException {
		out.write(cs.getBytes(obj));
		out.write(0);
	}

	static String getString(final InputStream in,
	                        final CharSets cs) throws IOException {
		ByteArrayOutputStreamExt es = null;
		try {
			es = new ByteArrayOutputStreamExt(1024);

			return getString(in,
			                 es,
			                 cs);
		}
		finally {
			ObjectFinalizer.close(es);
		}
	}

	static String getString(final InputStream in,
	                        final ByteArrayOutputStreamExt es,
	                        final CharSets cs) throws IOException {
		es.reset();

		int read = -1;

		while (true) {
			read = in.read();

			if (read < 0) {
				throw new EOFException();
			}

			if (read == 0) {// finish string
				return es.toString(cs);
			} else {
				es.write(read);
			}
		}
	}

	static void and(byte[] source,
	                byte[] target) {
		and(source,
		    target,
		    Math.min(source.length,
		             target.length));
	}

	static void and(final byte[] source,
	                final byte[] target,
	                final int length) {
		if (source == null || target == null) {
			throw new NullPointerException("source or target is null");
		}
		int size = Math.min(Math.min(source.length,
		                             target.length),
		                    length);
		for (int i = 0; i < size; i++) {
			source[i] &= target[i];
		}
	}

	static void or(byte[] source,
	               byte[] target) {
		or(source,
		   target,
		   Math.min(source.length,
		            target.length));
	}

	static void or(final byte[] source,
	               final byte[] target,
	               final int length) {
		if (source == null || target == null) {
			throw new NullPointerException("source or target is null");
		}
		int size = Math.min(Math.min(source.length,
		                             target.length),
		                    length);
		for (int i = 0; i < size; i++) {
			source[i] |= target[i];
		}
	}

	static void xor(byte[] source,
	                byte[] target) {
		xor(source,
		    target,
		    Math.min(source.length,
		             target.length));
	}

	static void xor(final byte[] source,
	                final byte[] target,
	                final int length) {
		if (source == null || target == null) {
			throw new NullPointerException("source or target is null");
		}
		int size = Math.min(Math.min(source.length,
		                             target.length),
		                    length);
		for (int i = 0; i < size; i++) {
			source[i] ^= target[i];
		}
	}

	static int normalizeCapacity(int requestedCapacity) {
		if (requestedCapacity < 0) {
			return Integer.MAX_VALUE;
		}

		int newCapacity = Integer.highestOneBit(requestedCapacity);
		newCapacity <<= newCapacity < requestedCapacity
		                ? 1
		                : 0;

		return newCapacity < 0
		       ? Integer.MAX_VALUE
		       : newCapacity;
	}

	/**
	 * @param value target value
	 * @param digit a decimal point digit
	 * @return rounded value
	 */
	static double trim(double value,
	                   int digit) {
		double divide = Math.pow(10,
		                         digit);

		return Math.round(value * divide) / divide;
	}

	static long round(double value) {
		return Math.round(value);
	}

	static long ceil(double value) {
		return (long) StrictMath.ceil(value);
	}

	static long floor(double value) {
		return (long) StrictMath.floor(value);
	}

	static long parseBytes(String source) {
		final String filtered = source.toUpperCase();

		for (Bytes bytes : Bytes.values()) {
			final String value = bytes.match(filtered);
			if (null == value) {
				continue;
			}

			return round(bytes.valueOf(Double.parseDouble(value)));
		}

		throw new IllegalArgumentException(StringAssist.format("[%s] is not matched [0-9]*(KB|MB|GB)",
		                                                       source));
	}

	enum Bytes {
		KB(1024L),
		MB(1024 * 1024L),
		GB(1024 * 1024 * 1024L);

		final long factor;

		final double divide;

		Bytes(long factor) {
			this.factor = factor;
			this.divide = (double) factor;
		}

		public long toBytes(long value) {
			return value * this.factor;
		}

		public double valueOf(long value) {
			return trim(value / this.divide,
			            3);
		}

		public double valueOf(double value) {
			return trim(value / this.divide,
			            3);
		}

		String match(String value) {
			return value.endsWith(this.name())
			       ? value.substring(0,
			                         value.length() - 2)
			              .trim()
			       : null;
		}
	}

	enum ENDIAN {

		BIG {
			@Override
			public void setChar(char val,
			                    byte[] b,
			                    int off) {
				validateArrayLength(b,
				                    off,
				                    2);
				b[off] = (byte) (val >> 8);
				b[off + 1] = (byte) (val);
			}

			@Override
			public void setChar(char val,
			                    OutputStream out) throws IOException {
				out.write((byte) (val >> 8));
				out.write((byte) (val));
			}

			@Override
			public char getChar(byte[] b,
			                    int off) {
				validateArrayLength(b,
				                    off,
				                    2);
				return (char) (((b[off + 1] & 0xFF)) + ((b[off] & 0xFF) << 8));
			}

			@Override
			char getChar(int v0,
			             int v1) {
				return (char) (((v1 & 0xFF)) + ((v0 & 0xFF) << 8));
			}

			@Override
			public void setShort(short val,
			                     byte[] b,
			                     int off) {
				validateArrayLength(b,
				                    off,
				                    2);
				b[off] = (byte) (val >> 8);
				b[off + 1] = (byte) (val);
			}

			@Override
			public void putShort(short val,
			                     OutputStream out) throws IOException {
				out.write((byte) (val >> 8));
				out.write((byte) (val));
			}

			@Override
			public short getShort(byte[] b,
			                      int off) {
				validateArrayLength(b,
				                    off,
				                    2);
				return (short) ((b[off + 1] & 0xFF) | (b[off] & 0xFF) << 8);
			}

			@Override
			short getShort(int v0,
			               int v1) {
				return (short) (((v1 & 0xFF)) + ((v0 & 0xFF) << 8));
			}

			@Override
			public int getUnsignedShort(byte[] b,
			                            int off) {
				validateArrayLength(b,
				                    off,
				                    2);
				return (b[off + 1] & 0xFF) | (b[off] & 0xFF) << 8;
			}

			@Override
			int getUnsignedShort(int v0,
			                     int v1) {
				return (v1 & 0xFF) | (v0 & 0xFF) << 8;
			}

			@Override
			public void setInt(int val,
			                   byte[] b,
			                   int off) {
				validateArrayLength(b,
				                    off,
				                    4);

				b[off] = (byte) (val >> 24);
				b[off + 1] = (byte) (val >> 16);
				b[off + 2] = (byte) (val >> 8);
				b[off + 3] = (byte) (val);
			}

			@Override
			public void setInt(int val,
			                   OutputStream out) throws IOException {
				out.write((byte) (val >> 24));
				out.write((byte) (val >> 16));
				out.write((byte) (val >> 8));
				out.write((byte) (val));
			}

			@Override
			public int getInt(byte[] b,
			                  int off) {
				// validation array
				validateArrayLength(b,
				                    off,
				                    4);

				return (b[off + 3] & 0xFF) | (b[off + 2] & 0xFF) << 8 | (b[off + 1] & 0xFF) << 16 | (b[off] & 0xFF) << 24;
			}

			@Override
			public int getInt(InputStream in) throws IOException {
				return (read(in) & 0xFF) << 24 | (read(in) & 0xFF) << 16 | (read(in) & 0xFF) << 8 | (read(in) & 0xFF);
			}

			@Override
			public long getUnsignedInt(byte[] b,
			                           int off) {
				validateArrayLength(b,
				                    off,
				                    4);
				return ((b[off + 3] & 0xFF) | (b[off + 2] & 0xFF) << 8 | (b[off + 1] & 0xFF) << 16 | (long) (b[off] & 0xFF) << 24);
			}

			@Override
			public long getUnsignedInt(InputStream in) throws IOException {
				return (long) (read(in) & 0xFF) << 24 | (read(in) & 0xFF) << 16 | (read(in) & 0xFF) << 8 | (read(in) & 0xFF);
			}

			@Override
			public void setLong(long v,
			                    byte[] b,
			                    int off) {
				validateArrayLength(b,
				                    off,
				                    8);
				b[off] = (byte) (v >> 56);
				b[off + 1] = (byte) (v >> 48);
				b[off + 2] = (byte) (v >> 40);
				b[off + 3] = (byte) (v >> 32);
				b[off + 4] = (byte) (v >> 24);
				b[off + 5] = (byte) (v >> 16);
				b[off + 6] = (byte) (v >> 8);
				b[off + 7] = (byte) (v);
			}

			@Override
			public void setLong(long val,
			                    OutputStream out) throws IOException {
				out.write((byte) (val >> 56));
				out.write((byte) (val >> 48));
				out.write((byte) (val >> 40));
				out.write((byte) (val >> 32));
				out.write((byte) (val >> 24));
				out.write((byte) (val >> 16));
				out.write((byte) (val >> 8));
				out.write((byte) (val));
			}

			@Override
			public long getLong(byte[] b,
			                    int off) {
				validateArrayLength(b,
				                    off,
				                    8);
				return (b[off + 7] & 0xFFL)
						| (b[off + 6] & 0xFFL) << 8
						| (b[off + 5] & 0xFFL) << 16
						| (b[off + 4] & 0xFFL) << 24
						| (b[off + 3] & 0xFFL) << 32
						| (b[off + 2] & 0xFFL) << 40
						| (b[off + 1] & 0xFFL) << 48
						| (b[off] & 0xFFL) << 56;
			}

			@Override
			public long getLong(InputStream in) throws IOException {
				return readAsLong(in) << 56
						| readAsLong(in) << 48
						| readAsLong(in) << 40
						| readAsLong(in) << 32
						| readAsLong(in) << 24
						| readAsLong(in) << 16
						| readAsLong(in) << 8
						| readAsLong(in);
			}
		},

		LITTLE {
			@Override
			public long getLong(InputStream in) throws IOException {
				return readAsLong(in)
						| readAsLong(in) << 8
						| readAsLong(in) << 16
						| readAsLong(in) << 24
						| readAsLong(in) << 32
						| readAsLong(in) << 40
						| readAsLong(in) << 48
						| readAsLong(in) << 56;
			}

			@Override
			public long getLong(byte[] b,
			                    int off) {
				validateArrayLength(b,
				                    off,
				                    8);
				return (b[off] & 0xFFL)
						| (b[off + 1] & 0xFFL) << 8
						| (b[off + 2] & 0xFFL) << 16
						| (b[off + 3] & 0xFFL) << 24
						| (b[off + 4] & 0xFFL) << 32
						| (b[off + 5] & 0xFFL) << 40
						| (b[off + 6] & 0xFFL) << 48
						| (b[off + 7] & 0xFFL) << 56;
			}

			@Override
			public void setLong(long val,
			                    OutputStream out) throws IOException {
				out.write((byte) (val));
				out.write((byte) (val >> 8));
				out.write((byte) (val >> 16));
				out.write((byte) (val >> 24));
				out.write((byte) (val >> 32));
				out.write((byte) (val >> 40));
				out.write((byte) (val >> 48));
				out.write((byte) (val >> 56));
			}

			@Override
			public void setLong(long val,
			                    byte[] b,
			                    int off) {
				validateArrayLength(b,
				                    off,
				                    8);
				b[off] = (byte) (val);
				b[off + 1] = (byte) (val >> 8);
				b[off + 2] = (byte) (val >> 16);
				b[off + 3] = (byte) (val >> 24);
				b[off + 4] = (byte) (val >> 32);
				b[off + 5] = (byte) (val >> 40);
				b[off + 6] = (byte) (val >> 48);
				b[off + 7] = (byte) (val >> 56);
			}

			@Override
			public long getUnsignedInt(InputStream in) throws IOException {
				return (read(in) & 0xFF) | (read(in) & 0xFF) << 8 | (read(in) & 0xFF) << 16 | (long) (read(in) & 0xFF) << 24;
			}

			@Override
			public long getUnsignedInt(byte[] b,
			                           int off) {
				validateArrayLength(b,
				                    off,
				                    4);
				return (b[off] & 0xFF) | (b[off + 1] & 0xFF) << 8 | (b[off + 2] & 0xFF) << 16 | (long) (b[off + 3] & 0xFF) << 24;
			}

			@Override
			public int getInt(InputStream in) throws IOException {
				return (read(in) & 0xFF) | (read(in) & 0xFF) << 8 | (read(in) & 0xFF) << 16 | (read(in) & 0xFF) << 24;
			}

			@Override
			public int getInt(byte[] b,
			                  int off) {
				validateArrayLength(b,
				                    off,
				                    4);

				int i = 0;

				i |= (b[off] & 0xFF);
				i |= (b[off + 1] & 0xFF) << 8;
				i |= (b[off + 2] & 0xFF) << 16;
				i |= (b[off + 3] & 0xFF) << 24;

				return i;
			}

			@Override
			public void setInt(int val,
			                   OutputStream out) throws IOException {
				out.write((byte) (val));
				out.write((byte) (val >> 8));
				out.write((byte) (val >> 16));
				out.write((byte) (val >> 24));
			}

			@Override
			public void setInt(int val,
			                   byte[] b,
			                   int off) {
				validateArrayLength(b,
				                    off,
				                    4);

				b[off] = (byte) (val);
				b[off + 1] = (byte) (val >> 8);
				b[off + 2] = (byte) (val >> 16);
				b[off + 3] = (byte) (val >> 24);
			}

			@Override
			int getUnsignedShort(int v0,
			                     int v1) {
				return (v0 & 0xFF) | (v1 & 0xFF) << 8;
			}

			@Override
			public int getUnsignedShort(byte[] b,
			                            int off) {
				validateArrayLength(b,
				                    off,
				                    2);
				return (b[off] & 0xFF) | (b[off + 1] & 0xFF) << 8;
			}

			@Override
			short getShort(int v0,
			               int v1) {
				return (short) (((v0 & 0xFF)) + ((v1 & 0xFF) << 8));
			}

			@Override
			public short getShort(byte[] b,
			                      int off) {
				validateArrayLength(b,
				                    off,
				                    2);
				return (short) ((b[off] & 0xFF) | (b[off + 1] & 0xFF) << 8);
			}

			@Override
			public void putShort(short val,
			                     OutputStream out) throws IOException {
				out.write((byte) (val));
				out.write((byte) (val >> 8));
			}

			@Override
			public void setShort(short val,
			                     byte[] buffer,
			                     int offset) {
				buffer[offset] = (byte) (val);
				buffer[offset + 1] = (byte) (val >> 8);
			}

			@Override
			public void setChar(char val,
			                    byte[] b,
			                    int off) {
				validateArrayLength(b,
				                    off,
				                    2);
				b[off] = (byte) (val);
				b[off + 1] = (byte) (val >> 8);
			}

			@Override
			public void setChar(char val,
			                    OutputStream out) throws IOException {
				out.write((byte) (val));
				out.write((byte) (val >> 8));
			}

			@Override
			public char getChar(byte[] b,
			                    int off) {
				validateArrayLength(b,
				                    off,
				                    2);
				return (char) (((b[off] & 0xFF)) + ((b[off + 1] & 0xFF) << 8));
			}

			@Override
			char getChar(int v0,
			             int v1) {
				return (char) (((v0 & 0xFF)) + ((v1 & 0xFF) << 8));
			}
		};

		public abstract void setChar(char val,
		                             byte[] buffer,
		                             int offset);

		public abstract void setChar(char val,
		                             OutputStream out) throws IOException;

		public abstract char getChar(byte[] b,
		                             int off);

		public char getChar(InputStream in) throws IOException {
			int v0 = in.read();
			int v1 = in.read();

			if (v0 < 0 || v1 < 0) {
				throw new EOFException();
			}

			return this.getChar(v0,
			                    v1);
		}

		abstract char getChar(int v0,
		                      int v1);

		public abstract void setShort(short val,
		                              byte[] b,
		                              int off);

		public abstract void putShort(final short val,
		                              final OutputStream out) throws IOException;

		public abstract short getShort(byte[] b,
		                               int off);

		public final short getShort(InputStream in) throws IOException {
			int v0 = in.read();
			int v1 = in.read();

			if (v0 < 0 || v1 < 0) {
				throw new EOFException();
			}

			return this.getShort(v0,
			                     v1);
		}

		abstract short getShort(int v0,
		                        int v1);

		public abstract int getUnsignedShort(byte[] b,
		                                     int off);

		public int getUnsignedShort(InputStream in) throws IOException {
			int v0 = in.read();
			int v1 = in.read();

			if (v0 < 0 || v1 < 0) {
				throw new EOFException();
			}

			return this.getUnsignedShort(v0,
			                             v1);
		}

		abstract int getUnsignedShort(int v0,
		                              int v1);

		public abstract void setInt(int val,
		                            byte[] b,
		                            int off);

		public abstract void setInt(int val,
		                            OutputStream out) throws IOException;

		public abstract int getInt(byte[] b,
		                           int off);

		public abstract int getInt(InputStream in) throws IOException;

		public abstract long getUnsignedInt(byte[] buffer,
		                                    int offset);

		public abstract long getUnsignedInt(InputStream in) throws IOException;

		public abstract void setLong(long val,
		                             byte[] b,
		                             int off);

		public abstract void setLong(long val,
		                             OutputStream out) throws IOException;

		public abstract long getLong(byte[] b,
		                             int off);

		public abstract long getLong(InputStream in) throws IOException;
	}
}
