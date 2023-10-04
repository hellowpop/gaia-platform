package gaia.common;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gaia.ConstValues;
import gaia.ConstValues.CrashedException;
import gaia.common.ConcurrentAssist.ConditionedLock;
import gaia.common.FunctionAssist.ErrorHandleFunction;
import gaia.common.GWCommon.SingleTonInstance;
import gaia.common.PredicateAssist.PredicateBuilder;


/**
 * @author dragon
 * @since 2022. 03. 22.
 */
public interface ReflectAssist {
	Logger log = LoggerFactory.getLogger(ReflectAssist.class);

	Predicate<Field> predicateStaticField = field -> Modifier.isStatic(field.getModifiers());
	Predicate<Field> predicateNoneStaticField = predicateStaticField.negate();
	Predicate<Field> predicateTransientField = field -> Modifier.isTransient(field.getModifiers());
	Predicate<Field> predicateNoneTransientField = predicateTransientField.negate();

	Predicate<Class<?>> predicateAbstract = FunctionAssist.supply(() -> {
		PredicateBuilder<Class<?>> builder = new PredicateBuilder<>();

		builder.or(Class::isInterface);
		builder.or(type -> Modifier.isAbstract(type.getModifiers()));

		return builder.build();
	});

	Predicate<Class<?>> predicateNoneAbstract = predicateAbstract.negate();

	static Predicate<Class<?>> predicateOf(Class<?> superType) {
		return new PredicateBuilder<>(superType::isAssignableFrom).negateAnd(type -> Modifier.isAbstract(type.getModifiers()))
		                                                          .build();
	}

	abstract class InstanceRegistry<T> {
		final ConditionedLock lock = new ConditionedLock();
		final Class<T> type;
		final Map<Class<?>, T> hash;
		final Map<String, Class<?>> classes;

		@SuppressWarnings("unchecked")
		public InstanceRegistry() {
			super();
			this.type = (Class<T>) ReflectAssist.getGenericParameter(this.getClass(),
			                                                         InstanceRegistry.class,
			                                                         "T");
			this.hash = new HashMap<>();
			this.classes = new HashMap<>();
		}

		@SuppressWarnings("unchecked")
		public final T reference(String name) {
			final Class<?> candidate = this.lock.tran(() -> this.classes.computeIfAbsent(name,
			                                                                             key -> {
				                                                                             Class<?> refType = ClassScanComponent.forName(key);
				                                                                             if (this.type.isAssignableFrom(refType)) {
					                                                                             this.check(name,
					                                                                                        refType);
					                                                                             return refType;
				                                                                             }
				                                                                             throw new IllegalArgumentException(StringAssist.format("[%s] is not a sub class of [%s]",
				                                                                                                                                    key,
				                                                                                                                                    this.type));
			                                                                             }));

			return candidate.isAnnotationPresent(SingleTonInstance.class)
			       ? this.lock.tran(() -> this.hash.computeIfAbsent(candidate,
			                                                        key -> (T) ReflectAssist.createInstance(candidate)))
			       : (T) ReflectAssist.createInstance(candidate);
		}

		protected void check(String key,
		                     Class<?> origin) {
		}
	}

	abstract class ReflectAssistMethod {
		static void $scanDeclaredFields(final Class<?> type,
		                                final Consumer<Field> list) {
			if (Objects.isNull(type) || type.getName()
			                                .startsWith("java")) {
				return;
			}

			for (Field declaredField : type.getDeclaredFields()) {
				list.accept(declaredField);
			}

			$scanDeclaredFields(type.getSuperclass(),
			                    list);
		}

		static final Predicate<Type> predicateParameterizedType = type -> type instanceof ParameterizedType;

		static Optional<Class<?>> findIndexedType(Type type,
		                                          int index) {
			return Optional.of(type)
			               .filter(predicateParameterizedType)
			               .map(candidate -> (ParameterizedType) candidate)
			               .map(ParameterizedType::getActualTypeArguments)
			               .filter(types -> types.length > index)
			               .map(types -> types[index])
			               .map(candidate -> {
				               if (candidate instanceof Class) {
					               return (Class<?>) candidate;
				               } else if (candidate instanceof ParameterizedType) {
					               return (Class<?>) ((ParameterizedType) candidate).getRawType();
				               }
				               return null;
			               });
		}

