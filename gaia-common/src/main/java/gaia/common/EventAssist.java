package gaia.common;



import static gaia.common.FunctionAssist.supply;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import lombok.Getter;

import gaia.ConstValues;
import gaia.common.CollectionSpec.ExtendList;
import gaia.common.CollectionSpec.UniqueList;
import gaia.common.GWCommon.AttributeAccessorContainer;
import gaia.common.GWCommon.AttributeContext;
import gaia.common.GWCommon.AttributeContextAccessor;
import gaia.common.GWCommon.AttributeContextImpl;
import gaia.common.GWCommon.EntryDuplicatedException;
import gaia.common.GWCommon.EntryNotFoundException;
import gaia.common.GWCommon.Priority;
import gaia.common.GWCommon.PriorityType;
import gaia.common.ResourceAssist.ResourceContainer;
import gaia.common.StringAssist.NamePattern;

public interface EventAssist {
	Logger log = LoggerFactory.getLogger(EventAssist.class);

	boolean eventSyncMode = supply(() -> {
		final boolean sync = StringUtils.equalsIgnoreCase(System.getProperty("event.sync.mode",
		                                                                     "false"),
		                                                  "true");
		log.info(StringAssist.format("<<EVENT>> sync mode [%s]",
		                             sync));
		return sync;
	});

	class TypedEventAccessor
			extends AttributeAccessorContainer {
		public AttributeContextAccessor<Object> rejectReason;

		private TypedEventAccessor() {
		}
	}

	TypedEventAccessor accessorEvent = new TypedEventAccessor();

	Function<AnonymousEventListener, Class<?>> event_type_of = listener -> {
		if (listener instanceof TypedEventListener) {
			return ((TypedEventListener<?>) listener).getEventType();
		}

		return TypedEvent.class;
	};

	interface TypedEvent<T extends TypedEventListenerContainer> {
		default EventComposer shared(final T container) {
			return new EventComposer(this,
			                         container).pre(context -> {
				                                   // first reject reason reset
				                                   accessorEvent.rejectReason.unset(container);
				                                   return this.preHandle(container,
				                                                         context);
			                                   })
			                                   .post((context, pre) -> this.postHandle(pre,
			                                                                           container,
			                                                                           context,
			                                                                           accessorEvent.rejectReason.reference(container)
			                                                                                                     .orElse(null)));
		}

		default EventComposer dedicate(final T container) {
			return this.shared(container)
			           .dedicate();
		}

		default EventComposer with(final T container,
		                           final AttributeContext context) {
			return this.shared(container)
			           .with(context);
		}

		default EventComposer composer(final T container) {
			return this.shared(container)
			           .with(container);
		}

		/**
		 * event broadcast synchronous
		 *
		 * @param container listener container
		 */
		default void transfer(final T container) {
			this.composer(container)
			    .handle();
		}

		default void transfer(final T container,
		                      final AttributeContext context) {
			this.shared(container)
			    .with(context)
			    .handle();
		}

		default void trigger(final T container) {
			this.trigger(container,
			             NamePattern.kebab.encode(this.toString()));
		}

		default void trigger(final T container,
		                     final AttributeContext context) {
			this.shared(container)
			    .with(context)
			    .async(NamePattern.kebab.encode(this.toString()));
		}

		/**
		 * event broadcast asynchronous
		 *
		 * @param container   listener container
		 * @param triggerName trigger name
		 */
		default void trigger(T container,
		                     String triggerName) {
			this.composer(container)
			    .async(triggerName);
		}

		default Object preHandle(T container,
		                         AttributeContext context) {
			if (log.isTraceEnabled()) {
				log.trace(StringAssist.format("<<ET>> [%s] [%s]",
				                              TypedEvent.eventType(this),
				                              this.toString()));
			}
			return null;
		}

		default void postHandle(Object pre,
		                        T container,
		                        AttributeContext context,
		                        Object reason) {
		}

		static String eventType(TypedEvent<?> event) {
			return typeOf(event).getSimpleName();
		}

