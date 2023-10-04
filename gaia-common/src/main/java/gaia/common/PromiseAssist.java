package gaia.common;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import gaia.common.ConcurrentAssist.ConditionedLock;
import gaia.common.EventAssist.TypedEvent;
import gaia.common.EventAssist.TypedEventListenerContainer;
import gaia.common.EventAssist.TypedEventListenerContainerBase;
import gaia.common.GWCommon.AttributeContext;
import gaia.common.GWCommon.Disposable;
import gaia.common.GWCommon.Priority;

public interface PromiseAssist {
	Logger log = LoggerFactory.getLogger(PromiseAssist.class);

	Predicate<Promise<?, ?, ?>> predicate_promise_done = Promise::isDone;
	Predicate<Promise<?, ?, ?>> predicate_promise_running = predicate_promise_done.negate();

	interface PromiseEvent<T extends Promise<?, ?, ?>>
			extends TypedEvent<T> {
		boolean isComplete();

		boolean isSuccess();
	}

	@Getter
	@RequiredArgsConstructor
	class PromiseEventEntry<T extends Promise<?, ?, ?>, E extends PromiseEvent<T>>
			implements Disposable {
		final long timestamp = System.currentTimeMillis();
		final E event;
		final AttributeContext extension;

		@Override
		public void dispose() {
			this.extension.dispose();
		}
	}

	interface Promise<PT extends Promise<?, ?, ?>, T extends PromiseEvent<PT>, V>
			extends AttributeContext,
			        TypedEventListenerContainer,
			        Future<V> {
		void sync();

		boolean sync(long timeout,
		             TimeUnit unit);

		void event(T event,
		           AttributeContext context);

		void history(Consumer<PromiseEventEntry<PT, T>> consumer);

		Stream<PromiseEventEntry<PT, T>> history();
	}

	class PromiseCollection
			extends ArrayList<Promise<?, ?, ?>> {
		private static final long serialVersionUID = 3719034292813404853L;
		final ConditionedLock lock = new ConditionedLock();

		public boolean complete() {
			return this.lock.tran(() -> this.stream()
			                                .allMatch(Promise::isDone));
		}
	}

	interface PromiseCancelRunnable
			extends Runnable,
			        Priority {
		String getName();
	}

