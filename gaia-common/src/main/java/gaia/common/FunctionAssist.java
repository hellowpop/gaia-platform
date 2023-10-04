package gaia.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gaia.common.ExceptionAssist.ExceptionSafeHolder;


/**
 * @see java.util.Map
 */
public interface FunctionAssist {
	Logger log = LoggerFactory.getLogger(FunctionAssist.class);

	static <T> void doOptional(T source,
	                           Consumer<T> consumer,
	                           Runnable runnable) {
		if (null == source) {
			runnable.run();
		} else {
			consumer.accept(source);
		}
	}

	interface ErrorHandleSupplier<T> {
		T get() throws Throwable;
	}

	interface ErrorHandleRunnable {
		void run() throws Throwable;
	}

	static <T> ConsumerBuilder<T> consumerBuilder(Consumer<T> init) {
		return new ConsumerBuilder<>(init);
	}

	class ConsumerBuilder<T> {
		Consumer<T> consumer;

		ConsumerBuilder(Consumer<T> init) {
			this.consumer = init;
		}

		public ConsumerBuilder<T> before(Consumer<T> add) {
			if (null != add) {
				this.consumer = null == this.consumer
				                ? add
				                : add.andThen(this.consumer);
			}
			return this;
		}

		public ConsumerBuilder<T> after(Consumer<T> add) {
			if (null != add) {
				this.consumer = null == this.consumer
				                ? add
				                : this.consumer.andThen(add);
			}
			return this;
		}

		public Consumer<T> build() {
			return this.consumer;
		}
	}

	Runnable VOID_RUNNABLE = () -> {
	};

	Supplier<Object> VOID_SUPPLIER = () -> Void.class;

	Consumer<Object> VOID_CONSUMER = object -> {
	};

	static RunnableBuilder runnableBuilder(Runnable init) {
		return new RunnableBuilder(init);
	}

	class RunnableBuilder {
		Runnable runnable;

		RunnableBuilder(Runnable init) {
			this.runnable = init;
		}

		public RunnableBuilder before(Runnable add) {
			if (null != add) {
				this.runnable = null == this.runnable
				                ? add
				                : join(add,
				                       this.runnable);
			}
			return this;
		}

		public RunnableBuilder after(Runnable add) {
			if (null != add) {
				this.runnable = null == this.runnable
				                ? add
				                : join(this.runnable,
				                       add);
			}
			return this;
		}

		public Runnable build() {
			return this.runnable;
		}
	}

	static Runnable join(Runnable head,
	                     Runnable tail) {
		return () -> {
			head.run();
			tail.run();
		};
	}


	// ---------------------------------------------------------------------
	// Section default handler
	// ---------------------------------------------------------------------
	static void rethrow(Throwable throwable) {
		Throwable filtered = ExceptionAssist.extract(throwable);

		if (filtered instanceof RuntimeException) {
			throw (RuntimeException) filtered;
		}
		if (filtered instanceof Error) {
			throw (Error) filtered;
		}

		throw new ExceptionSafeHolder(filtered);
	}

	static void echoError(Throwable throwable) {
		log.error("just error echo",
		          throwable);
	}

	Consumer<Throwable> THROWABLE_CONSUMER = FunctionAssist::rethrow;

	Consumer<Throwable> BYPASS = throwable -> {
		if (log.isTraceEnabled()) {
			log.trace(StringAssist.format("execute bypass Error [%s]",
			                              throwable.getMessage()));
		}
	};

	@SuppressWarnings("SameReturnValue")
	static <T> T rethrowFunction(Throwable throwable) {
		rethrow(throwable);
		return null;
	}

	// ---------------------------------------------------------------------
	// Section iterate
	// ---------------------------------------------------------------------
	static void iterate(int from,
	                    int to,
	                    IntConsumer consumer) {
		IntStream.range(from,
		                to)
		         .forEach(consumer);
	}

	// ---------------------------------------------------------------------
	// Section supply
	// ---------------------------------------------------------------------

