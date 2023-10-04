package gaia.common;


import static gaia.common.ResourceAssist.stringBuilderWith;
import static gaia.common.FunctionAssist.supply;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import gaia.ConstValues;
import gaia.common.CollectionSpec.CaseInsensitiveMap;
import gaia.common.ConcurrentAssist.ConditionedLock;

/**
 * @author dragon
 * @since 2020. 11. 30.
 */
public interface CharsetSpec {
	Logger log = LoggerFactory.getLogger(CharsetSpec.class);
	int caseDiff = 'a' - 'A';

	byte verifierStart = (byte) 0;

	byte verifierError = (byte) 1;

	byte verifierItsMe = (byte) 2;

	int verifierIndexSft4bits = 3;

	int verifierSftMsk4bits = 7;

	int verifierBitSft4bits = 2;

	int verifierUnitMsk4bits = 0x0000000F;

	float Big5_FirstByteStdDev = 0.020606f; // Lead Byte StdDev

	float Big5_FirstByteMean = 0.010638f; // Lead Byte Mean

	float Big5_FirstByteWeight = 0.675261f; // Lead Byte Weight
	float Big5_SecondByteStdDev = 0.009909f; // Trail Byte StdDev

	float Big5_SecondByteMean = 0.010638f; // Trail Byte Mean

	float Big5_SecondByteWeight = 0.324739f; // Trial Byte Weight

	int BIG5_stFactor = 5;

	float EUCJP_FirstByteStdDev = 0.050407f; // Lead Byte StdDev

	float EUCJP_FirstByteMean = 0.010638f; // Lead Byte Mean

	float EUCJP_FirstByteWeight = 0.640871f; // Lead Byte Weight

	float EUCJP_SecondByteStdDev = 0.028247f; // Trail Byte StdDev

	// ---------------------------------------------------------------------
	// Section statistics
	// ---------------------------------------------------------------------
	float EUCJP_SecondByteMean = 0.010638f; // Trail Byte Mean

	float EUCJP_SecondByteWeight = 0.359129f; // Trial Byte Weight

	int EUCJP_stFactor = 6;

	float EUCKR_FirstByteStdDev = 0.025593f; // Lead Byte StdDev

	float EUCKR_FirstByteMean = 0.010638f; // Lead Byte Mean

	float EUCKR_FirstByteWeight = 0.647437f; // Lead Byte Weight

	float EUCKR_SecondByteStdDev = 0.013937f; // Trail Byte StdDev

	float EUCKR_SecondByteMean = 0.010638f; // Trail Byte Mean

	float EUCKR_SecondByteWeight = 0.352563f; // Trial Byte Weight
	int EUCKR_stFactor = 4;

	float EUCTW_FirstByteStdDev = 0.016681f; // Lead Byte StdDev

	float EUCTW_FirstByteMean = 0.010638f; // Lead Byte Mean

	float EUCTW_FirstByteWeight = 0.715599f; // Lead Byte Weight

	float EUCTW_SecondByteStdDev = 0.006630f; // Trail Byte StdDev

	float EUCTW_SecondByteMean = 0.010638f; // Trail Byte Mean

	float EUCTW_SecondByteWeight = 0.284401f; // Trial Byte Weight

	int EUCTW_stFactor = 7;

	float GB2312_FirstByteStdDev = 0.020081f; // Lead Byte StdDev

	float GB2312_FirstByteMean = 0.010638f; // Lead Byte Mean

	float GB2312_FirstByteWeight = 0.586533f; // Lead Byte Weight
	float GB2312_SecondByteStdDev = 0.014156f; // Trail Byte StdDev

	float GB2312_SecondByteMean = 0.010638f; // Trail Byte Mean

	float GB2312_SecondByteWeight = 0.413467f; // Trial Byte Weight

	int GB2312_stFactor = 4;
	int CP1252_stFactor = 3;
	int GB18030_stFactor = 7;
	int HZ_stFactor = 6;
	int ISO2022CN_stFactor = 9;
	int ISO2022JP_stFactor = 8;
	int ISO2022KR_stFactor = 6;
	int SJIS_stFactor = 6;
	int UCS2BE_stFactor = 6;
	int UCS2LE_stFactor = 6;
	int UTF8_stFactor = 16;

	@Setter
	@Getter
	@NoArgsConstructor
	class CharSetConst
			implements Serializable {
		private static final long serialVersionUID = 7456514030293027338L;
		BitSet dontNeedEncoding;
		int[] BIG5_cclass;
		float[] Big5_FirstByteFreq;
		float[] Big5_SecondByteFreq;
		int[] BIG5_states;
		float[] EUCJP_FirstByteFreq;
		float[] EUCJP_SecondByteFreq;
		int[] EUCJP_cclass;
		int[] EUCJP_states;
		float[] EUCKR_FirstByteFreq;
		float[] EUCKR_SecondByteFreq;
		int[] EUCKR_cclass;
		int[] EUCKR_states;
		float[] EUCTW_FirstByteFreq;
		float[] EUCTW_SecondByteFreq;
		int[] EUCTW_cclass;
		int[] EUCTW_states;
		float[] GB2312_FirstByteFreq;
		float[] GB2312_SecondByteFreq;
		int[] GB2312_cclass;
		int[] GB2312_states;
		int[] CP1252_cclass;
		int[] CP1252_states;
		int[] GB18030_cclass;
		int[] GB18030_states;
		int[] HZ_cclass;
		int[] HZ_states;
		int[] ISO2022CN_cclass;
		int[] ISO2022CN_states;
		int[] ISO2022JP_cclass;
		int[] ISO2022JP_states;
		int[] ISO2022KR_cclass;
		int[] ISO2022KR_states;
		int[] SJIS_cclass;
		int[] SJIS_states;
		int[] UCS2BE_cclass;
		int[] UCS2BE_states;
		int[] UCS2LE_cclass;
		int[] UCS2LE_states;
		int[] UTF8_cclass;
		int[] UTF8_states;
	}

	CharSetConst constValue = supply(() -> {
		InputStream in = Thread.currentThread()
		                       .getContextClassLoader()
		                       .getResourceAsStream("META-INF/CharSetConst.bin");
		ObjectInputStream o = new ObjectInputStream(in);

		CharSetConst target = (CharSetConst) o.readObject();

		o.close();
		in.close();

		return target;
	});


	Map<String, CharSets> amap = supply(() -> {
		Map<String, CharSets> map = new CaseInsensitiveMap<>();

		for (CharSets cs : CharSets.values()) {
			map.put(cs.name,
			        cs);
			for (final String alias : cs.aliases) {
				map.put(alias,
				        cs);
			}
		}

		return map;
	});


