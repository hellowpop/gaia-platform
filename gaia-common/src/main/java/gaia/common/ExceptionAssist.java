package gaia.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author sklee
 */
public interface ExceptionAssist {
	Logger log = LoggerFactory.getLogger(ExceptionAssist.class);

	class ExceptionSafeHolder
			extends
			RuntimeException {
		final Throwable target;

		ExceptionSafeHolder(Throwable throwable) {
			this(throwable.getMessage(),
			     throwable);
		}

		ExceptionSafeHolder(String message,
		                    Throwable throwable) {
			super(message);
			this.target = throwable;

			if (throwable instanceof NullPointerException) {
				log.warn("NPE raised!",
				         throwable);
			}
		}

		public Throwable getTargetException() {
			return this.target;
		}

		/**
		 * Returns the cause of this exception (the thrown target exception,
		 * which may be {@code null}).
		 *
		 * @return the cause of this exception.
		 * @since 1.4
		 */
		@Override
		public synchronized Throwable getCause() {
			return this.target;
		}
	}

	static RuntimeException safe(Throwable throwable) {
		if (throwable instanceof RuntimeException) {
			return (RuntimeException) throwable;
		}
		return new ExceptionSafeHolder(extract(throwable));
	}

	static void rethrow(Throwable throwable) {
		throw safe(throwable);
	}

	/**
	 *
	 */
	static Throwable extract(Throwable thw) {
		if (thw == null) {
			return new Error("extract hierarchy contain null!");
		}

		if (thw instanceof InvocationTargetException) {
			return extract(((InvocationTargetException) thw).getTargetException());
		}

		if (thw instanceof ExceptionSafeHolder) {
			return extract(((ExceptionSafeHolder) thw).getTargetException());
		}

		if (thw instanceof UndeclaredThrowableException) {
			return extract(((UndeclaredThrowableException) thw).getUndeclaredThrowable());
		}

		return thw;
	}

	static void handle(String message,
	                   Throwable thw) {
		if (thw instanceof RuntimeException) {
			throw (RuntimeException) thw;
		}
		if (thw instanceof Error) {
			throw (Error) thw;
		}

		throw new RuntimeException(message,
		                           thw);
	}

	static void safeThrow(Throwable thw) {
		throw wrap(thw);
	}

	static void safeThrow(String message,
	                      Throwable thw) {
		throw wrap(message,
		           thw);
	}

	/**
	 *
	 */
	static RuntimeException wrap(Throwable thw) {
		return wrap("abnormal exception",
		            thw);
	}

	/**
	 *
	 */
	static RuntimeException wrap(String message,
	                             Throwable thw) {
		Throwable filtered = extract(thw);

		if (filtered instanceof Error) {
			throw (Error) filtered;
		}

		if (filtered instanceof RuntimeException) {
			return (RuntimeException) filtered;
		}

		return new ExceptionSafeHolder(message,
		                               filtered);
	}

	static Throwable getRootCause(Throwable thw) {
		final Throwable filter = extract(thw);

		final Throwable cause = filter.getCause();

		if (cause == null || cause == filter) {
			return filter;
		}

		return getRootCause(cause);
	}

	/**
	 *
	 */
	static String getStackMessage(Throwable thw) {
		if (thw == null) {
			return "[NULL STACK]";
		}

		final Throwable filter = extract(thw);

		if (filter instanceof NullPointerException) {
			return getStackTrace(filter);
		}

		final String message = filter.getMessage();

		return StringUtils.isEmpty(message)
		       ? filter.getClass()
		               .getSimpleName()
		       : message;
	}

	/**
	 *
	 */
	static String getStackTrace(Throwable thw) {
		if (thw == null) {
			return "[NULL STACK]";
		}
		StringWriter sw = null;
		PrintWriter pw = null;

		try {
			sw = new StringWriter();
			pw = new PrintWriter(sw);
			thw.printStackTrace(pw);
			return sw.toString();
		}
		finally {
			ObjectFinalizer.close(pw);
			ObjectFinalizer.close(sw);
		}
	}

	/**
	 *
	 */
	static void ignore(Throwable thw) {
		ignore("ignore - throwable",
		       thw);
	}

	/**
	 *
	 */
	static void ignore(String string,
	                   Throwable thw) {
		if (log.isTraceEnabled()) {
			log.trace(string,
			          thw);
		}
	}

	static void warning(final Logger log,
	                    final String message,
	                    final Throwable stack) {
		if (log.isDebugEnabled()) {
			log.warn(message,
			         stack);
		} else {
			log.warn(StringAssist.format("%s [%s]",
			                             message,
			                             stack.getMessage()));
		}
	}
}
