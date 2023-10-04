package gaia.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gaia.ConstValues.CrashedException;
import gaia.common.FunctionAssist.ErrorHandleConsumer;
import gaia.common.FunctionAssist.ErrorHandleFunction;
import gaia.common.FunctionAssist.ErrorHandleRunnable;
import gaia.common.GWCommon.EntryDuplicatedException;
import gaia.common.GWCommon.EntryNotFoundException;
import gaia.common.GWEnvironment;
import gaia.common.StringAssist.NamePattern;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

public interface ConcurrentAssist {
	Logger log = LoggerFactory.getLogger(ConcurrentAssist.class);
	Map<String, RejectPolicy> rejectPolicyAliases = FunctionAssist.supply(() -> {
		Map<String, RejectPolicy> map = new HashMap<>();

		for (RejectPolicy value : RejectPolicy.values()) {
			map.put(value.name(),
			        value);
			map.put(value.name()
			             .toLowerCase(),
			        value);
			map.put(value.name()
			             .toUpperCase(),
			        value);
			map.put(NamePattern.snake.encode(value.name()),
			        value);
			map.put(NamePattern.kebab.encode(value.name()),
			        value);
		}

		return map;
	});
	String sharedRootName = "lock-shared-root";

	static DedicateExecutorBuilder dedicateBuilder(String name,
	                                               int size) {
		return new DedicateExecutorBuilder(name,
		                                   size);
	}

	static ConcurrentThreadPoolProperties poolProperties(String name,
	                                                     Consumer<ConcurrentThreadPoolProperties> customizer) {
		final ConcurrentThreadPoolProperties properties = new ConcurrentThreadPoolProperties();

		customizer.accept(properties);

		properties.setName(name);

		return properties.validate();
	}

	static Optional<RejectPolicy> determineRejectPolicy(String name) {
		return Optional.ofNullable(rejectPolicyAliases.get(name));
	}

	static Optional<RejectedExecutionHandler> determineRejectedExecutionHandler(String name) {
		return Optional.ofNullable(rejectPolicyAliases.get(name));
	}

	enum RejectPolicy
			implements RejectedExecutionHandler {
		DiscardOldest {
			@Override
			void doRejected(Runnable r,
			                ThreadPoolExecutor executor) {
				if (!executor.isShutdown()) {
					executor.getQueue()
					        .poll();
					executor.execute(r);
				}
			}
		},
		Discard {
			@Override
			void doRejected(Runnable r,
			                ThreadPoolExecutor executor) {
				// do nothing
			}
		},
		Abort {
			@Override
			void doRejected(Runnable r,
			                ThreadPoolExecutor executor) {
				throw new RejectedExecutionException("Task " + r.toString() + " rejected from " + executor.toString());
			}
		},
		CallerRuns {
			@Override
			void doRejected(Runnable r,
			                ThreadPoolExecutor executor) {
				if (!executor.isShutdown()) {
					r.run();
				}
			}
		};

		abstract void doRejected(Runnable r,
		                         ThreadPoolExecutor executor);

		@Override
		public void rejectedExecution(Runnable r,
		                              ThreadPoolExecutor executor) {
			this.doRejected(r,
			                executor);
		}
	}

	// ---------------------------------------------------------------------
	// Section Distributed Lock
	// ---------------------------------------------------------------------
	interface NamedLockService {
		/**
		 * @param node
		 */
		Lock getLock(String node);

		/**
		 * @param node
		 */
		void releaseLock(String node);
	}

	final class SuppliedReference<T>
			extends AtomicReference<T> {
		private static final long serialVersionUID = -2543079105504279350L;

		final Supplier<T> supplier;

		public SuppliedReference(Supplier<T> supplier) {
			super();
			this.supplier = supplier;
		}

		public T supplied() {
			return this.updateAndGet(before -> null == before
			                                   ? this.supplier.get()
			                                   : before);
		}

		public void execute(ErrorHandleConsumer<T> consumer) {
			final T supplied = this.supplied();

			FunctionAssist.consume(supplied,
			                       consumer);
		}

		public <V> V function(ErrorHandleFunction<T, V> function) {
			return this.function(function,
			                     FunctionAssist::rethrowFunction);
		}

		public <V> V function(ErrorHandleFunction<T, V> function,
		                      Function<Throwable, V> error) {
			final T supplied = this.supplied();

			return FunctionAssist.function(supplied,
			                               function,
			                               error);
		}
	}

	class UnaryReference<T>
			extends AtomicReference<T> {
		final UnaryOperator<T> unary;

		public UnaryReference(UnaryOperator<T> unary) {
			super();
			this.unary = unary;
		}

		public T updateAndGet() {
			return this.updateAndGet(this.unary);
		}
	}