		static Class<?> getIndexedType(Type type,
		                               int index) throws IllegalArgumentException {
			return findIndexedType(type,
			                       index).orElseThrow(() -> new IllegalArgumentException("not found"));
		}

		static int getTypeVariableIndex(Class<?> type,
		                                String paramName) {
			TypeVariable<?>[] typeParams = type.getTypeParameters();
			for (int i = 0; i < typeParams.length; i++) {
				if (paramName.equals(typeParams[i].getName())) {
					return i;
				}
			}
			throw new IllegalArgumentException(StringAssist.format("cannot find parameter name[%s] : %s",
			                                                       type.getSimpleName(),
			                                                       paramName));
		}

		static Class<?> getIndexedTypeInHierarchy(final Type type,
		                                          final Class<?> superType,
		                                          final int index) throws IllegalArgumentException {
			return findIndexedTypeInHierarchy(type,
			                                  superType,
			                                  index).orElseThrow(() -> new IllegalArgumentException(StringAssist.format("genericSuperType not found [%s][%s][%s]",
			                                                                                                            type.toString(),
			                                                                                                            superType.getSimpleName(),
			                                                                                                            index)));
		}

		static Optional<Class<?>> findIndexedTypeInHierarchy(final Type type,
		                                                     final Class<?> superType,
		                                                     final String indexName) throws IllegalArgumentException {
			return findIndexedTypeInHierarchy(type,
			                                  superType,
			                                  getTypeVariableIndex(superType,
			                                                       indexName));
		}

		static Optional<Class<?>> findIndexedTypeInHierarchy(Type type,
		                                                     Class<?> superType,
		                                                     int index) throws IllegalArgumentException {
			if (!superType.isInterface()) {
				throw new IllegalArgumentException("indexed must interface super type!");
			}

			return findInInterfaceStructure0(type,
			                                 superType,
			                                 index);
		}

		/**
		 * find interface hierarchy
		 */
		static Optional<Class<?>> findInInterfaceStructure0(final Type targetClass,
		                                                    final Class<?> superClass,
		                                                    final int typeParamIndex) {

			final LinkedList<Type> genericSuperType = pathFindTo0(new LinkedList<>(),
			                                                      targetClass,
			                                                      superClass);

			if (null == genericSuperType) {
				if (log.isTraceEnabled()) {
					log.trace(StringAssist.format("genericSuperType not found [%s][%s][%s]",
					                              targetClass.toString(),
					                              superClass.getSimpleName(),
					                              typeParamIndex));
				}
				return Optional.empty();
			}

			final Optional<Type> actualParam = genericSuperType.stream()
			                                                   .filter(predicateParameterizedType)
			                                                   .map(candidate -> (ParameterizedType) candidate)
			                                                   .map(ParameterizedType::getActualTypeArguments)
			                                                   .filter(types -> types.length > typeParamIndex)
			                                                   .map(types -> types[typeParamIndex])
			                                                   .map(ReflectAssistMethod::mapActualParam)
			                                                   .filter(Objects::nonNull)
			                                                   .filter(type -> type instanceof Class)
			                                                   .findFirst();

			if (!actualParam.isPresent()) {
				if (log.isTraceEnabled()) {
					log.trace(StringAssist.format("parameter extract fail [%s][%s][%s]",
					                              targetClass.toString(),
					                              superClass.getSimpleName(),
					                              typeParamIndex));
				}
				return Optional.empty();
			}
			return Optional.of((Class<?>) actualParam.get());
		}