	static <T> T supplySafe(ErrorHandleSupplier<T> supplier) {
		try {
			return supplier.get();
		}
		catch (Throwable e) {
			// ignore all
		}
		return null;
	}

	static <T> T supply(ErrorHandleSupplier<T> supplier) {
		return supply(supplier,
		              FunctionAssist::rethrowFunction,
		              null);
	}

	static <T> T supply(ErrorHandleSupplier<T> supplier,
	                    Function<Throwable, T> error) {
		return supply(supplier,
		              error,
		              null);
	}

	static <T> T supply(ErrorHandleSupplier<T> supplier,
	                    Runnable finalizer) {
		return supply(supplier,
		              FunctionAssist::rethrowFunction,
		              finalizer);
	}

	static <T> T supply(ErrorHandleSupplier<T> supplier,
	                    Function<Throwable, T> error,
	                    Runnable finalizer) {
		try {
			return null == supplier
			       ? null
			       : supplier.get();
		}
		catch (Throwable throwable) {
			return error.apply(throwable);
		}
		finally {
			if (null != finalizer) {
				finalizer.run();
			}
		}
	}

	// ---------------------------------------------------------------------
	// Section function
	// ---------------------------------------------------------------------
	interface ErrorHandleFunction<T, R> {
		R apply(T t) throws Exception;
	}

	static <T, R> R functionSafe(T param,
	                             ErrorHandleFunction<T, R> function) {

		return function(param,
		                function,
		                error -> null,
		                null);
	}

	static <T, R> R function(T param,
	                         ErrorHandleFunction<T, R> function) {
		return function(param,
		                function,
		                FunctionAssist::rethrowFunction,
		                null);
	}

	static <T, R> R function(T param,
	                         ErrorHandleFunction<T, R> function,
	                         Function<Throwable, R> error) {
		return function(param,
		                function,
		                error,
		                null);
	}

	static <T, R> R function(T param,
	                         ErrorHandleFunction<T, R> function,
	                         Function<Throwable, R> error,
	                         Runnable finalizer) {
		try {
			return function.apply(param);
		}
		catch (Exception throwable) {
			return null == error
			       ? null
			       : error.apply(throwable);
		}
		finally {
			if (null != finalizer) {
				finalizer.run();
			}
		}
	}

	// ---------------------------------------------------------------------
	// Section execute
	// ---------------------------------------------------------------------
	static void safeExecute(ErrorHandleRunnable runnable) {
		execute(runnable,
		        BYPASS,
		        null,
		        null);
	}

	static void execute(ErrorHandleRunnable runnable) {
		execute(runnable,
		        THROWABLE_CONSUMER,
		        null,
		        null);
	}

	static void execute(ErrorHandleRunnable runnable,
	                    Consumer<Throwable> consumer) {
		execute(runnable,
		        consumer,
		        null,
		        null);
	}

	static void execute(ErrorHandleRunnable runnable,
	                    Runnable finalizer) {
		execute(runnable,
		        THROWABLE_CONSUMER,
		        null,
		        finalizer);
	}

	static void execute(ErrorHandleRunnable runnable,
	                    Runnable initializer,
	                    Runnable finalizer) {
		execute(runnable,
		        THROWABLE_CONSUMER,
		        initializer,
		        finalizer);
	}

	static void execute(ErrorHandleRunnable runnable,
	                    Consumer<Throwable> consumer,
	                    Runnable finalizer) {
		execute(runnable,
		        consumer,
		        null,
		        finalizer);
	}

	static void execute(ErrorHandleRunnable runnable,
	                    Consumer<Throwable> consumer,
	                    Runnable initializer,
	                    Runnable finalizer) {
		try {
			if (null != initializer) {
				initializer.run();
			}
			runnable.run();
		}
		catch (Throwable e) {
			consumer.accept(e);
		}
		finally {
			if (null != finalizer) {
				finalizer.run();
			}
		}
	}

