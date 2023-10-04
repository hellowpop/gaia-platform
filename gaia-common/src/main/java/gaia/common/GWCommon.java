package gaia.common;

import static gaia.common.ReflectAssist.createInstance;
import static gaia.common.ReflectAssist.updateInstanceValue;
import static gaia.common.ResourceAssist.VOID_APPENDER;
import static gaia.common.ResourceAssist.stringComposer;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import gaia.ConstValues;
import gaia.ConstValues.CrashedException;
import gaia.common.ConcurrentAssist.ConditionedLock;
import gaia.common.FunctionAssist.ErrorHandleSupplier;
import gaia.common.PrimitiveTypes.ObjectType;
import gaia.common.StringAssist.NamePattern;

public interface GWCommon {
	Logger log = LoggerFactory.getLogger(GWCommon.class);

	Function<String, String> extension_encoder = key -> key.startsWith("x-")
	                                                    ? key
	                                                    : "x-".concat(key);
	Function<String, String> extension_decoder = key -> key.startsWith("x-")
	                                                    ? key.substring(2)
	                                                    : key;
	CommonAccessor accessorCommon = new CommonAccessor();

	Supplier<AttributeContext> threadSharedContextSupplier = accessorCommon::get0;


	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@interface SingleTonInstance {

	}

	interface Disposable {
		default void dispose() {
			final Runnable disposer = this.getDisposer();

			if (disposer == null) {
				throw new IllegalStateException("disposer is null?");
			}

			disposer.run();
		}

		default void setDisposer(Runnable disposer) {

		}

		default Runnable getDisposer() {
			return null;
		}
	}

	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@interface PriorityOf {
		int value();
	}

	interface Priority {
		int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;

		int NORMAL_PRECEDENCE = 0;

		int LOWEST_PRECEDENCE = Integer.MAX_VALUE;

		default int getPriority() {
			return NORMAL_PRECEDENCE;
		}
	}

	enum PriorityType
			implements Comparator<Priority> {
		ASC {
			@Override
			public int compare(Priority o1,
			                   Priority o2) {
				return Integer.compare(o1.getPriority(),
				                       o2.getPriority());
			}
		},
		DESC {
			@Override
			public int compare(Priority o1,
			                   Priority o2) {
				return Integer.compare(o2.getPriority(),
				                       o1.getPriority());
			}
		};

		public void sort(List<? extends Priority> list) {
			list.sort(this);
		}
	}

	interface Identify
			extends Priority {
		@SuppressWarnings("SameReturnValue")
		String getIdentity();
	}

	final class IdentifyContext<T extends Identify> {
		final ConditionedLock lock = new ConditionedLock();
		final List<T> collection = new ArrayList<>();
		final AtomicReference<Map<String, T>> holder = new AtomicReference<>(Collections.emptyMap());
		final AtomicBoolean dirty = new AtomicBoolean();

		public void register(T entry) {
			this.lock.execute(() -> {
				if (this.collection.contains(entry)) {
					throw new CrashedException("duplicate generator");
				}
				this.collection.add(entry);
				this.dirty.set(true);
			});
		}

		public void deregister(T entry) {
			this.lock.execute(() -> {
				if (this.collection.remove(entry)) {
					this.dirty.set(true);
					return;
				}
				throw new CrashedException("unknown generator");
			});
		}

		public boolean containIdentity0(String identity) {
			return this.collection.stream()
			                      .anyMatch(entry -> StringUtils.equals(entry.getIdentity(),
			                                                            identity));
		}

		public Map<String, T> touch0() {
			if (this.dirty.compareAndSet(true,
			                             false)) {
				this.lock.execute(() -> {
					final Map<String, T> map = new HashMap<>();
					this.collection.forEach(element -> map.put(element.getIdentity(),
					                                           element));
					this.holder.set(map);
				});
			}
			return this.holder.get();
		}

		public Optional<T> referenceEntry(String name) {
			return Optional.ofNullable(this.touch0()
			                               .get(name));
		}

		public T takeEntry(String name) {
			return this.referenceEntry(name)
			           .orElseThrow(() -> new IllegalArgumentException(StringAssist.format("[%s] is unknown script runtime",
			                                                                               name)));
		}

		public <X extends T> Optional<X> referenceEntry(final String name,
		                                                final Class<X> type) {
			return Optional.ofNullable(this.touch0()
			                               .get(name))
			               .filter(type::isInstance)
			               .map(type::cast);
		}
	}


	interface ActivateHandler {
		ConditionedLock getLock();

		AtomicBoolean getActivate();

		default void activate() {
			final AtomicBoolean activate = this.getActivate();
			this.getLock()
			    .execute(() -> {
				    if (activate.compareAndSet(false,
				                               true)) {
					    try {
						    this.onActivated();
					    }
					    catch (Throwable throwable) {
						    if (log.isDebugEnabled()) {
							    log.error(StringAssist.format("[%s] activate error",
							                                  this),
							              throwable);
						    }

						    activate.set(false);
						    throw ExceptionAssist.wrap("activate error",
						                               throwable);
					    }
				    } else {
					    throw new RuntimeException("already activated");
				    }
			    });
		}

		default void deactivate() {
			final AtomicBoolean activate = this.getActivate();
			this.getLock()
			    .execute(() -> {
				    if (activate.compareAndSet(true,
				                               false)) {
					    this.onDeactivated();
				    } else {
					    throw new RuntimeException("current not activated");
				    }
			    });
		}

		default boolean isActive() {
			return this.getActivate()
			           .get();
		}

		default boolean isInactive() {
			return !this.getActivate()
			            .get();
		}

		void onActivated();

		void onDeactivated();
	}

	@Getter
	abstract class ActivateSupport
			implements ActivateHandler {
		protected final ConditionedLock lock = new ConditionedLock();
		@Getter
		final AtomicBoolean activate = new AtomicBoolean(false);
	}
	// ---------------------------------------------------------------------
	// Section invocation chain
	// ---------------------------------------------------------------------