		static LinkedList<Type> pathFindTo0(final LinkedList<Type> hierarchy,
		                                    final Type targetType,
		                                    final Class<?> superClass) {
			if (null == targetType) {
				return null;
			}

			hierarchy.addFirst(targetType);

			Class<?> targetClass = (Class<?>) rawTypeOf(targetType);

			if (superClass.isInterface()) {
				// match by interfaces
				final Class<?>[] interfaces = targetClass.getInterfaces();
				final Type[] genericInterfaces = targetClass.getGenericInterfaces();

				final int index = ArrayAssist.findArrayIndex(interfaces,
				                                             superClass);

				if (index >= 0) {
					hierarchy.addFirst(genericInterfaces[index]);
					return hierarchy;
				}
				// lookup parent interfaces
				LinkedList<Type> lookup = Arrays.stream(genericInterfaces)
				                                .map(anInterface -> pathFindTo0(hierarchy,
				                                                                anInterface,
				                                                                superClass))
				                                .filter(Objects::nonNull)
				                                .findFirst()
				                                .orElseGet(() -> pathFindTo0(hierarchy,
				                                                             targetClass.getGenericSuperclass(),
				                                                             superClass));
				if (null != lookup) {
					return lookup;
				}
			} else if (targetClass.getSuperclass() == superClass) {
				// match by super class
				hierarchy.addFirst(targetClass.getGenericSuperclass());
				return hierarchy;
			}

			// lookup parent class
			LinkedList<Type> lookup = pathFindTo0(hierarchy,
			                                      targetClass.getSuperclass(),
			                                      superClass);

			if (null != lookup) {
				return lookup;
			}

			hierarchy.removeFirst();
			return null;
		}

		static Type mapActualParam(Type actualTypeParam) {
			Type filtered = actualTypeParam instanceof ParameterizedType
			                ? ((ParameterizedType) actualTypeParam).getRawType()
			                : actualTypeParam;

			if (actualTypeParam instanceof Class) {
				return actualTypeParam;
			}

			if (filtered instanceof GenericArrayType) {
				Type componentType = ((GenericArrayType) actualTypeParam).getGenericComponentType();
				if (componentType instanceof ParameterizedType) {
					componentType = ((ParameterizedType) componentType).getRawType();
				}
				if (componentType instanceof Class) {
					return Array.newInstance((Class<?>) componentType,
					                         0)
					            .getClass();
				}
			}
			if (filtered instanceof TypeVariable) {
				// Resolved type parameter points to another type parameter.
				TypeVariable<?> v = (TypeVariable<?>) actualTypeParam;
				if (!(v.getGenericDeclaration() instanceof Class)) {
					return Object.class;
				}
			}
			return null;
		}


		static Type rawTypeOf(Type type) {
			if (type instanceof ParameterizedType) {
				return rawTypeOf(((ParameterizedType) type).getRawType());
			}

			if (type instanceof Class) {
				return type;
			}

			throw new IllegalArgumentException(StringAssist.format("cannot extract raw type (%s)/(%s)",
			                                                       type.getTypeName(),
			                                                       type.getClass()
			                                                           .getName()));
		}

		static Optional<Class<?>> findParameterType(final Class<?> thisClass,
		                                            final Class<?> superClass,
		                                            final String paramName) {
			Class<?> parametrizedSuperclass = superClass;
			Class<?> currentClass = thisClass;
			String typeParamName = paramName;

			for (; ; ) {
				if (currentClass.getSuperclass() == parametrizedSuperclass) {
					int typeParamIndex = getTypeVariableIndex(currentClass.getSuperclass(),
					                                          typeParamName);

					if (typeParamIndex < 0) {
						return Optional.empty();
					}

					Type genericSuperType = currentClass.getGenericSuperclass();
					if (!(genericSuperType instanceof ParameterizedType)) {
						return Optional.of(Object.class);
					}

					Type[] actualTypeParams = ((ParameterizedType) genericSuperType).getActualTypeArguments();

					Type actualTypeParam = actualTypeParams[typeParamIndex];

					Optional<Class<?>> optionalClass = extractActualParam(actualTypeParam);

					if (optionalClass.isPresent()) {
						return optionalClass;
					}
					if (actualTypeParam instanceof ParameterizedType) {
						actualTypeParam = ((ParameterizedType) actualTypeParam).getRawType();
					}
					if (actualTypeParam instanceof TypeVariable) {
						// Resolved type parameter points to another type parameter.
						TypeVariable<?> v = (TypeVariable<?>) actualTypeParam;
						if (!(v.getGenericDeclaration() instanceof Class)) {
							return Optional.of(Object.class);
						}

						currentClass = thisClass;
						parametrizedSuperclass = (Class<?>) v.getGenericDeclaration();
						typeParamName = v.getName();
						if (parametrizedSuperclass.isAssignableFrom(thisClass)) {
							continue;
						}
						return Optional.of(Object.class);
					}

					return Optional.empty();
				}
				currentClass = currentClass.getSuperclass();
				if (currentClass == null) {
					return Optional.empty();
				}
			}
		}