		static Class<?> typeOf(TypedEvent<?> event) {
			Class<?> type = event.getClass();
			String typeName = type.getSimpleName();
			if (StringUtils.isEmpty(typeName)) {
				do {
					type = type.getSuperclass();
					typeName = type.getSimpleName();
				} while (StringUtils.isEmpty(typeName));
			}

			return type;
		}
	}

	class AttributeContextContainer
			extends ResourceContainer<AttributeContext> {
		private AttributeContextContainer() {
			super();
		}

		@Override
		protected AttributeContext create() {
			return new AttributeContextEntry();
		}

		@Override
		protected boolean isValidForRecycle(AttributeContext instance) {
			instance.truncate();
			return super.isValidForRecycle(instance);
		}

		class AttributeContextEntry
				extends AttributeContextImpl {
			final AttributeContextContainer container;

			AttributeContextEntry() {
				this.container = AttributeContextContainer.this;
			}

			@Override
			public void dispose() {
				this.container.recycle(this);
			}
		}
	}

	AttributeContextContainer attribute_container = new AttributeContextContainer();

	AttributeContextAccessor<AsyncTaskStack> async_task_stack = AttributeContextAccessor.of(AsyncTaskStack.class,
	                                                                                        AsyncTaskStack::new);

	Function<AttributeContext, Object> VOID_FUNCTION = context -> null;

	BiConsumer<AttributeContext, Object> VOID_CONSUMER = (context, o) -> {
	};

	class EventComposer {
		final TypedEvent<?> event;
		final AnonymousEventListener listener;
		AttributeContext context;
		@Getter
		String name;
		Function<AttributeContext, Object> preExecutor = VOID_FUNCTION;
		BiConsumer<AttributeContext, Object> postExecutor = VOID_CONSUMER;

		public EventComposer(final TypedEvent<?> event,
		                     final AnonymousEventListener listener) {
			this.event = event;
			this.listener = listener;
			this.context = GWCommon.threadSharedContextSupplier.get();
		}

		public EventComposer dedicate() {
			this.context = attribute_container.getResource();
			return this;
		}

		public EventComposer with(final AttributeContext context) {
			if (null == context) {
				throw new IllegalStateException("context cannot be null!");
			}
			this.context = context;
			return this;
		}

		public EventComposer pre(final Function<AttributeContext, Object> supplier) {
			if (null == supplier) {
				throw new IllegalStateException("runnable cannot be null!");
			}
			this.preExecutor = supplier;
			return this;
		}

		public EventComposer post(final BiConsumer<AttributeContext, Object> consumer) {
			if (null == consumer) {
				throw new IllegalStateException("runnable cannot be null!");
			}
			this.postExecutor = consumer;
			return this;
		}

		public <T> EventComposer set(final AttributeContextAccessor<T> property,
		                             final T value) {
			property.set(this.context,
			             value);

			return this;
		}

		public <T> EventComposer unset(final AttributeContextAccessor<T> property) {
			property.unset(this.context);
			return this;
		}

		private void handle0() {
			final Object pre = this.preExecutor.apply(this.context);
			try {
				this.listener.handleEvent(this.event,
				                          this.context);
			}
			catch (Throwable throwable) {
				if (log.isDebugEnabled()) {
					log.error("event broadcast error",
					          throwable);
				}
				throw ExceptionAssist.wrap("event broadcast error",
				                           throwable);
			}
			finally {
				this.postExecutor.accept(this.context,
				                         pre);
			}
		}

		public void handle() {
			this.handle0();
			if (!(this.context instanceof AnonymousEventListener)) {
				this.context.dispose();
			}
		}

		public void async(String name) {
			this.name = name;
			async_task_stack.take()
			                .execute(this);
		}

		public AttributeContext transfer() {
			this.handle0();
			return this.context;
		}
	}

	class AsyncTaskStack {
		final LinkedList<EventComposer> stack = new LinkedList<>();
		final AtomicBoolean running = new AtomicBoolean(false);

		AsyncTaskStack() {
		}

