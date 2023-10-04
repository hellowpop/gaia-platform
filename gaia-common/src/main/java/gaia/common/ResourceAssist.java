package gaia.common;

import java.io.File;
import java.io.OutputStream;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import lombok.Getter;

import gaia.ConstValues;
import gaia.common.CharsetSpec.ByteArrayOutputStreamExt;
import gaia.common.ConcurrentAssist.ConditionedLock;
import gaia.common.EventAssist.TypedEvent;
import gaia.common.EventAssist.TypedEventListener;
import gaia.common.EventAssist.TypedEventListenerContainerBase;
import gaia.common.FunctionAssist.ErrorHandleConsumer;
import gaia.common.GWCommon.AttributeAccessorContainer;
import gaia.common.GWCommon.AttributeContextAccessor;
import gaia.common.GWCommon.ResourceExhaustException;

/**
 * @author dragon
 * @since 2022. 04. 12.
 */
public interface ResourceAssist {
	Logger log = LoggerFactory.getLogger(ResourceAssist.class);

	AttributeContextAccessor<StringComposer> STRING_BUILDER = AttributeContextAccessor.of(StringComposer.class,
	                                                                                      StringComposer::new);

	Consumer<StringBuilder> VOID_APPENDER = builder -> {
	};

	static StringComposer stringComposer() {
		return STRING_BUILDER.get()
		                     .open();
	}

	class StringComposer {
		final AtomicReference<StringBuilder> holder;
		final InnerWriter writer = new InnerWriter();
		StringBuilder builder;

		public StringComposer() {
			this.holder = new AtomicReference<>(new StringBuilder());
		}

		public StringComposer open() {
			this.builder = this.holder.get();
			return this;
		}

		public StringComposer newline() {
			this.builder.append(ConstValues.NEW_LINE_CHARS);
			return this;
		}

		public StringComposer line(String string) {
			this.builder.append(string);
			this.builder.append(ConstValues.NEW_LINE_CHARS);
			return this;
		}

		public StringComposer tab(int count) {
			IntStream.range(0,
			                count)
			         .forEach(i -> this.builder.append(ConstValues.CHAR_TAB));
			return this;
		}

		public StringComposer self(Consumer<StringComposer> composer) {
			composer.accept(this);
			return this;
		}

		public StringComposer add(String string) {
			this.builder.append(string);
			return this;
		}

		@SuppressWarnings("UnusedReturnValue")
		public StringComposer appendWith(String string,
		                                 String addon) {
			if (this.builder.length() > 0) {
				this.builder.append(addon);
			}
			this.builder.append(string);
			return this;
		}

		public StringComposer add(char string) {
			this.builder.append(string);
			return this;
		}

		public StringComposer add(char... string) {
			this.builder.append(string);
			return this;
		}

		public StringComposer add(Object object) {
			this.builder.append(object);
			return this;
		}

		public StringComposer add(Consumer<StringBuilder> consumer) {
			consumer.accept(this.builder);
			return this;
		}

		public StringComposer writer(ErrorHandleConsumer<Writer> consumer,
		                             Consumer<Throwable> errorHandle) {
			FunctionAssist.consume(this.writer,
			                       consumer,
			                       errorHandle);
			return this;
		}

		public StringComposer length(int amount) {
			this.builder.setLength(Math.max(this.builder.length() - amount,
			                                0));
			return this;
		}

		public String build() {
			try {
				return this.builder.toString();
			}
			finally {
				Optional.ofNullable(this.builder)
				        .ifPresent(buffer -> {
					        if (buffer.length() > 8096) {
						        // reset too large content
						        this.holder.set(new StringBuilder());
					        } else {
						        buffer.setLength(0);
					        }
				        });
			}
		}