		static Optional<Class<?>> extractActualParam(Type actualTypeParam) {
			if (actualTypeParam instanceof ParameterizedType) {
				actualTypeParam = ((ParameterizedType) actualTypeParam).getRawType();
			}
			if (actualTypeParam instanceof Class) {
				return Optional.of((Class<?>) actualTypeParam);
			}
			if (actualTypeParam instanceof GenericArrayType) {
				Type componentType = ((GenericArrayType) actualTypeParam).getGenericComponentType();
				if (componentType instanceof ParameterizedType) {
					componentType = ((ParameterizedType) componentType).getRawType();
				}
				if (componentType instanceof Class) {
					return Optional.of(Array.newInstance((Class<?>) componentType,
					                                     0)
					                        .getClass());
				}
			}
			if (actualTypeParam instanceof TypeVariable) {
				// Resolved type parameter points to another type parameter.
				TypeVariable<?> v = (TypeVariable<?>) actualTypeParam;
				if (!(v.getGenericDeclaration() instanceof Class)) {
					return Optional.of(Object.class);
				}
			}
			return Optional.empty();
		}
	}

	static Class<?> getRawType(Type type) {
		if (type instanceof Class) {
			return (Class<?>) type;
		}
		if (type instanceof ParameterizedType) {
			return getRawType(((ParameterizedType) type).getRawType());
		}
		throw new IllegalArgumentException("raw?".concat(type.toString()));
	}

	static Stream<Field> streamField(Class<?> type) {
		List<Field> list = new ArrayList<>();

		ReflectAssistMethod.$scanDeclaredFields(type,
		                                        list::add);

		return list.stream();
	}

	static Class<?> getIndexedType(final Type type,
	                               final int index) {
		return ReflectAssistMethod.getIndexedType(type,
		                                          index);
	}

	static Class<?> getCollectionComponentType(final Field field) {
		return getCollectionComponentType(field.getGenericType());
	}

	static Class<?> getCollectionComponentType(final Type type) {
		if (type instanceof ParameterizedType) {
			return ReflectAssistMethod.getIndexedType(type,
			                                          0);
		}

		return ReflectAssistMethod.getIndexedTypeInHierarchy(type,
		                                                     Collection.class,
		                                                     0);
	}

	static Class<?> getDictionaryKeyType(final Field field) {
		return getDictionaryKeyType(field.getGenericType());
	}

	static Class<?> getDictionaryKeyType(final Type type) {
		if (type instanceof ParameterizedType) {
			return ReflectAssistMethod.getIndexedType(type,
			                                          0);
		}

		return ReflectAssistMethod.getIndexedTypeInHierarchy(type,
		                                                     Map.class,
		                                                     0);
	}

	static Class<?> getDictionaryValueType(final Field field) {
		return getDictionaryValueType(field.getGenericType());
	}

	static Class<?> getDictionaryValueType(final Type type) {
		if (type instanceof ParameterizedType) {
			return ReflectAssistMethod.getIndexedType(type,
			                                          1);
		}

		return ReflectAssistMethod.getIndexedTypeInHierarchy(type,
		                                                     Map.class,
		                                                     1);
	}

	static Class<?> getGenericParameter(final Class<?> thisClass,
	                                    final Class<?> superClass,
	                                    final String paramName) {
		return findGenericParameter(thisClass,
		                            superClass,
		                            paramName).orElseThrow(() -> new IllegalArgumentException(StringAssist.format("cannot find generic parameter type [%s]->[%s] (%s)",
		                                                                                                          thisClass.getSimpleName(),
		                                                                                                          superClass.getSimpleName(),
		                                                                                                          paramName)));
	}

	static Optional<Class<?>> findGenericParameter(final Class<?> thisClass,
	                                               final Class<?> superClass,
	                                               final String paramName) {
		return superClass.isInterface()
		       ? ReflectAssistMethod.findIndexedTypeInHierarchy(thisClass,
		                                                        superClass,
		                                                        paramName)
		       : ReflectAssistMethod.findParameterType(thisClass,
		                                               superClass,
		                                               paramName);
	}