	@Getter
	abstract class InvocationChain<T>
			implements InvocationHandler {
		/**
		 * target type
		 */
		final Class<T> invocationType;
		/**
		 * proxy instance
		 */
		@Getter
		final T proxy;
		/**
		 * concurrent lock of this proxy
		 */
		@Getter
		final ConditionedLock lock = new ConditionedLock();
		/**
		 * container of instance
		 */
		protected final ArrayList<T> instanceList = new ArrayList<>();
		/**
		 * snapshot of container
		 */
		final AtomicReference<List<T>> instanceHolder = new AtomicReference<>();

		@SuppressWarnings("unchecked")
		protected InvocationChain() {
			super();

			final Class<T> type = (Class<T>) ReflectAssist.getGenericParameter(this.getClass(),
			                                                                   InvocationChain.class,
			                                                                   "T");
			this.proxy = type.cast(Proxy.newProxyInstance(this.getClass()
			                                                  .getClassLoader(),
			                                              new Class[]{type},
			                                              this));

			this.invocationType = type;
		}

		public final int chainSize() {
			return this.lock.tran(this.instanceList::size);
		}

		public final void clearChain() {
			this.lock.execute(() -> {
				this.instanceList.clear();
				this.chainChanged();
				this.instanceHolder.set(null);
			});
		}

		public final Optional<T> proxy() {
			return this.chainSize() > 0
			       ? Optional.of(this.proxy)
			       : Optional.empty();
		}

		public final void proxy(Consumer<T> listener) {
			if (this.chainSize() > 0) {
				listener.accept(this.proxy);
			}
		}

		public final void registerIfAbsent(final T instance) {
			if (this.instanceHolder.get()
			                       .contains(instance)) {
				return;
			}

			this.registerChain(instance);
		}

		public final void registerChain(final T instance) {
			this.registerChain(instance,
			                   () -> {
			                   });
		}

		public final void registerChain(final T instance,
		                                final Runnable runnable) {
			if (instance == null || instance == this.proxy) {
				return;
			}

			// validate instance
			this.checkInstance(instance);

			final ArrayList<T> array = this.instanceList;

			this.lock.execute(() -> {
				if (array.contains(instance)) {
					if (log.isTraceEnabled()) {
						log.trace(StringAssist.format("[%s] is already exist in [%s]",
						                              instance,
						                              array));
					}
					throw new EntryDuplicatedException(instance.toString(),
					                                   "chained instances");
				}

				array.add(instance);

				// event notify
				this.chainChanged();

				this.instanceHolder.set(new ArrayList<>(array));

				runnable.run();
			});
		}

		public final void deregisterChain(T instance) {
			this.deregisterChain(instance,
			                     null);
		}

		public final void deregisterChain(final T instance,
		                                  final Runnable runnable) {
			if (null == instance) {
				throw new CrashedException("instance must not null");
			}

			final ArrayList<T> array = this.instanceList;

			this.lock.execute(() -> {
				if (array.remove(instance)) {
					// event notify
					this.chainChanged();

					this.instanceHolder.set(new ArrayList<>(array));

					Optional.ofNullable(runnable)
					        .ifPresent(Runnable::run);
					return;
				}
				throw new EntryNotFoundException(instance.toString(),
				                                 "chained instances");
			});
		}

		@Override
		public final Object invoke(Object proxy,
		                           Method method,
		                           Object[] args) throws Throwable {
			// object method
			if (Object.class == method.getDeclaringClass()) {
				if (StringUtils.equalsIgnoreCase(method.getName(),
				                                 "equals")) {
					return this.proxy == args[0];
				}
				return method.invoke(this,
				                     args);
			}

			if (this.isAbleToInvoke(method)) {
				return method.invoke(this,
				                     args);
			}

			final List<T> instanceArray = this.instanceHolder.get();

			if (null == instanceArray || instanceArray.size() == 0 || !this.isAbleToStart()) {
				return defaultValueOf(method);
			}

			final Object ret = instanceArray.stream()
			                                .filter(this::canInvokeType)
			                                .filter(this::checkContinue)
			                                .map(target -> {
				                                try {
					                                return method.invoke(target,
					                                                     args);
				                                }
				                                catch (Exception thw) {
					                                return this.handleException(target,
					                                                            thw,
					                                                            args);
				                                }
			                                })
			                                .filter(Objects::nonNull)
			                                .findFirst()
			                                .orElseGet(() -> defaultValueOf(method));

			if (ret instanceof Throwable) {
				throw ExceptionAssist.extract((Throwable) ret);
			}

			return ret;
		}

		protected boolean isAbleToInvoke(Method method) {
			return false;
		}

		protected boolean canInvokeType(T listener) {
			return listener != null;
		}

		@SuppressWarnings("SameReturnValue")
		protected boolean isAbleToStart() {
			// customize
			return true;
		}

		@SuppressWarnings("SameReturnValue")
		protected boolean checkContinue(T listener) {
			return true;
		}

		/**
		 * if value has returned, continue execution chain, or returned value is result value
		 *
		 * @param instance  invoke target instance
		 * @param throwable caught throwable
		 * @param args      invoke arguments
		 * @return exception handle result
		 */
		protected Object handleException(T instance,
		                                 Throwable throwable,
		                                 Object[] args) {
			return ExceptionAssist.safe(throwable);
		}

		/**
		 * when register instance call this method
		 */
		@SuppressWarnings("EmptyMethod")
		protected void checkInstance(T instance) {

		}

		protected void chainChanged() {

		}

		protected final Stream<T> chainStream() {
			return Optional.ofNullable(this.instanceHolder.get())
			               .map(List::stream)
			               .orElse(Stream.empty());
		}
	}

	abstract class OrderedInvocationChain<T extends Priority>
			extends InvocationChain<T>
			implements Priority {
		final Comparator<Priority> comparator;

		@Setter
		@Getter
		int priority = 0;

		protected OrderedInvocationChain() {
			this(PriorityType.ASC);
		}

		protected OrderedInvocationChain(Comparator<Priority> comparator) {
			super();
			this.comparator = comparator;
		}

		@Override
		protected void chainChanged() {
			this.instanceList.sort(this.comparator);
			super.chainChanged();
		}

		@Override
		protected boolean isAbleToInvoke(Method method) {
			return method.getDeclaringClass() == Priority.class;
		}
	}

	static Object defaultValueOf(Method method) {
		Class<?> rt = method.getReturnType();

		if (!rt.isPrimitive() || rt.equals(void.class)) {
			return null;
		}

		return PrimitiveTypes.determine(rt)
		                     .getNullValue();
	}

	// ---------------------------------------------------------------------
	// Section attribute context
	// ---------------------------------------------------------------------