		void execute(final EventComposer composer) {
			if (eventSyncMode) {
				composer.handle();
				return;
			}
			if (this.running.get()) {
				this.stack.addLast(composer);
				if (log.isTraceEnabled()) {
					log.trace(StringAssist.format("<<ET>> stack push [%s] size [%s]",
					                              composer.getName(),
					                              this.stack.size()));
				}
				return;
			}

			// check run twice
			if (!this.running.compareAndSet(false,
			                                true)) {
				if (log.isTraceEnabled()) {
					log.trace("<<ET>> exit twice",
					          new Throwable("TRACE"));
				}
				throw new ConstValues.CrashedException("exit twice?");
			}
			// process registered
			if (log.isTraceEnabled()) {
				log.trace(StringAssist.format("<<ET>> [%s] master event stack execute start",
				                              composer.getName()));
			}

			try {
				this.running.set(true);

				composer.handle();

				EventComposer entry = this.popupEntry();

				if (null != entry) {
					do {
						entry.handle();
						if (log.isTraceEnabled()) {
							log.trace(StringAssist.format("<<ET>> stack handled [%s]",
							                              entry.name));
						}
						entry = this.popupEntry();
					} while (null != entry);
				}
			}
			catch (Exception throwable) {
				// error raised, so after execution is canceled
				this.stack.clear();
				throw throwable;
			}
			finally {
				this.running.set(false);
			}

			if (log.isTraceEnabled()) {
				log.trace(StringAssist.format("<<ET>> [%s] master event stack execute finished",
				                              composer.getName()));
			}
		}

		EventComposer popupEntry() {
			if (this.stack.isEmpty()) {
				return null;
			}

			EventComposer entry = this.stack.removeFirst();

			if (log.isTraceEnabled()) {
				log.trace(StringAssist.format("<<ET>> stack popup [%s] size [%s]",
				                              entry.name,
				                              this.stack.size()));
			}

			return entry;
		}
	}

	interface AnonymousEventListener
			extends Priority {
		void handleEvent(TypedEvent<?> event,
		                 AttributeContext context);
	}

	interface TypedEventListener<T extends TypedEvent<?>>
			extends AnonymousEventListener {
		Class<T> getEventType();

		@Override
		default void handleEvent(final TypedEvent<?> event,
		                         final AttributeContext context) {
			this.doHandleEvent(this.getEventType()
			                       .cast(event),
			                   context);
		}

		void doHandleEvent(T event,
		                   AttributeContext attribute);
	}

	interface TypedEventListenerContainer
			extends AnonymousEventListener,
			        AttributeContext {
		void registerTrigger(TypedEventListenerContainer container);

		void deregisterTrigger(TypedEventListenerContainer container);

		void registerListener(AnonymousEventListener listener);

		void registerListeners(AnonymousEventListener... listeners);

		void registerListeners(Stream<AnonymousEventListener> listeners);

		void deregisterListener(AnonymousEventListener listener);

		void deregisterListeners(AnonymousEventListener... listeners);

		void deregisterListeners(Stream<AnonymousEventListener> listeners);

		Map<String, Object> hierarchy();

		void buildHierarchy();
	}

	@Getter
	class TypedEventListenerSet
			extends ExtendList<AnonymousEventListener>
			implements AnonymousEventListener {
		final Class<?> eventType;

		TypedEventListenerSet(Class<?> eventType) {
			super(PriorityType.ASC);
			this.eventType = eventType;
		}

		@Override
		public void handleEvent(final TypedEvent<?> event,
		                        final AttributeContext attribute) {
			this.forEach(listener -> listener.handleEvent(event,
			                                              attribute));
		}
	}

	@Target({ElementType.METHOD})
	@Retention(RetentionPolicy.RUNTIME)
	@interface OnTypedEvent {
		/**
		 * execute time
		 *
		 * @return true : before , false : after
		 */
		int value() default Priority.NORMAL_PRECEDENCE;
	}