	// ---------------------------------------------------------------------
	// Section refactoring before
	// ---------------------------------------------------------------------

	static <T> T getFieldValue(Class<?> clazz,
	                           String name,
	                           Class<T> type) {
		try {
			return type.cast(clazz.getField(name)
			                      .get(null));
		}
		catch (NoSuchFieldException noSuchFieldException) {
			// ignore
		}
		catch (Exception throwable) {
			if (log.isTraceEnabled()) log.trace(StringAssist.format("[%s@%s] extract value error",
			                                                        name,
			                                                        clazz),
			                                    throwable);
		}
		return null;
	}

	static boolean setFieldValue(Object bean,
	                             Field field,
	                             Object value) {
		try {
			field.setAccessible(true);
			field.set(bean,
			          value);
			return true;
		}
		catch (IllegalArgumentException | IllegalAccessException e) {
			log.error(StringAssist.format("invoker attribute set up error [%s][%s][%s]",
			                              field.getName(),
			                              value.getClass()
			                                   .getName(),
			                              value),
			          e);
		}
		return false;
	}

	static void extractFilteredMemberFields(final Class<?> clazz,
	                                        final int include,
	                                        final int exclude,
	                                        final Consumer<Field> array) {
		if (clazz == null) {
			return;
		}

		final Class<?> superClazz = clazz.getSuperclass();

		if (superClazz != null) {
			extractFilteredMemberFields(superClazz,
			                            include,
			                            exclude,
			                            array);
		}

		if (clazz.getName()
		         .startsWith("java.") || clazz.getName()
		                                      .startsWith("javax.") || clazz.getName()
		                                                                    .startsWith("org.")) {
			return;
		}

		final Field[] fields = clazz.getDeclaredFields();
		for (final Field field : fields) {
			final int mod = field.getModifiers();
			if ((exclude & mod) != 0) {
				continue;
			}

			if ((include & mod) != include) {
				continue;
			}

			array.accept(field);
		}
	}

	static Stream<Class<?>> streamSuperTypesOf(Class<?> clazz) {
		final List<Class<?>> types = new ArrayList<>();

		scanSuperTypesOf(clazz,
		                 types);

		return types.stream();
	}

	static void scanSuperTypesOf(Class<?> clazz,
	                             Consumer<Class<?>> consumer) {
		streamSuperTypesOf(clazz).forEach(consumer);
	}

	static void scanSuperTypesOf(Class<?> clazz,
	                             List<Class<?>> set) {
		if (clazz == null) {
			return;
		}

		if (set.contains(clazz)) {
			return;
		}

		set.add(clazz);


		for (Class<?> anInterface : clazz.getInterfaces()) {
			scanSuperTypesOf(anInterface,
			                 set);
		}

		scanSuperTypesOf(clazz.getSuperclass(),
		                 set);
	}

	interface TypedLoader {
		<T> T load(Class<T> beanType);
	}

	TypedLoader reflect_typed_loader = new TypedLoader() {
		@Override
		public <T> T load(Class<T> beanType) {
			return createInstance(beanType,
			                      beanType);
		}
	};

	static <T> T createInstance(final Class<?> clazz,
	                            final Class<T> type) {
		final Object instance = createInstance(clazz);

		if (type.isInstance(instance)) {
			return type.cast(instance);
		}

		throw new IllegalArgumentException(StringAssist.format("reflect.instance.not.comport [%s][%s]",
		                                                       clazz.getName(),
		                                                       type.getName()));

	}

	static <T> T createInstance(final Constructor<T> constructor,
	                            final ErrorHandleFunction<Class<?>, Object> argumentFunction) {
		final Class<?>[] types = constructor.getParameterTypes();
		final int typeLength = types.length;
		try {
			if (typeLength == 0) {
				return constructor.newInstance();

			} else {
				final Object[] arguments = new Object[typeLength];

				for (int i = 0; i < typeLength; i++) {
					arguments[i] = FunctionAssist.function(types[i],
					                                       argumentFunction);
				}

				return constructor.newInstance(arguments);
			}
		}
		catch (Exception e) {
			if (log.isDebugEnabled()) {
				log.debug(StringAssist.format("[%s] create instance fail!",
				                              constructor),
				          e);
			}
		}

		return null;
	}