		class InnerWriter
				extends Writer {
			@Override
			public void write(int c) {
				StringComposer.this.builder.append((char) c);
			}

			@Override
			public void write(char[] buffer) {
				StringComposer.this.builder.append(buffer);
			}

			@Override
			public void write(String str) {
				StringComposer.this.builder.append(str);
			}

			@Override
			public void write(String str,
			                  int off,
			                  int len) {
				StringComposer.this.builder.append(str,
				                                   off,
				                                   len);
			}

			@Override
			public Writer append(CharSequence csq) {
				StringComposer.this.builder.append(csq);
				return this;
			}

			@Override
			public Writer append(CharSequence csq,
			                     int start,
			                     int end) {
				StringComposer.this.builder.append(csq,
				                                   start,
				                                   end);
				return this;
			}

			@Override
			public Writer append(char c) {
				StringComposer.this.builder.append(c);
				return this;
			}

			@Override
			public void write(char[] buffer,
			                  int off,
			                  int len) {
				StringComposer.this.builder.append(buffer,
				                                   off,
				                                   len);
			}

			@Override
			public void flush() {

			}

			@Override
			public void close() {

			}
		}
	}

	AttributeContextAccessor<StreamComposer> STREAM_BUILDER = AttributeContextAccessor.of(StreamComposer.class,
	                                                                                      StreamComposer::new);

	static StreamComposer streamComposer() {
		return STREAM_BUILDER.get()
		                     .open();
	}

	class StreamComposer {
		final AtomicReference<ByteArrayOutputStreamExt> holder;
		ByteArrayOutputStreamExt builder;

		StreamComposer() {
			this.holder = new AtomicReference<>(new ByteArrayOutputStreamExt());
		}

		StreamComposer open() {
			this.builder = this.holder.get();
			this.builder.reset();
			return this;
		}

		public StreamComposer stream(ErrorHandleConsumer<ByteArrayOutputStreamExt> consumer) {
			return this.stream(consumer,
			                   FunctionAssist::rethrow);
		}

		public StreamComposer stream(ErrorHandleConsumer<ByteArrayOutputStreamExt> consumer,
		                             Consumer<Throwable> errorHandle) {
			FunctionAssist.consume(this.builder,
			                       consumer,
			                       errorHandle);
			return this;
		}

		public byte[] build() {
			try {
				return this.builder.toByteArray();
			}
			finally {
				Optional.ofNullable(this.builder)
				        .ifPresent(buffer -> {
					        if (buffer.size() > 8096) {
						        // reset too large content
						        this.holder.set(new ByteArrayOutputStreamExt());
					        } else {
						        buffer.reset();
					        }
				        });
			}
		}

		public void writeTo(OutputStream out) {
			try {
				this.builder.writeTo(out);
			}
			catch (Throwable throwable) {
				FunctionAssist.rethrow(throwable);
			}
			finally {
				Optional.ofNullable(this.builder)
				        .ifPresent(buffer -> {
					        if (buffer.size() > 8096) {
						        // reset too large content
						        this.holder.set(new ByteArrayOutputStreamExt());
					        } else {
						        buffer.reset();
					        }
				        });
			}
		}
	}

	static URL detect(String path) throws MalformedURLException {
		// first file
		File file = new File(path);

		if (file.exists()) {
			return file.toURI()
			           .toURL();
		}

		URL url = ClassLoader.getSystemResource(path);

		if (null != url) {
			return url;
		}

		return new URL(path);
	}