	interface AttributeContext
			extends Disposable {
		AttributeContext getSupport();

		default ConditionedLock getLock() {
			return this.getSupport()
			           .lock();
		}

		default ConditionedLock lock() {
			return this.getSupport()
			           .lock();
		}

		default Object setAttribute(String key,
		                            Object value) {
			return this.getSupport()
			           .setAttribute(key,
			                         value);
		}

		default void attributes(BiConsumer<String, Object> consumer) {
			this.getSupport()
			    .attributes(consumer);
		}

		default boolean setAttributeIfAbsent(String key,
		                                     Supplier<?> supplier) {
			return this.getSupport()
			           .setAttributeIfAbsent(key,
			                                 supplier);
		}

		default <T> void setTypeAttribute(T value) {
			this.getSupport()
			    .setAttribute(value.getClass()
			                       .getName(),
			                  value);
		}

		default <T> T getTypeAttribute(Class<T> type) {
			return this.refTypeAttribute(type)
			           .orElse(null);
		}

		default <T> T getTypeAttributeIfAbsent(Class<T> type,
		                                       Supplier<T> supplier) {
			return this.refTypeAttribute(type)
			           .orElseGet(() -> {
				           T value = supplier.get();

				           this.setAttribute(type.getName(),
				                             value);

				           return value;
			           });
		}

		default <T> T takeTypeAttribute(Class<T> type) {
			return this.refTypeAttribute(type)
			           .orElseThrow(() -> new IllegalStateException(StringAssist.format("[%s] not found",
			                                                                            type.getName())));
		}

		default <T> Optional<T> refTypeAttribute(Class<T> type) {
			return Optional.ofNullable(type.cast(this.getSupport()
			                                         .getAttribute(type.getName())));
		}

		/**
		 * @param key      key
		 * @param type     type
		 * @param supplier default value supplier
		 */
		default <T> T getAttribute(String key,
		                           Class<T> type,
		                           ErrorHandleSupplier<T> supplier) {
			return this.getSupport()
			           .getAttribute(key,
			                         type,
			                         supplier);
		}

		default Object removeAttribute(String key) {
			return this.getSupport()
			           .removeAttribute(key);
		}

		default <T> T removeAttribute(String key,
		                              Class<T> type) {
			return this.getSupport()
			           .removeAttribute(key,
			                            type);
		}

		default void truncate() {
			this.getSupport()
			    .truncate();
		}

		default boolean hasAttribute(String key) {
			return this.getSupport()
			           .hasAttribute(key);
		}

		default boolean emptyAttribute(String key) {
			return !this.hasAttribute(key);
		}

		default Object getAttribute(String key) {
			return this.getAttribute(key,
			                         Object.class,
			                         null);
		}

		default <T> T getAttribute(String key,
		                           Class<T> type) {
			return this.getAttribute(key,
			                         type,
			                         null);
		}

		default void loadAttribute(Map<String, Object> otherMap) {
			this.getSupport()
			    .loadAttribute(otherMap);
		}

		default Map<String, Object> exportAttribute() {
			return this.getSupport()
			           .exportAttribute();
		}

		default Map<String, Object> exportAttribute(AttributeAccessorContainer container) {
			final AttributeContext context = this.getSupport();
			final Map<String, Object> map = new HashMap<>();

			container.stream(AttributeContext.class)
			         .forEach(accessor -> accessor.reference(context)
			                                      .ifPresent(value -> map.put(accessor.getAttributeName(),
			                                                                  value)));

			return map;
		}

		default <T> Optional<T> peekAttribute(String key,
		                                      Class<T> type) {
			return Optional.ofNullable(this.getAttribute(key,
			                                             type,
			                                             null));
		}

		default <T> Optional<T> peekAttribute(String key,
		                                      Class<T> type,
		                                      ErrorHandleSupplier<T> supplier) {
			return Optional.ofNullable(this.getAttribute(key,
			                                             type,
			                                             supplier));
		}

		@Override
		@SuppressWarnings("EmptyMethod")
		default void dispose() {
			//
		}
	}

	@Getter
	class AttributeContextComposer {
		public static AttributeContextComposer of() {
			return new AttributeContextComposer(GWCommon.threadSharedContextSupplier.get());
		}

		public static AttributeContextComposer of(AttributeContext context) {
			return new AttributeContextComposer(context);
		}

		final AttributeContext context;

		private AttributeContextComposer(AttributeContext context) {
			this.context = context;
		}

		public <T> AttributeContextComposer set(AttributeContextAccessor<T> property,
		                                        T value) {
			property.set(this.context,
			             value);

			return this;
		}

		public <T> AttributeContextComposer unset(AttributeContextAccessor<T> property) {
			property.unset(this.context);
			return this;
		}
	}

	class AttributeContextImpl
			implements AttributeContext {
		private transient final Map<String, Object> attribute = new HashMap<>();
		@Getter
		protected transient final ConditionedLock lock = new ConditionedLock();
		@Getter
		transient final AttributeContext support = this;

		@Override
		public final ConditionedLock lock() {
			return this.lock;
		}

		@Override
		public void attributes(BiConsumer<String, Object> consumer) {
			this.lock.tran(() -> new HashMap<>(this.attribute))
			         .forEach(consumer);
		}

		@Override
		public final Object setAttribute(String key,
		                                 Object value) {
			return this.lock.tran(() -> Objects.isNull(value)
			                            ? this.attribute.remove(key)
			                            : this.attribute.put(key,
			                                                 value));
		}

		@Override
		public boolean setAttributeIfAbsent(String key,
		                                    Supplier<?> supplier) {
			return this.lock.tran(() -> {
				if (null == this.attribute.get(key)) {
					final Object generateValue = supplier.get();
					this.attribute.put(key,
					                   generateValue);
					return true;
				}

				return false;
			});
		}

		@Override
		public final <T> T getAttribute(final String key,
		                                final Class<T> type,
		                                final ErrorHandleSupplier<T> supplier) {
			return this.lock.tran(() -> {
				final Object value = Optional.ofNullable(this.attribute.get(key))
				                             .orElseGet(() -> {
					                             if (null != supplier) {
						                             final Object generated = FunctionAssist.supply(supplier);
						                             this.attribute.put(key,
						                                                generated);
						                             return generated;
					                             }
					                             return null;
				                             });

				return null == value
				       ? null
				       : type.cast(value);
			});
		}

		@Override
		public final Object removeAttribute(String key) {
			return this.setAttribute(key,
			                         null);
		}

		@Override
		public <T> T removeAttribute(String key,
		                             Class<T> type) {
			Object before = this.setAttribute(key,
			                                  null);
			return null == before
			       ? null
			       : type.cast(before);
		}

		@Override
		public boolean hasAttribute(String key) {
			return this.lock.tran(() -> Objects.nonNull(this.attribute.get(key)));
		}

		@Override
		public void loadAttribute(final Map<String, Object> otherMap) {
			if (null == otherMap || otherMap.isEmpty())
				return;
			this.lock.execute(() -> this.attribute.putAll(otherMap));
		}

		@Override
		public Map<String, Object> exportAttribute() {
			return this.lock.tran(() -> new HashMap<>(this.attribute));
		}

		@Override
		public void truncate() {
			this.lock.execute(this.attribute::clear);
		}
	}