	static <T> T createInstance(final Class<T> type,
	                            final ErrorHandleFunction<Class<?>, Object> argumentFunction) {
		if (type.isInterface()) {
			throw new CrashedException("interface cannot create direct");
		}
		if (Modifier.isAbstract(type.getModifiers())) {
			throw new CrashedException("abstract class cannot create direct");
		}

		return Stream.of(type.getConstructors())
		             .map(constructor -> createInstance(constructor,
		                                                argumentFunction))
		             .filter(Objects::nonNull)
		             .findFirst()
		             .map(type::cast)
		             .orElseThrow(() -> new RuntimeException("create fail"));
	}

	/**
	 *
	 */
	static <T> T createInstance(final Class<T> type,
	                            final Object... objects) {
		try {
			if (type.isInterface()) {
				if (List.class.equals(type) || Collection.class.equals(type)) {
					return type.cast(new ArrayList<>());
				}
				if (Map.class.equals(type)) {
					return type.cast(new HashMap<>());
				}
				throw new IllegalArgumentException("unsupported interface type:".concat(type.getName()));
			}

			if (Modifier.isAbstract(type.getModifiers())) {
				throw new IllegalArgumentException("unsupported abstract type:".concat(type.getName()));
			}

			final int length = objects == null
			                   ? 0
			                   : objects.length;
			final Class<?>[] types = new Class[length];

			for (int i = 0; i < length; i++) {
				types[i] = objects[i] == null
				           ? Object.class
				           : objects[i].getClass();
			}
			try {
				Constructor<?> constructor = type.getDeclaredConstructor(types);

				constructor.setAccessible(true);

				return type.cast(constructor.newInstance(objects));
			}
			catch (Exception thw) {
				throw new IllegalArgumentException("create instance fail:".concat(type.getName()),
				                                   thw);
			}
		}
		catch (Exception thw) {
			throw new IllegalArgumentException("create instance type:".concat(type.getName()),
			                                   thw);
		}
	}

	static Stream<Method> streamMethod(Class<?> type) {
		List<Method> list = new ArrayList<>();

		scanDeclaredMethods(type,
		                    list);

		return list.stream();
	}

	static void scanMethod(Class<?> type,
	                       Consumer<Method> consumer) {
		if (null == type) {
			return;
		}

		scanMethod(type.getSuperclass(),
		           consumer);

		for (Method declaredMethod : type.getDeclaredMethods()) {
			consumer.accept(declaredMethod);
		}
	}

	static void scanDeclaredMethods(final Class<?> type,
	                                final List<Method> list) {
		if (null == type) {
			return;
		}

		list.addAll(Arrays.asList(type.getDeclaredMethods()));

		scanDeclaredMethods(type.getSuperclass(),
		                    list);
	}

	static Optional<Field> detectDeclaredField(final Class<?> type,
	                                           final String name) {
		try {
			return Optional.of(type.getDeclaredField(name));
		}
		catch (NoSuchFieldException | SecurityException e) {
			return Optional.empty();
		}
	}

	static boolean compareMethod(Method source,
	                             Method target) {
		if (source.equals(target)) return true;

		// except public protected (mod & PRIVATE) != 0
		if ((source.getModifiers() & ConstValues.MOD_MASK_PUBLIC_PROTECTED) == 0) {
			return false;
		}

		// compare name & parameter type & return type
		if (!StringUtils.equals(source.getName(),
		                        target.getName())) {
			return false;
		}

		if (!source.getReturnType()
		           .isAssignableFrom(target.getReturnType())) {
			return false;
		}

		Class<?>[] this_types = source.getParameterTypes();
		Class<?>[] that_types = target.getParameterTypes();

		if (this_types.length != that_types.length) {
			return false;
		}

		for (int i = 0; i < that_types.length; i++) {
			if (!that_types[i].isAssignableFrom(this_types[i])) {
				return false;
			}
		}

		return true;
	}