	abstract class ResourceContainer<T>
			implements AutoCloseable {
		private static final int DEFAULT_LIMIT = 8;
		final boolean fixed;
		final AtomicInteger limit;
		private final LinkedList<T> buffer = new LinkedList<>();
		private final ConditionedLock lock = new ConditionedLock();

		protected ResourceContainer() {
			this(DEFAULT_LIMIT,
			     false);
		}

		protected ResourceContainer(final int limit) {
			this(limit,
			     false);
		}

		protected ResourceContainer(final int limit,
		                            final boolean fixed) {
			this.limit = new AtomicInteger(limit <= 0
			                               ? DEFAULT_LIMIT
			                               : limit);
			this.fixed = fixed;
		}

		/**
		 * @param limit
		 */
		public final void setLimit(int limit) {
			if (this.fixed) {
				throw new IllegalStateException("this is fixed size container!");
			}
			final int before = this.limit.getAndSet(limit <= 0
			                                        ? DEFAULT_LIMIT
			                                        : limit);

			if (before > limit) {
				// shrink size
				this.lock.execute(() -> {
					while (this.buffer.size() > limit) {
						ObjectFinalizer.closeObject(this.buffer.removeFirst());
					}
				});
			}
		}

		/**
		 * @return
		 */
		public final int size() {
			return this.lock.tran(this.buffer::size);
		}

		public final List<T> drain() {
			return this.lock.tran(() -> {
				final List<T> drained = new ArrayList<>(this.buffer);
				this.buffer.clear();
				return drained;
			});
		}

		public final void accept(Consumer<T> consumer) {
			T resource = null;
			try {
				resource = this.getResource();
				consumer.accept(resource);
			}
			finally {
				this.recycle(resource);
			}
		}

		public final <V> V apply(Function<T, V> function) {
			T resource = null;
			try {
				resource = this.getResource();
				return function.apply(resource);
			}
			finally {
				this.recycle(resource);
			}
		}

		public final T getResource(Consumer<T> consumer) {
			T resource = this.getResource();

			consumer.accept(resource);

			return resource;
		}

		/**
		 * @return
		 */
		public final T getResource() {
			final LinkedList<T> buffer = this.buffer;
			return this.lock.tran(() -> {
				                      if (buffer.isEmpty()) {
					                      return this.afterCreated(this.create());
				                      }
				                      return this.afterPopup(buffer.removeFirst());
			                      },
			                      thw -> {
				                      final Throwable filtered = ExceptionAssist.extract(thw);

				                      if (filtered instanceof ResourceExhaustException) {
					                      throw (ResourceExhaustException) filtered;
				                      }

				                      // ignore
				                      if (log.isTraceEnabled()) {
					                      log.trace("resource buffer popup error",
					                                thw);
				                      }
				                      return this.afterCreated(this.create());
			                      });

		}

		protected T afterPopup(T instance) {
			return instance;
		}

		protected void release(T instance) {
			ObjectFinalizer.closeObject(instance);
		}

		/**
		 * @param instance
		 * @return
		 */
		protected T afterCreated(T instance) {
			return instance;
		}

		/**
		 * @return
		 */
		protected abstract T create();

		protected final void load(final T instance) {
			this.buffer.add(instance);
		}

		public boolean checkRecycled(T instance) {
			return this.lock.tran(() -> this.checkRecycled0(instance));
		}

		private boolean checkRecycled0(T instance) {
			return this.buffer.contains(instance);
		}

		public final boolean recycle(final T instance) {
			if (this.isValidForRecycle(instance)) {
				return null == this.lock.tran(() -> {
					                              final LinkedList<T> buffer = this.buffer;
					                              if (this.checkRecycled0(instance)) {
						                              if (log.isTraceEnabled()) {
							                              StringBuilder builder = null;
							                              try {
								                              builder = new StringBuilder();

								                              for (T element : new ArrayList<>(buffer)) {
									                              builder.append(element.toString());
									                              builder.append('\n');
								                              }

								                              log.trace(StringAssist.format("already buffered instance recycle? %s\n=== entries ==\n%s\n",
								                                                            instance.toString(),
								                                                            builder),
								                                        new Error("Crashed!"));
							                              }
							                              finally {
								                              ObjectFinalizer.close(builder);
							                              }
						                              } else {
							                              log.warn("already buffered instance recycle?");
						                              }
						                              return instance;
					                              }
					                              if (buffer.size() < this.limit.get()) {
						                              buffer.addLast(instance);
						                              return null;
					                              }
					                              this.release(instance);
					                              return instance;
				                              },
				                              thw -> {
					                              // ignore
					                              if (log.isTraceEnabled()) {
						                              log.trace("resource buffer recycle error",
						                                        thw);
					                              }
					                              this.release(instance);
					                              return instance;
				                              });
			}

			this.release(instance);
			return false;
		}

		/**
		 * @param instance
		 * @return
		 */
		protected boolean isValidForRecycle(T instance) {
			return Objects.nonNull(instance);
		}

		@Override
		public void close() throws Exception {
			final ArrayList<T> copy = new ArrayList<>();

			this.lock.execute(() -> {
				copy.addAll(this.buffer);
				this.buffer.clear();
			});

			copy.forEach(ObjectFinalizer::closeObject);
		}
	}

	int STRING_BUILDER_INSTANCE = 256;
	int STRING_BUILDER_LIMIT = 8196;

	StringBuilderContainer stringContainer = new StringBuilderContainer();