	interface AttributeAccessor<S, T> {
		Field getDeclaredField();

		Class<S> getContextType();

		Class<T> getAttributeType();

		String getAttributeKey();

		String getAttributeName();

		String getAttributeDescription();

		@SuppressWarnings("SameReturnValue")
		default ErrorHandleSupplier<T> getDefaultSupplier() {
			return null;
		}

		default Optional<ErrorHandleSupplier<T>> optionDefaultSupplier() {
			return Optional.ofNullable(this.getDefaultSupplier());
		}

		Supplier<S> getContextSupplier();

		@SuppressWarnings("unchecked")
		default void setContextSupplierAnonymous(Supplier<?> supplier) {
			this.setContextSupplier((Supplier<S>) supplier);
		}

		void setContextSupplier(Supplier<S> supplier);

		@SuppressWarnings("unchecked")
		default void supplierAnonymous(ErrorHandleSupplier<?> supplier) {
			this.setDefaultSupplier((ErrorHandleSupplier<T>) supplier);
		}

		void setDefaultSupplier(ErrorHandleSupplier<T> supplier);

		default boolean has() {
			return this.has(this.getContextSupplier()
			                    .get());
		}

		default boolean has(S context) {
			return this.getAttribute(context)
			           .isPresent();
		}

		default boolean has(Map<String, Object> context) {
			return this.reference(context)
			           .isPresent();
		}

		default boolean empty() {
			return this.empty(this.getContextSupplier()
			                      .get());
		}

		default boolean empty(S context) {
			return !this.has(context);
		}

		default boolean empty(Map<String, Object> context) {
			return !this.has(context);
		}

		default T set(T value) {
			return this.set(this.getContextSupplier()
			                    .get(),
			                value);
		}

		default T set(S context,
		              T value) {
			return this.getAttributeType()
			           .cast(this.setAttribute(context,
			                                   value));
		}

		default void update(S context,
		                    Object value) {
			final Class<T> attributeType = this.getAttributeType();

			if (!attributeType.isInstance(value)) {
				ObjectType objectType = PrimitiveTypes.determine(attributeType);

				if (objectType == ObjectType.VOID_TYPE) {
					throw new CrashedException("invalid attribute type");
				}

				value = objectType.cast(value);
			}
			this.setAttribute(context,
			                  value);
		}

		default void set(Map<String, Object> context,
		                 T value) {
			context.put(this.getAttributeKey(),
			            value);
		}

		default T setIfAbsent(Supplier<T> supplier) {
			return this.setIfAbsent(this.getContextSupplier()
			                            .get(),
			                        supplier);
		}

		default T setIfAbsent(S context,
		                      Supplier<T> supplier) {
			T value = this.get(context);

			if (null == value) {
				value = supplier.get();
				this.set(context,
				         value);
			}
			return value;
		}

		default boolean setIfAbsent(Map<String, Object> context,
		                            Supplier<T> supplier) {
			if (this.empty(context)) {
				this.set(context,
				         supplier.get());
				return true;
			}
			return false;
		}

		default T get() {
			return this.get(this.getContextSupplier()
			                    .get());
		}

		default T getIfAbsent(ErrorHandleSupplier<T> supplier) {
			return this.getIfAbsent(this.getContextSupplier()
			                            .get(),
			                        supplier);
		}

		default T get(ErrorHandleSupplier<T> supplier) {
			return this.getIfAbsent(this.getContextSupplier()
			                            .get(),
			                        supplier);
		}

		default T get(S context) {
			return this.reference(context)
			           .orElse(null);
		}

		default <M> M get(final S context,
		                  final Class<M> type) {
			final T value = this.get(context);

			if (null == value) {
				return null;
			}

			if (type.isInstance(value)) {
				return type.cast(value);
			}

			throw new TypeMissMatchException(type,
			                                 value);
		}

		default T getIfAbsent(S context,
		                      ErrorHandleSupplier<T> supplier) {
			return this.referenceIfAbsent(context,
			                              supplier)
			           .orElse(null);
		}

		default T get(Map<String, Object> context) {
			return this.reference(context)
			           .orElse(null);
		}

		default T take() {
			return this.take(this.getContextSupplier()
			                     .get());
		}

		default T take(S context) {
			return this.reference(context)
			           .orElseThrow(() -> {
				           if (log.isTraceEnabled() && context instanceof AttributeContext) {
					           log.trace(StringAssist.format("take fail current values [%s]",
					                                         ((AttributeContext) context).exportAttribute()));
				           }
				           return new RuntimeException(StringAssist.format("Attribute not found [%s] @ [%s]",
				                                                           this.getAttributeName(),
				                                                           context.getClass()
				                                                                  .getSimpleName()));
			           });
		}

		default <M> M take(S context,
		                   Class<M> type) {
			final T value = this.take(context);

			if (type.isInstance(value)) {
				return type.cast(value);
			}

			throw new TypeMissMatchException(type,
			                                 value);
		}

		default T take(Map<String, Object> context) {
			return this.reference(context)
			           .orElseThrow(() -> new RuntimeException("Attribute not found:" + this.getAttributeName()));
		}

		default T remove() {
			return this.remove(this.getContextSupplier()
			                       .get());
		}

		default T remove(S context) {
			return this.getAttributeType()
			           .cast(this.removeAttribute(context)
			                     .orElse(null));
		}

		default T unset() {
			return this.unset(this.getContextSupplier()
			                      .get());
		}

		default T unset(S context) {
			return this.remove(context);
		}

		default void unset(Map<String, Object> context) {
			context.remove(this.getAttributeKey());
		}

		default Optional<T> extract() {
			return this.extract(this.getContextSupplier()
			                        .get());
		}

		default Optional<T> extract(S context) {
			return Optional.ofNullable(this.remove(context));
		}

		default <M> Optional<M> extract(S context,
		                                Class<M> type) {
			final T value = this.remove(context);

			if (null == value) {
				return Optional.empty();
			}

			if (type.isInstance(value)) {
				return Optional.of(type.cast(value));
			}

			throw new TypeMissMatchException(type,
			                                 value);
		}