	// ---------------------------------------------------------------------
	// Section lookup method / field
	// ---------------------------------------------------------------------
	static <T> boolean updateInstanceValue(final T instance,
	                                       final Class<T> type,
	                                       final String fieldName,
	                                       final Object fieldValue) {
		// first lookup setter
		final Optional<Method> setter = lookupSetter(type,
		                                             fieldValue.getClass(),
		                                             fieldName);
		if (setter.isPresent()) {
			FunctionAssist.safeExecute(() -> setter.get()
			                                       .invoke(instance,
			                                               fieldValue));
			return true;
		}
		// second lookup field
		return detectDeclaredField(type,
		                           fieldName).filter(value -> setFieldValue(instance,
		                                                                    value,
		                                                                    fieldValue))
		                                     .isPresent();


	}

	static Optional<Method> lookupSetter(Class<?> type,
	                                     Class<?> fieldType,
	                                     String fieldName) {
		final String methodName = ResourceAssist.stringComposer()
		                                        .add("set")
		                                        .add(Character.toUpperCase(fieldName.charAt(0)))
		                                        .add(fieldName.substring(1))
		                                        .build();

		try {
			return Stream.of(type.getMethods())
			             .filter(method -> StringUtils.equals(method.getName(),
			                                                  methodName))
			             .filter(method -> method.getReturnType() == void.class)
			             .filter(method -> method.getParameterCount() == 1)
			             .filter(method -> PrimitiveTypes.isSameType(method.getParameterTypes()[0],
			                                                         fieldType))
			             .findFirst();
		}
		catch (Exception throwable) {
			if (log.isTraceEnabled()) log.trace(StringAssist.format("%s (%s) not found in [%s] by [%s]",
			                                                        methodName,
			                                                        fieldName,
			                                                        type.getName(),
			                                                        throwable.getMessage()));
		}

		return Optional.empty();
	}

	static boolean isSetter(Method method) {
		if (method.getReturnType() != void.class) return false;

		return method.getName()
		             .startsWith("set") && method.getParameterTypes().length == 1;
	}

	static boolean isGetter(Method method) {
		final Class<?> returnType = method.getReturnType();
		if (returnType == void.class) return false;

		return method.getName()
		             .startsWith(boolean.class == returnType
		                         ? "is"
		                         : "get") && method.getParameterTypes().length == 0;
	}

	static Optional<Method> lookupGetter(Class<?> type,
	                                     Class<?> fieldType,
	                                     String fieldName) {
		final String methodName = ResourceAssist.stringComposer()
		                                        .add(boolean.class == fieldType
		                                             ? "is"
		                                             : "get")
		                                        .add(Character.toUpperCase(fieldName.charAt(0)))
		                                        .add(fieldName.substring(1))
		                                        .build();

		try {
			return Stream.of(type.getMethods())
			             .filter(method -> StringUtils.equals(method.getName(),
			                                                  methodName))
			             .filter(method -> method.getParameterCount() == 0)
			             .filter(method -> PrimitiveTypes.isSameType(method.getReturnType(),
			                                                         fieldType))
			             .findFirst();
		}
		catch (Exception throwable) {
			if (log.isTraceEnabled()) log.trace(StringAssist.format("%s (%s) not found in [%s] by [%s]",
			                                                        methodName,
			                                                        fieldName,
			                                                        type.getName(),
			                                                        throwable.getMessage()));
		}

		return Optional.empty();
	}

	// ---------------------------------------------------------------------
	// Section TypeParameterMatcher
	// ---------------------------------------------------------------------
	static Stream<Class<?>> streamMemberTypes(final Class<?> target) {
		if (!target.isInterface()) {
			throw new IllegalArgumentException("only for interface type!");
		}
		return Stream.of(target.getDeclaredClasses());
	}

	static <A extends Annotation> void streamMemberTypeWithAnnotation(final Class<?> target,
	                                                                  final Class<A> annotationType,
	                                                                  final BiConsumer<Class<?>, A> consumer) {
		if (!target.isInterface()) {
			throw new IllegalArgumentException("only for interface type!");
		}
		Stream.of(target.getDeclaredClasses())
		      .filter(type -> type.isAnnotationPresent(annotationType))
		      .forEach(type -> {
			      final A annotation = Optional.ofNullable(type.getAnnotation(annotationType))
			                                   .orElseThrow(() -> new IllegalArgumentException(StringAssist.format("[@%s] not found in [%s]",
			                                                                                                       annotationType.getSimpleName(),
			                                                                                                       type.getName())));

			      consumer.accept(type,
			                      annotation);
		      });
	}
}
