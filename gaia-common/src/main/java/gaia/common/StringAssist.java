package gaia.common;


import static gaia.common.ResourceAssist.stringBuilderWith;
import static gaia.common.ResourceAssist.stringComposer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

import gaia.ConstValues;
import gaia.common.GWCommon.AttributeContextAccessor;


/**
 * @author sklee
 */
public interface StringAssist {
	enum NamePattern {
		snake("_"),
		kebab("-"),
		camel("([a-z])([A-Z]+)") {
			@Override
			Stream<String> elements0(String source) {
				final Matcher matcher = this.pattern.matcher(source);

				if (matcher.find()) {
					List<String> buffer = new ArrayList<>();

					int index = 0;
					do {
						buffer.add(source.substring(index,
						                            matcher.start() + 1)
						                 .toLowerCase());

						index = matcher.start() + 1;
					} while (matcher.find());

					buffer.add(source.substring(index)
					                 .toLowerCase());

					return buffer.stream();
				}

				return Stream.of(source);
			}

			@Override
			public String compose(Stream<String> elements) {
				final AtomicBoolean upper = new AtomicBoolean(false);
				return stringComposer().add(builder -> elements.forEach(element -> {
					                       if (upper.compareAndSet(false,
					                                               true)) {
						                       builder.append(element);
					                       } else {
						                       if (!element.isEmpty()) {
							                       builder.append(element.substring(0,
							                                                        1)
							                                             .toUpperCase());
						                       }
						                       if (element.length() > 1) {
							                       builder.append(element.substring(1)
							                                             .toLowerCase());
						                       }
					                       }
				                       }))
				                       .build();
			}
		};

		final Pattern pattern;
		final String source;

		NamePattern(String source) {
			this.source = source;
			this.pattern = Pattern.compile(source);
		}

		public final Stream<String> elements(String source) {
			if (StringUtils.isEmpty(source)) {
				return Stream.empty();
			}
			return this.elements0(source);
		}

		public final String encode(String source) {
			return transfer(source,
			                this);
		}

		public final String decode(String source) {
			return transfer(source,
			                this,
			                camel);
		}

		public String compose(Stream<String> elements) {
			return elements.map(String::toLowerCase)
			               .collect(Collectors.joining(this.source));
		}

		Stream<String> elements0(String source) {
			return this.pattern.splitAsStream(source)
			                   .map(String::toLowerCase);
		}

		boolean detect(String source) {
			return this.pattern.matcher(source)
			                   .find();
		}

		public static boolean match(String source,
		                            String target) {
			if (StringUtils.equalsIgnoreCase(source,
			                                 target)) {
				return true;
			}
			if (StringUtils.isEmpty(source) || StringUtils.isEmpty(target)) {
				return false;
			}

			return matched(source,
			               target);
		}

		static boolean matched(String source,
		                       String target) {
			return StringUtils.equalsIgnoreCase(transfer(source),
			                                    transfer(target));
		}

		public static String transfer(String source,
		                              NamePattern from,
		                              NamePattern to) {
			return to.compose(from.elements(source));
		}

		public static String transfer(final String source) {
			return transfer(source,
			                camel);
		}

		public static String transfer(final String source,
		                              final NamePattern to) {
			return Stream.of(NamePattern.values())
			             .filter(p -> p.detect(source))
			             .findFirst()
			             .map(from -> to.compose(from.elements(source)))
			             .orElseGet(source::toLowerCase);
		}

		public static BiPredicate<String, String> predicate = NamePattern::match;
	}

	interface StringSplitter {
		Stream<String> stream(CharSequence input);

		default List<String> list(CharSequence input) {
			return this.stream(input)
			           .collect(Collectors.toList());
		}

		default String[] array(CharSequence input) {
			return this.stream(input)
			           .toArray(String[]::new);
		}

		default Set<String> set(CharSequence input) {
			return this.stream(input)
			           .collect(Collectors.toSet());
		}
	}

	enum SplitPattern
			implements StringSplitter {
		space(Pattern.compile("\\s+")),
		comma(Pattern.compile(",+")),
		common(Pattern.compile("[,\\s]+")),
		path(Pattern.compile(FunctionAssist.supply(() -> "[" + File.pathSeparator + "]+"))),
		directory(Pattern.compile("[/\\\\]+")),
		semi_colon(Pattern.compile(";"));

		final Pattern pattern;

		SplitPattern(Pattern pattern) {
			this.pattern = pattern;
		}

		@Override
		public Stream<String> stream(CharSequence input) {
			return Objects.isNull(input)
			       ? Stream.empty()
			       : this.pattern.splitAsStream(input);
		}
	}

