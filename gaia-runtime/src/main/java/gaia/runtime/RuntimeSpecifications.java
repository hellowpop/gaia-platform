package gaia.runtime;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import gaia.common.EventAssist.TypedEvent;
import gaia.common.EventAssist.TypedEventListener;
import gaia.common.EventAssist.TypedEventListenerContainer;
import gaia.common.FunctionAssist;
import gaia.common.GWCommon.AttributeAccessorContainer;
import gaia.common.GWCommon.AttributeContext;
import gaia.common.GWCommon.AttributeContextAccessor;
import gaia.common.GWCommon.EntryNotFoundException;
import gaia.common.GWCommon.TooManyEntryException;
import gaia.common.GWCommonModels.NamespacePackage;
import gaia.common.JacksonAssist.JacksonMapper;
import gaia.common.PredicateAssist.PredicateBuilder;
import gaia.common.ReflectAssist;
import gaia.common.ReflectAssist.TypedLoader;
import gaia.common.ResourceAssist;
import gaia.common.ResourceAssist.StringComposer;
import gaia.common.StringAssist.NamePattern;

public interface RuntimeSpecifications {
	@Setter
	@Getter
	@NoArgsConstructor
	@JsonInclude(Include.NON_NULL)
	class RuntimeConfigure {
		List<String> scanPackage;
		List<Class<?>> triggerComponents;
		List<Class<?>> scanComponents;
		List<NamespacePackage> services;

		public Stream<Class<?>> scanComponents() {
			return null == this.scanComponents
			       ? Stream.empty()
			       : this.scanComponents.stream();
		}

		public void addScanComponent(Class<?> componentType) {
			if (null == this.scanComponents) {
				this.scanComponents = new ArrayList<>();
			}
			this.scanComponents.add(componentType);
		}

		public Stream<Class<?>> triggers() {
			return null == this.triggerComponents
			       ? Stream.empty()
			       : this.triggerComponents.stream();
		}

		public void addTriggerComponent(Class<?> componentType) {
			if (null == this.triggerComponents) {
				this.triggerComponents = new ArrayList<>();
			}
			this.triggerComponents.add(componentType);
		}

		public void addScanPackage(String pkg) {
			if (null == this.scanPackage) {
				this.scanPackage = new ArrayList<>();
			}

			if (this.scanPackage.contains(pkg)) {
				return;
			}
			this.scanPackage.add(pkg);
		}

		public void addService(NamespacePackage namespacePackage) {
			if (null == this.services) {
				this.services = new ArrayList<>();
			}

			if (this.services.contains(namespacePackage)) {
				return;
			}

			this.services.add(namespacePackage);
		}

		public void merge(RuntimeConfigure other) {
			Optional.ofNullable(other.getScanPackage())
			        .ifPresent(list -> list.forEach(this::addScanPackage));
			Optional.ofNullable(other.getServices())
			        .ifPresent(env -> env.forEach(this::addService));
			Optional.ofNullable(other.getTriggerComponents())
			        .ifPresent(env -> env.forEach(this::addTriggerComponent));
		}
	}
	enum ComponentDefinitionSource {
		type, // define from class (prototype)
		instance, // define from instance
		reference, // define by factory pattern
		component
	}
	interface ComponentDefinition
			extends AttributeContext {
		ComponentDefinitionSource getSource();

		String getBeanName();

		Class<?> getType();

		Object getInstance();
		boolean isBoot();

		default boolean isLazy() {
			return !this.isBoot();
		}
		<A extends Annotation> Optional<A> refAnnotation(Class<A> annotationType);

		default boolean annotationPresent(Class<? extends Annotation> annotationType) {
			return this.refAnnotation(annotationType)
			           .isPresent();
		}

		default boolean isInterface() {
			return this.getType()
			           .isInterface();
		}

		default boolean isTypeOf(Class<?> ref) {
			return ref.isAssignableFrom(this.getType());
		}

		default String description() {
			return ResourceAssist.stringComposer()
			                     .self(this::description)
			                     .build();
		}

		default void description(StringComposer composer) {
			 composer.add(this.isBoot()
			             ? "(BOOT)"
			             : "(LAZY)")
			        .add('(')
			        .add(this.getSource())
			        .add(')')
			        .add("<<")
			        .add(this.getType()
			                 .getSimpleName())
			        .add(">>");
		}
	}