		default Optional<T> reference() {
			return this.reference(this.getContextSupplier()
			                          .get());
		}

		default Optional<T> reference(S context) {
			return this.referenceIfAbsent(context,
			                              this.getDefaultSupplier());
		}

		default Optional<T> referenceIfAbsent(S context,
		                                      ErrorHandleSupplier<T> supplier) {
			return Optional.ofNullable(this.getAttributeType()
			                               .cast(this.getAttribute(context)
			                                         .orElseGet(() -> {
				                                         T newValue = FunctionAssist.supply(supplier);

				                                         if (null != newValue) {
					                                         final Object before = this.setAttribute(context,
					                                                                                 newValue);

					                                         if (null != before) {
						                                         log.warn("empty generate before value is not null?");
					                                         }
				                                         }

				                                         return newValue;
			                                         })));
		}

		default Optional<T> reference(Map<String, Object> context) {
			return Optional.ofNullable(context.get(this.getAttributeKey()))
			               .filter(value -> this.getAttributeType()
			                                    .isInstance(value))
			               .map(value -> this.getAttributeType()
			                                 .cast(value));
		}

		default void copy(final S fromContext,
		                  final S toContext) {
			this.set(toContext,
			         this.take(fromContext));
		}

		default void copyIfAbsent(final S fromContext,
		                          final S toContext) {
			if (this.has(toContext)) {
				return;
			}

			this.reference(fromContext)
			    .ifPresent(value -> this.set(toContext,
			                                 value));
		}

		Optional<Object> getAttribute(S context);

		Optional<Object> removeAttribute(S context);

		Object setAttribute(S context,
		                    Object value);
	}

	@Getter
	abstract class AttributeAccessorAdapter<S, T, K extends AttributeAccessorAdapter<S, T, ?>>
			implements AttributeAccessor<S, T> {
		protected final Class<T> attributeType;
		@Getter
		protected Field declaredField;
		@Getter
		@Setter(AccessLevel.PACKAGE)
		String attributeKey;
		@Getter
		@Setter(AccessLevel.PACKAGE)
		String attributeName;
		@Getter
		@Setter(AccessLevel.PACKAGE)
		String attributeDescription;
		@Getter
		@Setter
		ErrorHandleSupplier<T> defaultSupplier;
		@Setter
		Supplier<S> contextSupplier;


		protected AttributeAccessorAdapter(final Class<T> type) {
			this.attributeType = type;
			this.attributeKey = RandomAssist.getUUID();
			this.attributeName = type.getSimpleName();
		}

		protected AttributeAccessorAdapter(final Class<T> type,
		                                   final Field field) {
			this.attributeType = type;
			this.declaredField = field;
			this.attributeKey = StringAssist.format("%s@%s",
			                                        field.getName(),
			                                        field.getDeclaringClass()
			                                             .getSimpleName());
			this.attributeName = field.getName();
		}

		protected abstract K self();

		@Override
		public Supplier<S> getContextSupplier() {
			return Optional.ofNullable(this.contextSupplier)
			               .orElseThrow(() -> new CrashedException("context supplier is not set"));
		}
	}

	class TypedAttributeAccessorContextSupplier
			implements Supplier<AttributeContext> {
		@Override
		public AttributeContext get() {
			return accessorCommon.get0();
		}
	}

	@AccessorContextSupplier(TypedAttributeAccessorContextSupplier.class)
	interface AttributeContextAccessor<T>
			extends AttributeAccessor<AttributeContext, T> {
		@Override
		default Class<AttributeContext> getContextType() {
			return AttributeContext.class;
		}

		static <T> AttributeContextAccessor<T> of(Class<T> type) {
			return new AttributeContextAccessorImpl<>(type);
		}

		static <T> AttributeContextAccessor<T> of(Class<T> type,
		                                          ErrorHandleSupplier<T> supplier) {
			return new AttributeContextAccessorImpl<>(type,
			                                          supplier);
		}

		static <T> AttributeContextAccessor<T> of(Class<T> type,
		                                          Field field) {
			return new AttributeContextAccessorImpl<>(type,
			                                          field);
		}
	}

	class AttributeContextAccessorImpl<T>
			extends AttributeAccessorAdapter<AttributeContext, T, AttributeContextAccessorImpl<T>>
			implements AttributeContextAccessor<T> {
		AttributeContextAccessorImpl(Class<T> type) {
			this(type,
			     (ErrorHandleSupplier<T>) null);
		}

		AttributeContextAccessorImpl(Class<T> type,
		                             ErrorHandleSupplier<T> supplier) {
			super(type);
			this.setDefaultSupplier(supplier);
			this.setContextSupplier(threadSharedContextSupplier);
		}

		AttributeContextAccessorImpl(Class<T> type,
		                             Field field) {
			super(type,
			      field);
		}

		@Override
		protected AttributeContextAccessorImpl<T> self() {
			return this;
		}

		@Override
		public Optional<Object> getAttribute(AttributeContext context) {
			return Optional.ofNullable(context.getAttribute(this.attributeKey));
		}

		@Override
		public Optional<Object> removeAttribute(AttributeContext context) {
			return Optional.ofNullable(context.removeAttribute(this.attributeKey));
		}

		@Override
		public Object setAttribute(AttributeContext context,
		                           Object value) {
			return context.setAttribute(this.attributeKey,
			                            value);
		}

		@Override
		public String toString() {
			return this.attributeDescription;
		}
	}


	interface AttributePropertyAccessor<T>
			extends AttributeContextAccessor<T> {
		default boolean match(String name) {
			if (StringUtils.equalsIgnoreCase(this.getAttributeKey(),
			                                 name)) {
				return true;
			}

			return this.aliases()
			           .anyMatch(alias -> StringUtils.equalsIgnoreCase(alias,
			                                                           name));
		}

		Stream<String> aliases();
	}

	class AttributePropertyAccessorImpl<T>
			extends AttributeContextAccessorImpl<T>
			implements AttributePropertyAccessor<T> {
		final List<String> aliases = new ArrayList<>();

		AttributePropertyAccessorImpl(Class<T> type,
		                              Field field) {
			super(type,
			      field);
		}

		void addAlias(String alias) {
			if (this.aliases.contains(alias)) {
				return;
			}
			this.aliases.add(alias);
		}

		@Override
		public Stream<String> aliases() {
			return this.aliases.stream();
		}

		@Override
		public Optional<T> reference(Map<String, Object> context) {
			final Optional<T> attributeValue = super.reference(context);

			return attributeValue.isPresent()
			       ? attributeValue
			       : this.aliases()
			             .map(context::get)
			             .filter(Objects::nonNull)
			             .findFirst()
			             .map(value -> this.getAttributeType()
			                               .cast(value));
		}
	}