	// ---------------------------------------------------------------------
	// Section refactoring base line
	// ---------------------------------------------------------------------
	AttributeContextAccessor<Formatter> FORMATTER = AttributeContextAccessor.of(Formatter.class,
	                                                                            Formatter::new);

	static String format(String format,
	                     Object... args) {
		final Formatter formatter = FORMATTER.get();
		try {
			return formatter.format(format,
			                        args)
			                .toString();
		}
		finally {
			((StringBuilder) formatter.out()).setLength(0);
		}
	}

	Pattern PTN_DOLLAR = Pattern.compile("\\$\\{([^}]*)}");

	Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{([^}]*)}");

	String PATH_EXPRESSION = "([^/]*)";

	static String transformDollarPattern(final String query,
	                                     final Map<String, Object> vo) {
		return transformPattern(PTN_DOLLAR,
		                        query,
		                        vo);
	}

	static String transformPattern(final Pattern ptn,
	                               final String query,
	                               final Map<String, Object> map) {
		final Matcher matcher = ptn.matcher(query);

		if (matcher.find()) {
			StringBuilder buffer = new StringBuilder();

			int index = 0;
			do {
				buffer.append(query,
				              index,
				              matcher.start());
				final String key = query.substring(matcher.start(1),
				                                   matcher.end(1));

				final String value = valueOf(map.get(key));

				buffer.append(value);

				index = matcher.end();
			} while (matcher.find());
			buffer.append(query.substring(index));

			return buffer.toString();
		}

		return query;
	}


	static void transformPattern(final Pattern ptn,
	                             final String query,
	                             final BiConsumer<Boolean, String> supplier) {
		final Matcher matcher = ptn.matcher(query);

		if (matcher.find()) {
			int index = 0;
			do {
				supplier.accept(false,
				                query.substring(index,
				                                matcher.start()));
				supplier.accept(true,
				                query.substring(matcher.start(1),
				                                matcher.end(1)));
				index = matcher.end();
			} while (matcher.find());
			supplier.accept(false,
			                query.substring(index));

		} else {
			supplier.accept(false,
			                query);
		}
	}

	static String valueOf(final Object obj) {
		return valueOf(obj,
		               ConstValues.BLANK_STRING);
	}

	static String valueOf(final Object obj,
	                      final String format) {
		if (obj == null) {
			return ConstValues.BLANK_STRING;
		}

		return obj.toString();
	}

	static String toString(Object obj) {
		if (obj == null) {
			return ConstValues.BLANK_STRING;
		}

		return obj.toString();
	}

	static boolean equals(final String s1,
	                      final String s2) {
		return equals(s1,
		              s2,
		              false);
	}

	static boolean equals(final String s1,
	                      final String s2,
	                      final boolean ignoreCase) {
		return ignoreCase
		       ? StringUtils.equalsIgnoreCase(s1,
		                                      s2)
		       : StringUtils.equals(s1,
		                            s2);
	}

	static String trim(Object source) {
		return source == null
		       ? ConstValues.BLANK_STRING
		       : trim(source.toString());
	}

	// -------------------------------------------------------------------------------------- camel case

	static String trim(CharSequence source) {
		if (source == null) {
			return ConstValues.BLANK_STRING;
		}
		final int length = source.length();
		final int st = trimStart(source);
		final int len = trimEnd(source,
		                        st);

		return st > 0 || len < length
		       ? source.subSequence(st,
		                            len)
		               .toString()
		       : source.toString();
	}

	static int trimStart(CharSequence source) {
		int len = source.length();
		int st = 0;

		while (st < len) {
			switch (source.charAt(st)) {
				case ConstValues.CHAR_BLANK:
				case ConstValues.CHAR_CR:
				case ConstValues.CHAR_LF:
				case ConstValues.CHAR_TAB:
				case ConstValues.CHAR_NULL: {
					st++;
					break;
				}

				default: {
					return st;
				}
			}
		}

		return st;
	}

	static int trimEnd(final CharSequence source,
	                   final int st) {
		int len = source.length();

		while (st < len) {
			switch (source.charAt(len - 1)) {
				case ConstValues.CHAR_BLANK:
				case ConstValues.CHAR_CR:
				case ConstValues.CHAR_LF:
				case ConstValues.CHAR_TAB:
				case ConstValues.CHAR_NULL: {
					len--;
					break;
				}

				default: {
					return len;
				}
			}
		}

		return len;
	}

	static boolean equalsIgnoreCase(final String s1,
	                                final String s2) {
		return equals(s1,
		              s2,
		              true);
	}

	static String lpad(final String source,
	                   final char empty,
	                   final int size) {
		final int length = null == source
		                   ? 0
		                   : source.length();

		if (length >= size) {
			return source;
		}

		return stringComposer().add(buffer -> {
			                       IntStream.range(length,
			                                       size)
			                                .forEach(i -> buffer.append(empty));
			                       buffer.append(source);
		                       })
		                       .build();
	}

	static String[] split(final String source,
	                      final String delim) {
		return split(source,
		             delim,
		             true,
		             true);
	}

	static String[] split(final String source,
	                      final String delim,
	                      final boolean trim,
	                      final boolean skipNull) {
		final String[] elms = source.split(delim);
		boolean containNull = false;

		final int length = elms.length;
		for (int i = 0; i < length; i++) {
			if (trim) {
				elms[i] = trim(elms[i]);
			}

			if (containNull) {
				continue;
			}

			containNull = elms[i].length() < 1;
		}

		if (skipNull && containNull) {
			ArrayList<String> list = new ArrayList<>();
			for (String elm : elms) {
				if (elm.length() > 0) {
					list.add(elm);
				}
			}

			return list.toArray(new String[0]);
		}
		return elms;
	}

	static String[] split(final String source,
	                      final char delim,
	                      final boolean trim,
	                      final boolean skipNull) {
		return split(source,
		             delim,
		             trim,
		             skipNull,
		             false);
	}

	static String[] split(final String source,
	                      final char delim,
	                      final boolean trim,
	                      final boolean skipNull,
	                      final boolean applyEscape) {
		if (source == null) {
			return ConstValues.BLANK_STRING_ARRAY;
		}

		final ArrayList<String> array = new ArrayList<>();
		final int length = source.length();

		stringBuilderWith(builder -> {
			if (applyEscape) {
				boolean escape = false;
				for (int i = 0; i < length; i++) {
					final char elm = source.charAt(i);

					if (elm == '\\') {
						if (escape) {
							builder.append(elm);
							escape = false;
						} else {
							escape = true;
						}
					} else {
						if (escape) {
							switch (elm) {
								case 't': {
									builder.append('\t');
									break;
								}

								case 'r': {
									builder.append('\r');
									break;
								}
								case 'n': {
									builder.append('\n');
									break;
								}
								default: {
									builder.append(elm);
									break;
								}
							}
							escape = false;
						} else {
							if (elm == delim) {
								appendSplitElm(array,
								               builder,
								               trim,
								               skipNull);
								builder.setLength(0);
							} else {
								builder.append(elm);
							}
						}
					}
				}
			} else {
				for (int i = 0; i < length; i++) {
					final char elm = source.charAt(i);
					if (elm == delim) {
						appendSplitElm(array,
						               builder,
						               trim,
						               skipNull);
						builder.setLength(0);
					} else {
						builder.append(elm);
					}
				}
			}
			appendSplitElm(array,
			               builder,
			               trim,
			               skipNull);
			builder.setLength(0);
		});

		return array.toArray(new String[0]);
	}

	static void appendSplitElm(List<String> array,
	                           StringBuilder source,
	                           final boolean trim,
	                           final boolean skipNull) {
		if (source.length() == 0) {
			if (skipNull) {
				return;
			}

			array.add(ConstValues.BLANK_STRING);
			return;
		}

		String val = source.toString();

		if (trim) {
			val = trim(val);

			if (skipNull && val.length() == 0) {
				return;
			}
		}

		array.add(val);
	}

	static String concat(final String... source) {
		if (source == null || source.length == 0) {
			return ConstValues.BLANK_STRING;
		}

		StringBuilder buffer = null;
		try {
			buffer = new StringBuilder();

			for (final String elm : source) {
				if (StringUtils.isNotEmpty(elm)) {
					buffer.append(elm);
				}
			}

			return buffer.toString();
		}
		finally {
			ObjectFinalizer.close(buffer);
		}
	}

	static String concat(final Object[] source,
	                     final Consumer<StringBuilder> builder) {
		if (source == null || source.length == 0) {
			return ConstValues.BLANK_STRING;
		}

		return stringComposer().add(buffer -> {
			                       for (final Object elm : source) {
				                       if (buffer.length() > 0) {
					                       builder.accept(buffer);
				                       }
				                       buffer.append(valueOf(elm));
			                       }
		                       })
		                       .build();
	}

	static String concat(final Iterable<Object> collection,
	                     final Consumer<StringBuilder> builder) {
		if (collection == null) {
			return ConstValues.BLANK_STRING;
		}
		return stringComposer().add(buffer -> collection.forEach(elm -> {
			                       if (buffer.length() > 0) {
				                       builder.accept(buffer);
			                       }
			                       buffer.append(valueOf(elm));
		                       }))
		                       .build();
	}

	/**
	 * character 단위로 지정 사이즈로 분리함.
	 *
	 * @param content 내용
	 * @param block   블럭 사이즈
	 * @return
	 */
	static String[] split(final String content,
	                      final int block) {
		ArrayList<String> array = new ArrayList<>();
		final int limit = content.length();
		int index = 0;

		while (index < limit) {
			final int toIndex = Math.min(index + block,
			                             limit);
			String part = content.substring(index,
			                                toIndex);

			array.add(part);

			index = toIndex;
		}

		return array.toArray(new String[0]);
	}

	static String rpad(final String source,
	                   final char empty,
	                   final int size) {
		final byte[] sourceBytes = source.getBytes();
		final int iSourceLength = sourceBytes.length;
		if (iSourceLength == size) {
			return source;
		}

		if (iSourceLength > size) {
			return new String(sourceBytes,
			                  0,
			                  size);
		}

		return stringComposer().add(buffer -> {
			                       buffer.append(source);
			                       while (buffer.length() < size) {
				                       buffer.append(empty);
			                       }
		                       })
		                       .build();
	}

	static String trimSpace(String source) {
		if (source == null) {
			return ConstValues.BLANK_STRING;
		}

		return stringComposer().add(buffer -> {
			                       boolean appendBlank = false;

			                       for (char at : source.trim()
			                                            .toCharArray()) {
				                       switch (at) {
					                       case ConstValues.CHAR_BLANK:
					                       case ConstValues.CHAR_CR:
					                       case ConstValues.CHAR_LF:
					                       case ConstValues.CHAR_TAB: {
						                       if (!appendBlank) {
							                       appendBlank = true;
							                       buffer.append(' ');
						                       }
						                       break;
					                       }

					                       default: {
						                       appendBlank = false;
						                       buffer.append(at);
						                       break;
					                       }
				                       }
			                       }
		                       })
		                       .build();
	}

	static String nvl(Object source,
	                  String df) {
		if (source == null) {
			return df;
		}

		return nvl(source.toString(),
		           df);
	}

	static String nvl(String source,
	                  String df) {
		return StringUtils.isEmpty(source)
		       ? df
		       : source;
	}

	static Stream<String> delimitStream(final String source,
	                                    final char delim) {
		return delimitList(source,
		                   delim).stream();
	}

	@SuppressWarnings("unchecked")
	static List<String> delimitList(final String source,
	                                final char delim) {
		if (source == null) {
			return Collections.EMPTY_LIST;
		}

		ArrayList<String> array = new ArrayList<>();

		final int length = source.length();

		StringBuilder buffer = null;
		try {
			buffer = new StringBuilder();

			for (int i = 0; i < length; i++) {
				final char elm = source.charAt(i);
				if (elm == delim) {
					appendSplitElm(array,
					               buffer,
					               true,
					               true);
					buffer.setLength(0);
				} else {
					buffer.append(elm);
				}
			}
			appendSplitElm(array,
			               buffer,
			               true,
			               true);
			buffer.setLength(0);
		}
		finally {
			ObjectFinalizer.close(buffer);
		}

		return array;
	}

	static void appendBackSlashEscape(StringBuilder builder,
	                                  String source) {
		for (char elm : source.toCharArray()) {
			if (elm == '\\') {
				builder.append('\\');
			}

			builder.append(elm);
		}
	}
}