	static void execute(ErrorHandleRunnable runnable,
	                    Supplier<Runnable> initializer) {
		execute(runnable,
		        THROWABLE_CONSUMER,
		        initializer);

	}

	static void execute(ErrorHandleRunnable runnable,
	                    Consumer<Throwable> consumer,
	                    Supplier<Runnable> initializer) {
		Runnable finalizer = null;
		try {
			finalizer = initializer.get();

			runnable.run();
		}
		catch (Throwable e) {
			consumer.accept(e);
		}
		finally {
			if (null != finalizer) {
				finalizer.run();
			}
		}
	}

	// ---------------------------------------------------------------------
	// Section consumer
	// ---------------------------------------------------------------------
	ThreadLocal<ErrorStack> error_warning = new ThreadLocal<>();

	class ErrorStack
			extends RuntimeException {
		List<Throwable> history;

		ErrorStack(Throwable cause) {
			super(cause);
		}

		void add(Throwable throwable) {
			if (this.history == null) {
				this.history = new ArrayList<>();
			}
			this.history.add(throwable);
		}
	}

	interface ErrorWarningConsumer<V> {
		void accept(V value) throws Throwable;

		default void handle(V value) {
			try {
				this.accept(value);
			}
			catch (Throwable e) {
				ErrorStack errorStack = error_warning.get();
				if (null == errorStack) {
					error_warning.set(new ErrorStack(e));
				} else {
					errorStack.add(e);
				}
			}
		}

		default ErrorWarningConsumer<V> andThen(ErrorWarningConsumer<? super V> after) {
			Objects.requireNonNull(after);
			return (V t) -> {
				this.accept(t);
				after.accept(t);
			};
		}

		static <V> Optional<ErrorStack> handleErrorWarningConsumer(ErrorWarningConsumer<V> consumer,
		                                                           V value) {
			error_warning.remove();

			consumer.handle(value);

			try {
				return Optional.ofNullable(error_warning.get());
			}
			finally {
				error_warning.remove();
			}
		}

		static <V> ErrorWarningConsumer<V> of(Consumer<V> consumer) {
			return consumer::accept;
		}
	}

	interface ErrorHandleConsumer<V> {
		void accept(V value) throws Throwable;
	}

	static <V> void consumeSafe(V instance,
	                            ErrorHandleConsumer<V> consumer) {
		consume(instance,
		        consumer,
		        BYPASS,
		        null);
	}

	static <V> void consume(V instance,
	                        ErrorHandleConsumer<V> consumer) {
		consume(instance,
		        consumer,
		        FunctionAssist::rethrow,
		        null);
	}

	static <V> void consume(V instance,
	                        ErrorHandleConsumer<V> consumer,
	                        Consumer<Throwable> error) {
		consume(instance,
		        consumer,
		        error,
		        null);
	}

	static <V> void consume(V instance,
	                        ErrorHandleConsumer<V> consumer,
	                        Runnable finalizer) {
		consume(instance,
		        consumer,
		        FunctionAssist::rethrow,
		        finalizer);
	}

	static <V> void consume(V instance,
	                        ErrorHandleConsumer<V> consumer,
	                        Consumer<Throwable> error,
	                        Runnable finalizer) {
		// skip when target instance is null
		if (instance == null) return;

		try {
			consumer.accept(instance);
		}
		catch (Throwable e) {
			if (null != error) {
				error.accept(e);
			}
		}
		finally {
			if (null != finalizer) {
				finalizer.run();
			}
		}
	}

	interface ErrorHandleBiConsumer<T, U> {
		void accept(T t,
		            U u);
	}

	static <T, U> void consumeBiSafe(T t,
	                                 U u,
	                                 ErrorHandleBiConsumer<T, U> consumer) {
		consumeBi(t,
		          u,
		          consumer,
		          null,
		          null);
	}

	static <T, U> void consumeBi(T t,
	                             U u,
	                             ErrorHandleBiConsumer<T, U> consumer) {
		consumeBi(t,
		          u,
		          consumer,
		          FunctionAssist::rethrow,
		          null);
	}