	enum CharSets
			implements Comparable<CharSets> {
		UTF_8("UTF-8",
		      "utf-8",
		      "utf8",
		      "utf_8"),
		ASCII("8859_1",
		      "ascii",
		      "iso-8859-1"),
		EUC_KR("EUC-KR",
		       "euc-kr",
		       "euckr",
		       "euc_kr",
		       "ksc5601"),
		EUC_JP("EUC-JP",
		       "eucjp",
		       "euc_jp"),
		EUC_TW("x-euc-tw"),
		BIG5("Big5"),
		GB18030("GB18030"),
		GB2312("GB2312"),
		HZ_GB("GB2312"),
		CP1252("windows-1252"),
		ISO_2022_CN("ISO-2022-CN"),
		ISO_2022_JP("ISO-2022-JP"),
		ISO_2022_KR("ISO-2022-KR"),
		SHIFT_JIS("Shift_JIS",
		          "sjis"),
		UTF_16BE("UTF-16BE"),
		UTF_16LE("UTF-16LE"),
		SYS_DEPEND(null),
		UNKNOWN(null);

		final String[] aliases;
		@Getter
		final String publicName;
		@Getter
		private String name;
		@Getter
		private byte[] nameBytes;
		@Getter
		private Charset charset;
		@Getter
		final Predicate<String> predicateString;
		@Getter
		final Predicate<String> predicateStringN;
		@Getter
		final Predicate<CharSets> predicate;
		@Getter
		final Predicate<CharSets> predicateN;

		CharSets(String idx,
		         String... aliases) {
			this.name = idx == null
			            ? "DEFAULT"
			            : StringUtils.trim(idx);
			this.charset = idx == null
			               ? Charset.defaultCharset()
			               : Charset.forName(idx);

			this.aliases = aliases == null || aliases.length == 0
			               ? new String[]{this.name}
			               : aliases;
			this.publicName = this.aliases[0].toLowerCase();
			this.nameBytes = this.publicName.getBytes();

			this.predicateString = string -> {
				CharSets cs = amap.get(string);

				return cs == this;
			};
			this.predicateStringN = this.predicateString.negate();
			this.predicate = other -> other.ordinal() == this.ordinal();
			this.predicateN = this.predicate.negate();

		}

		public boolean compare(Object object) {
			if (this == object) {
				return true;
			}

			if (object instanceof String) {
				return this.predicateString.test(object.toString());
			}

			return false;
		}

		public final void updateOther(CharSets cs) {
			if (!StringUtils.isEmpty(this.name) && this.charset != cs.charset) {
				throw new IllegalStateException("fixed cs cannot update other cs");
			}

			this.name = cs.name;
			this.nameBytes = cs.nameBytes;
			this.charset = cs.charset;
		}

		public byte[] getBytes(final char[] array,
		                       final int off,
		                       final int len) {
			return new String(array,
			                  off,
			                  len).getBytes(this.charset);
		}

		/**
		 * @param bytes
		 * @return
		 */
		public final String toString(final byte[] bytes) {
			if (bytes == null) {
				return ConstValues.BLANK_STRING;
			}
			return new String(bytes,
			                  this.charset);
		}

		/**
		 * @param source
		 * @return
		 */
		public final int getLength(final String source) {
			Writer writer = null;
			LengthCheckOutputStream out = null;
			try {
				out = new LengthCheckOutputStream();
				writer = new OutputStreamWriter(out,
				                                this.charset);
				writer.write(source);
				writer.flush();

				return out.length();
			}
			catch (Exception thw) {
				log.warn("cs check length error",
				         thw);
			}
			finally {
				ObjectFinalizer.close(out);
				ObjectFinalizer.close(writer);
			}
			return source.getBytes(this.charset).length;
		}

		public final void split(SizedTokenListener listener,
		                        String source,
		                        int limit) {
			LengthedTokenOutputStream stream = null;
			Writer writer = null;
			try {
				stream = new LengthedTokenOutputStream(listener,
				                                       limit);
				writer = new LengthTokenOutputWriter(stream,
				                                     this.charset);
				writer.write(source);
				if (stream.getCount() > 0) {
					stream.raise();
				}

			}
			catch (Exception thw) {
				throw new RuntimeException("split raise error",
				                           thw);
			}
			finally {
				ObjectFinalizer.close(stream);
				ObjectFinalizer.close(writer);

			}
		}

		/**
		 * @param stream
		 * @return
		 */
		public final Reader getReader(InputStream stream) {
			return new InputStreamReader(stream,
			                             this.charset);
		}

		public final String readStream(final InputStream in) {
			return StreamAssist.read(in,
			                         this);
		}

		/**
		 * @param source
		 * @return
		 */
		public String urlDecode(String source) {
			if (StringUtils.isEmpty(source)) {
				return source;
			}

			if (source.indexOf('+') < 0 && source.indexOf('%') < 0) {
				return source;
			}

			return stringBuilderWith(sb -> {
				int numChars = source.length();
				int i = 0;

				char c;
				byte[] bytes = null;
				while (i < numChars) {
					c = source.charAt(i);
					switch (c) {
						case '+': {
							sb.append(' ');
							i++;
							break;
						}
						case '%':
							/*
							 * Starting with this instance of %, process all
							 * consecutive substrings of the form %xy. Each
							 * substring %xy will yield a byte. Convert all
							 * consecutive  bytes obtained this way to whatever
							 * character(s) they represent in the provided
							 * encoding.
							 */
							try {

								// (numChars-i)/3 is an upper bound for the number
								// of remaining bytes
								if (bytes == null)
									bytes = new byte[(numChars - i) / 3];
								int pos = 0;

								while (((i + 2) < numChars) &&
										(c == '%')) {
									int v = Integer.parseInt(source.substring(i + 1,
									                                          i + 3),
									                         16);
									if (v < 0)
										throw new IllegalArgumentException("URLDecoder: Illegal hex characters in escape (%) pattern - negative value");
									bytes[pos++] = (byte) v;
									i += 3;
									if (i < numChars)
										c = source.charAt(i);
								}

								// A trailing, incomplete byte encoding such as
								// "%x" will cause an exception to be thrown

								if ((i < numChars) && (c == '%'))
									throw new IllegalArgumentException(
											"URLDecoder: Incomplete trailing escape (%) pattern");

								sb.append(this.toString(bytes,
								                        0,
								                        pos));
							}
							catch (NumberFormatException e) {
								throw new IllegalArgumentException(
										"URLDecoder: Illegal hex characters in escape (%) pattern - "
												+ e.getMessage());
							}
							break;
						default:
							sb.append(c);
							i++;
							break;
					}
				}
			});
		}

		/**
		 * @param bytes
		 * @param off
		 * @param len
		 * @return
		 */
		public final String toString(final byte[] bytes,
		                             final int off,
		                             final int len) {
			return new String(bytes,
			                  off,
			                  len,
			                  this.charset);
		}

		/**
		 * byte 단위로 분리
		 *
		 * @param content  내용
		 * @param block    블럭 사이즈
		 * @param consumer consumer
		 */
		public void split(final String content,
		                  final int block,
		                  final Consumer<String> consumer) {
			final int limit = content.length();
			int index = 0;

			Set<Integer> history = new HashSet<>();

			while (index < limit) {
				final int toLimit = Math.min(index + block,
				                             limit);
				int toIndex = toLimit;

				history.clear();

				do {
					String part = content.substring(index,
					                                toIndex);

					final int gab = block - this.getBytes(part).length;

					if (gab == 0) {
						// case exact
						consumer.accept(part);
						break;
					}

					int nextCandidate = Math.min(limit,
					                             toIndex + (Math.abs(gab) == 1
					                                        ? gab
					                                        : gab / 2));

					if (history.contains(nextCandidate) && gab > 0) {
						// next is same position...
						if (log.isTraceEnabled()) {
							log.trace(StringAssist.format("===>[%s ~ %s][%s]",
							                              index,
							                              toLimit,
							                              history));
						}
						consumer.accept(part);
						break;
					}

					history.add(nextCandidate);
					toIndex = nextCandidate;
				} while (toIndex > 0);

				index = toIndex;
			}
		}

		/**
		 * @param s
		 * @return
		 * @throws UnsupportedEncodingException
		 * @see {@link java.net.URLEncoder#encode(String, String)}
		 */
		public final String encodeUrl(String s) {
			boolean needToChange = false;
			CharArrayWriter charArrayWriter = new CharArrayWriter();

			StringBuilder buffer = null;
			try {
				buffer = new StringBuilder();
				for (int i = 0; i < s.length(); ) {
					int c = s.charAt(i);
					if (constValue.dontNeedEncoding.get(c)) {
						if (c == ' ') {
							c = '+';
							needToChange = true;
						}
						buffer.append((char) c);
						i++;
					} else {
						// convert to external encoding before hex conversion
						do {
							charArrayWriter.write(c);
							/*
							 * If this character represents the start of a Unicode surrogate pair, then pass in two characters. It's not clear what should
							 * be done if a bytes reserved in the surrogate pairs range occurs outside of a legal surrogate pair. For now, just treat it
							 * as if it were any other character.
							 */
							if (c >= 0xD800 && c <= 0xDBFF) {
								if (i + 1 < s.length()) {
									int d = s.charAt(i + 1);
									if (d >= 0xDC00 && d <= 0xDFFF) {
										charArrayWriter.write(d);
										i++;
									}
								}
							}
							i++;
						} while (i < s.length() && !constValue.dontNeedEncoding.get(c = s.charAt(i)));

						charArrayWriter.flush();
						String str = new String(charArrayWriter.toCharArray());
						byte[] ba = this.getBytes(str);
						for (byte b : ba) {
							buffer.append('%');
							char ch = Character.forDigit(b >> 4 & 0xF,
							                             16);
							// converting to use uppercase letter as part of
							// the hex value if ch is a letter.
							if (Character.isLetter(ch)) {
								ch -= caseDiff;
							}
							buffer.append(ch);
							ch = Character.forDigit(b & 0xF,
							                        16);
							if (Character.isLetter(ch)) {
								ch -= caseDiff;
							}
							buffer.append(ch);
						}
						charArrayWriter.reset();
						needToChange = true;
					}
				}

				return needToChange
				       ? buffer.toString()
				       : s;
			}
			finally {
				ObjectFinalizer.closeObject(buffer);
			}
		}

		public final byte[] encodeUrlBytes(String s) {
			// translate later
			return this.encodeUrl(s)
			           .getBytes();
		}

		/**
		 * @return
		 */
		public byte[] getBytes(String source) {
			return source.getBytes(this.charset);
		}

		public Writer openWriter(File file) {
			try {
				return new EncodingWriter(file);
			}
			catch (IOException e) {
				throw new IllegalArgumentException("file not found!",
				                                   e);
			}
		}

		/**
		 * @author dragon
		 */
		public interface SizedTokenListener {
			void raiseToken(String token);
		}

		/**
		 * @author dragon
		 */
		public static class LengthCheckOutputStream
				extends OutputStream {
			/**
			 *
			 */
			final AtomicInteger counter = new AtomicInteger();

			/**
			 * @return
			 */
			public final int length() {
				return this.counter.get();
			}

			/**
			 *
			 */
			@Override
			public void write(int b) {
				this.counter.addAndGet(1);
			}

			/**
			 *
			 */
			@Override
			public void write(byte[] b) {
				this.counter.addAndGet(b.length);
			}

			/**
			 *
			 */
			@Override
			public void write(byte[] b,
			                  int off,
			                  int len) {
				this.counter.addAndGet(len);
			}

			public void reset() {
				this.counter.set(0);
			}
		}

		static final class LengthTokenOutputWriter
				extends OutputStreamWriter {
			/**
			 *
			 */
			final char[] elm = new char[1];

			/**
			 * @param out
			 * @param cs
			 */
			public LengthTokenOutputWriter(OutputStream out,
			                               Charset cs) {
				super(out,
				      cs);
			}

			/**
			 *
			 */
			@Override
			public void write(int c) throws IOException {
				this.elm[0] = (char) c;
				this.innerWrite();
			}

			/**
			 *
			 */
			private void innerWrite() throws IOException {
				super.write(this.elm,
				            0,
				            1);
				super.flush();
			}

			/**
			 *
			 */
			@Override
			public void write(char[] cbuf,
			                  int off,
			                  int len) throws IOException {
				final char[] elm = this.elm;
				final int to = off + len;
				for (int i = off; i < to; i++) {
					elm[0] = cbuf[i];
					this.innerWrite();
				}
			}

			/**
			 *
			 */
			@Override
			public void write(String str,
			                  int off,
			                  int len) throws IOException {
				final char[] elm = this.elm;
				final int to = off + len;
				for (int i = off; i < to; i++) {
					elm[0] = str.charAt(i);
					this.innerWrite();
				}
			}
		}

		/**
		 * @author dragon
		 */
		final class LengthedTokenOutputStream
				extends OutputStream {
			final ConditionedLock lock = new ConditionedLock();
			final SizedTokenListener listener;
			final byte[] buf;
			final int limit;
			@Getter
			transient int count;

			public LengthedTokenOutputStream(final SizedTokenListener listener,
			                                 final int limit) {
				this.buf = new byte[limit];
				this.count = 0;
				this.limit = limit;
				this.listener = listener;
			}

			@Override
			public void write(int b) {
				this.lock.execute(() -> {
					this.ensureCapacity(this.count + 1);
					this.buf[this.count] = (byte) b;
					this.count += 1;
				});
			}

			private void ensureCapacity(int minCapacity) {
				if (minCapacity - this.buf.length > 0) {
					this.raise();
				}
			}

			/**
			 *
			 */
			private void raise() {
				this.listener.raiseToken(CharSets.this.toString(this.buf,
				                                                0,
				                                                this.count));
				this.count = 0;
			}

			/**
			 *
			 */
			@Override
			public void write(byte[] b,
			                  int off,
			                  int len) {
				this.lock.execute(() -> {
					if (off < 0 || off > b.length || len < 0 || off + len - b.length > 0) {
						throw new IndexOutOfBoundsException();
					}
					this.ensureCapacity(this.count + len);
					System.arraycopy(b,
					                 off,
					                 this.buf,
					                 this.count,
					                 len);
					this.count += len;
				});
			}

		}

		public class EncodingWriter
				extends OutputStreamWriter {
			EncodingWriter(File file) throws IOException {
				super(Files.newOutputStream(file.toPath()),
				      CharSets.this.getCharset());
			}
		}

		/**
		 * @param pattern
		 * @return
		 */
		public static CharSets of(String pattern) {
			CharSets cs = amap.get(pattern);
			if (cs == null) {
				throw new IllegalArgumentException(StringAssist.format("unknown charset name [%s]",
				                                                       pattern));
			}
			return cs;
		}

		/**
		 * @param cs
		 * @return
		 */
		public static CharSets of(Charset cs) {
			for (CharSets elm : CharSets.values()) {
				if (elm.charset.compareTo(cs) == 0) {
					return elm;
				}
			}

			throw new Error(StringAssist.format("cannot determine charset [%s]",
			                                    cs.toString()));
		}

		public static CharSets detectCharset(InputStream in) throws IOException {
			return detectCharset(in,
			                     TargetLocale.KOREAN);
		}

		public static CharSets detectCharset(InputStream in,
		                                     TargetLocale tl) throws IOException {
			return in == null
			       ? null
			       : new CharsetDetector(tl).detectCharset(in);
		}
	}