	interface RuntimeContext
			extends TypedEventListenerContainer,
			        TypedLoader {
		<T> T getBean(Class<T> type);

		<T> T getBean(String name,
		              Class<T> type);

		@Override
		<T> T load(Class<T> beanType);

		<T> T load(T bean);

		RuntimeConfigure getRuntimeConfigure();

		void setRuntimeConfigure(RuntimeConfigure configure);

		<T> Stream<T> findLabelProvidedBean(Class<T> type,
		                                    String value);

		<T> Stream<T> getBeansOfType(Class<T> type);

		/**
		 * @return stream of candidate classes
		 */
		Stream<ComponentDefinition> componentDefines();

		/**
		 * used in portable registry
		 *
		 * @param componentType target portable type
		 * @param consumer      customizer
		 */
		default <T> void loadTypedComponents(final Class<T> componentType,
		                                     final Consumer<T> consumer) {
			this.loadTypedDefinitions(componentType)
			    .map(ComponentDefinition::getInstance)
			    .map(componentType::cast)
			    .forEach(consumer);
		}

		/**
		 * use in interface type scan
		 *
		 * @param componentType abstract super type
		 */
		default Stream<ComponentDefinition> loadTypedDefinitions(final Class<?> componentType) {
			return this.componentDefines()
			           .filter(new TypeOfComponentDefinitionPredicate(componentType));
		}
		default Stream<ComponentDefinition> loadAnnotatedDefinitions(final Class<? extends Annotation> annotationType) {
			return this.componentDefines()
			           .filter(new AnnotatedComponentDefinitionPredicate(annotationType));
		}

		default Optional<Object> loadDefinitionInstance(final Class<?> componentType) {
			final List<ComponentDefinition> definitions = new ArrayList<>();
			this.loadTypedDefinitions(componentType)
			    .filter(predicateNoneAbstractDefinition)
			    .forEach(definitions::add);

			switch (definitions.size()) {
				case 0: {
					throw new EntryNotFoundException(componentType.getName(),
					                                 "context components");
				}

				case 1: {
					return Optional.of(definitions.get(0)
					                              .getInstance());
				}

				default: {
					// first single activated
					final List<ComponentDefinition> activated = definitions.stream()
					                                                       .filter(ComponentDefinition::isBoot)
					                                                       .collect(Collectors.toList());

					switch (activated.size()) {
						case 0: {
							// choice PrimaryComponent
							return definitions.stream()
							                  .filter(predicatePrimaryComponentDefinition)
							                  .findFirst()
							                  .map(ComponentDefinition::getInstance);
						}

						case 1: {
							return Optional.of(activated.get(0)
							                            .getInstance());
						}

						default: {
							throw new TooManyEntryException(componentType.getName(),
							                                "context components");
						}
					}
				}
			}

		}

		default void definitionEntryChanged() {
			this.definitionEntryChanged(false);
		}

		void definitionEntryChanged(boolean actual);

		void definitionBootConditionChanged();

		default ComponentDefinition getDefinition(Class<?> type) {
			return this.findDefinition(type)
			           .orElseThrow(() -> new EntryNotFoundException(type.getSimpleName(),
			                                                         RuntimeContext.class.getSimpleName()));
		}

		default Optional<ComponentDefinition> findDefinition(Class<?> type) {
			final List<ComponentDefinition> defines = this.getDefinitions(type);
			switch (defines.size()) {
				case 0: {
					break;
				}
				case 1: {
					return Optional.of(defines.get(0));
				}
				default: {
					throw new TooManyEntryException(type.getSimpleName(),
					                                RuntimeContext.class.getSimpleName());
				}
			}
			return Optional.empty();
		}

		default <T> void forActiveComponents(Class<T> type,
		                                     Consumer<T> consumer) {
			this.findDefinition(type)
			    .filter(ComponentDefinition::isBoot)
			    .map(ComponentDefinition::getInstance)
			    .map(type::cast)
			    .ifPresent(consumer);
		}

		ComponentDefinition findOrRegisterDefinition(final Class<?> type);

		default List<ComponentDefinition> getDefinitions(final List<Class<?>> types) {
			return null == types || types.isEmpty()
			       ? Collections.emptyList()
			       : types.stream()
			              .map(this::findOrRegisterDefinition)
			              .collect(Collectors.toList());
		}

		default Stream<ComponentDefinition> getDefinitionOfType(Class<?> type) {
			return this.componentDefines()
			           .filter(definition -> type.isAssignableFrom(definition.getType()));
		}

		List<ComponentDefinition> getDefinitions(Class<?> type);

		void singletons(BiConsumer<Class<?>, Object> consumer);

		default <T> void registerSingleton(T bean) {
			this.registerSingleton(beanNameFunction.apply(bean.getClass()
			                                                  .getSimpleName()),
			                       bean);
		}

		void registerSingleton(String name,
		                       Object bean);

		default RuntimeContext boot() {
			return this.boot(VOID_EVENT_LISTENER);
		}

		RuntimeContext boot(RuntimeEventListener aware);

		default void close() {
			this.close(VOID_EVENT_LISTENER);
		}

		void close(RuntimeEventListener aware);
	}
	class TypeOfComponentDefinitionPredicate
			implements Predicate<ComponentDefinition> {
		final Class<?> componentType;
		final Predicate<ComponentDefinition> predicate;

		public TypeOfComponentDefinitionPredicate(final Class<?> componentType) {
			this.componentType = componentType;
			this.predicate = predicateNoneAbstractDefinition.and(definition -> componentType.isAssignableFrom(definition.getType()));
		}

		@Override
		public boolean test(ComponentDefinition definition) {
			return this.predicate.test(definition);
		}
	}
	class AnnotatedComponentDefinitionPredicate
			implements Predicate<ComponentDefinition> {
		final Predicate<ComponentDefinition> predicate;

		public AnnotatedComponentDefinitionPredicate(final Class<? extends Annotation> annotationType) {
			this.predicate = predicateNoneAbstractDefinition.and(definition -> definition.getType()
			                                                                             .isAnnotationPresent(annotationType));
		}

		@Override
		public boolean test(ComponentDefinition definition) {
			return this.predicate.test(definition);
		}
	}
	interface RuntimeEventListener
			extends TypedEventListener<RuntimeEvent> {
		@Override
		default Class<RuntimeEvent> getEventType() {
			return RuntimeEvent.class;
		}
	}