	static <T, U> void consumeBi(T t,
	                             U u,
	                             ErrorHandleBiConsumer<T, U> consumer,
	                             Consumer<Throwable> error) {
		consumeBi(t,
		          u,
		          consumer,
		          error,
		          null);
	}

	static <T, U> void consumeBi(T t,
	                             U u,
	                             ErrorHandleBiConsumer<T, U> consumer,
	                             Runnable finalizer) {
		consumeBi(t,
		          u,
		          consumer,
		          FunctionAssist::rethrow,
		          finalizer);
	}

	static <T, U> void consumeBi(T t,
	                             U u,
	                             ErrorHandleBiConsumer<T, U> consumer,
	                             Consumer<Throwable> error,
	                             Runnable finalizer) {
		try {
			consumer.accept(t,
			                u);
		}
		catch (Throwable e) {
			if (null != error) {
				error.accept(e);
			}
		}
		finally {
			if (null != finalizer) {
				finalizer.run();
			}
		}
	}

	// ---------------------------------------------------------------------
	// Section with lock mode
	// ---------------------------------------------------------------------

	static Optional<Throwable> transaction(Lock lock,
	                                       ErrorHandleRunnable runnable) {
		return transaction0(lock,
		                    runnable,
		                    null);
	}

	static void transaction(Lock lock,
	                        ErrorHandleRunnable runnable,
	                        Consumer<Throwable> error) {
		transaction0(lock,
		             runnable,
		             null).ifPresent(error);
	}

	static Optional<Throwable> transaction0(final Lock lock,
	                                        final ErrorHandleRunnable runnable,
	                                        final Runnable after) {
		do {
			try {
				if (lock.tryLock(1,
				                 TimeUnit.SECONDS)) {
					try {
						runnable.run();

						if (null != after) {
							after.run();
						}
					}
					catch (Throwable thw) {
						return Optional.of(thw);
					}
					finally {
						lock.unlock();
					}

					return Optional.empty();
				}
			}
			catch (Exception exception) {
				return Optional.of(exception);
			}
		} while (true);
	}

	static <T> T callable(final Lock lock,
	                      final Callable<T> callable) {
		return callable0(lock,
		                 callable,
		                 null,
		                 error -> {
			                 throw new RuntimeException("callable transaction error",
			                                            error);
		                 });
	}

	static <T> T callable(final Lock lock,
	                      final Callable<T> callable,
	                      final Function<Throwable, T> error) {
		return callable0(lock,
		                 callable,
		                 null,
		                 error);
	}

	static <T> T callable0(final Lock lock,
	                       final Callable<T> callable,
	                       final Runnable complete,
	                       final Function<Throwable, T> error) {
		do {
			try {
				if (lock.tryLock(1,
				                 TimeUnit.SECONDS)) {
					try {
						return callable.call();
					}
					catch (InterruptedException interrupted) {
						throw new RuntimeException("current transaction thread is interrupted",
						                           interrupted);
					}
					catch (Exception thw) {
						return error.apply(thw);
					}
					finally {
						try {
							if (null != complete) {
								complete.run();
							}
						}
						finally {
							lock.unlock();
						}
					}
				}
			}
			catch (InterruptedException interrupted) {
				if (log.isTraceEnabled()) {
					log.trace("interrupted thread return null",
					          interrupted);
				}
				return null;
			}
		} while (true);
	}

	// ---------------------------------------------------------------------
	// Section execute null check
	// ---------------------------------------------------------------------
	static <T> void executeNullable(final T value,
	                                Consumer<T> notNull,
	                                ErrorHandleRunnable nullExecutor) {
		if (null == value) {
			execute(nullExecutor);
		} else {
			notNull.accept(value);
		}
	}

	@SafeVarargs
	static <T> Optional<T> optionals(Supplier<T>... suppliers) {
		for (Supplier<T> supplier : suppliers) {
			T value = supplier.get();

			if (null != value) {
				return Optional.of(value);
			}
		}

		return Optional.empty();
	}
}