	final class CharacterAppendStream
			extends ByteArrayOutputStream {
		/**
		 *
		 */
		final CharSets charset;

		/**
		 *
		 */
		public CharacterAppendStream() {
			this(CharSets.UTF_8,
			     32);
		}

		/**
		 * @param charset
		 */
		public CharacterAppendStream(final CharSets charset) {
			this(charset,
			     32);
		}

		/**
		 * @param size
		 */
		public CharacterAppendStream(final int size) {
			this(CharSets.UTF_8,
			     size);
		}

		/**
		 *
		 */
		public CharacterAppendStream(final CharSets charset,
		                             final int size) {
			super(size);
			this.charset = charset;
		}

		/**
		 * @param cbuf
		 * @throws IOException
		 */
		public void write(final char[] cbuf) throws IOException {
			this.write(cbuf,
			           0,
			           cbuf.length);
		}

		/**
		 * @param cbuf
		 * @param off
		 * @param len
		 * @throws IOException
		 */
		public void write(final char[] cbuf,
		                  final int off,
		                  final int len) throws IOException {
			this.write(this.charset.getBytes(cbuf,
			                                 off,
			                                 len));
		}

		/**
		 * @param source
		 * @throws IOException
		 */
		public void write(String source) throws IOException {
			this.write(this.charset.getBytes(source));
		}

		/**
		 * @param str
		 * @param off
		 * @param len
		 * @throws IOException
		 */
		public void write(final String str,
		                  final int off,
		                  final int len) throws IOException {
			char[] cbuf = new char[len];

			str.getChars(off,
			             off + len,
			             cbuf,
			             0);
			this.write(cbuf,
			           0,
			           len);
		}

		/**
		 *
		 */
		@Override
		public synchronized String toString() {
			return this.charset.toString(this.buf,
			                             0,
			                             this.count);
		}
	}