	class TypedEventListenEntry<T extends TypedEvent<?>>
			implements TypedEventListener<T> {
		final TypedEventListenerContainer container;
		final Method method;
		@Getter
		final Class<T> eventType;
		@Getter
		final int priority;

		@SuppressWarnings("unchecked")
		TypedEventListenEntry(final TypedEventListenerContainer container,
		                      final Method method) {
			this.container = container;
			this.priority = Optional.ofNullable(method.getAnnotation(OnTypedEvent.class))
			                        .orElseThrow(() -> new Error("annotation is null?"))
			                        .value();
			this.method = method;
			this.eventType = (Class<T>) method.getParameterTypes()[0];
			this.method.setAccessible(true);
			if (!Modifier.isPrivate(method.getModifiers())) {
				log.warn(StringAssist.format("recommend [%s@%s] declare private for override side effect!",
				                             method.getName(),
				                             method.getDeclaringClass()
				                                   .getName()));
			}
		}

		@Override
		public void doHandleEvent(T event,
		                          AttributeContext attribute) {
			try {
				this.method.invoke(this.container,
				                   event,
				                   attribute);
			}
			catch (Throwable cause) {
				if (log.isDebugEnabled()) {
					log.error(StringAssist.format("container event method execute error [%s]@[%s]",
					                              this.method.getName(),
					                              this.method.getDeclaringClass()
					                                         .getName()),
					          cause);
				}

				throw ExceptionAssist.wrap("event method execute error",
				                           cause);
			}
		}

		static final Map<Class<?>, List<Method>> methods = new ConcurrentHashMap<>();

		static void of(TypedEventListenerContainer container) {
			final List<Method> target = methods.computeIfAbsent(container.getClass(),
			                                                    type -> {
				                                                    List<Method> entries = new ArrayList<>();

				                                                    AnnotationAssist.scanMemberMethod(type,
				                                                                                      OnTypedEvent.class,
				                                                                                      entries::add);

				                                                    return entries.isEmpty()
				                                                           ? Collections.emptyList()
				                                                           : entries;
			                                                    });
			if (target.isEmpty()) {
				return;
			}

			target.stream()
			      .map(method -> new TypedEventListenEntry<>(container,
			                                                 method))
			      .forEach(container::registerListener);
		}
	}