	abstract class PromiseBase<PT extends Promise<?, ?, ?>, T extends PromiseEvent<PT>, V>
			extends TypedEventListenerContainerBase
			implements Promise<PT, T, V> {
		final AtomicBoolean done = new AtomicBoolean(false);
		final AtomicBoolean finish = new AtomicBoolean(false);
		final AtomicBoolean canceling = new AtomicBoolean(false);
		final AtomicBoolean cancelled = new AtomicBoolean(false);
		final List<PromiseEventEntry<PT, T>> history = new ArrayList<>();
		final AtomicBoolean disposed = new AtomicBoolean(false);
		final List<Supplier<PromiseCancelRunnable>> cancelRunners = new ArrayList<>();
		@Setter
		@Getter
		volatile V value;
		@Setter
		@Getter
		volatile Throwable cause;
		@Getter
		protected volatile T completeEvent;

		protected abstract PT self();

		void checkListener() {
			if (this.cancelled.get()) {
				throw new IllegalStateException("already cancelled promise");
			}

			if (this.done.get()) {
				throw new IllegalStateException("already done promise");
			}
		}

		public void registerPromiseCancelRunnable(Supplier<PromiseCancelRunnable> runnable) {
			this.cancelRunners.add(runnable);
		}

		@Override
		public void registerTrigger(TypedEventListenerContainer container) {
			this.checkListener();

			super.registerTrigger(container);

			if (this.history.isEmpty()) {
				return;
			}
			this.history.forEach(entry -> container.handleEvent(entry.getEvent(),
			                                                    entry.getExtension()));
		}

		@Override
		protected void doRegisterListener(gaia.common.EventAssist.AnonymousEventListener listener) {
			this.checkListener();

			super.doRegisterListener(listener);

			if (this.history.isEmpty()) {
				return;
			}
			this.history.forEach(entry -> listener.handleEvent(entry.getEvent(),
			                                                   entry.getExtension()));
		}


		@Override
		public final void dispose() {
			if (this.disposed.compareAndSet(false,
			                                true)) {
				// custom dispose
				this.doDispose();

				this.history.forEach(PromiseEventEntry::dispose);
			} else if (log.isTraceEnabled()) {
				log.warn(StringAssist.format("promise [%s] is already disposed!",
				                             this),
				         new Throwable("<TRACE>"));
			}
		}

		@Override
		public void history(Consumer<PromiseEventEntry<PT, T>> consumer) {
			if (this.disposed.get()) {
				throw new IllegalStateException("already disposed promise!");
			}

			this.lock()
			    .execute(() -> this.history.forEach(consumer));
		}

		@Override
		public Stream<PromiseEventEntry<PT, T>> history() {
			if (this.disposed.get()) {
				throw new IllegalStateException("already disposed promise!");
			}
			return this.lock()
			           .tran(() -> new ArrayList<>(this.history).stream());
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			if (this.cancelled.get()) {
				return true;
			}
			if (this.canceling.compareAndSet(false,
			                                 true)) {
				// execute cancel runnable
				this.cancelRunners.stream()
				                  .map(Supplier::get)
				                  .forEach(runner -> {
					                  log.info(StringAssist.format("%s execute",
					                                               runner.getName()));

					                  runner.run();

					                  if (log.isTraceEnabled()) {
						                  log.trace(StringAssist.format("%s executed",
						                                                runner.getName()));
					                  }
				                  });
				this.canceling.set(false);
			}

			this.cancelled.set(true);

			return true;
		}

		@Override
		public boolean isCancelled() {
			return this.cancelled.get();
		}

		@Override
		public void sync() {
			while (predicate_promise_running.test(this)) {
				this.lock()
				    .waitFor(1,
				             TimeUnit.SECONDS,
				             () -> predicate_promise_running.test(this));
			}
		}

		@Override
		public boolean sync(long time,
		                    TimeUnit unit) {
			final long limit = System.currentTimeMillis() + unit.toMillis(time);
			while (predicate_promise_running.test(this) && System.currentTimeMillis() < limit) {
				final long gab = limit - System.currentTimeMillis();
				if (gab > 0) {
					this.lock()
					    .waitFor(gab,
					             TimeUnit.MILLISECONDS,
					             () -> predicate_promise_running.test(this));
				}
			}

			return predicate_promise_done.test(this);
		}

		@Override
		public V get() {
			this.sync();
			return this.value;
		}

		@Override
		public V get(long time,
		             TimeUnit unit) {
			return this.sync(time,
			                 unit)
			       ? this.value
			       : null;
		}

		@Override
		public boolean isDone() {
			return this.cancelled.get() || this.done.get();
		}

		@Override
		public void event(final T event,
		                  final AttributeContext context) {
			if (this.cancelled.get()) {
				if (log.isDebugEnabled()) {
					log.debug(StringAssist.format("[%s] is raised after cancelled",
					                              event));
				}
				return;
			}
			if (this.done.get()) {
				throw new gaia.ConstValues.CrashedException("already done promise");
			}

			if (log.isTraceEnabled()) {
				log.trace(StringAssist.format("<<Promise Event>> [%s]",
				                              event));
			}

			final PromiseEventEntry<PT, T> eventEntry = new PromiseEventEntry<>(event,
			                                                                    context);
			this.lock()
			    .execute(() -> this.history.add(eventEntry));


			this.proxy(event,
			           eventEntry);

			this.lock()
			    .notifyFor();
		}

		void proxy(final T event,
		           final PromiseEventEntry<PT, T> eventEntry) {
			if (event.isComplete()) {
				this.done.set(true);
				this.completeEvent = event;
				this.onComplete(eventEntry);
			}

			event.composer(this.self())
			     .with(eventEntry.extension)
			     .handle();

			if (event.isComplete()) {
				if (this.finish.compareAndSet(false,
				                              true)) {
					this.onFinished(eventEntry);
				} else {
					log.warn("finish twice?");
				}
			}
		}

		protected void doDispose() {
			// customize entry point
		}

		protected void onComplete(PromiseEventEntry<PT, T> eventEntry) {

		}

		protected void onFinished(PromiseEventEntry<PT, T> eventEntry) {

		}
	}
}