	// ---------------------------------------------------------------------
	// Section analyzer
	// ---------------------------------------------------------------------
	int NO_OF_LANGUAGES = 6;

	int MAX_VERIFIERS = 16;

	// @fomatter:off


	// ---------------------------------------------------------------------
	// Section verifier
	// ---------------------------------------------------------------------


	// ---------------------------------------------------------------------
	// Section detector
	// ---------------------------------------------------------------------
	enum TargetLocale {
		ALL,
		JAPANESE,
		CHINESE,
		SIMPLIFIED_CHINESE,
		TRADITIONAL_CHINESE,
		KOREAN
	}

	interface ICharsetDetector {

		/**
		 * @param observer
		 */
		void init(ICharsetDetectionObserver observer);

		/**
		 * @param aBuf
		 * @param aLen
		 * @param oDontFeedMe
		 * @return
		 */
		boolean doIt(byte[] aBuf,
		             int aLen,
		             boolean oDontFeedMe);

		/**
		 *
		 */
		void done();
	}

	interface ICharsetDetectionObserver {
		/**
		 * @param charset
		 */
		void notify(CharSets charset);
	}

	interface Verifier {

		CharSets charset();

		int stFactor();

		/**
		 * @return
		 */
		int[] cclass();

		int[] states();

		boolean isUCS2();
	}

	interface EUCStatistics {

		float[] mFirstByteFreq();

		float mFirstByteStdDev();

		float mFirstByteMean();

		float mFirstByteWeight();

		float[] mSecondByteFreq();

		float mSecondByteStdDev();

		float mSecondByteMean();

		float mSecondByteWeight();
	}

	class ByteArrayOutputStreamExt
			extends ByteArrayOutputStream {
		public ByteArrayOutputStreamExt() {
			super();
		}

		public ByteArrayOutputStreamExt(int size) {
			super(size);
		}

		public String toString(CharSets cs) {
			return cs.toString(this.buf,
			                   0,
			                   this.count);
		}

		public String toString(final Charset cs) {
			return new String(this.buf,
			                  0,
			                  this.count,
			                  cs);
		}

		public ByteArrayInputStream openInputStream() {
			return new ByteArrayInputStream(this.buf,
			                                0,
			                                this.count);
		}
	}

	class EUCSampler {

		public final int[] mFirstByteCnt = new int[94];

		public final int[] mSecondByteCnt = new int[94];

		public final float[] mFirstByteFreq = new float[94];

		public final float[] mSecondByteFreq = new float[94];

		int mTotal = 0;

		final int mThreshold = 200;

		int mState = 0;

		public EUCSampler() {
			this.Reset();
		}

		public void Reset() {
			this.mTotal = 0;
			this.mState = 0;
			for (int i = 0; i < 94; i++) {
				this.mFirstByteCnt[i] = this.mSecondByteCnt[i] = 0;
			}
		}

		boolean EnoughData() {
			return this.mTotal > this.mThreshold;
		}

		boolean GetSomeData() {
			return this.mTotal > 1;
		}

		boolean Sample(byte[] aIn,
		               int aLen) {

			if (this.mState == 1) {
				return false;
			}

			int p = 0;

			int i;
			for (i = 0; i < aLen && 1 != this.mState; i++, p++) {
				switch (this.mState) {
					case 0:
						if ((aIn[p] & 0x0080) != 0) {
							if (0xff == (0xff & aIn[p]) || 0xa1 > (0xff & aIn[p])) {
								this.mState = 1;
							} else {
								this.mTotal++;
								this.mFirstByteCnt[(0xff & aIn[p]) - 0xa1]++;
								this.mState = 2;
							}
						}
						break;
					case 1:
						break;
					case 2:
						if ((aIn[p] & 0x0080) != 0) {
							if (0xff == (0xff & aIn[p]) || 0xa1 > (0xff & aIn[p])) {
								this.mState = 1;
							} else {
								this.mTotal++;
								this.mSecondByteCnt[(0xff & aIn[p]) - 0xa1]++;
								this.mState = 0;
							}
						} else {
							this.mState = 1;
						}
						break;
					default:
						this.mState = 1;
				}
			}
			return 1 != this.mState;
		}

		void CalFreq() {
			for (int i = 0; i < 94; i++) {
				this.mFirstByteFreq[i] = (float) this.mFirstByteCnt[i] / (float) this.mTotal;
				this.mSecondByteFreq[i] = (float) this.mSecondByteCnt[i] / (float) this.mTotal;
			}
		}

		float GetScore(float[] aFirstByteFreq,
		               float aFirstByteWeight,
		               float[] aSecondByteFreq,
		               float aSecondByteWeight) {
			return aFirstByteWeight * this.GetScore(aFirstByteFreq,
			                                        this.mFirstByteFreq) + aSecondByteWeight * this.GetScore(aSecondByteFreq,
			                                                                                                 this.mSecondByteFreq);
		}

		float GetScore(float[] array1,
		               float[] array2) {
			float s;
			float sum = 0.0f;

			for (int i = 0; i < 94; i++) {
				s = array1[i] - array2[i];
				sum += s * s;
			}
			return (float) Math.sqrt(sum) / 94.0f;
		}
	}

	abstract class PSMDetector {
		public static final int NO_OF_LANGUAGES = 6;

		public static final int MAX_VERIFIERS = 16;

		protected final AtomicBoolean done = new AtomicBoolean(false);

		Verifier[] mVerifier;

		EUCStatistics[] mStatisticsData;

		final EUCSampler mSampler = new EUCSampler();

		final byte[] mState = new byte[MAX_VERIFIERS];

		final int[] mItemIdx = new int[MAX_VERIFIERS];

		int mItems;

		int mClassItems;

		boolean mRunSampler;

		boolean mClassRunSampler;

		public PSMDetector() {
			this.initVerifiers(TargetLocale.ALL);
			this.reset();
		}

		public PSMDetector(TargetLocale langFlag) {
			this.initVerifiers(langFlag);
			this.reset();
		}

		public PSMDetector(int aItems,
		                   Verifier[] aVerifierSet,
		                   EUCStatistics[] aStatisticsSet) {
			this.mClassRunSampler = aStatisticsSet != null;
			this.mStatisticsData = aStatisticsSet;
			this.mVerifier = aVerifierSet;

			this.mClassItems = aItems;
			this.reset();
		}

		public static byte getNextState(Verifier v,
		                                byte b,
		                                byte s) {
			return (byte) (0xFF & v.states()[(s * v.stFactor() + (v.cclass()[(b & 0xFF) >> verifierIndexSft4bits] >> ((b & verifierSftMsk4bits) << verifierBitSft4bits) & verifierUnitMsk4bits) & 0xFF) >> verifierIndexSft4bits] >>
					((s * v.stFactor() + (v.cclass()[(b & 0xFF) >> verifierIndexSft4bits] >> ((b & verifierSftMsk4bits) << verifierBitSft4bits) & verifierUnitMsk4bits) & 0xFF & verifierSftMsk4bits) << verifierBitSft4bits) & verifierUnitMsk4bits);

		}

