package gaia.common;

import java.io.File;
import java.io.Flushable;
import java.lang.reflect.Array;
import java.net.DatagramPacket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.security.auth.Destroyable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gaia.common.GWCommon.Priority;
import gaia.common.GWCommon.PriorityType;

/**
 * @author dragon
 */

public interface ObjectFinalizer {
	Logger log = LoggerFactory.getLogger(ObjectFinalizer.class);

	/**
	 *
	 */
	ThreadLocal<Map<String, ArrayList<Object>>> TEMP = new ThreadLocal<>();

	List<Terminator<?>> terminators = new ArrayList<>();

	static void registerTerminator(Terminator<?> terminator) {
		if (terminators.contains(terminator)) {
			return;
		}

		terminators.add(terminator);
		terminators.sort(PriorityType.ASC);
	}

	abstract class Terminator<T>
			implements Priority {
		final Class<T> type;

		@SuppressWarnings("unchecked")
		protected Terminator() {
			super();
			this.type = (Class<T>) ReflectAssist.getGenericParameter(this.getClass(),
			                                                         Terminator.class,
			                                                         "T");
		}

		final boolean terminate(Object target) {
			return this.type.isInstance(target) && this.doTerminate(this.type.cast(target));
		}

		protected abstract boolean doTerminate(T target);
	}

	/**
	 *
	 */
	static void clearTempObject() {
		try {
			final Map<String, ArrayList<Object>> map = TEMP.get();

			if (map == null) {
				return;
			}

			for (final Map.Entry<String, ArrayList<Object>> entry : map.entrySet()) {
				final ArrayList<?> stack = entry.getValue();

				for (final Object obj : stack) {
					closeObject(obj);
				}
			}
		}
		finally {
			TEMP.remove();
		}
	}

	/**
	 *
	 */
	static void registerTempObject(final String key,
	                               final Object value) {
		if (value == null) {
			return;
		}

		Map<String, ArrayList<Object>> map = TEMP.get();

		if (map == null) {
			map = new HashMap<>();
			TEMP.set(map);
		}

		map.computeIfAbsent(key,
		                    candidate -> new ArrayList<>())
		   .add(value);
	}

	/**
	 *
	 */
	static void closeTempObject(final String key) {
		final Map<String, ArrayList<Object>> map = TEMP.get();

		if (map == null) {
			return;
		}

		final ArrayList<Object> stack = map.remove(key);

		if (map.size() == 0) {
			// clear all element is removed
			TEMP.remove();
		}

		if (stack == null) {
			return;
		}

		for (final Object obj : stack) {
			closeObject(obj);
		}
	}

	/**
	 *
	 */
	static void destroy(final Object obj) {
		if (obj == null) {
			return;
		}

		try {
			if (obj instanceof Destroyable) {
				((Destroyable) obj).destroy();
			}
		}
		catch (Exception thw) {
			if (log.isTraceEnabled()) {
				log.trace(StringAssist.format("Destroyable close error [%s]",
				                              obj.getClass()),
				          thw);
			}
		}
	}

	/**
	 *
	 */
	static void closeObject(final Object obj) {
		if (obj == null) {
			return;
		}

		if (obj instanceof AutoCloseable) {
			close((AutoCloseable) obj);
			return;
		}

		if (obj instanceof javax.naming.Context) {
			close((javax.naming.Context) obj);
			return;
		}

		if (obj.getClass()
		       .isArray()) {
			final int length = Array.getLength(obj);
			for (int i = 0; i < length; i++) {
				closeObject(Array.get(obj,
				                      i));
			}
			return;
		}

		if (obj instanceof Collection) {
			for (final Object elm : (Collection<?>) obj) {
				closeObject(elm);
			}
			return;
		}

		for (Terminator<?> terminator : terminators) {
			if (terminator.terminate(obj)) {
				return;
			}
		}
	}

	static void close(final Process process) {
		if (process == null) {
			return;
		}
		try {
			process.destroy();
		}
		catch (Exception thw) {
			// ignore all error
		}
	}

	static void close(final Process process,
	                  final Consumer<Throwable> consumer) {
		if (process == null) {
			return;
		}
		try {
			process.destroy();
		}
		catch (Exception thw) {
			consumer.accept(thw);
		}
	}

	/**
	 *
	 */
	static void close(final javax.naming.Context obj) {
		if (obj == null) {
			return;
		}
		try {
			obj.close();
		}
		catch (Exception thw) {
			// ignore
		}
	}

	static void close(final AutoCloseable obj) {
		close(obj,
		      thw -> {
			      if (log.isTraceEnabled()) {
				      log.trace(StringAssist.format("closeable close error [%s]",
				                                    obj.getClass()),
				                thw);
			      }
		      });
	}

	/**
	 *
	 */
	static void close(final AutoCloseable obj,
	                  Consumer<Throwable> error) {
		if (obj == null) {
			return;
		}

		if (obj instanceof Flushable) {
			try {
				((Flushable) obj).flush();
			}
			catch (Exception thw) {
				//
			}
		}

		try {
			obj.close();
		}
		catch (Exception thw) {
			error.accept(thw);
		}
	}

	/**
	 *
	 */
	static void close(final File obj) {
		if (obj == null) {
			return;
		}

		if (obj.isDirectory()) {
			Optional.ofNullable(obj.listFiles())
			        .ifPresent(files -> Stream.of(files)
			                                  .forEach(ObjectFinalizer::close));
		}
		try {
			if (obj.exists() && !obj.delete()) {
				if (log.isTraceEnabled()) log.trace(StringAssist.format("[%s] delete fail!",
				                                                        obj.getAbsolutePath()),
				                                    new Throwable("trace"));
				obj.deleteOnExit();
			}
		}
		catch (Exception thw) {
			if (log.isTraceEnabled()) log.trace(StringAssist.format("[%s] delete fail!",
			                                                        obj.getAbsolutePath()),
			                                    thw);
		}
	}

	/**
	 *
	 */
	static void close(java.sql.ResultSet obj) {
		if (obj == null) {
			return;
		}
		try {
			obj.close();
		}
		catch (Exception e) {
			// ignore
		}
	}

	/**
	 *
	 */
	static void close(java.sql.Statement obj) {
		if (obj == null) {
			return;
		}
		try {
			obj.close();
		}
		catch (Exception e) {
			// ignore
		}
	}

	/**
	 *
	 */
	static void close(java.sql.Connection conn) {
		if (conn == null) {
			return;
		}
		try {
			conn.close();
		}
		catch (Exception e) {
			//
		}
	}

	/**
	 *
	 */
	static void close(java.sql.Clob obj) {
		if (obj == null) {
			return;
		}
		try {
			obj.free();
		}
		catch (Exception e) {
			// ignore
		}
	}

	/**
	 *
	 */
	static void close(java.sql.Blob obj) {
		if (obj == null) {
			return;
		}
		try {
			obj.free();
		}
		catch (Exception e) {
			// ignore
		}
	}

	/**
	 *
	 */
	static void close(DatagramPacket send) {
		//
	}

	/**
	 *
	 */
	static void close(StringBuilder buffer) {
		//
	}

	/**
	 *
	 */
	static void cleanObject(Object object) {
		if (object instanceof AutoCloseable) {
			ObjectFinalizer.close((AutoCloseable) object);
		}
		if (object instanceof Map) {
			clean((Map<?, ?>) object);
		}
	}

	/**
	 *
	 */
	static void clean(Map<?, ?> map) {
		List<?> keys = new ArrayList<>(map.keySet());
		for (Object key : keys) {
			Object value = map.remove(key);
			ObjectFinalizer.closeObject(value);
		}
	}
}