	RuntimeEventListener VOID_EVENT_LISTENER = (event, attribute) -> {
	};

	enum RuntimeEvent
			implements TypedEvent<RuntimeContext> {
		boot_step_changed,
		definition_entry_changed;

		@Override
		public Object preHandle(RuntimeContext container,
		                        AttributeContext attribute) {
			accessorRuntime.runtime_context.set(attribute,
			                                    container);
			return this.onPreHandle(container,
			                        attribute);
		}

		Object onPreHandle(RuntimeContext container,
		                   AttributeContext attribute) {
			return null;
		}

		@Override
		public void postHandle(Object pre,
		                       RuntimeContext container,
		                       AttributeContext attribute,
		                       Object reason) {
			this.onPostHandle(container,
			                  attribute,
			                  reason);
		}

		void onPostHandle(RuntimeContext container,
		                  AttributeContext attribute,
		                  Object reason) {

		}
	}


	class RuntimeAccessor
			extends AttributeAccessorContainer {
		public AttributeContextAccessor<RuntimeContext> runtime_context;
		public AttributeContextAccessor<Throwable> cause;
		private RuntimeAccessor() {
		}
	}

	RuntimeAccessor accessorRuntime = new RuntimeAccessor();

	// ---------------------------------------------------------------------
	// Section predicates
	// ---------------------------------------------------------------------

	Predicate<Class<?>> predicateComponent = FunctionAssist.supply(() -> {
		PredicateBuilder<Class<?>> builder = new PredicateBuilder<>();

		builder.or(type -> type.isAnnotationPresent(RuntimeComponent.class));

		return builder.build();
	});
	Predicate<ComponentDefinition> predicateComponentDefinition = definition -> predicateComponent.test(definition.getType());
	Predicate<ComponentDefinition> predicateNoneAbstractDefinition = definition -> ReflectAssist.predicateNoneAbstract.test(definition.getType());
	Predicate<ComponentDefinition> predicatePrimaryComponentDefinition = predicateComponentDefinition.and(predicateNoneAbstractDefinition)
	                                                                                                 .and(definition -> definition.getType()
	                                                                                                                              .isAnnotationPresent(PrimaryComponent.class));
	// ---------------------------------------------------------------------
	// Section annotations
	// ---------------------------------------------------------------------
	@Target({ElementType.TYPE})
	@Retention(RetentionPolicy.RUNTIME)
	@interface RuntimeComponent {
		/**
		 * indicate boot time default load
		 *
		 * @return true : boot time instance create or lazy instance
		 */
		boolean value() default false;

		String name() default "";

		String property() default "";
	}
	@Target({ElementType.TYPE})
	@Retention(RetentionPolicy.RUNTIME)
	@interface PrimaryComponent {
	}

	// ---------------------------------------------------------------------
	// Section functions
	// ---------------------------------------------------------------------
	Function<String, String> beanNameFunction = NamePattern.kebab::encode;
}