		protected void initVerifiers(TargetLocale targetLocale) {
			switch (targetLocale) {
				case TRADITIONAL_CHINESE: {
					this.mVerifier = new Verifier[]{new UTF8Verifier(),
					                                new BIG5Verifier(),
					                                new ISO2022CNVerifier(),
					                                new EUCTWVerifier(),
					                                new CP1252Verifier(),
					                                new UCS2BEVerifier(),
					                                new UCS2LEVerifier()};

					this.mStatisticsData = new EUCStatistics[]{null,
					                                           new Big5Statistics(),
					                                           null,
					                                           new EUCTWStatistics(),
					                                           null,
					                                           null,
					                                           null};
					break;
				}

				case KOREAN: {
					this.mVerifier = new Verifier[]{new UTF8Verifier(),
					                                new EUCKRVerifier(),
					                                new ISO2022KRVerifier(),
					                                new CP1252Verifier(),
					                                new UCS2BEVerifier(),
					                                new UCS2LEVerifier()};
					break;
				}

				case SIMPLIFIED_CHINESE: {
					this.mVerifier = new Verifier[]{new UTF8Verifier(),
					                                new GB2312Verifier(),
					                                new GB18030Verifier(),
					                                new ISO2022CNVerifier(),
					                                new HZVerifier(),
					                                new CP1252Verifier(),
					                                new UCS2BEVerifier(),
					                                new UCS2LEVerifier()};
					break;
				}

				case JAPANESE: {
					this.mVerifier = new Verifier[]{new UTF8Verifier(),
					                                new SJISVerifier(),
					                                new EUCJPVerifier(),
					                                new ISO2022JPVerifier(),
					                                new CP1252Verifier(),
					                                new UCS2BEVerifier(),
					                                new UCS2LEVerifier()};
					break;
				}

				case CHINESE: {
					this.mVerifier = new Verifier[]{new UTF8Verifier(),
					                                new GB2312Verifier(),
					                                new GB18030Verifier(),
					                                new BIG5Verifier(),
					                                new ISO2022CNVerifier(),
					                                new HZVerifier(),
					                                new EUCTWVerifier(),
					                                new CP1252Verifier(),
					                                new UCS2BEVerifier(),
					                                new UCS2LEVerifier()};

					this.mStatisticsData = new EUCStatistics[]{null,
					                                           new GB2312Statistics(),
					                                           null,
					                                           new Big5Statistics(),
					                                           null,
					                                           null,
					                                           new EUCTWStatistics(),
					                                           null,
					                                           null,
					                                           null};
					break;
				}

				case ALL: {
					this.mVerifier = new Verifier[]{new UTF8Verifier(),
					                                new SJISVerifier(),
					                                new EUCJPVerifier(),
					                                new ISO2022JPVerifier(),
					                                new EUCKRVerifier(),
					                                new ISO2022KRVerifier(),
					                                new BIG5Verifier(),
					                                new EUCTWVerifier(),
					                                new GB2312Verifier(),
					                                new GB18030Verifier(),
					                                new ISO2022CNVerifier(),
					                                new HZVerifier(),
					                                new CP1252Verifier(),
					                                new UCS2BEVerifier(),
					                                new UCS2LEVerifier()};

					this.mStatisticsData = new EUCStatistics[]{null,
					                                           null,
					                                           new EUCJPStatistics(),
					                                           null,
					                                           new EUCKRStatistics(),
					                                           null,
					                                           new Big5Statistics(),
					                                           new EUCTWStatistics(),
					                                           new GB2312Statistics(),
					                                           null,
					                                           null,
					                                           null,
					                                           null,
					                                           null,
					                                           null};
					break;
				}
			}

			this.mClassRunSampler = this.mStatisticsData != null;
			this.mClassItems = this.mVerifier.length;

		}

		public void reset() {
			this.mRunSampler = this.mClassRunSampler;
			this.done.set(false);
			this.mItems = this.mClassItems;

			for (int i = 0; i < this.mItems; i++) {
				this.mState[i] = 0;
				this.mItemIdx[i] = i;
			}

			this.mSampler.Reset();
		}

		/**
		 * @param aBuf
		 * @param len
		 * @return
		 */
		public void handleData(byte[] aBuf,
		                       int len) {

			int i, j;
			byte b, st;

			for (i = 0; i < len; i++) {
				b = aBuf[i];

				for (j = 0; j < this.mItems; ) {
					st = getNextState(this.mVerifier[this.mItemIdx[j]],
					                  b,
					                  this.mState[j]);
					if (st == verifierItsMe) {
						this.report(this.mVerifier[this.mItemIdx[j]].charset());
						this.done.set(true);
						return;

					} else if (st == verifierError) {
						this.mItems--;
						if (j < this.mItems) {
							this.mItemIdx[j] = this.mItemIdx[this.mItems];
							this.mState[j] = this.mState[this.mItems];
						}

					} else {

						this.mState[j++] = st;

					}
				}

				if (this.mItems <= 1) {
					if (1 == this.mItems) {
						this.report(this.mVerifier[this.mItemIdx[0]].charset());
					}
					this.done.set(true);
					return;

				} else {

					int nonUCS2Num = 0;
					int nonUCS2Idx = 0;

					for (j = 0; j < this.mItems; j++) {
						if (this.mVerifier[this.mItemIdx[j]].isUCS2() && this.mVerifier[this.mItemIdx[j]].isUCS2()) {
							nonUCS2Num++;
							nonUCS2Idx = j;
						}
					}

					if (1 == nonUCS2Num) {
						this.report(this.mVerifier[this.mItemIdx[nonUCS2Idx]].charset());
						this.done.set(true);
						return;
					}
				}

			} // End of for( i=0; i < len ...

			if (this.mRunSampler) {
				this.sample(aBuf,
				            len);
			}
		}

		/**
		 * @param charset
		 */
		public abstract void report(CharSets charset);

		public void sample(byte[] aBuf,
		                   int aLen) {
			this.sample(aBuf,
			            aLen,
			            false);
		}

		public void sample(byte[] aBuf,
		                   int aLen,
		                   boolean aLastChance) {
			int possibleCandidateNum = 0;
			int j;
			int eucNum = 0;

			for (j = 0; j < this.mItems; j++) {
				if (null != this.mStatisticsData[this.mItemIdx[j]]) {
					eucNum++;
				}
				if (this.mVerifier[this.mItemIdx[j]].isUCS2() && !this.mVerifier[this.mItemIdx[j]].charset()
				                                                                                  .getName()
				                                                                                  .equalsIgnoreCase("GB18030")) {
					possibleCandidateNum++;
				}
			}

			this.mRunSampler = eucNum > 1;

			if (this.mRunSampler) {
				this.mRunSampler = this.mSampler.Sample(aBuf,
				                                        aLen);
				if ((aLastChance && this.mSampler.GetSomeData() || this.mSampler.EnoughData()) && eucNum == possibleCandidateNum) {
					this.mSampler.CalFreq();

					int bestIdx = -1;
					int eucCnt = 0;
					float bestScore = 0.0f;
					for (j = 0; j < this.mItems; j++) {
						if (null != this.mStatisticsData[this.mItemIdx[j]] && !this.mVerifier[this.mItemIdx[j]].charset()
						                                                                                       .getName()
						                                                                                       .equalsIgnoreCase("Big5")) {
							float score = this.mSampler.GetScore(this.mStatisticsData[this.mItemIdx[j]].mFirstByteFreq(),
							                                     this.mStatisticsData[this.mItemIdx[j]].mFirstByteWeight(),
							                                     this.mStatisticsData[this.mItemIdx[j]].mSecondByteFreq(),
							                                     this.mStatisticsData[this.mItemIdx[j]].mSecondByteWeight());
							if (0 == eucCnt++ || bestScore > score) {
								bestScore = score;
								bestIdx = j;
							} // if(( 0 == eucCnt++) || (bestScore > score ))
						} // if(null != ...)
					} // for
					if (bestIdx >= 0) {
						this.report(this.mVerifier[this.mItemIdx[bestIdx]].charset());
						this.done.set(true);
					}
				} // if (eucNum == possibleCandidateNum)
			} // if(mRunSampler)
		}