	class StringBuilderHolder
			implements AutoCloseable {
		final StringBuilder builder;

		StringBuilderHolder() {
			this.builder = new StringBuilder(STRING_BUILDER_LIMIT);
		}

		@Override
		public void close() throws Exception {
			if (this.builder.capacity() > STRING_BUILDER_LIMIT) {
				return;
			}
			stringContainer.recycle(this);
		}
	}

	class StringBuilderContainer
			extends ResourceContainer<StringBuilderHolder> {
		private StringBuilderContainer() {
			super(STRING_BUILDER_INSTANCE,
			      true);
		}

		@Override
		protected StringBuilderHolder create() {
			return new StringBuilderHolder();
		}

		@Override
		protected StringBuilderHolder afterPopup(StringBuilderHolder instance) {
			instance.builder.setLength(0);
			return super.afterPopup(instance);
		}
	}


	static String stringBuilderWith(Consumer<StringBuilder> consumer) {
		try (StringBuilderHolder holder = stringContainer.getResource()) {
			consumer.accept(holder.builder);
			return holder.builder.toString();
		}
		catch (Exception exception) {
			throw ExceptionAssist.wrap("",
			                           exception);
		}
	}

	static void stringBuilderWith(Consumer<StringBuilder> consumer,
	                              Consumer<String> result) {
		try (StringBuilderHolder holder = stringContainer.getResource()) {
			consumer.accept(holder.builder);
			result.accept(holder.builder.toString());
		}
		catch (Exception exception) {
			throw ExceptionAssist.wrap("",
			                           exception);
		}
	}

	// ---------------------------------------------------------------------
	// Section resource related event handle
	// ---------------------------------------------------------------------
	interface Traceable {
		String getUuid();
	}

	Consumer<Traceable> VOID_TRACEABLE = traceable -> {
	};

	enum ResourceAllocateEvent
			implements TypedEvent<ResourceAllocateEventManager> {
		allocate,
		deallocate
	}

	interface ResourceAllocateEventListener
			extends TypedEventListener<ResourceAllocateEvent> {
		@Override
		default Class<ResourceAllocateEvent> getEventType() {
			return ResourceAllocateEvent.class;
		}
	}

	@Getter
	class ResourceAllocateInfo {
		final Traceable resource;
		final Throwable stack;

		public ResourceAllocateInfo(final Traceable resource,
		                            final boolean allocate) {
			this.resource = resource;
			this.stack = new Throwable(StringAssist.format("resource %s [%s] (%s)",
			                                               allocate
			                                               ? "allocate"
			                                               : "deallocate",
			                                               this.resource.getUuid(),
			                                               this.resource.getClass()
			                                                            .getSimpleName()));
		}

		@Override
		public String toString() {
			return this.stack.getMessage();
		}
	}

	class ResourceAllocateEventManager
			extends TypedEventListenerContainerBase {
		public static final ResourceAllocateEventManager INSTANCE = new ResourceAllocateEventManager();
		final AtomicBoolean active = new AtomicBoolean(false);

		public boolean isActive() {
			return this.active.get();
		}

		private void touchActive() {
			this.active.set(!this.trigger.isEmpty() || !this.listeners.isEmpty());
		}

		public void allocate(Traceable traceable) {
			if (this.active.get()) {
				ResourceAllocateEvent.allocate.shared(this)
				                              .set(accessorAllocateEvent.resourceAllocateInfo,
				                                   new ResourceAllocateInfo(traceable,
				                                                            true))
				                              .handle();
			}
		}

		public void deallocate(Traceable traceable) {
			if (this.active.get()) {
				ResourceAllocateEvent.deallocate.shared(this)
				                                .set(accessorAllocateEvent.resourceAllocateInfo,
				                                     new ResourceAllocateInfo(traceable,
				                                                              false))
				                                .handle();
			}
		}

		@Override
		protected void triggerChanged() {
			super.triggerChanged();
			this.touchActive();
		}

		@Override
		protected void listenerChanged() {
			super.listenerChanged();
			this.touchActive();
		}
	}

	class ResourceAllocateEventAccessor
			extends AttributeAccessorContainer {
		public AttributeContextAccessor<ResourceAllocateInfo> resourceAllocateInfo;

		private ResourceAllocateEventAccessor() {
		}
	}

	ResourceAllocateEventAccessor accessorAllocateEvent = new ResourceAllocateEventAccessor();
}
