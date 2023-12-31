package gaia.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface StringMatcherSpec {

	/**
	 * Default path separator: "/".
	 */
	String pathSeparator = "/";
	int CACHE_TURNOFF_THRESHOLD = 1024;
	Pattern VARIABLE_PATTERN = Pattern.compile("\\{[^/]+?\\}");
	char[] WILDCARD_CHARS = {'*',
	                         '?',
	                         '{'};

	AntPathMatcher matcher = new AntPathMatcher();

	class AntPathMatcher {
		private final boolean caseSensitive = true;

		private final boolean trimTokens = false;

		private volatile Boolean cachePatterns;

		private final Map<String, String[]> tokenizedPatternCache = new ConcurrentHashMap<>(256);

		final Map<String, AntPathStringMatcher> stringMatcherCache = new ConcurrentHashMap<>(256);


		AntPathMatcher() {
		}

		private void deactivatePatternCache() {
			this.tokenizedPatternCache.clear();
			this.stringMatcherCache.clear();
		}

		public boolean isPattern(String path) {
			if (org.apache.commons.lang3.StringUtils.isEmpty(path)) {
				return false;
			}
			boolean uriVar = false;
			for (int i = 0; i < path.length(); i++) {
				char c = path.charAt(i);
				if (c == '*' || c == '?') {
					return true;
				}
				if (c == '{') {
					uriVar = true;
					continue;
				}
				if (c == '}' && uriVar) {
					return true;
				}
			}
			return false;
		}

		public boolean match(String pattern,
		                     String path) {
			return this.doMatch(pattern,
			                    path,
			                    true,
			                    null);
		}

		public boolean matchStart(String pattern,
		                          String path) {
			return this.doMatch(pattern,
			                    path,
			                    false,
			                    null);
		}

		/**
		 * Actually match the given {@code path} against the given {@code pattern}.
		 *
		 * @param pattern   the pattern to match against
		 * @param path      the path to test
		 * @param fullMatch whether a full pattern match is required (else a pattern match
		 *                  as far as the given base path goes is sufficient)
		 * @return {@code true} if the supplied {@code path} matched, {@code false} if it didn't
		 */
		protected boolean doMatch(String pattern,
		                          String path,
		                          boolean fullMatch,
		                          Map<String, String> uriTemplateVariables) {

			if (path == null || path.startsWith(pathSeparator) != pattern.startsWith(pathSeparator)) {
				return false;
			}

			String[] pattDirs = this.tokenizePattern(pattern);
			if (fullMatch && this.caseSensitive && !this.isPotentialMatch(path,
			                                                              pattDirs)) {
				return false;
			}

			String[] pathDirs = this.tokenizePath(path);
			int pattIdxStart = 0;
			int pattIdxEnd = pattDirs.length - 1;
			int pathIdxStart = 0;
			int pathIdxEnd = pathDirs.length - 1;

			// Match all elements up to the first **
			while (pattIdxStart <= pattIdxEnd && pathIdxStart <= pathIdxEnd) {
				String pattDir = pattDirs[pattIdxStart];
				if ("**".equals(pattDir)) {
					break;
				}
				if (this.matchStrings(pattDir,
				                      pathDirs[pathIdxStart],
				                      uriTemplateVariables)) {
					return false;
				}
				pattIdxStart++;
				pathIdxStart++;
			}

			if (pathIdxStart > pathIdxEnd) {
				// Path is exhausted, only match if rest of pattern is * or **'s
				if (pattIdxStart > pattIdxEnd) {
					return (pattern.endsWith(pathSeparator) == path.endsWith(pathSeparator));
				}
				if (!fullMatch) {
					return true;
				}
				if (pattIdxStart == pattIdxEnd && pattDirs[pattIdxStart].equals("*") && path.endsWith(pathSeparator)) {
					return true;
				}
				for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
					if (!pattDirs[i].equals("**")) {
						return false;
					}
				}
				return true;
			} else if (pattIdxStart > pattIdxEnd) {
				// String not exhausted, but pattern is. Failure.
				return false;
			} else if (!fullMatch && "**".equals(pattDirs[pattIdxStart])) {
				// Path start definitely matches due to "**" part in pattern.
				return true;
			}

			// up to last '**'
			while (pattIdxStart <= pattIdxEnd && pathIdxStart <= pathIdxEnd) {
				String pattDir = pattDirs[pattIdxEnd];
				if (pattDir.equals("**")) {
					break;
				}
				if (this.matchStrings(pattDir,
				                      pathDirs[pathIdxEnd],
				                      uriTemplateVariables)) {
					return false;
				}
				pattIdxEnd--;
				pathIdxEnd--;
			}
			if (pathIdxStart > pathIdxEnd) {
				// String is exhausted
				for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
					if (!pattDirs[i].equals("**")) {
						return false;
					}
				}
				return true;
			}

			while (pattIdxStart != pattIdxEnd && pathIdxStart <= pathIdxEnd) {
				int patIdxTmp = -1;
				for (int i = pattIdxStart + 1; i <= pattIdxEnd; i++) {
					if (pattDirs[i].equals("**")) {
						patIdxTmp = i;
						break;
					}
				}
				if (patIdxTmp == pattIdxStart + 1) {
					// '**/**' situation, so skip one
					pattIdxStart++;
					continue;
				}
				// Find the pattern between padIdxStart & padIdxTmp in str between
				// strIdxStart & strIdxEnd
				int patLength = (patIdxTmp - pattIdxStart - 1);
				int strLength = (pathIdxEnd - pathIdxStart + 1);
				int foundIdx = -1;

				strLoop:
				for (int i = 0; i <= strLength - patLength; i++) {
					for (int j = 0; j < patLength; j++) {
						String subPat = pattDirs[pattIdxStart + j + 1];
						String subStr = pathDirs[pathIdxStart + i + j];
						if (this.matchStrings(subPat,
						                      subStr,
						                      uriTemplateVariables)) {
							continue strLoop;
						}
					}
					foundIdx = pathIdxStart + i;
					break;
				}

				if (foundIdx == -1) {
					return false;
				}

				pattIdxStart = patIdxTmp;
				pathIdxStart = foundIdx + patLength;
			}

			for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
				if (!pattDirs[i].equals("**")) {
					return false;
				}
			}

			return true;
		}

		private boolean isPotentialMatch(String path,
		                                 String[] pattDirs) {
			if (!this.trimTokens) {
				int pos = 0;
				for (String pattDir : pattDirs) {
					int skipped = this.skipSeparator(path,
					                                 pos,
					                                 pathSeparator);
					pos += skipped;
					skipped = this.skipSegment(path,
					                           pos,
					                           pattDir);
					if (skipped < pattDir.length()) {
						return (skipped > 0 || (pattDir.length() > 0 && this.isWildcardChar(pattDir.charAt(0))));
					}
					pos += skipped;
				}
			}
			return true;
		}

		private int skipSegment(String path,
		                        int pos,
		                        String prefix) {
			int skipped = 0;
			for (int i = 0; i < prefix.length(); i++) {
				char c = prefix.charAt(i);
				if (this.isWildcardChar(c)) {
					return skipped;
				}
				int currPos = pos + skipped;
				if (currPos >= path.length()) {
					return 0;
				}
				if (c == path.charAt(currPos)) {
					skipped++;
				}
			}
			return skipped;
		}

		private int skipSeparator(String path,
		                          int pos,
		                          String separator) {
			int skipped = 0;
			while (path.startsWith(separator,
			                       pos + skipped)) {
				skipped += separator.length();
			}
			return skipped;
		}

		private boolean isWildcardChar(char c) {
			for (char candidate : WILDCARD_CHARS) {
				if (c == candidate) {
					return true;
				}
			}
			return false;
		}

		protected String[] tokenizePattern(String pattern) {
			String[] tokenized = this.tokenizedPatternCache.get(pattern);
			if (tokenized == null) {
				tokenized = this.tokenizePath(pattern);
				if (this.cachePatterns == null && this.tokenizedPatternCache.size() >= CACHE_TURNOFF_THRESHOLD) {
					// Try to adapt to the runtime situation that we're encountering:
					// There are obviously too many different patterns coming in here...
					// So let's turn off the cache since the patterns are unlikely to be reoccurring.
					this.deactivatePatternCache();
					return tokenized;
				}
				this.tokenizedPatternCache.put(pattern,
				                               tokenized);
			}
			return tokenized;
		}

		/**
		 * Tokenize the given path into parts, based on this matcher's settings.
		 *
		 * @param path the path to tokenize
		 * @return the tokenized path parts
		 */
		protected String[] tokenizePath(String path) {
			return tokenizeToStringArray(path,
			                             pathSeparator,
			                             this.trimTokens,
			                             true);
		}

		/**
		 * Test whether a string matches against a pattern.
		 *
		 * @param pattern the pattern to match against (never {@code null})
		 * @param str     the String which must be matched against the pattern (never {@code null})
		 * @return {@code true} if the string matches against the pattern, or {@code false} otherwise
		 */
		private boolean matchStrings(String pattern,
		                             String str,
		                             Map<String, String> uriTemplateVariables) {

			return !this.getStringMatcher(pattern)
			            .matchStrings(str,
			                          uriTemplateVariables);
		}

		protected AntPathStringMatcher getStringMatcher(String pattern) {
			AntPathStringMatcher matcher = this.stringMatcherCache.get(pattern);
			if (matcher == null) {
				matcher = new AntPathStringMatcher(pattern,
				                                   this.caseSensitive);
				if (this.stringMatcherCache.size() >= CACHE_TURNOFF_THRESHOLD) {
					// Try to adapt to the runtime situation that we're encountering:
					// There are obviously too many different patterns coming in here...
					// So let's turn off the cache since the patterns are unlikely to be reoccurring.
					this.deactivatePatternCache();
					return matcher;
				}
				this.stringMatcherCache.put(pattern,
				                            matcher);
			}
			return matcher;
		}

		public String extractPathWithinPattern(String pattern,
		                                       String path) {
			String[] patternParts = tokenizeToStringArray(pattern,
			                                              pathSeparator,
			                                              this.trimTokens,
			                                              true);
			String[] pathParts = tokenizeToStringArray(path,
			                                           pathSeparator,
			                                           this.trimTokens,
			                                           true);
			StringBuilder builder = new StringBuilder();
			boolean pathStarted = false;

			for (int segment = 0; segment < patternParts.length; segment++) {
				String patternPart = patternParts[segment];
				if (patternPart.indexOf('*') > -1 || patternPart.indexOf('?') > -1) {
					for (; segment < pathParts.length; segment++) {
						if (pathStarted || (segment == 0 && !pattern.startsWith(pathSeparator))) {
							builder.append(pathSeparator);
						}
						builder.append(pathParts[segment]);
						pathStarted = true;
					}
				}
			}

			return builder.toString();
		}

		public Map<String, String> extractUriTemplateVariables(String pattern,
		                                                       String path) {
			Map<String, String> variables = new LinkedHashMap<>();
			boolean result = this.doMatch(pattern,
			                              path,
			                              true,
			                              variables);
			if (!result) {
				throw new IllegalStateException("Pattern \"" + pattern + "\" is not a match for \"" + path + "\"");
			}
			return variables;
		}
	}

	class AntPathStringMatcher {
		private static final Pattern GLOB_PATTERN = Pattern.compile("\\?|\\*|\\{((?:\\{[^/]+?\\}|[^/{}]|\\\\[{}])+?)\\}");
		private static final String DEFAULT_VARIABLE_PATTERN = "((?s).*)";
		private final String rawPattern;
		private final boolean caseSensitive;
		private final boolean exactMatch;
		private final Pattern pattern;
		private final List<String> variableNames = new ArrayList<>();

		public AntPathStringMatcher(String pattern) {
			this(pattern,
			     true);
		}

		public AntPathStringMatcher(String pattern,
		                            boolean caseSensitive) {
			this.rawPattern = pattern;
			this.caseSensitive = caseSensitive;
			StringBuilder patternBuilder = new StringBuilder();
			Matcher matcher = GLOB_PATTERN.matcher(pattern);
			int end = 0;
			while (matcher.find()) {
				patternBuilder.append(this.quote(pattern,
				                                 end,
				                                 matcher.start()));
				String match = matcher.group();
				if ("?".equals(match)) {
					patternBuilder.append('.');
				} else if ("*".equals(match)) {
					patternBuilder.append(".*");
				} else if (match.startsWith("{") && match.endsWith("}")) {
					int colonIdx = match.indexOf(':');
					if (colonIdx == -1) {
						patternBuilder.append(DEFAULT_VARIABLE_PATTERN);
						this.variableNames.add(matcher.group(1));
					} else {
						String variablePattern = match.substring(colonIdx + 1,
						                                         match.length() - 1);
						patternBuilder.append('(');
						patternBuilder.append(variablePattern);
						patternBuilder.append(')');
						String variableName = match.substring(1,
						                                      colonIdx);
						this.variableNames.add(variableName);
					}
				}
				end = matcher.end();
			}
			// No glob pattern was found, this is an exact String match
			if (end == 0) {
				this.exactMatch = true;
				this.pattern = null;
			} else {
				this.exactMatch = false;
				patternBuilder.append(this.quote(pattern,
				                                 end,
				                                 pattern.length()));
				this.pattern = Pattern.compile(patternBuilder.toString(),
				                               Pattern.DOTALL | (this.caseSensitive
				                                                 ? 0
				                                                 : Pattern.CASE_INSENSITIVE));
			}
		}

		private String quote(String s,
		                     int start,
		                     int end) {
			if (start == end) {
				return "";
			}
			return Pattern.quote(s.substring(start,
			                                 end));
		}

		/**
		 * Main entry point.
		 *
		 * @return {@code true} if the string matches against the pattern, or {@code false} otherwise.
		 */
		public boolean matchStrings(String str,
		                            Map<String, String> uriTemplateVariables) {
			if (this.exactMatch) {
				return this.caseSensitive
				       ? this.rawPattern.equals(str)
				       : this.rawPattern.equalsIgnoreCase(str);
			} else if (this.pattern != null) {
				Matcher matcher = this.pattern.matcher(str);
				if (matcher.matches()) {
					if (uriTemplateVariables != null) {
						if (this.variableNames.size() != matcher.groupCount()) {
							throw new IllegalArgumentException("The number of capturing groups in the pattern segment " + this.pattern + " does not match the number of URI template variables it defines, " + "which can occur if capturing groups are used in a URI template regex. " + "Use non-capturing groups instead.");
						}
						for (int i = 1; i <= matcher.groupCount(); i++) {
							String name = this.variableNames.get(i - 1);
							if (name.startsWith("*")) {
								throw new IllegalArgumentException("Capturing patterns (" + name + ") are not " + "supported by the AntPathMatcher. Use the PathPatternParser instead.");
							}
							String value = matcher.group(i);
							uriTemplateVariables.put(name,
							                         value);
						}
					}
					return true;
				}
			}
			return false;
		}

	}

	// ---------------------------------------------------------------------
	// Section StringUtils
	// ---------------------------------------------------------------------
	String[] EMPTY_STRING_ARRAY = {};

	static String[] tokenizeToStringArray(String str,
	                                      String delimiters) {
		return tokenizeToStringArray(str,
		                             delimiters,
		                             true,
		                             true);
	}

	static String[] tokenizeToStringArray(String str,
	                                      String delimiters,
	                                      boolean trimTokens,
	                                      boolean ignoreEmptyTokens) {

		if (str == null) {
			return EMPTY_STRING_ARRAY;
		}

		StringTokenizer st = new StringTokenizer(str,
		                                         delimiters);
		List<String> tokens = new ArrayList<>();
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			if (trimTokens) {
				token = token.trim();
			}
			if (!ignoreEmptyTokens || token.length() > 0) {
				tokens.add(token);
			}
		}
		return toStringArray(tokens);
	}

	static String[] toStringArray(Collection<String> collection) {
		return (CollectionSpec.predicate_empty_collection.test(collection)
		        ? EMPTY_STRING_ARRAY
		        : collection.toArray(EMPTY_STRING_ARRAY));
	}
}