		public void dataEnd() {
			if (this.done.get()) {
				return;
			}

			if (this.mItems == 2) {
				if (this.mVerifier[this.mItemIdx[0]].charset()
				                                    .getName()
				                                    .equalsIgnoreCase("GB18030")) {
					this.report(this.mVerifier[this.mItemIdx[1]].charset());
					this.done.set(true);
				} else if (this.mVerifier[this.mItemIdx[1]].charset()
				                                           .getName()
				                                           .equalsIgnoreCase("GB18030")) {
					this.report(this.mVerifier[this.mItemIdx[0]].charset());
					this.done.set(true);
				}
			}

			if (this.mRunSampler) {
				this.sample(null,
				            0,
				            true);
			}
		}

		public CharSets[] getProbableCharsets() {

			if (this.mItems <= 0) {
				CharSets[] nomatch = new CharSets[1];
				nomatch[0] = CharSets.SYS_DEPEND;
				return nomatch;
			}

			CharSets[] ret = new CharSets[this.mItems];
			for (int i = 0; i < this.mItems; i++) {
				ret[i] = this.mVerifier[this.mItemIdx[i]].charset();
			}
			return ret;
		}
	}

	@Slf4j
	class Detector
			extends PSMDetector
			implements ICharsetDetector {

		final ConditionedLock lock = new ConditionedLock();

		ICharsetDetectionObserver mObserver = null;

		public Detector() {
			super();
		}

		public Detector(TargetLocale langFlag) {
			super(langFlag);
		}

		@Override
		public void init(ICharsetDetectionObserver aObserver) {
			this.mObserver = aObserver;
		}

		/**
		 *
		 */
		@Override
		public boolean doIt(byte[] aBuf,
		                    int aLen,
		                    boolean oDontFeedMe) {

			if (aBuf == null || oDontFeedMe) {
				return false;
			}

			this.handleData(aBuf,
			                aLen);
			return this.done.get();
		}

		/**
		 *
		 */
		@Override
		public void done() {
			this.dataEnd();
		}

		@Override
		public void report(CharSets charset) {
			if (this.mObserver != null) {
				this.mObserver.notify(charset);
			}
		}

		/**
		 * @param imp
		 * @return
		 */
		public CharSets detect(InputStream imp) {
			final AtomicReference<CharSets> found = new AtomicReference<>();

			final ICharsetDetectionObserver before = this.mObserver;

			return this.lock.tran(() -> {
				this.reset();
				this.mObserver = found::set;

				byte[] buf = new byte[1024];
				int len;
				boolean done = false;
				boolean isAscii = true;

				while ((len = imp.read(buf,
				                       0,
				                       buf.length)) != -1) {

					// Check if the stream is only ascii.
					if (isAscii) {
						isAscii = this.isAscii(buf,
						                       len);
					}

					// DoIt if non-ascii and not done yet.
					if (!isAscii) {
						done = this.doIt(buf,
						                 len,
						                 false);
					}

					if (done) {
						break;
					}
				}
				this.dataEnd();

				if (isAscii) {
					if (log.isTraceEnabled()) {
						log.trace("CHARSET = ASCII");
					}
					found.set(CharSets.ASCII);
				}

				this.mObserver = before;

				return found.get();
			});
		}

		/**
		 * @param aBuf
		 * @param aLen
		 * @return
		 */
		public boolean isAscii(byte[] aBuf,
		                       int aLen) {

			for (int i = 0; i < aLen; i++) {
				if ((0x0080 & aBuf[i]) != 0) {
					return false;
				}
			}
			return true;
		}
	}

	class NLSDetector
			extends PSMDetector
			implements ICharsetDetector {
		/**
		 *
		 */
		boolean ascii = true;

		private CharSets result = CharSets.SYS_DEPEND;

		/**
		 * @param langFlag
		 */
		private NLSDetector(TargetLocale langFlag) {
			super(langFlag);
		}

		/**
		 * @param file
		 * @return
		 * @throws IOException
		 */
		public static CharSets detect(File file) throws IOException {
			return detect(file,
			              TargetLocale.KOREAN);
		}

		/**
		 * @param file
		 * @param lang
		 * @return
		 * @throws IOException
		 */
		public static CharSets detect(File file,
		                              TargetLocale lang) throws IOException {
			InputStream in = null;
			try {
				in = new FileInputStream(file);
				return detect(in,
				              lang);
			}
			finally {
				ObjectFinalizer.close(in);
			}
		}

		/**
		 * @param in
		 * @param lang
		 * @return
		 * @throws IOException
		 */
		public static CharSets detect(InputStream in,
		                              TargetLocale lang) throws IOException {
			return create(in,
			              lang).getResult();
		}

		/**
		 * @param in
		 * @param lang
		 * @return
		 * @throws IOException
		 */
		public static NLSDetector create(InputStream in,
		                                 TargetLocale lang) throws IOException {
			NLSDetector det = new NLSDetector(lang);

			int len;

			byte[] bytes = null;
			bytes = new byte[1024];
			while ((len = in.read(bytes,
			                      0,
			                      bytes.length)) != -1) {

				// Check if the stream is only ascii.
				if (det.isAscii()) {
					det.detectAscii(bytes,
					                len);
				}

				// DoIt if non-ascii and not done yet.
				if (!det.isAscii() && !det.isDone()) {
					det.doIt(bytes,
					         len,
					         false);
				}

				if (det.isDone()) {
					break;
				}
			}

			det.dataEnd();

			return det;
		}

		/**
		 * @return
		 */
		public CharSets getResult() {
			if (this.isAscii()) {
				return CharSets.ASCII;
			}

			return this.result;
		}

		/**
		 * @return the ascii
		 */
		public boolean isAscii() {
			return this.ascii;
		}

		/**
		 * @param aBuf
		 * @param aLen
		 * @return
		 */
		public void detectAscii(byte[] aBuf,
		                        int aLen) {

			for (int i = 0; i < aLen; i++) {
				if ((0x0080 & aBuf[i]) != 0) {
					this.ascii = false;
					return;
				}
			}
			this.ascii = true;
		}

		/**
		 * @return
		 */
		public boolean isDone() {
			return this.done.get();
		}

		/**
		 *
		 */
		@Override
		public void init(ICharsetDetectionObserver aObserver) {

		}

		/**
		 *
		 */
		@Override
		public boolean doIt(byte[] aBuf,
		                    int aLen,
		                    boolean oDontFeedMe) {

			if (aBuf == null || oDontFeedMe) {
				return false;
			}

			this.handleData(aBuf,
			                aLen);
			return this.done.get();
		}

		/**
		 *
		 */
		@Override
		public void done() {
			this.dataEnd();
		}

		/**
		 *
		 */
		@Override
		public void report(CharSets charset) {
			this.result = charset;
		}
	}