	abstract class AttributeAccessorContainer {
		final List<AttributeAccessor<?, ?>> accessors = new ArrayList<>();


		protected AttributeAccessorContainer() {
			super();
			accessorCommon.buildAttributeAccessor(this);
		}

		void appendAccessor(AttributeAccessor<?, ?> accessor) {
			this.accessors.add(accessor);
		}

		public final Map<String, Object> exportAttributeMap() {
			return accessorCommon.get0()
			                     .exportAttribute();
		}

		public final void importEventData(Map<String, Object> data) {
			accessorCommon.get0()
			              .loadAttribute(data);
		}

		public Stream<AttributeAccessor<AttributeContext, ?>> stream() {
			return this.stream(AttributeContext.class);
		}

		@SuppressWarnings("unchecked")
		public <T> Stream<AttributeAccessor<T, ?>> stream(Class<T> contextType) {
			return this.accessors.stream()
			                     .filter(accessor -> contextType.isAssignableFrom(accessor.getContextType()))
			                     .map(accessor -> (AttributeAccessor<T, ?>) accessor);
		}
	}

	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@interface AccessorProperty {
		String value() default "";

		String suffix() default "";

		NamePattern pattern() default NamePattern.kebab;
	}

	abstract class AccessorPropertyContainer {
		static final ConditionedLock lock = new ConditionedLock();
		static final Map<Class<?>, AccessorPropertyContainer> containers = new HashMap<>();

		public static <T extends AccessorPropertyContainer> T of(Class<T> type) {
			return type.cast(lock.tran(() -> containers.computeIfAbsent(type,
			                                                            key -> {
				                                                            try {
					                                                            Constructor<T> constructor = type.getDeclaredConstructor();
					                                                            constructor.setAccessible(true);
					                                                            return constructor.newInstance();
				                                                            }
				                                                            catch (Throwable e) {
					                                                            throw new RuntimeException("container create error",
					                                                                                       e);
				                                                            }
			                                                            })));
		}

		final List<AttributePropertyAccessor<?>> accessors = new ArrayList<>();

		protected AccessorPropertyContainer() {
			super();
			final Class<?> type = this.getClass();

			final AccessorProperty property = AnnotationAssist.refAnnotation(type,
			                                                                 AccessorProperty.class)
			                                                  .orElseThrow(() -> new IllegalStateException("AccessorProperty must define!"));

			Consumer<StringBuilder> prefixAppender = StringUtils.isNotEmpty(property.value())
			                                         ? builder -> builder.append(property.value())
			                                                             .append('-')
			                                         : VOID_APPENDER;
			Consumer<StringBuilder> suffixAppender = StringUtils.isNotEmpty(property.suffix())
			                                         ? builder -> builder.append('-')
			                                                             .append(property.suffix())
			                                         : VOID_APPENDER;

			Stream.of(type.getDeclaredFields())
			      .filter(field -> AttributePropertyAccessor.class.isAssignableFrom(field.getType()))
			      .forEach(field -> {
				      final String fieldName = field.getName();
				      final Class<?> componentType = ReflectAssist.getIndexedType(field.getGenericType(),
				                                                                  0);


				      final AttributePropertyAccessorImpl<?> created = new AttributePropertyAccessorImpl<>(componentType,
				                                                                                           field);

				      final String namePatternValue = property.pattern()
				                                              .encode(fieldName);

				      final String attributeKey = stringComposer().add(prefixAppender)
				                                                  .add(namePatternValue)
				                                                  .add(suffixAppender)
				                                                  .build();

				      final String attributeName = AnnotationAssist.refAnnotation(field,
				                                                                  AccessorDescription.class)
				                                                   .map(AccessorDescription::value)
				                                                   .orElse(stringComposer().add(fieldName)
				                                                                           .add('@')
				                                                                           .add(field.getDeclaringClass()
				                                                                                     .getSimpleName())
				                                                                           .add('(')
				                                                                           .add(componentType.getSimpleName())
				                                                                           .add(')')
				                                                                           .build());
				      final String attributeDescription = AnnotationAssist.refAnnotation(field,
				                                                                         AccessorDescription.class)
				                                                          .map(AccessorDescription::description)
				                                                          .orElse(attributeName);


				      created.setAttributeKey(attributeKey);
				      created.setAttributeName(attributeName);
				      created.setAttributeDescription(attributeDescription);

				      // register aliases
				      created.addAlias(attributeKey);
				      created.addAlias(namePatternValue);
				      created.addAlias(fieldName);
				      created.addAlias(attributeKey.toLowerCase(Locale.ROOT));
				      created.addAlias(attributeKey.toUpperCase(Locale.ROOT));
				      created.addAlias(namePatternValue.toLowerCase(Locale.ROOT));
				      created.addAlias(namePatternValue.toUpperCase(Locale.ROOT));
				      created.addAlias(fieldName.toLowerCase(Locale.ROOT));
				      created.addAlias(fieldName.toUpperCase(Locale.ROOT));

				      AnnotationAssist.refAnnotation(field,
				                                     AccessorAlias.class)
				                      .map(AccessorAlias::value)
				                      .map(Stream::of)
				                      .ifPresent(stream -> stream.forEach(created::addAlias));

				      this.accessors.add(created);


				      // field accessible change
				      field.setAccessible(true);

				      FunctionAssist.execute(() -> field.set(this,
				                                             created));
			      });


		}

		public Map<String, Object> getExtension(AttributeContext context) {
			final Map<String, Object> map = new LinkedHashMap<>();
			this.accessors.forEach(accessor -> accessor.getAttribute(context)
			                                           .ifPresent(value -> map.put(accessor.getAttributeKey(),
			                                                                       value)));

			return map;
		}

		public void loadExtension(AttributeContext context,
		                          Map<String, Object> otherMap) {
			if (null == otherMap || otherMap.isEmpty())
				return;
			otherMap.forEach((key, value) -> this.accessors.stream()
			                                               .filter(accessor -> accessor.match(key))
			                                               .findFirst()
			                                               .ifPresent(accessor -> accessor.setAttribute(context,
			                                                                                            value)));
			context.loadAttribute(otherMap);
		}

