package gaia.common;

import java.util.function.BiPredicate;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;

public interface ArrayAssist {
	BiPredicate<String, String> case_sensitive = StringUtils::equals;
	BiPredicate<String, String> case_insensitive = StringUtils::equalsIgnoreCase;

	static int findStringIndex(final String[] source,
	                           final String target,
	                           final boolean flagCase) {
		if (source == null || source.length == 0) {
			return -1;
		}

		final BiPredicate<String, String> predicate = flagCase
		                                              ? case_sensitive
		                                              : case_insensitive;

		for (int idx = source.length - 1; idx >= 0; idx--) {
			if (source[idx] == null) {
				continue;
			}

			if (predicate.test(source[idx],
			                   target)) {
				return idx;
			}
		}

		return -1;
	}

	static int findArrayIndex(final char[] source,
	                          final char target) {
		return findArrayIndex(source,
		                      target,
		                      0,
		                      source.length);
	}

	static int findArrayIndex(final char[] source,
	                          final char target,
	                          final int from,
	                          final int to) {
		final int fixed = Math.min(to,
		                           source.length);
		for (int idx = from; idx < fixed; idx++) {
			if (source[idx] == target) {
				return idx;
			}
		}

		return -1;
	}

	static int findArrayIndex(final Object[] source,
	                          final Object target) {
		return findArrayIndex(source,
		                      target,
		                      0,
		                      source.length);
	}

	static int findArrayIndex(final Object[] source,
	                          final Object target,
	                          final int start,
	                          final int length) {
		for (int idx = length - 1; idx >= start; idx--) {
			if (source[idx] == null) {
				continue;
			}
			if (source[idx].equals(target)) {
				return idx;
			}
		}

		return -1;
	}

	static boolean compare(byte[] source,
	                       byte[] target) {
		return compare(source,
		               target,
		               source == null
		               ? 0
		               : source.length);

	}

	static boolean compare(final byte[] source,
	                       final byte[] target,
	                       final int length) {
		if (source == null && target == null) {
			return true;
		}

		if (source == null || target == null) {
			return false;
		}

		if (length > target.length) {
			return false;
		}

		for (int i = 0; i < length; i++) {
			if (source[i] != target[i]) {
				return false;
			}
		}

		return true;
	}

	static void compare(final byte[] source,
	                    final byte[] target,
	                    final int length,
	                    final IntConsumer listener) {
		if (source == null && target == null) {
			return;
		}

		if (source == null || target == null) {
			return;
		}

		if (length > target.length) {
			listener.accept(-1);
			return;
		}

		for (int i = 0; i < length; i++) {
			if (source[i] != target[i]) {
				listener.accept(i);
				return;
			}
		}
	}

	static byte[] replace(byte[] source,
	                      byte oldByte,
	                      byte newByte) {
		if (source == null) {
			return source;
		}
		return replace(source,
		               0,
		               source.length,
		               oldByte,
		               newByte);
	}

	static byte[] replace(byte[] source,
	                      int offset,
	                      int length,
	                      byte oldByte,
	                      byte newByte) {
		if (source == null) {
			return source;
		}

		final int end = offset + length;

		for (int i = offset; i < end; i++) {
			if (source[i] == oldByte) {
				source[i] = newByte;
			}
		}

		return source;
	}

	static int compare(int[] a,
	                   int[] b) {
		if (a == b) {
			return 0;
		}
		if (a == null || b == null) {
			return a == null
			       ? -1
			       : 1;
		}
		if (a.length != b.length) {
			return -1;
		}

		return IntStream.range(0,
		                       a.length)
		                .filter(i -> a[i] != b[i])
		                .findAny()
		                .isPresent()
		       ? -1
		       : 0;
	}

	static int compare(float[] a,
	                   float[] b) {
		if (a == b) {
			return 0;
		}
		if (a == null || b == null) {
			return a == null
			       ? -1
			       : 1;
		}
		if (a.length != b.length) {
			return -1;
		}

		return IntStream.range(0,
		                       a.length)
		                .filter(i -> Float.compare(a[i],
		                                           b[i]) != 0)
		                .findAny()
		                .isPresent()
		       ? -1
		       : 0;
	}
}