	final class CharsetDetector
			implements ICharsetDetectionObserver {
		final TargetLocale lang;

		final Detector det;

		final AtomicReference<CharSets> detectCharset = new AtomicReference<>();

		public CharsetDetector(TargetLocale lang) {
			super();
			this.lang = lang;
			this.det = new Detector(lang);
			this.det.init(this);
		}

		@Override
		public void notify(CharSets charset) {
			this.detectCharset.set(charset);
		}

		public CharSets detectCharset(byte[] source) {
			final Detector det = this.det;
			final AtomicReference<CharSets> detectCharset = this.detectCharset;

			boolean ascii = det.isAscii(source,
			                            source.length);

			if (ascii) {
				// cannot find continue next block
				return CharSets.ASCII;
			}

			det.doIt(source,
			         source.length,
			         false);

			return detectCharset.get();
		}

		public CharSets detectCharset(InputStream in) throws IOException {
			final Detector det = this.det;
			final AtomicReference<CharSets> detectCharset = this.detectCharset;

			final byte[] buffer = new byte[2048];

			StreamAssist.iterateStream(in,
			                           1024,
			                           (sequence, block, length) -> {
				                           byte[] scanTarget = block;
				                           int scanLength = length;
				                           if (sequence > 0) {
					                           scanTarget = buffer;
					                           scanLength = length + 1024;

					                           System.arraycopy(buffer,
					                                            0,
					                                            buffer,
					                                            1024,
					                                            1024);
				                           }

				                           if (length == 1024) {
					                           System.arraycopy(block,
					                                            0,
					                                            buffer,
					                                            1024,
					                                            length);
				                           }

				                           boolean ascii = det.isAscii(scanTarget,
				                                                       scanLength);
				                           if (!ascii) {
					                           det.doIt(scanTarget,
					                                    scanLength,
					                                    false);
				                           }

				                           if (ascii) {
					                           // cannot find continue next block
					                           return false;
				                           }

				                           return detectCharset.get() != null || length < 1024;
			                           });

			return detectCharset.get();
		}
	}

	final class CharsetAnalyzer
			implements ICharsetDetectionObserver {

		/**
		 *
		 */
		final TargetLocale lang;

		/**
		 *
		 */
		final byte[] buf;

		/**
		 *
		 */
		final int length;

		/**
		 *
		 */
		final CharSets defaultCharset;

		/**
		 *
		 */
		CharSets detectCharset = null;

		/**
		 * @param in
		 * @param lang
		 */
		public CharsetAnalyzer(byte[] in,
		                       int length,
		                       TargetLocale lang,
		                       CharSets defaultCharset) {
			super();
			this.lang = lang;
			this.length = length;
			this.buf = in;
			this.defaultCharset = defaultCharset;
		}

		@Override
		public void notify(CharSets charset) {
			this.detectCharset = charset;
		}

		/**
		 * @return
		 */
		public String getString() {
			final CharSets charset = this.getCharset();

			if (charset == null) {
				return new String(this.buf,
				                  0,
				                  this.length);
			}

			return new String(this.buf,
			                  0,
			                  this.length,
			                  charset.getCharset());
		}

		/**
		 * @return
		 */
		public CharSets getCharset() {
			Detector det = new Detector(this.lang);
			det.init(this);
			boolean ascii = det.isAscii(this.buf,
			                            this.length);
			if (!ascii) {
				det.doIt(this.buf,
				         this.length,
				         false);
			}

			if (ascii) {
				return CharSets.SYS_DEPEND;
			}

			return this.detectCharset == null
			       ? this.defaultCharset
			       : this.detectCharset;
		}
	}

	class UTF8Verifier
			implements Verifier {

		@Override
		public CharSets charset() {
			return CharSets.UTF_8;
		}

		@Override
		public int stFactor() {
			return UTF8_stFactor;
		}

		@Override
		public int[] cclass() {
			return constValue.UTF8_cclass;
		}

		@Override
		public int[] states() {
			return constValue.UTF8_states;
		}

		@Override
		public boolean isUCS2() {
			return true;
		}

	}

	class UCS2LEVerifier
			implements Verifier {

		@Override
		public CharSets charset() {
			return CharSets.UTF_16LE;
		}

		@Override
		public int stFactor() {
			return UCS2LE_stFactor;
		}

		@Override
		public int[] cclass() {
			return constValue.UCS2LE_cclass;
		}

		@Override
		public int[] states() {
			return constValue.UCS2LE_states;
		}

		@Override
		public boolean isUCS2() {
			return false;
		}

	}

	class UCS2BEVerifier
			implements Verifier {

		@Override
		public CharSets charset() {
			return CharSets.UTF_16BE;
		}

		@Override
		public int stFactor() {
			return UCS2BE_stFactor;
		}

		@Override
		public int[] cclass() {
			return constValue.UCS2BE_cclass;
		}

		@Override
		public int[] states() {
			return constValue.UCS2BE_states;
		}

		@Override
		public boolean isUCS2() {
			return false;
		}

	}

	class SJISVerifier
			implements Verifier {

		@Override
		public CharSets charset() {
			return CharSets.SHIFT_JIS;
		}

		@Override
		public int stFactor() {
			return SJIS_stFactor;
		}

		@Override
		public int[] cclass() {
			return constValue.SJIS_cclass;
		}

		@Override
		public int[] states() {
			return constValue.SJIS_states;
		}

		@Override
		public boolean isUCS2() {
			return true;
		}

	}

	class ISO2022KRVerifier
			implements Verifier {

		@Override
		public CharSets charset() {
			return CharSets.ISO_2022_KR;
		}

		@Override
		public int stFactor() {
			return ISO2022KR_stFactor;
		}

		@Override
		public int[] cclass() {
			return constValue.ISO2022KR_cclass;
		}

		@Override
		public int[] states() {
			return constValue.ISO2022KR_states;
		}

		@Override
		public boolean isUCS2() {
			return true;
		}

	}

	// ISO2022KR

	class ISO2022JPVerifier
			implements Verifier {

		@Override
		public CharSets charset() {
			return CharSets.ISO_2022_JP;
		}

		@Override
		public int stFactor() {
			return ISO2022JP_stFactor;
		}

		@Override
		public int[] cclass() {
			return constValue.ISO2022JP_cclass;
		}

		@Override
		public int[] states() {
			return constValue.ISO2022JP_states;
		}

		@Override
		public boolean isUCS2() {
			return true;
		}

	}

	class ISO2022CNVerifier
			implements Verifier {

		@Override
		public CharSets charset() {
			return CharSets.ISO_2022_CN;
		}

		@Override
		public int stFactor() {
			return ISO2022CN_stFactor;
		}

		@Override
		public int[] cclass() {
			return constValue.ISO2022CN_cclass;
		}

		@Override
		public int[] states() {
			return constValue.ISO2022CN_states;
		}

		@Override
		public boolean isUCS2() {
			return true;
		}

	}

	class HZVerifier
			implements Verifier {

		@Override
		public CharSets charset() {
			return CharSets.HZ_GB;
		}

		@Override
		public int stFactor() {
			return HZ_stFactor;
		}

		@Override
		public int[] cclass() {
			return constValue.HZ_cclass;
		}

		@Override
		public int[] states() {
			return constValue.HZ_states;
		}

		@Override
		public boolean isUCS2() {
			return true;
		}

	}

	class GB2312Verifier
			implements Verifier {

		@Override
		public CharSets charset() {
			return CharSets.GB2312;
		}

		@Override
		public int stFactor() {
			return GB2312_stFactor;
		}

		@Override
		public int[] cclass() {
			return constValue.GB2312_cclass;
		}

		@Override
		public int[] states() {
			return constValue.GB2312_states;
		}

		@Override
		public boolean isUCS2() {
			return true;
		}

	}

	class GB18030Verifier
			implements Verifier {

		@Override
		public CharSets charset() {
			return CharSets.GB18030;
		}

		@Override
		public int stFactor() {
			return GB18030_stFactor;
		}

		@Override
		public int[] cclass() {
			return constValue.GB18030_cclass;
		}

		@Override
		public int[] states() {
			return constValue.GB18030_states;
		}

		@Override
		public boolean isUCS2() {
			return true;
		}

	}