	final class ConditionedLock
			extends ReentrantLock {
		private static final long serialVersionUID = 317992488210190230L;

		@Getter
		final transient Condition condition;

		public ConditionedLock() {
			this(false);
		}

		public ConditionedLock(boolean fair) {
			super(fair);
			this.condition = this.newCondition();
		}

		public void mustInTransaction() {
			if (this.isLocked() && this.isHeldByCurrentThread()) {
				// check OK
				return;
			}

			throw new IllegalStateException("must execute in transaction mode!");
		}

		public void waitInterval(long time,
		                         TimeUnit unit) {
			this.waitFor(time,
			             unit,
			             () -> true);
		}

		public void waitOneTime(long time) {
			this.waitOneTime(time,
			                 TimeUnit.MILLISECONDS);
		}

		public void waitOneTime(long time,
		                        TimeUnit timeUnit) {
			final AtomicBoolean flag = new AtomicBoolean(true);
			this.waitFor(time,
			             timeUnit,
			             () -> flag.compareAndSet(true,
			                                      false));
		}

		public void waitFor(Supplier<Boolean> callable) {
			while (callable.get()) {
				this.waitFor(1,
				             TimeUnit.SECONDS,
				             callable);
			}
		}

		public void waitFor(long time,
		                    Supplier<Boolean> callable) {
			this.waitFor(time,
			             TimeUnit.MILLISECONDS,
			             callable);
		}

		public void waitFor(long time,
		                    TimeUnit unit,
		                    Supplier<Boolean> callable) {
			final long start = System.currentTimeMillis();
			final long finish = start + unit.toMillis(time);

			boolean cond = callable.get();

			while (cond) {
				this.lock();
				try {
					final long gab = finish - System.currentTimeMillis();
					if (gab < 1L) {
						return;
					}

					final boolean result = this.condition.await(gab,
					                                            TimeUnit.MILLISECONDS);

					if (result && finish - System.currentTimeMillis() < 10L) {
						return;
					}
				}
				catch (InterruptedException e) {
					if (log.isTraceEnabled()) {
						log.trace("wait interrupted");
					}
					Thread.currentThread()
					      .interrupt();
					return;
				}
				finally {
					this.unlock();
				}
				cond = callable.get();
			}
		}

		/**
		 * if exception raised, throw handled exception or return callable value
		 *
		 * @param <T>
		 * @param callable
		 * @return transact callable value
		 */
		public <T> T tran(Callable<T> callable) {
			return this.tran(callable,
			                 thw -> {
				                 ExceptionAssist.handle("raise tran error",
				                                        thw);
				                 return null;
			                 });
		}

		/**
		 * @param callable execute target
		 * @param error    error handler
		 * @param <T>      return type
		 * @return transact callable value
		 */
		public <T> T tran(Callable<T> callable,
		                  Function<Throwable, T> error) {
			do {
				try {
					if (this.tryLock(1,
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
							this.condition.signalAll();
							this.unlock();
						}
					} else if (log.isTraceEnabled()) {
						log.info("try lock fail");
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

		/**
		 * @param runnable target runnable
		 * @return option of fail reason
		 */
		public void execute(ErrorHandleRunnable runnable) {
			this.execute(runnable,
			             thw -> ExceptionAssist.safeThrow("abnormal error in transaction",
			                                              thw));
		}

		/**
		 * use execute
		 *
		 * @param runnable target runnable
		 * @param error    error process consumer
		 * @return option of fail reason
		 */
		public void execute(ErrorHandleRunnable runnable,
		                    Consumer<Throwable> error) {
			this.execute0(runnable)
			    .ifPresent(error);
		}

		private Optional<Throwable> execute0(ErrorHandleRunnable runnable) {
			do {
				try {
					if (this.tryLock(1,
					                 TimeUnit.SECONDS)) {
						try {
							runnable.run();

							this.condition.signalAll();
						}
						catch (Throwable thw) {
							return Optional.of(thw);
						}
						finally {
							this.unlock();
						}

						return Optional.empty();
					} else if (log.isTraceEnabled()) {
						log.info("try lock fail");
					}
				}
				catch (Exception exception) {
					return Optional.of(exception);
				}
			} while (true);
		}

		/**
		 * @param time
		 * @param unit
		 */
		public void wait(long time,
		                 TimeUnit unit) {
			this.lock();
			try {
				this.condition.await(time,
				                     unit);
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			finally {
				this.unlock();
			}
		}

		/**
		 *
		 */
		public void notifyFor() {
			this.lock();
			try {
				this.condition.signalAll();
			}
			finally {
				this.unlock();
			}
		}
	}

	// ---------------------------------------------------------------------
	// Section future
	// ---------------------------------------------------------------------

	interface ConditionedFutureListener<T> {
		void operationComplete(ConditionedFuture<T> future);

		default void onOperationError(ConditionedFuture<T> future,
		                              Throwable thw) {
			log.error(StringAssist.format("operation error [%s]",
			                              future),
			          thw);
		}
	}

	class ConditionedFutures<T> {
		@Getter
		final ConditionedLock lock = new ConditionedLock();
		final List<ConditionedFuture<T>> futures = new ArrayList<>();
		final AtomicReference<List<ConditionedFuture<T>>> futureHolder = new AtomicReference<>(Collections.emptyList());
		final AtomicBoolean dirtyFlag = new AtomicBoolean();

		public final ConditionedFuture<T> register(final ConditionedFuture<T> future) {
			this.lock.execute(() -> {
				this.futures.add(future.addListener(this::unregister));
				this.dirtyFlag.set(true);
			});
			return future;
		}

		void unregister(final ConditionedFuture<T> future) {
			this.lock.execute(() -> {
				if (this.futures.remove(future)) {
					this.dirtyFlag.set(true);
				} else {
					log.warn(StringAssist.format("[%s] is not registered future",
					                             future));
				}
			});
		}

		List<ConditionedFuture<T>> futureList() {
			return this.lock.tran(() -> {
				if (this.dirtyFlag.compareAndSet(true,
				                                 false)) {
					this.futureHolder.set(new ArrayList<>(this.futures));
				}
				return this.futureHolder.get();
			});
		}

		public final void complete(T value) {
			// prevent futures change during process future complete
			this.futureList()
			    .stream()
			    .filter(Objects::nonNull)
			    .forEach(future -> future.complete(value));
		}

		public void clear() {
			this.futureList()
			    .stream()
			    .filter(Objects::nonNull)
			    .forEach(future -> future.cancel(true));

			this.lock.execute(() -> {
				if (this.futures.size() > 0) {
					throw new Error("after clear remain future...");
				}
			});
		}
	}

	class ConditionedFuture<T>
			implements Future<T> {
		final ConditionedLock lock = new ConditionedLock();

		final List<ConditionedFutureListener<T>> listeners = new ArrayList<>();

		final Function<T, Boolean> function;

		final AtomicReference<T> value = new AtomicReference<>();

		final AtomicBoolean canceled = new AtomicBoolean();

		public ConditionedFuture(Function<T, Boolean> function) {
			this.function = function;
		}

		public void complete(T value) {
			this.lock.execute(() -> {
				// check canceled and function
				if (!this.canceled.get() && this.function.apply(value)) {
					this.value.set(value);

					this.completeNotify();
				}
			});
		}

		public boolean isComplete() {
			return this.lock.tran(this::isComplete0);
		}

		public boolean isNotComplete() {
			return this.lock.tran(this::isNotComplete0);
		}

		public void sync() {
			while (!this.isComplete()) {
				this.lock.waitFor(1L,
				                  TimeUnit.SECONDS,
				                  this::isNotComplete);
			}
		}

		public ConditionedFuture<T> addListener(final ConditionedFutureListener<T> listener) {
			this.lock.execute(() -> {
				if (this.listeners.contains(listener)) {
					throw new IllegalArgumentException(StringAssist.format("[%s] is already registered",
					                                                       listener));
				}

				if (this.isComplete0()) {
					listener.operationComplete(this);
				} else {
					this.listeners.add(listener);
				}
			});
			return this;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return this.lock.tran(() -> {
				// check done
				if (this.value.get() != null) {
					return false;
				}

				if (this.canceled.compareAndSet(false,
				                                true)) {
					this.completeNotify();
					return true;
				}

				return false;
			});
		}

		@Override
		public boolean isCancelled() {
			return this.lock.tran(this::canceled);
		}

		@Override
		public boolean isDone() {
			return this.lock.tran(this::hasValue);
		}

		@Override
		public T get() {
			this.sync();

			return Optional.ofNullable(this.value0())
			               .orElseThrow(() -> new Error("sync after value / cause both null?"));
		}

		@Override
		public T get(long timeout,
		             TimeUnit unit) {
			this.lock.waitFor(timeout,
			                  unit,
			                  this::isNotComplete0);

			return this.lock.tran(() -> {
				if (this.isCancelled()) {
					throw new IllegalStateException("current future is canceled");
				}

				return Optional.ofNullable(this.value0())
				               .orElseThrow(() -> new IllegalStateException("timed out"));
			});
		}

		public Optional<T> reference() {
			return Optional.ofNullable(this.lock.tran(this::value0));
		}

		void completeNotify() {
			this.listeners.forEach(listener -> {
				try {
					listener.operationComplete(this);
				}
				catch (Exception thw) {
					listener.onOperationError(this,
					                          thw);
				}
			});

			// after notify clear listeners
			this.listeners.clear();
		}

		T value0() {
			return this.value.get();
		}

		boolean canceled() {
			return this.canceled.get();
		}

		boolean hasValue() {
			return null != this.value.get();
		}

		boolean isComplete0() {
			return this.canceled.get() || null != this.value.get();
		}

		boolean isNotComplete0() {
			return !this.isComplete0();
		}
	}

	// ---------------------------------------------------------------------
	// Section semaphore
	// ---------------------------------------------------------------------

	class FlexibleSemaphore {
		private final Sync sync;

		/**
		 * Creates a {@code Semaphore} with the given number of
		 * permits and nonfair fairness setting.
		 *
		 * @param permits the initial number of permits available.
		 *                This value may be negative, in which case releases
		 *                must occur before any acquires will be granted.
		 */
		public FlexibleSemaphore(int permits) {
			this(permits,
			     false);
		}

		/**
		 * Creates a {@code Semaphore} with the given number of
		 * permits and the given fairness setting.
		 *
		 * @param permits the initial number of permits available.
		 *                This value may be negative, in which case releases
		 *                must occur before any acquires will be granted.
		 * @param fair    {@code true} if this semaphore will guarantee
		 *                first-in first-out granting of permits under contention,
		 *                else {@code false}
		 */
		public FlexibleSemaphore(int permits,
		                         boolean fair) {
			this.sync = fair
			            ? new FairSync(permits)
			            : new NoneFairSync(permits);
		}

		public final void changePermits(int permits) {
			this.sync.setPermits(permits);
		}

		public void acquire() throws InterruptedException {
			this.sync.acquireSharedInterruptibly(1);
		}

		public void acquire(int permits) throws InterruptedException {
			if (permits < 0) {
				throw new IllegalArgumentException();
			}
			this.sync.acquireSharedInterruptibly(permits);
		}

		public void acquireWithoutInterrupt() {
			this.sync.acquireShared(1);
		}

		public void acquireWithoutInterrupt(int permits) {
			if (permits < 0) {
				throw new IllegalArgumentException();
			}
			this.sync.acquireShared(permits);
		}

		public boolean tryAcquire() {
			return this.sync.nonfairTryAcquireShared(1) >= 0;
		}

		public boolean tryAcquire(int permits) {
			if (permits < 0) {
				throw new IllegalArgumentException();
			}
			return this.sync.nonfairTryAcquireShared(permits) >= 0;
		}

		public boolean tryAcquire(long timeout,
		                          TimeUnit unit) throws InterruptedException {
			return this.sync.tryAcquireSharedNanos(1,
			                                       unit.toNanos(timeout));
		}

		public boolean tryAcquire(int permits,
		                          long timeout,
		                          TimeUnit unit) throws InterruptedException {
			if (permits < 0) {
				throw new IllegalArgumentException();
			}
			return this.sync.tryAcquireSharedNanos(permits,
			                                       unit.toNanos(timeout));
		}

		public void release() {
			this.sync.releaseShared(1);
		}

		public void release(int permits) {
			if (permits < 0) {
				throw new IllegalArgumentException();
			}
			this.sync.releaseShared(permits);
		}

		public int availablePermits() {
			return this.sync.getPermits();
		}

		public int drainPermits() {
			return this.sync.drainPermits();
		}

		protected void reducePermits(int reduction) {
			if (reduction < 0) {
				throw new IllegalArgumentException();
			}
			this.sync.reducePermits(reduction);
		}

		public boolean isFair() {
			return this.sync.isFair();
		}

		public final boolean hasQueuedThreads() {
			return this.sync.hasQueuedThreads();
		}

		public final int getQueueLength() {
			return this.sync.getQueueLength();
		}

		protected Collection<Thread> getQueuedThreads() {
			return this.sync.getQueuedThreads();
		}
	}

	class Sync
			extends AbstractQueuedSynchronizer {
		private static final long serialVersionUID = 1192457210091910933L;

		@Getter
		private final boolean fair;

		Sync(boolean fair,
		     int permits) {
			this.fair = fair;
			this.setState(permits);
		}

		/**
		 * @param permits
		 */
		final void setPermits(int permits) {
			this.setState(permits);
		}

		/**
		 * @return
		 */
		final int getPermits() {
			return this.getState();
		}

		final int nonfairTryAcquireShared(int acquires) {
			for (; ; ) {
				int available = this.getState();
				int remaining = available - acquires;
				if (remaining < 0 || this.compareAndSetState(available,
				                                             remaining)) {
					return remaining;
				}
			}
		}

		@Override
		protected final boolean tryReleaseShared(int releases) {
			for (; ; ) {
				int current = this.getState();
				int next = current + releases;
				if (next < current) {
					throw new Error("Maximum permit count exceeded");
				}
				if (this.compareAndSetState(current,
				                            next)) {
					return true;
				}
			}
		}

		final void reducePermits(int reductions) {
			for (; ; ) {
				int current = this.getState();
				int next = current - reductions;
				if (next > current) {
					throw new Error("Permit count underflow");
				}
				if (this.compareAndSetState(current,
				                            next)) {
					return;
				}
			}
		}

		final int drainPermits() {
			for (; ; ) {
				int current = this.getState();
				if (current == 0 || this.compareAndSetState(current,
				                                            0)) {
					return current;
				}
			}
		}
	}

	class NoneFairSync
			extends Sync {
		private static final long serialVersionUID = 7751027049293298737L;

		NoneFairSync(int permits) {
			super(false,
			      permits);
		}

		@Override
		protected int tryAcquireShared(int acquires) {
			return this.nonfairTryAcquireShared(acquires);
		}
	}

	/**
	 * Fair version
	 */
	class FairSync
			extends Sync {
		private static final long serialVersionUID = -3239805148558956777L;

		FairSync(int permits) {
			super(true,
			      permits);
		}

		@Override
		protected int tryAcquireShared(int acquires) {
			for (; ; ) {
				if (this.hasQueuedPredecessors()) {
					return -1;
				}
				int available = this.getState();
				int remaining = available - acquires;
				if (remaining < 0 || this.compareAndSetState(available,
				                                             remaining)) {
					return remaining;
				}
			}
		}
	}

	class DedicateExecutorBuilder {
		final String name;
		final int corePoolSize;

		DedicateExecutorBuilder(String name,
		                        int size) {
			this.name = name;
			this.corePoolSize = size;
		}

		long keepAliveTime = 10L;
		TimeUnit unit = TimeUnit.MILLISECONDS;

		public DedicateExecutorBuilder keepAliveTime(long value,
		                                             TimeUnit unit) {
			this.keepAliveTime = value;
			this.unit = unit;
			return this;
		}

		ThreadFactory threadFactory;

		public DedicateExecutorBuilder threadFactory(ThreadFactory value) {
			this.threadFactory = value;
			return this;
		}

		RejectedExecutionHandler handler;

		public DedicateExecutorBuilder handler(RejectedExecutionHandler value) {
			this.handler = value;
			return this;
		}

		public DedicateExecutor build() {
			return new DedicateExecutor(this.corePoolSize,
			                            this.keepAliveTime,
			                            this.unit,
			                            this.threadFactory == null
			                            ? new ConcurrentThreadFactory(this.name)
			                            : this.threadFactory,
			                            this.handler == null
			                            ? RejectPolicy.Abort
			                            : this.handler);
		}
	}

	class DedicateExecutor
			extends ThreadPoolExecutor {
		final ConditionedLock lock = new ConditionedLock();

		DedicateExecutor(int corePoolSize,
		                 long keepAliveTime,
		                 TimeUnit unit,
		                 ThreadFactory threadFactory,
		                 RejectedExecutionHandler handler) {
			super(corePoolSize,
			      corePoolSize,
			      keepAliveTime,
			      unit,
			      new ArrayBlockingQueue<>(1),
			      threadFactory,
			      handler);
		}

		public void changeCapacity(int size) {
			if (this.getCorePoolSize() > size) {
				// case reduce
				this.setCorePoolSize(size);
				this.setMaximumPoolSize(size);
			} else {
				// case increase
				this.setMaximumPoolSize(size);
				this.setCorePoolSize(size);
			}
		}

		@Override
		protected void beforeExecute(Thread t,
		                             Runnable r) {
			super.beforeExecute(t,
			                    r);
			this.lock.notifyFor();
		}

		@Override
		protected void afterExecute(Runnable r,
		                            Throwable t) {
			super.afterExecute(r,
			                   t);
			this.lock.notifyFor();
		}

		@Override
		public void execute(Runnable command) {
			while (this.full()) {
				this.lock.waitFor(100L,
				                  TimeUnit.MILLISECONDS,
				                  this::full);
			}
			super.execute(command);
		}

		boolean full() {
			return this.getQueue()
			           .size() > 0;
		}
	}

	class ConcurrentThreadFactory
			implements ThreadFactory {
		private final ThreadGroup group;

		private final AtomicInteger threadNumber = new AtomicInteger(1);

		private final String namePrefix;

		public ConcurrentThreadFactory(String name) {
			this.group = Thread.currentThread()
			                   .getThreadGroup();
			this.namePrefix = name.concat("-");
		}

		@Override
		public Thread newThread(Runnable r) {
			Thread t = new ConcurrentThread(this.group,
			                                r,
			                                this.namePrefix + this.threadNumber.getAndIncrement());
			if (t.isDaemon()) t.setDaemon(false);
			if (t.getPriority() != Thread.NORM_PRIORITY) t.setPriority(Thread.NORM_PRIORITY);
			return t;
		}
	}

	final class ConcurrentThread
			extends Thread {
		ConcurrentThread(ThreadGroup group,
		                 Runnable target,
		                 String name) {
			super(group,
			      target,
			      name,
			      0);
		}
	}

	@Data
	class ConcurrentThreadPoolProperties {
		String name;

		Integer coreSize;

		Integer maxSize;

		Long keepAliveSeconds;

		ThreadFactory threadFactory;

		RejectedExecutionHandler rejectPolicy;

		BlockingQueue<Runnable> queue;

		public final ConcurrentThreadPoolProperties validate() {
			// set default core size
			if (this.coreSize == null) this.coreSize = GWEnvironment.getInteger("work.offer.core.pool.size",
			                                                                    10);

			// set default max size
			if (this.maxSize == null) this.maxSize = GWEnvironment.getInteger("work.offer.max.pool.size",
			                                                                  10);
			// set default keep alive seconds
			if (this.keepAliveSeconds == null) this.keepAliveSeconds = GWEnvironment.getLong("work.offer.keep.alive.seconds",
			                                                                                 10L);
			// set default queue
			if (this.queue == null) this.queue = new LinkedBlockingQueue<>();
			// set default thread factory
			if (this.threadFactory == null) this.threadFactory = new ConcurrentThreadFactory(this.name);

			// set default reject policy
			if (this.rejectPolicy == null) this.rejectPolicy = RejectPolicy.Abort;

			return this;
		}
	}

	abstract class ResourceHolder<T extends AutoCloseable> {
		final ConditionedLock lock = new ConditionedLock();

		final LinkedList<T> holder = new LinkedList<>();

		public final <RES> RES function(ErrorHandleFunction<T, RES> function,
		                                Function<Throwable, RES> error) {
			T resource = null;
			try {
				resource = this.popup();
				return FunctionAssist.function(resource,
				                               function,
				                               error);
			}
			finally {
				this.push(resource);
			}
		}

		private T popup() {
			return this.lock.tran(() -> this.holder.isEmpty()
			                            ? this.create()
			                            : this.onPopup(this.holder.removeFirst()));
		}

		private void push(T instance) {
			this.lock.execute(() -> {
				if (this.onPush(this.holder.size(),
				                instance)) {
					this.holder.addLast(instance);
				} else {
					ObjectFinalizer.close(instance);
				}
			});

		}

		protected abstract T create();

		protected T onPopup(T instance) {
			return instance;
		}

		@SuppressWarnings({"unused",
		                   "SameReturnValue"})
		protected boolean onPush(int size,
		                         T instance) {
			// customize entry point
			return true;
		}
	}

	abstract class DaemonThread
			implements Runnable {
		@Getter
		protected final ConditionedLock lock;
		final AtomicReference<Thread> threadReference = new AtomicReference<>();
		final AtomicBoolean alive = new AtomicBoolean(false);
		@Setter
		@Getter
		String name;
		@Setter
		int priority = Thread.NORM_PRIORITY;
		@Setter
		boolean daemon = true;
		@Setter
		long errorSleep = 1000L;
		@Setter
		@Getter
		TimeUnit timeUnit = TimeUnit.MILLISECONDS;

		protected DaemonThread() {
			this(new ConditionedLock());
		}

		protected DaemonThread(final ConditionedLock lock) {
			super();
			this.lock = lock;
			this.name = this.getClass()
			                .getSimpleName();
		}

		public boolean isActive() {
			return this.alive.get();
		}

		public final void executeImmediately() {
			this.checkAndStart();
			this.lock.notifyFor();
		}

		@Override
		public final void run() {
			final AtomicLong wait = new AtomicLong();
			try {
				log.info(StringAssist.format("[%s] execute start",
				                             this.getName()));
				while (this.alive.get()) {
					try {
						wait.set(this.execute());
					}
					catch (Throwable e) {
						// default 1 second wait
						wait.set(this.errorSleep);

						final String message = StringAssist.format("[%s] execute error [%s]",
						                                           this.name,
						                                           e.getMessage());
						if (log.isDebugEnabled()) {
							log.error(message,
							          e);
						} else {
							log.error(message);
						}
					}

					if (this.alive.get() && wait.get() > 0) {
						this.lock.waitOneTime(wait.get(),
						                      this.timeUnit);
					}

					if (wait.get() < 0) {
						log.info(StringAssist.format("[%s] execute result is negative so finish",
						                             this.getName()));
						this.alive.set(false);
					}
				}
			}
			finally {
				log.info(StringAssist.format("[%s] execute finished",
				                             this.getName()));
				this.threadReference.set(null);
				this.lock.notifyFor();
			}
		}

		/**
		 * execute main
		 *
		 * @return sleep time in millisecond
		 * @throws Throwable
		 */
		protected abstract long execute() throws Throwable;

		public final void checkAndStart() {
			if (this.isActive()) {
				return;
			}

			this.startDaemon();
		}

		public final void startDaemon() {
			this.lock.execute(() -> {
				final Thread currentWorking = this.threadReference.get();
				if (null != currentWorking) {
					throw new CrashedException(StringAssist.format("already thread is running [%s]",
					                                               currentWorking.getName()));
				}

				this.alive.set(true);

				final Thread thread = new Thread(this,
				                                 this.name.concat("-daemon"));
				thread.setDaemon(this.daemon);
				thread.setPriority(this.priority);

				this.threadReference.set(thread);

				thread.start();

				log.info(StringAssist.format("[%s] start complete [%s(%s)] (%s)",
				                             this.name,
				                             thread.getName(),
				                             thread.isAlive(),
				                             this.threadReference.get()));
			});
		}

		public final void stopDaemon() {
			this.stopDaemon(TimeUnit.SECONDS.toMillis(10L));
		}

		final boolean hasThreadReference() {
			return this.threadReference.get() != null;
		}

		public final void stopDaemon(long time) {
			final Thread workingThread = this.lock.tran(() -> {
				final Thread thread = this.threadReference.get();
				this.alive.set(false);
				return thread;
			});

			if (workingThread == null) {
				log.info(StringAssist.format("[%s] closed by external condition",
				                             this.name));
				return;
			}

			final long closeWait = time < 0
			                       ? Long.MAX_VALUE
			                       : System.currentTimeMillis() + time;

			// wait for working thread
			this.lock.waitFor(100,
			                  TimeUnit.MILLISECONDS,
			                  this::hasThreadReference);

			Thread thread = this.threadReference.get();

			if (null != thread) {
				do {
					log.warn(StringAssist.format("[%s] close interrupt => [%s]",
					                             this.name,
					                             thread.getName()));
					this.interruptCurrent();

					this.lock.waitFor(100,
					                  TimeUnit.MILLISECONDS,
					                  this::hasThreadReference);

					thread = this.threadReference.get();
				} while (null != thread && System.currentTimeMillis() < closeWait);

				log.warn(StringAssist.format("[%s] close wait loop end (%s)",
				                             this.name,
				                             this.threadReference.get()));
			} else {
				log.info(StringAssist.format("[%s] close directly (%s)",
				                             this.name,
				                             this.threadReference.get()));
			}

			try {
				workingThread.join(1000L);
			}
			catch (InterruptedException ioe) {
				//
			}
			log.info(StringAssist.format("[%s] close complete [%s(%s)] (%s)",
			                             this.name,
			                             workingThread.getName(),
			                             workingThread.isAlive(),
			                             this.threadReference.get()));
		}

		protected abstract void interruptCurrent();
	}

	abstract class TTLCounterManager<T extends TTLCounterEntry<?>>
			extends DaemonThread {
		final Map<String, T> entries = new HashMap<>();

		@Override
		protected long execute() throws Throwable {
			// for async, use entry copy, check and synchronous mode ( loop in lock mode )
			final ArrayList<T> candidate = this.lock.tran(() -> new ArrayList<>(this.entries.values()));

			candidate.forEach(TTLCounterEntry::manageLocal);

			return 500L - System.currentTimeMillis() % 500L;
		}

		@Override
		protected void interruptCurrent() {

		}

		protected T createOrReplace(final String identity,
		                            final Supplier<T> supplier,
		                            final Consumer<T> updater) {
			return this.lock.tran(() -> {
				final T exist = this.entries.get(identity);

				if (null == exist) {
					final T create = supplier.get();

					if (null != this.entries.put(identity,
					                             create)) {
						throw new CrashedException("none exist duplicated?");
					}

					create.bindManager(this);

					return create;
				}

				updater.accept(exist);

				return exist;
			});
		}

		public void register(final T entry) {
			this.lock.execute(() -> {

				if (null != this.entries.put(entry.getIdentity(),
				                             entry)) {
					throw new EntryDuplicatedException(entry.toString(),
					                                   "ttl context");
				}
				entry.bindManager(this);
			});
		}

		public void deregister(final T entry) {
			this.lock.execute(() -> {
				if (entry != this.entries.remove(entry.getIdentity())) {
					throw new EntryNotFoundException(entry.toString(),
					                                 "ttl context");
				}
				entry.unbindManager(this);
			});
		}
	}

	abstract class TTLCounterEntry<T> {
		@Getter
		protected final ConditionedLock lock = new ConditionedLock();
		protected final AtomicLong globalCounter = new AtomicLong();
		protected final AtomicLong localCounter = new AtomicLong();
		protected long localStamp;
		protected long globalStamp;
		@Getter
		final String identity;
		long ttl;
		final Function<T, Long> mapper;
		final List<InnerEntry<T>> entries = new ArrayList<>();
		TTLCounterManager<?> manager;

		protected TTLCounterEntry(final long ttl,
		                          final Function<T, Long> mapper) {
			this(RandomAssist.getUUID(),
			     ttl,
			     mapper);
		}

		protected TTLCounterEntry(final String identity,
		                          final long ttl,
		                          final Function<T, Long> mapper) {
			this.identity = identity;
			this.ttl = ttl;
			this.mapper = mapper;
		}

		protected void updateTtl(final long ttl) {
			this.ttl = ttl;
		}

		void bindManager(final TTLCounterManager<?> manager) {
			this.manager = manager;
		}

		void unbindManager(final TTLCounterManager<?> manager) {
			if (this.manager != manager) {
				throw new Error("manager different?");
			}
			this.manager = null;
		}

		private void manageLocal() {
			final long time = System.currentTimeMillis();
			final AtomicLong counter = this.localCounter;
			counter.set(0);
			this.lock.execute(() -> {
				final List<InnerEntry<T>> backup = new ArrayList<>(this.entries);
				this.entries.clear();
				backup.stream()
				      .filter(candidate -> {
					      if (candidate.isValid(time)) {
						      counter.getAndAdd(this.mapper.apply(candidate.entry));
						      return true;
					      }

					      return false;
				      })
				      .forEach(this.entries::add);

				// notify current counter to implements
				this.globalCounter.set(this.updateCurrentCounter(counter.get()));
			});

			final long localCounter = counter.get();
			final long currentCounter = this.globalCounter.get();

			if (log.isTraceEnabled() && (this.localStamp != localCounter || this.globalStamp != currentCounter)) {
				log.trace(StringAssist.format("<TTL> %s local [%s -> %s] global [%s -> %s]",
				                              this.identity,
				                              this.localStamp,
				                              localCounter,
				                              this.globalStamp,
				                              currentCounter));
			}

			this.localStamp = localCounter;
			this.globalStamp = currentCounter;
		}

		public void onNext(T next) {
			// add to current counter
			this.globalCounter.getAndAdd(this.mapper.apply(next));

			this.lock.execute(() -> this.entries.add(new InnerEntry<>(this.ttl,
			                                                          next)));
		}

		public long current() {
			return this.globalCounter.get();
		}

		protected abstract long updateCurrentCounter(long current);

		static class InnerEntry<T> {
			final long timestamp;
			final T entry;

			InnerEntry(final long delta,
			           final T entry) {
				this.timestamp = System.currentTimeMillis() + delta;
				this.entry = entry;
			}

			boolean isValid(long base) {
				return this.timestamp > base;
			}
		}
	}

	abstract class CounterManager<T extends CounterEntry<?>>
			extends DaemonThread {
		final Map<String, T> entries = new HashMap<>();

		@Override
		protected long execute() throws Throwable {
			// for async, use entry copy, check and synchronous mode ( loop in lock mode )
			final ArrayList<T> candidate = this.lock.tran(() -> new ArrayList<>(this.entries.values()));

			candidate.forEach(CounterEntry::manageLocal);

			return 999L - System.currentTimeMillis() % 1000L;
		}

		@Override
		protected void interruptCurrent() {

		}

		protected T createOrReplace(final String identity,
		                            final Supplier<T> supplier,
		                            final Consumer<T> updater) {
			return this.lock.tran(() -> {
				final T exist = this.entries.get(identity);

				if (null == exist) {
					final T create = supplier.get();

					if (null != this.entries.put(identity,
					                             create)) {
						throw new CrashedException("none exist duplicated?");
					}

					create.bindManager(this);

					return create;
				}

				updater.accept(exist);

				return exist;
			});
		}

		public void register(final T entry) {
			this.lock.execute(() -> {

				if (null != this.entries.put(entry.getIdentity(),
				                             entry)) {
					throw new EntryDuplicatedException(entry.toString(),
					                                   "ttl context");
				}
				entry.bindManager(this);
			});
		}

		public void deregister(final T entry) {
			this.lock.execute(() -> {
				if (entry != this.entries.remove(entry.getIdentity())) {
					throw new EntryNotFoundException(entry.toString(),
					                                 "ttl context");
				}
				entry.unbindManager(this);
			});
		}
	}

	abstract class CounterEntry<T> {
		@Getter
		protected final ConditionedLock lock = new ConditionedLock();
		protected final AtomicLong globalCounter = new AtomicLong();
		protected final AtomicLong localCounter = new AtomicLong();
		protected long localStamp;
		protected long globalStamp;
		@Getter
		final String identity;
		final Function<T, Long> mapper;
		CounterManager<?> manager;

		protected CounterEntry(final Function<T, Long> mapper) {
			this(RandomAssist.getUUID(),
			     mapper);
		}

		protected CounterEntry(final String identity,
		                       final Function<T, Long> mapper) {
			this.identity = identity;
			this.mapper = mapper;
		}

		void bindManager(final CounterManager<?> manager) {
			this.manager = manager;
		}

		void unbindManager(final CounterManager<?> manager) {
			if (this.manager != manager) {
				throw new Error("manager different?");
			}
			this.manager = null;
		}

		private void manageLocal() {
			this.lock.execute(() -> {
				// notify current counter to implements
				this.globalCounter.set(this.updateCurrentCounter(this.localCounter.get()));

				if (log.isTraceEnabled() && (this.localStamp != this.localCounter.get() || this.globalStamp != this.globalCounter.get())) {
					log.trace(StringAssist.format("<COUNTER> %s local [%s -> %s] global [%s -> %s]",
					                              this.identity,
					                              this.localStamp,
					                              this.localCounter.get(),
					                              this.globalStamp,
					                              this.globalCounter.get()));
				}

				this.localStamp = this.localCounter.get();
				this.globalStamp = this.globalCounter.get();
			});
		}

		public void onNext(T next) {
			// add to current counter
			this.lock.execute(() -> this.localCounter.getAndAdd(this.mapper.apply(next)));
		}

		public long current() {
			return this.globalCounter.get();
		}

		protected abstract long updateCurrentCounter(long current);
	}
}