	class TypedEventListenerContainerBase
			extends AttributeContextImpl
			implements TypedEventListenerContainer {
		protected final UniqueList<AnonymousEventListener> listeners = new UniqueList<>();
		final Map<Class<?>, TypedEventListenerSet> types = new HashMap<>();
		final AtomicReference<Map<Class<?>, TypedEventListenerSet>> typeHolder = new AtomicReference<>(Collections.emptyMap());
		final AtomicBoolean listenerChanged = new AtomicBoolean();
		protected final UniqueList<TypedEventListenerContainer> trigger = new UniqueList<>();
		final AtomicReference<List<TypedEventListenerContainer>> triggerHolder = new AtomicReference<>(Collections.emptyList());
		final AtomicBoolean triggerChanged = new AtomicBoolean();

		protected TypedEventListenerContainerBase() {
			super();
			TypedEventListenEntry.of(this);
		}

		@Override
		public Map<String, Object> hierarchy() {
			return this.lock.tran(() -> {
				final Map<String, Object> map = new HashMap<>();

				map.put("listener",
				        this.listeners.stream()
				                      .map(i -> i.getClass()
				                                 .getName())
				                      .collect(Collectors.toList()));
				map.put("trigger",
				        this.trigger.stream()
				                    .map(TypedEventListenerContainer::hierarchy)
				                    .collect(Collectors.toList()));

				return map;
			});
		}

		@Override
		public void buildHierarchy() {
			this.lock.execute(() -> {
				if (this.listenerChanged.compareAndSet(true,
				                                       false)) {
					this.reorganizeListener();
				}

				if (this.triggerChanged.compareAndSet(true,
				                                      false)) {
					this.triggerHolder.set(new ArrayList<>(this.trigger));
				}
			});
		}

		@Override
		public void handleEvent(final TypedEvent<?> event,
		                        final AttributeContext attribute) {
			this.touchListeners(event)
			    .ifPresent(set -> set.handleEvent(event,
			                                      attribute));

			this.touchTriggers()
			    .forEach(container -> container.handleEvent(event,
			                                                attribute));
		}

		protected void triggerChanged() {
			this.triggerChanged.set(true);
		}

		@Override
		public void registerTrigger(final TypedEventListenerContainer container) {
			this.lock.execute(() -> {
				this.trigger.add(container);
				this.triggerChanged();
			});
		}

		@Override
		public void deregisterTrigger(final TypedEventListenerContainer container) {
			this.lock.execute(() -> {
				this.trigger.remove(container);
				this.triggerChanged();
			});
		}

		protected List<TypedEventListenerContainer> touchTriggers() {
			this.buildHierarchy();

			return this.triggerHolder.get();
		}

		protected void listenerChanged() {
			this.listenerChanged.set(true);
		}

		protected Optional<TypedEventListenerSet> touchListeners(TypedEvent<?> event) {
			this.buildHierarchy();

			final Class<?> eventType = TypedEvent.typeOf(event);

			final TypedEventListenerSet set = this.typeHolder.get()
			                                                 .get(eventType);

			if (log.isTraceEnabled() && null != set) {
				log.trace(StringAssist.format("<<ET>> [%s(%s)] matched [%s]",
				                              eventType.getSimpleName(),
				                              event.toString(),
				                              set.stream()
				                                 .map(elm -> elm.getClass()
				                                                .getSimpleName())
				                                 .collect(Collectors.joining(","))));
			}


			return Optional.ofNullable(set);
		}

		@Override
		public void registerListener(final AnonymousEventListener listener) {
			this.lock.execute(() -> {
				this.doRegisterListener(listener);
				this.listenerChanged();
			});
		}

		@Override
		public void registerListeners(final AnonymousEventListener... listeners) {
			this.lock.execute(() -> {
				for (AnonymousEventListener element : listeners) {
					this.doRegisterListener(element);
				}
				this.listenerChanged();
			});
		}

		@Override
		public void registerListeners(final Stream<AnonymousEventListener> listeners) {
			this.lock.execute(() -> {
				listeners.forEach(this::doRegisterListener);
				this.listenerChanged();
			});
		}

		protected void doRegisterListener(final AnonymousEventListener listener) {
			if (!this.listeners.add(listener)) {
				throw new EntryDuplicatedException(listener.toString(),
				                                   "session listener context");
			}
		}

		@Override
		public void deregisterListener(final AnonymousEventListener listener) {
			this.lock.execute(() -> {
				this.deregisterListener0(listener);
				this.listenerChanged();
			});
		}

		@Override
		public void deregisterListeners(final AnonymousEventListener... listeners) {
			this.lock.execute(() -> {
				for (AnonymousEventListener element : listeners) {
					this.deregisterListener0(element);
				}
				this.listenerChanged();
			});
		}

		@Override
		public void deregisterListeners(final Stream<AnonymousEventListener> listeners) {
			this.lock.execute(() -> {
				listeners.forEach(this::deregisterListener0);
				this.listenerChanged();
			});
		}

		private void deregisterListener0(final AnonymousEventListener listener) {
			if (!this.listeners.remove(listener)) {
				throw new EntryNotFoundException(listener.toString(),
				                                 "event listener context");
			}
		}

		protected void reorganizeListener() {
			this.types.clear();

			// detect distinct event type
			this.listeners.forEach(listener -> Optional.ofNullable(event_type_of.apply(listener))
			                                           .filter(Class::isEnum)
			                                           .ifPresent(eventType -> this.types.computeIfAbsent(eventType,
			                                                                                              TypedEventListenerSet::new)));

			this.types.forEach((type, holder) -> this.listeners.stream()
			                                                   .filter(listener -> event_type_of.apply(listener)
			                                                                                    .isAssignableFrom(type))
			                                                   .forEach(holder::add));

			this.typeHolder.set(new HashMap<>(this.types));
		}
	}
}