	class EUCTWVerifier
			implements Verifier {

		@Override
		public CharSets charset() {
			return CharSets.EUC_TW;
		}

		@Override
		public int stFactor() {
			return EUCTW_stFactor;
		}

		@Override
		public int[] cclass() {
			return constValue.EUCTW_cclass;
		}

		@Override
		public int[] states() {
			return constValue.EUCTW_states;
		}

		@Override
		public boolean isUCS2() {
			return true;
		}

	}

	class EUCKRVerifier
			implements Verifier {

		@Override
		public CharSets charset() {
			return CharSets.EUC_KR;
		}

		@Override
		public int stFactor() {
			return EUCKR_stFactor;
		}

		@Override
		public int[] cclass() {
			return constValue.EUCKR_cclass;
		}

		@Override
		public int[] states() {
			return constValue.EUCKR_states;
		}

		@Override
		public boolean isUCS2() {
			return true;
		}

	}

	class EUCJPVerifier
			implements Verifier {

		@Override
		public CharSets charset() {
			return CharSets.EUC_JP;
		}

		@Override
		public int stFactor() {
			return EUCJP_stFactor;
		}

		@Override
		public int[] cclass() {
			return constValue.EUCJP_cclass;
		}

		@Override
		public int[] states() {
			return constValue.EUCJP_states;
		}

		@Override
		public boolean isUCS2() {
			return true;
		}

	}

	class CP1252Verifier
			implements Verifier {

		@Override
		public CharSets charset() {
			return CharSets.CP1252;
		}

		@Override
		public int stFactor() {
			return CP1252_stFactor;
		}

		@Override
		public int[] cclass() {
			return constValue.CP1252_cclass;
		}

		@Override
		public int[] states() {
			return constValue.CP1252_states;
		}

		@Override
		public boolean isUCS2() {
			return true;
		}

	}

	class BIG5Verifier
			implements Verifier {

		@Override
		public CharSets charset() {
			return CharSets.BIG5;
		}

		@Override
		public int stFactor() {
			return BIG5_stFactor;
		}

		@Override
		public int[] cclass() {
			return constValue.BIG5_cclass;
		}

		@Override
		public int[] states() {
			return constValue.BIG5_states;
		}

		@Override
		public boolean isUCS2() {
			return true;
		}

	}

	class GB2312Statistics
			implements EUCStatistics {

		/**
		 *
		 */
		@Override
		public float[] mFirstByteFreq() {
			return constValue.GB2312_FirstByteFreq;
		}

		/**
		 *
		 */
		@Override
		public float mFirstByteStdDev() {
			return GB2312_FirstByteStdDev;
		}

		/**
		 *
		 */
		@Override
		public float mFirstByteMean() {
			return GB2312_FirstByteMean;
		}

		/**
		 *
		 */
		@Override
		public float mFirstByteWeight() {
			return GB2312_FirstByteWeight;
		}

		/**
		 *
		 */
		@Override
		public float[] mSecondByteFreq() {
			return constValue.GB2312_SecondByteFreq;
		}

		/**
		 *
		 */
		@Override
		public float mSecondByteStdDev() {
			return GB2312_SecondByteStdDev;
		}

		/**
		 *
		 */
		@Override
		public float mSecondByteMean() {
			return GB2312_SecondByteMean;
		}

		/**
		 *
		 */
		@Override
		public float mSecondByteWeight() {
			return GB2312_SecondByteWeight;
		}
	}

	class EUCTWStatistics
			implements EUCStatistics {

		@Override
		public float[] mFirstByteFreq() {
			return constValue.EUCTW_FirstByteFreq;
		}

		@Override
		public float mFirstByteStdDev() {
			return EUCTW_FirstByteStdDev;
		}

		@Override
		public float mFirstByteMean() {
			return EUCTW_FirstByteMean;
		}

		@Override
		public float mFirstByteWeight() {
			return EUCTW_FirstByteWeight;
		}

		@Override
		public float[] mSecondByteFreq() {
			return constValue.EUCTW_SecondByteFreq;
		}

		@Override
		public float mSecondByteStdDev() {
			return EUCTW_SecondByteStdDev;
		}

		@Override
		public float mSecondByteMean() {
			return EUCTW_SecondByteMean;
		}

		@Override
		public float mSecondByteWeight() {
			return EUCTW_SecondByteWeight;
		}
	}

	class EUCKRStatistics
			implements EUCStatistics {
		/**
		 *
		 */
		@Override
		public float[] mFirstByteFreq() {
			return constValue.EUCKR_FirstByteFreq;
		}

		/**
		 *
		 */
		@Override
		public float mFirstByteStdDev() {
			return EUCKR_FirstByteStdDev;
		}

		/**
		 *
		 */
		@Override
		public float mFirstByteMean() {
			return EUCKR_FirstByteMean;
		}

		/**
		 *
		 */
		@Override
		public float mFirstByteWeight() {
			return EUCKR_FirstByteWeight;
		}

		/**
		 *
		 */
		@Override
		public float[] mSecondByteFreq() {
			return constValue.EUCKR_SecondByteFreq;
		}

		/**
		 *
		 */
		@Override
		public float mSecondByteStdDev() {
			return EUCKR_SecondByteStdDev;
		}

		/**
		 *
		 */
		@Override
		public float mSecondByteMean() {
			return EUCKR_SecondByteMean;
		}

		/**
		 *
		 */
		@Override
		public float mSecondByteWeight() {
			return EUCKR_SecondByteWeight;
		}
	}

	class EUCJPStatistics
			implements EUCStatistics {
		/**
		 *
		 */
		@Override
		public float[] mFirstByteFreq() {
			return constValue.EUCJP_FirstByteFreq;
		}

		/**
		 *
		 */
		@Override
		public float mFirstByteStdDev() {
			return EUCJP_FirstByteStdDev;
		}

		/**
		 *
		 */
		@Override
		public float mFirstByteMean() {
			return EUCJP_FirstByteMean;
		}

		/**
		 *
		 */
		@Override
		public float mFirstByteWeight() {
			return EUCJP_FirstByteWeight;
		}

		/**
		 *
		 */
		@Override
		public float[] mSecondByteFreq() {
			return constValue.EUCJP_SecondByteFreq;
		}

		/**
		 *
		 */
		@Override
		public float mSecondByteStdDev() {
			return EUCJP_SecondByteStdDev;
		}

		/**
		 *
		 */
		@Override
		public float mSecondByteMean() {
			return EUCJP_SecondByteMean;
		}

		/**
		 *
		 */
		@Override
		public float mSecondByteWeight() {
			return EUCJP_SecondByteWeight;
		}
	}

	class Big5Statistics
			implements EUCStatistics {

		/**
		 *
		 */
		@Override
		public float[] mFirstByteFreq() {
			return constValue.Big5_FirstByteFreq;
		}

		/**
		 *
		 */
		@Override
		public float mFirstByteStdDev() {
			return Big5_FirstByteStdDev;
		}

		/**
		 *
		 */
		@Override
		public float mFirstByteMean() {
			return Big5_FirstByteMean;
		}

		/**
		 *
		 */
		@Override
		public float mFirstByteWeight() {
			return Big5_FirstByteWeight;
		}

		/**
		 *
		 */
		@Override
		public float[] mSecondByteFreq() {
			return constValue.Big5_SecondByteFreq;
		}

		/**
		 *
		 */
		@Override
		public float mSecondByteStdDev() {
			return Big5_SecondByteStdDev;
		}

		/**
		 *
		 */
		@Override
		public float mSecondByteMean() {
			return Big5_SecondByteMean;
		}

		/**
		 *
		 */
		@Override
		public float mSecondByteWeight() {
			return Big5_SecondByteWeight;
		}
	}
}
