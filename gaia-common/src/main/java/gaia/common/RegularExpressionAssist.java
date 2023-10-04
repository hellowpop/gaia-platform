package gaia.common;

import static gaia.common.ResourceAssist.stringComposer;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

import gaia.common.CollectionSpec.LRUCacheMap;
/**
 * @author 이상근
 */
public interface RegularExpressionAssist {
	/**
	 *
	 */
	Pattern BLOCK_COMMENT = Pattern.compile("/\\*([^*]|[\\r\\n]|(\\*+([^*/]|[\\r\\n])))*\\*+/");
	Pattern COMMON_SPLITTER = Pattern.compile("[,\\s]+");
	Pattern SPACE_SPLITTER = Pattern.compile("\\s+");

	/**
	 *
	 */
	InnerCache cache = new InnerCache();

	/**
	 *
	 */
	static Pattern getPattern(String pattern) {
		return cache.get(pattern == null
		                 ? ".*"
		                 : pattern);
	}

	static Pattern asWildcardPattern(final String pattern) {
		if (StringUtils.isEmpty(pattern) || pattern.indexOf('*') < 0) {
			throw new IllegalArgumentException(StringAssist.format("[%s] is not a wildcard pattern",
			                                                       pattern));
		}

		final String replace = stringComposer().self(composer -> pattern.chars()
		                                                                .forEach(at -> {
			                                                                if (at == '*') {
				                                                                composer.add('.');
			                                                                }
			                                                                if (at == '.') {
				                                                                composer.add('\\');
			                                                                }
			                                                                composer.add((char) at);
		                                                                }))
		                                       .build();

		return cache.get(replace);
	}

	/**
	 * escape Regular Expression special characters
	 */
	static void escape(StringBuilder buffer,
	                   String brace) {
		final int length = brace.length();

		for (int i = 0; i < length; i++) {
			final char ch = brace.charAt(i);

			switch (ch) {
				case '$':
				case '[':
				case ']':
				case '{':
				case '}':
				case '-': {
					buffer.append('\\');
					break;
				}
			}

			buffer.append(ch);
		}
	}

	static void scanBracedBlock(String content,
	                            String braceStart,
	                            String braceEnd,
	                            Consumer<String> normal,
	                            Consumer<String> handle) {
		Pattern ptn = Pattern.compile(stringComposer().add(buffer -> {
			                                              RegularExpressionAssist.escape(buffer,
			                                                                             StringAssist.nvl(braceStart,
			                                                                                              "${"));
			                                              buffer.append("([a-zA-Z0-9_\\.+\\-\\/\\*\\(\\)\\[\\]:\\^~\\?=\\'\", ]*)");
			                                              RegularExpressionAssist.escape(buffer,
			                                                                             StringAssist.nvl(braceEnd,
			                                                                                              "}"));
		                                              })
		                                              .build());

		final Matcher matcher = ptn.matcher(content);

		int index = 0;
		while (matcher.find()) {
			// append plain
			normal.accept(content.substring(index,
			                                matcher.start()));
			handle.accept(content.substring(matcher.start(1),
			                                matcher.end(1)));
			// append expression
			index = matcher.end();
		}
		normal.accept(content.substring(index));
	}

	/**
	 * @author 이상근
	 */
	final class InnerCache
			extends LRUCacheMap<String, Pattern> {
		/**
		 *
		 */
		private static final long serialVersionUID = -2260388350602274567L;

		/**
		 *
		 */
		InnerCache() {
			super(16,
			      100,
			      false);
		}

		@Override
		protected Pattern generate(String key) {
			return Pattern.compile(key);
		}
	}


	/**
	 *
	 */
	static void iterateParts(CharSequence source,
	                         Consumer<CharSequence> consumer) {

		iterateParts(source,
		             COMMON_SPLITTER,
		             consumer);
	}

	/**
	 *
	 */
	static void iterateParts(CharSequence source,
	                         Pattern pattern,
	                         Consumer<CharSequence> consumer) {
		// skip empty
		if (StringUtils.isEmpty(source)) {
			return;
		}

		streamParts(source,
		            pattern).forEach(consumer);
	}

	static Stream<String> streamParts(CharSequence source) {
		return streamParts(source,
		                   COMMON_SPLITTER);
	}

	static Stream<String> streamParts(CharSequence source,
	                                  Pattern pattern) {
		// skip empty
		if (StringUtils.isEmpty(source)) {
			return Stream.empty();
		}

		return pattern.splitAsStream(source);
	}
}