		public Map<String, Object> export(Map<String, Object> source) {
			Map<String, Object> created = new LinkedHashMap<>();

			this.accessors.forEach(accessor -> accessor.reference(source)
			                                           .ifPresent(value -> created.put(accessor.getAttributeKey(),
			                                                                           value)));

			return created;
		}

		public <T> T mappedInstance(final Class<T> type,
		                            final Map<String, Object> info) {
			final T instance = createInstance(type);

			this.accessors.forEach(accessor -> accessor.reference(info)
			                                           .ifPresent(value -> {
				                                           final String fieldName = accessor.getDeclaredField()
				                                                                            .getName();

				                                           if (updateInstanceValue(instance,
				                                                                   type,
				                                                                   fieldName,
				                                                                   value) && log.isTraceEnabled()) {
					                                           log.trace(StringAssist.format("[%s] update success with [%s]",
					                                                                         fieldName,
					                                                                         value));
				                                           }
			                                           }));

			return instance;
		}

		public Optional<AttributePropertyAccessor<?>> byName(String name) {
			return this.accessors.stream()
			                     .filter(accessor -> {
				                     if (NamePattern.predicate.test(accessor.getAttributeKey(),
				                                                    name)) {
					                     return true;
				                     }

				                     return accessor.aliases()
				                                    .anyMatch(alias -> alias.equalsIgnoreCase(name));
			                     })
			                     .findFirst();
		}
	}

	@Target({ElementType.FIELD})
	@Retention(RetentionPolicy.RUNTIME)
	@interface AccessorDescription {
		String value();

		String description() default "";
	}

	@Target({ElementType.FIELD})
	@Retention(RetentionPolicy.RUNTIME)
	@interface AccessorAlias {
		String[] value();
	}

	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface AccessorCreateWhenEmpty {
		Class<?> value() default Void.class;
	}

	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@interface AccessorContextSupplier {
		Class<? extends Supplier<?>> value();
	}

	class CommonAccessor {
		final ConditionedLock lock = new ConditionedLock();
		final Map<Thread, AttributeContextImpl> thread_scope_map = new HashMap<>();
		final Map<Class<?>, Supplier<?>> contextSupplierCache = new HashMap<>();
		final Map<Class<?>, Method> ofMethodCache = new HashMap<>();
		final Map<String, Field> attributeNameCache = new HashMap<>();

		AttributeContextImpl get0() {
			return this.lock.tran(() -> this.thread_scope_map.computeIfAbsent(Thread.currentThread(),
			                                                                  thread -> new AttributeContextImpl()));
		}

		public AttributeContext threadContext() {
			return this.get0();
		}

		/**
		 * clear all thread attribute map. must call shutdown application
		 */
		void cleanThreadAttribute() {
			this.lock.execute(() -> {
				final List<Thread> threads = new ArrayList<>();
				this.thread_scope_map.keySet()
				                     .stream()
				                     .filter(thread -> !thread.isAlive())
				                     .forEach(threads::add);
				threads.forEach(thread -> Optional.ofNullable(this.thread_scope_map.remove(thread))
				                                  .ifPresent(AttributeContextImpl::truncate));
			});
		}


		/**
		 * clear current thread attribute map
		 */
		void clearAttributes() {
			Optional.ofNullable(this.thread_scope_map.get(Thread.currentThread()))
			        .ifPresent(AttributeContextImpl::truncate);
		}

		void buildAttributeAccessor(final AttributeAccessorContainer container) {
			this.lock.execute(() -> this.buildAttributeAccessor0(container));
		}

		void buildAttributeAccessor0(final AttributeAccessorContainer container) {
			final Class<?> type = container.getClass();
			// initialize attribute accessor
			Stream.of(type.getDeclaredFields())
			      .filter(field -> AttributeAccessor.class.isAssignableFrom(field.getType()))
			      .forEach(field -> {
				      final String fieldName = field.getName();
				      final Class<?> rawType = field.getType();
				      final Class<?> componentType = ReflectAssist.getIndexedType(field.getGenericType(),
				                                                                  0);

				      final Method ofMethod = this.ofMethodCache.computeIfAbsent(rawType,
				                                                                 candidate -> FunctionAssist.supply(() -> candidate.getMethod("of",
				                                                                                                                              Class.class,
				                                                                                                                              Field.class)));

				      final Object createdInstance = FunctionAssist.supply(() -> ofMethod.invoke(null,
				                                                                                 componentType,
				                                                                                 field));

				      if (createdInstance == null) {
					      throw new CrashedException("of instance is null?");
				      }
				      if (!(createdInstance instanceof AttributeAccessorAdapter)) {
					      throw new CrashedException("of instance is not a AttributeAccessorAdapter?");
				      }

				      final String namePatternValue = NamePattern.kebab.encode(fieldName);

				      final String attributeKey = stringComposer().add(namePatternValue)
				                                                  .add('@')
				                                                  .add(type.getSimpleName())
				                                                  .build();
				      final String attributeName = stringComposer().add(fieldName)
				                                                   .add('@')
				                                                   .add(field.getDeclaringClass()
				                                                             .getSimpleName())
				                                                   .add('(')
				                                                   .add(componentType.getSimpleName())
				                                                   .add(')')
				                                                   .build();
				      final String attributeDescription = AnnotationAssist.refAnnotation(field,
				                                                                         AccessorDescription.class)
				                                                          .map(AccessorDescription::description)
				                                                          .orElse(attributeName);

				      Optional<ErrorHandleSupplier<?>> supplier = AnnotationAssist.refAnnotation(field,
				                                                                                 AccessorCreateWhenEmpty.class)
				                                                                  .map(candidate -> candidate.value() == Void.class
				                                                                                    ? componentType
				                                                                                    : candidate.value())
				                                                                  .filter(candidate -> {
					                                                                  if (!componentType.isAssignableFrom(candidate)) {
						                                                                  log.error(StringAssist.format("[%s] create type is invalid !!!!!!!!!!!!!!!!!!!!!!!!!!!",
						                                                                                                field.toString()));
						                                                                  return false;
					                                                                  }
					                                                                  return true;
				                                                                  })
				                                                                  .map(createType -> () -> FunctionAssist.supply(() -> createType.getConstructor()
				                                                                                                                                 .newInstance()));

				      final Supplier<?> contextSupplier = this.contextSupplierCache.computeIfAbsent(rawType,
				                                                                                    key -> AnnotationAssist.refAnnotation(key,
				                                                                                                                          AccessorContextSupplier.class)
				                                                                                                           .map(AccessorContextSupplier::value)
				                                                                                                           .map(supplierType -> FunctionAssist.supply(() -> supplierType.getConstructor()
				                                                                                                                                                                        .newInstance()))
				                                                                                                           .orElse(null));

				      // check for duplicate attribute name
				      final Field beforeField = this.attributeNameCache.get(attributeKey);

				      if (null != beforeField) {
					      throw new CrashedException(StringAssist.format("[%s] is duplicate in [%s] vs. [%s]",
					                                                     attributeKey,
					                                                     beforeField,
					                                                     field));
				      }

				      // register attribute name to cache for duplicate check
				      this.attributeNameCache.put(attributeKey,
				                                  field);

				      final AttributeAccessorAdapter<?, ?, ?> created = (AttributeAccessorAdapter<?, ?, ?>) createdInstance;

				      created.setAttributeKey(attributeKey);
				      created.setAttributeName(attributeName);
				      created.setAttributeDescription(attributeDescription);
				      created.setContextSupplierAnonymous(contextSupplier);

				      supplier.ifPresent(created::supplierAnonymous);

				      container.appendAccessor(created);


				      // field accessible change
				      field.setAccessible(true);

				      FunctionAssist.execute(() -> field.set(container,
				                                             createdInstance));
			      });

		}

		private CommonAccessor() {
		}
	}

	Supplier<String> event_message_fn = () -> ConstValues.BLANK_STRING;

	enum AttributeClearType {
		CLOSE {
			@Override
			public void clear(Object instance) {
				ObjectFinalizer.closeObject(instance);
			}
		},
		DESTROY {
			@Override
			public void clear(Object instance) {
				ObjectFinalizer.destroy(instance);
			}
		},
		NONE;

		public void clear(Object instance) {
			//
		}
	}

	interface StreamExportable {
		void write(OutputStream out) throws IOException;
	}

	interface ArgumentDepend {
		default void check(Object[] objects) {
			final int size = objects == null
			                 ? 0
			                 : objects.length;

			final Class<?>[] argsTypes = this.getArgsTypes();

			for (int i = 0; i < size; i++) {
				if (argsTypes[i].equals(Void.class)) {
					break;
				}
				if (objects[i] != null && !argsTypes[i].isInstance(objects[i])) {//
					throw new CrashedException(StringAssist.format("require : %s but : %s @ %s",
					                                               argsTypes[i].getSimpleName(),
					                                               objects[i].getClass()
					                                                         .getSimpleName(),
					                                               i));
				}
			}
		}

		Class<?>[] getArgsTypes();
	}

	// ---------------------------------------------------------------------
	// Section exceptions
	// ---------------------------------------------------------------------
	static void checkRequiredType(final Class<?> type,
	                              final Object object) {
		if (Objects.isNull(object)) {
			throw new NullPointerException("compare object is null!");
		}
		if (!type.isInstance(object)) {
			throw new TypeMissMatchException(type,
			                                 object);
		}
	}

	@Getter
	class TypeMissMatchException
			extends RuntimeException {
		private static final long serialVersionUID = -8157330859296793159L;
		final Class<?> type;

		@Getter
		final transient Object object;

		public TypeMissMatchException(final Class<?> type,
		                              final Object object) {
			super(StringAssist.format("[%s] is miss matched type of [%s]",
			                          object.getClass()
			                                .getName(),
			                          type.getName()));
			this.type = type;
			this.object = object;
		}
	}

	class EntryNotFoundException
			extends RuntimeException {
		private static final long serialVersionUID = -7202604646941826477L;

		public EntryNotFoundException(String identity,
		                              String domain) {
			super(StringAssist.format("[%s] not found in [%s]",
			                          identity,
			                          domain));
		}

		public EntryNotFoundException(String identity,
		                              String domain,
		                              String subDomain) {
			super(StringAssist.format("[%s]@[%s] not found in [%s]",
			                          identity,
			                          subDomain,
			                          domain));
		}
	}

	class TooManyEntryException
			extends RuntimeException {
		private static final long serialVersionUID = 4467671575134782818L;

		public TooManyEntryException(String identity,
		                             String domain) {
			super(StringAssist.format("[%s] too many in [%s]",
			                          identity,
			                          domain));
		}

		public TooManyEntryException(String identity,
		                             String domain,
		                             String subDomain) {
			super(StringAssist.format("[%s]@[%s] too many in [%s]",
			                          identity,
			                          subDomain,
			                          domain));
		}
	}

	@Getter
	class EntryDuplicatedException
			extends RuntimeException {
		private static final long serialVersionUID = -2224224406310205940L;

		final String identity;

		public EntryDuplicatedException(String identity,
		                                String domain) {
			super(StringAssist.format("[%s] duplicated in [%s]",
			                          identity,
			                          domain));
			this.identity = identity;
		}

		public EntryDuplicatedException(String identity,
		                                String domain,
		                                String subDomain) {
			super(StringAssist.format("[%s]@[%s] duplicated in [%s]",
			                          identity,
			                          subDomain,
			                          domain));
			this.identity = identity;
		}
	}

	@Getter
	class RecursiveReferencedException
			extends RuntimeException {
		final String identity;
		@Getter
		final String domain;

		public RecursiveReferencedException(String identity,
		                                    String domain) {
			super(StringAssist.format("[%s] is recursive in [%s]",
			                          identity,
			                          domain));
			this.identity = identity;
			this.domain = domain;
		}

		public RecursiveReferencedException(String identity,
		                                    Iterable<? extends CharSequence> holder) {
			this(identity,
			     String.join(",",
			                 holder));
		}
	}

	@Getter
	class PayloadException
			extends RuntimeException {
		private static final long serialVersionUID = -4120656641868613638L;
		final Object payload;

		public PayloadException(String message,
		                        Object payload) {
			super(message);
			this.payload = payload;
		}
	}

	class ValidateFailException
			extends PayloadException {
		private static final long serialVersionUID = 5316964089574605143L;

		public ValidateFailException(String message,
		                             Object payload) {
			super(message,
			      payload);
		}
	}

	class OperationTimeoutException
			extends RuntimeException {
		public static final OperationTimeoutException instance = new OperationTimeoutException();

		private OperationTimeoutException() {
			super("Default");
		}
	}

	class OperationCanceledException
			extends RuntimeException {
		public static final OperationCanceledException instance = new OperationCanceledException();

		private OperationCanceledException() {
			super("Default");
		}
	}

	class ResourceExhaustException
			extends RuntimeException {
		private static final long serialVersionUID = 8109237524296689457L;

		public ResourceExhaustException(String message) {
			super(message);
		}
	}
}
