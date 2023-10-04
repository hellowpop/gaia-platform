package gaia.common;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;


import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import gaia.ConstValues;
import gaia.common.CollectionSpec.HashedObjectContainer;
import gaia.common.ConcurrentAssist.ConditionedLock;
import gaia.common.GWCommon.Priority;

/**
 * @author dragon
 * @since 2020. 11. 30.
 */
@SuppressWarnings("unused")
public interface GenericSpec {
	Comparator<TypeDepends<?>> COMPARATOR = new Comparator<TypeDepends<?>>() {
		/**
		 *
		 */
		@Override
		public int compare(TypeDepends<?> o1,
		                   TypeDepends<?> o2) {
			if (o1.getType()
			      .isAssignableFrom(o2.getType())) {
				return 1;
			}
			if (o2.getType()
			      .isAssignableFrom(o1.getType())) {
				return -1;
			}

			return 0;
		}
	};

	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@interface GenericDefineProxy {
		Class<?>[] value();
	}

	@Getter
	class ParameterizedTypeProxy
			implements ParameterizedType {
		final Type[] actualTypeArguments;

		final Type rawType;

		final Type ownerType;

		ParameterizedTypeProxy(final Type owner,
		                       GenericDefineProxy proxy) {
			this.actualTypeArguments = proxy.value();
			this.rawType = owner;
			this.ownerType = owner;
		}
	}

	enum GenericDefineType {
		/**
		 *
		 */
		RAW(GenericDefineForRawType.class),
		/**
		 *
		 */
		PARAMETER(GenericDefineForParameterizedType.class),
		/**
		 *
		 */
		GENERIC_ARRAY(GenericDefineForGenericArrayType.class),
		/**
		 *
		 */
		TYPE_VARIABLE(GenericDefineForTypeVariable.class),
		/**
		 *
		 */
		WILD_CARD(GenericDefineForWildcardType.class);

		/**
		 *
		 */
		@Getter
		final Class<? extends GenericDefine> type;

		/**
		 * @param type target type
		 */
		GenericDefineType(Class<? extends GenericDefine> type) {
			this.type = type;
		}

		/**
		 * @param define define
		 * @return flag
		 */
		public final boolean isFor(GenericDefine define) {
			return this.type.isInstance(define);
		}
	}

	interface ClassMeta
			extends AnnotatedElement {
		ClassMetaContainer getMetaContainer();

		Class<?> getOrigin();

		default ClassMeta getSuperMeta() {
			return null;
		}

		default boolean compare(ClassMeta other) {
			return this.getOrigin()
			           .equals(other.getOrigin());
		}

		/**
		 * @return name
		 */
		String getName();

		/**
		 * @return simple name
		 */
		String getSimpleName();

		/**
		 * @return method array
		 */
		MethodMeta[] getMethods();

		/**
		 * @return declared method array
		 */
		MethodMeta[] getDeclaredMethods();

		/**
		 * @return declared field array
		 */
		FieldMeta[] getDeclaredFields();

		/**
		 * @param name name
		 * @return field meta
		 */
		default FieldMeta getDeclaredField(String name) {
			final FieldMeta[] fields = this.getDeclaredFields();
			for (FieldMeta meta : fields) {//
				if (StringAssist.equalsIgnoreCase(name,
				                                  meta.getName())) {
					return meta;
				}
			}
			return this.getSuperMeta() == null
			       ? null
			       : this.getSuperMeta()
			             .getDeclaredField(name);
		}

		/**
		 * @param name name of field
		 * @return field metea
		 */
		default FieldMeta getField(String name) {
			for (FieldMeta meta : this.getFields()) {//
				if (StringAssist.equalsIgnoreCase(name,
				                                  meta.getName())) {
					return meta;
				}
			}
			return null;
		}

		/**
		 * @return field array
		 */
		FieldMeta[] getFields();

		/**
		 * @param annotationType annotation type
		 * @return flag
		 */
		default boolean canTraceAnnotation(Class<? extends Annotation> annotationType) {
			return AnnotationAssist.isPresent(this,
			                                  annotationType);
		}

		/**
		 * @param annotationType annotation type
		 * @return declared annotation instance
		 */
		default <T extends Annotation> T getTraceAnnotation(Class<T> annotationType) {
			return AnnotationAssist.getAnnotation(this,
			                                      annotationType);
		}

		default <T extends Annotation> void iterateAnnotation(Class<T> annotationType,
		                                                      Consumer<T> acceptor) {
			AnnotationAssist.scanAnnotation(this,
			                                annotationType,
			                                acceptor);
		}

		default void iterateAllAnnotation(Consumer<Annotation> acceptor) {
			AnnotationAssist.scanAnnotation(this,
			                                Annotation.class,
			                                acceptor);
		}

		/**
		 * @param annotationType annotation type
		 * @return flag
		 */
		default boolean canTraceAnnotationRecursive(Class<? extends Annotation> annotationType) {
			return AnnotationAssist.isPresent(this,
			                                  annotationType);
		}

		/**
		 * @param annotationType annotation type
		 * @return annotation instance
		 */
		default <T extends Annotation> T getTraceAnnotationRecursive(Class<T> annotationType) {
			return AnnotationAssist.getAnnotation(this,
			                                      annotationType);
		}

		default <T extends Annotation> List<T> extractTypedAnnotationRecursive(Class<T> type) {
			final List<T> list = new ArrayList<>();

			this.appendTypedAnnotationRecursive(type,
			                                    list);

			return list;
		}

		default <T extends Annotation> void appendTypedAnnotationRecursive(final Class<T> type,
		                                                                   final List<T> list) {
			AnnotationAssist.scanAnnotation(this,
			                                type,
			                                list::add);

		}
	}

	interface MemberMeta
			extends Member,
			        AnnotatedElement {
		ElementType getElementType();

		/**
		 *
		 */
		ClassMeta getDeclaredMeta();

		default boolean missingAnnotation(Class<? extends Annotation> annotationType) {
			return !AnnotationAssist.isPresent(this,
			                                   annotationType);
		}

		default <T extends Annotation> T getTraceAnnotation(Class<T> annotationType) {
			return AnnotationAssist.getAnnotation(this,
			                                      annotationType);
		}
	}

	interface FieldMeta
			extends MemberMeta {
		FieldMeta[] NULL = new FieldMeta[0];

		Field getOrigin();

		Type getGenericType();

		Class<?> getType();

		Class<?>[] getComponentType();

		Object get(Object bean) throws IllegalArgumentException, IllegalAccessException;

		/**
		 * @param bean  target bean instance
		 * @param value value
		 */
		void set(Object bean,
		         Object value) throws IllegalArgumentException, IllegalAccessException;

		@Override
		default ElementType getElementType() {
			return ElementType.FIELD;
		}
	}

	interface MethodMeta
			extends MemberMeta,
			        GenericDeclaration {
		/**
		 *
		 */
		MethodMeta[] NULL = new MethodMeta[0];

		/**
		 * @return
		 */
		ClassMeta getClassMeta();

		/**
		 * @param obj
		 * @param args
		 * @return
		 * @throws IllegalAccessException
		 * @throws IllegalArgumentException
		 * @throws InvocationTargetException
		 */
		Object invoke(Object obj,
		              Object... args) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException;

		/**
		 * @return
		 */
		default Class<?>[] getParameterTypes() {
			Method origin = this.getOrigin();

			return origin == null
			       ? ConstValues.NULL_CLASS
			       : origin.getParameterTypes();
		}

		/**
		 * @return
		 */
		Method getOrigin();

		/**
		 * @return
		 */
		default Class<?> getReturnType() {
			Method origin = this.getOrigin();

			return origin == null
			       ? Void.class
			       : origin.getReturnType();
		}

		@Override
		default ElementType getElementType() {
			return ElementType.METHOD;
		}
	}

	interface TypeDepends<T>
			extends Priority {
		String getName();

		Class<T> getType();

		Class<?> getPrimitiveType();

		boolean isFor(Class<?> ref);

		boolean isMatch(Object instance);
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	@interface ParameterName {
		/**
		 * @return
		 */
		String value();
	}

	class ClassMetaContainer {
		final Map<Class<?>, Map<ClassMeta, ClassWrapper>> cache = new HashMap<>();

		final ConditionedLock lock = new ConditionedLock();

		/**
		 *
		 */
		final HashedObjectContainer<Class<?>, ClassWrapper> wrappers = new HashedObjectContainer<Class<?>, ClassWrapper>(false) {
			@Override
			protected ClassWrapper make(Class<?> key) {
				return new ClassWrapper(ClassMetaContainer.this,
				                        key);
			}
		};

		/**
		 * @param type
		 * @return
		 */
		public final ClassWrapper getClassWrapper(final Class<?> type,
		                                          final ClassMeta meta) {
			return this.lock.tran(() -> {
				final Map<Class<?>, Map<ClassMeta, ClassWrapper>> cache = this.cache;

				Map<ClassMeta, ClassWrapper> entry = cache.computeIfAbsent(type,
				                                                           k -> new HashMap<>());

				ClassWrapper wrapper = entry.get(meta);

				if (wrapper == null) {
					wrapper = new ClassWrapper(this,
					                           type,
					                           meta);

					entry.put(meta,
					          wrapper);
				}

				return wrapper;
			});
		}

		public final ClassWrapper getClassWrapper(Class<?> type) {
			return this.wrappers.getInstance(type);
		}
	}

	@Slf4j
	class ClassMetaDirect
			implements ClassMeta {
		@Getter
		final ClassMetaContainer metaContainer;

		@Getter
		final Class<?> origin;

		@Getter
		final MethodMeta[] methods;

		@Getter
		final MethodMeta[] declaredMethods;

		@Getter
		final FieldMeta[] fields;

		@Getter
		final FieldMeta[] declaredFields;

		@Getter
		final ClassMeta superMeta;

		public ClassMetaDirect(ClassMetaContainer metaContainer,
		                       Class<?> origin) {
			super();
			this.metaContainer = metaContainer;

			Class<?> candidateSuper = origin.getSuperclass();

			this.origin = origin;

			this.superMeta = candidateSuper != null && candidateSuper != Object.class
			                 ? new ClassMetaDirect(metaContainer,
			                                       candidateSuper)
			                 : null;

			final List<MethodMetaDirect> methodList = new ArrayList<>();

			ReflectAssist.streamMethod(origin)
			             .filter(method -> !method.getDeclaringClass()
			                                      .getSimpleName()
			                                      .contains("$$"))
			             .filter(method -> !Modifier.isStatic(method.getModifiers()))
			             .map(method -> new MethodMetaDirect(this,
			                                                 method))
			             .filter(method -> !methodList.contains(method))
			             .forEach(methodList::add);

			this.methods = methodList.stream()
			                         .filter(meta -> Modifier.isPublic(meta.getModifiers()))
			                         .toArray(MethodMeta[]::new);

			this.declaredMethods = methodList.toArray(MethodMeta.NULL);

			final List<FieldMeta> fieldList = new ArrayList<>();

			ReflectAssist.streamField(origin)
			             .filter(field -> !field.getDeclaringClass()
			                                    .getSimpleName()
			                                    .contains("$$"))
			             .filter(field -> !Modifier.isStatic(field.getModifiers()))
			             .map(field -> new FieldMetaDirect(this,
			                                               field))
			             .filter(field -> !fieldList.contains(field))
			             .forEach(fieldList::add);

			this.fields = fieldList.stream()
			                       .filter(meta -> Modifier.isPublic(meta.getModifiers()))
			                       .toArray(FieldMeta[]::new);

			this.declaredFields = fieldList.toArray(FieldMeta.NULL);
		}

		/**
		 * @param annotationClass annotation type class
		 * @param <T>             parameter type
		 * @return annotation instance
		 */
		@Override
		public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
			return this.origin.getAnnotation(annotationClass);
		}

		/**
		 *
		 */
		@Override
		public Annotation[] getAnnotations() {
			return this.origin.getAnnotations();
		}

		/**
		 *
		 */
		@Override
		public Annotation[] getDeclaredAnnotations() {
			return this.origin.getDeclaredAnnotations();
		}

		@Override
		public String getName() {
			return this.origin.getName();
		}

		@Override
		public String getSimpleName() {
			return this.origin.getSimpleName();
		}
	}

	final class ClassWrapper
			implements ClassMeta {
		/**
		 *
		 */
		@Getter
		final ClassMetaContainer metaContainer;

		/**
		 *
		 */
		@Getter
		final String name;

		/**
		 *
		 */
		@Getter
		final Class<?> type;

		/**
		 * @return
		 */
		@Getter
		final ClassMeta meta;

		/**
		 * @param type
		 */
		ClassWrapper(ClassMetaContainer metaContainer,
		             final Class<?> type) {
			this(metaContainer,
			     type,
			     null);
		}

		/**
		 * @param type
		 */
		ClassWrapper(ClassMetaContainer metaContainer,
		             final Class<?> type,
		             final ClassMeta meta) {
			super();
			this.metaContainer = metaContainer;
			this.type = type;
			// detect proxy class
			if (Proxy.isProxyClass(type)) {
				throw new Error("java proxy class not support!");
			}

			this.meta = meta == null
			            ? new ClassMetaDirect(metaContainer,
			                                  type)
			            : meta;
			this.name = meta == null
			            ? type.getName()
			            : StringAssist.format("%s-%s",
			                                  type.getName(),
			                                  meta.getName());
		}

		@Override
		public Class<?> getOrigin() {
			return this.type;
		}

		@Override
		public ClassMeta getSuperMeta() {
			return this.meta.getSuperMeta();
		}

		/**
		 * @return
		 */
		@Override
		public String getSimpleName() {
			return this.meta == null
			       ? this.type.getSimpleName()
			       : this.meta.getSimpleName();
		}

		@Override
		public MethodMeta[] getMethods() throws SecurityException {
			return this.meta.getMethods();
		}

		@Override
		public MethodMeta[] getDeclaredMethods() {
			return this.meta.getDeclaredMethods();
		}

		@Override
		public FieldMeta[] getDeclaredFields() {
			return this.meta.getDeclaredFields();
		}

		@Override
		public FieldMeta getDeclaredField(String name) {
			return this.meta.getDeclaredField(name);
		}

		@Override
		public FieldMeta getField(String name) {
			return this.meta.getField(name);
		}

		@Override
		public FieldMeta[] getFields() {
			return this.meta.getFields();
		}

		/**
		 *
		 */
		@Override
		public int hashCode() {
			return this.name.hashCode();
		}

		/**
		 *
		 */
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof ClassWrapper)) {
				return false;
			}
			ClassWrapper other = (ClassWrapper) obj;
			return this.name.equals(other.name) && this.meta.compare(other.meta);
		}

		/**
		 *
		 */
		@Override
		public String toString() {
			return StringAssist.format("class wrapper:%s",
			                           this.type.getName());
		}

		/**
		 *
		 */
		@Override
		public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
			return this.meta.isAnnotationPresent(annotationType);
		}

		/**
		 *
		 */
		@Override
		public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
			return this.meta.getAnnotation(annotationClass);
		}

		/**
		 *
		 */
		@Override
		public Annotation[] getAnnotations() {
			return this.meta.getAnnotations();
		}

		/**
		 *
		 */
		@Override
		public Annotation[] getDeclaredAnnotations() {
			return this.meta.getDeclaredAnnotations();
		}

		public <T extends Annotation> T getTypedAnnotation(Class<T> type) {
			if (Object.class.equals(type)) {
				return null;
			}

			T annotation = this.getAnnotation(type);

			if (annotation != null) {
				return annotation;
			}

			if (!this.type.isInterface()) {
				annotation = AnnotationAssist.getAnnotation(this.type.getSuperclass(),
				                                            type);

				if (annotation != null) {
					return annotation;
				}
			}

			for (Class<?> intf : this.type.getInterfaces()) {
				annotation = AnnotationAssist.getAnnotation(intf,
				                                            type);

				if (annotation != null) {
					return annotation;
				}
			}

			return null;
		}

		public List<FieldMeta> extractFilteredMemberFields(final int include,
		                                                   final int exclude) {
			return this.streamFields(include,
			                         exclude)
			           .collect(Collectors.toList());
		}

		public Stream<FieldMeta> streamFields(final int include,
		                                      final int exclude) {
			return Arrays.stream(this.getDeclaredFields())
			             .filter(field -> (exclude & field.getModifiers()) == 0)
			             .filter(field -> (include & field.getModifiers()) == include);
		}
	}

	class FieldMetaDirect
			implements FieldMeta {
		/**
		 *
		 */
		@Getter
		final ClassMeta declaredMeta;

		/**
		 *
		 */
		@Getter
		final Field origin;

		/**
		 * @param field
		 */
		public FieldMetaDirect(ClassMeta classMeta,
		                       Field field) {
			super();
			this.declaredMeta = classMeta;
			this.origin = field;
			field.setAccessible(true);
		}

		/**
		 *
		 */
		@Override
		public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
			return this.origin.getAnnotation(annotationClass);
		}

		/**
		 *
		 */
		@Override
		public Annotation[] getAnnotations() {
			return this.origin.getAnnotations();
		}

		/**
		 *
		 */
		@Override
		public Annotation[] getDeclaredAnnotations() {
			return this.origin.getDeclaredAnnotations();
		}

		@Override
		public Class<?> getDeclaringClass() {
			return this.origin.getDeclaringClass();
		}

		/**
		 *
		 */
		@Override
		public String getName() {
			return this.origin.getName();
		}

		/**
		 *
		 */
		@Override
		public int getModifiers() {
			return this.origin.getModifiers();
		}

		/**
		 *
		 */
		@Override
		public boolean isSynthetic() {
			return this.origin.isSynthetic();
		}

		@Override
		public Type getGenericType() {
			return this.origin.getGenericType();
		}

		@Override
		public Class<?> getType() {
			return this.origin.getType();
		}

		@Override
		public Class<?>[] getComponentType() {
			return getParameterTypes(this.origin.getGenericType());
		}

		@Override
		public Object get(Object handler) throws IllegalArgumentException, IllegalAccessException {
			return this.origin.get(handler);
		}

		@Override
		public void set(Object handler,
		                Object value) throws IllegalArgumentException, IllegalAccessException {
			this.origin.set(handler,
			                value);
		}

		/**
		 *
		 */
		@Override
		public String toString() {
			return this.origin.toString();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof FieldMeta)) {
				return false;
			}
			FieldMeta that = (FieldMeta) o;

			if (Objects.equals(this.origin,
			                   that.getOrigin())) {
				return true;
			}

			// except public protected (mod & PRIVATE) != 0
			if ((that.getModifiers() & ConstValues.MOD_MASK_PUBLIC_PROTECTED) == 0) {
				return false;
			}

			// compare name & parameter type & return type
			if (!StringUtils.equals(this.getName(),
			                        that.getName())) {
				return false;
			}

			return that.getType()
			           .isAssignableFrom(this.getType());
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.origin);
		}
	}

	class MethodMetaDirect
			implements MethodMeta {
		/**
		 *
		 */
		@Getter
		final ClassMeta classMeta;

		/**
		 *
		 */
		@Getter
		final Method origin;

		/**
		 *
		 */
		ClassMeta declaredMeta = null;

		/**
		 * @param method
		 */
		public MethodMetaDirect(ClassMeta classMeta,
		                        Method method) {
			super();
			this.classMeta = classMeta;
			this.origin = method;
		}

		@Override
		public ClassMeta getDeclaredMeta() {
			if (this.declaredMeta != null) {
				return this.declaredMeta;
			}

			Class<?> declared = this.origin.getDeclaringClass();

			if (this.classMeta.getOrigin()
			                  .equals(declared)) {
				this.declaredMeta = this.classMeta;
			} else {
				this.declaredMeta = new ClassMetaDirect(this.classMeta.getMetaContainer(),
				                                        declared);
			}

			return this.declaredMeta;
		}

		/**
		 *
		 */
		@Override
		public TypeVariable<?>[] getTypeParameters() {
			return this.origin.getTypeParameters();
		}

		/**
		 *
		 */
		@Override
		public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
			return this.origin.getAnnotation(annotationClass);
		}

		/**
		 *
		 */
		@Override
		public Annotation[] getAnnotations() {
			return this.origin.getAnnotations();
		}

		/**
		 *
		 */
		@Override
		public Annotation[] getDeclaredAnnotations() {
			return this.origin.getDeclaredAnnotations();
		}

		/**
		 *
		 */
		@Override
		public Class<?> getDeclaringClass() {
			return this.origin.getDeclaringClass();
		}

		/**
		 *
		 */
		@Override
		public String getName() {
			return this.origin.getName();
		}

		/**
		 *
		 */
		@Override
		public int getModifiers() {
			return this.origin.getModifiers();
		}

		/**
		 *
		 */
		@Override
		public boolean isSynthetic() {
			return this.origin.isSynthetic();
		}

		@Override
		public Object invoke(Object obj,
		                     Object... args) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			return this.origin.invoke(obj,
			                          args);
		}

		/**
		 *
		 */
		@Override
		public String toString() {
			return this.origin.toString();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof MethodMeta)) {
				return false;
			}
			MethodMeta that = (MethodMeta) o;

			return ReflectAssist.compareMethod(this.getOrigin(),
			                                   that.getOrigin());
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.origin);
		}
	}

	class MethodReflectInfo {
		/**
		 *
		 */
		@Getter
		final Method method;

		/**
		 *
		 */
		@Getter
		final Class<?> returnType;

		/**
		 *
		 */
		final GenericDefine define;

		/**
		 *
		 */
		@Getter
		final ParameterInfo[] paramInfos;

		/**
		 * @param method method
		 */
		public MethodReflectInfo(Method method) {
			super();
			this.method = method;
			this.define = getGenericDefine(method.getGenericReturnType());
			this.returnType = method.getReturnType();

			Annotation[][] annotations = method.getParameterAnnotations();
			Type[] paramTypes = method.getGenericParameterTypes();

			final int length = paramTypes.length;

			final ParameterInfo[] paramInfos = new ParameterInfo[length];

			for (int i = 0; i < length; i++) {
				ParameterName annotation = null;
				for (Annotation element : annotations[i]) {
					if (element instanceof ParameterName) {
						annotation = (ParameterName) element;
						break;
					}
				}
				paramInfos[i] = new ParameterInfo(annotation,
				                                  paramTypes[i]);
			}

			this.paramInfos = paramInfos;
		}

		public final Class<?> getParameterizedReturnType(int index) {
			return this.define.getParameterType(index);
		}
	}

	final class ParameterInfo {
		@Getter
		final ParameterName annotation;

		@Getter
		final String name;

		final GenericDefine define;

		@Getter
		final Class<?> type;

		public ParameterInfo(final ParameterName annotation,
		                     final Type type) {
			super();
			this.annotation = annotation;
			this.name = annotation == null
			            ? null
			            : annotation.value();
			this.define = getGenericDefine(type);
			this.type = this.define.getRawType();
		}

		/**
		 * @param index
		 * @return
		 */
		public Class<?> getParameterizedType(int index) {
			return this.define.getParameterType(index);
		}

		/**
		 *
		 */
		@Override
		public String toString() {
			return StringAssist.format("%s @ %s",
			                           this.name,
			                           this.define.toString());
		}
	}

	abstract class TypeDependAdapter<T>
			implements TypeDepends<T> {
		@Getter
		protected final Class<T> type;
		@Getter
		protected final Class<?> primitiveType;
		@Getter
		@Setter
		String name;

		@SuppressWarnings("unchecked")
		public TypeDependAdapter() {
			final Class<?> rootClass = this.getClass();

			this.type = (Class<T>) ReflectAssist.getGenericParameter(this.getClass(),
			                                                         TypeDependAdapter.class,
			                                                         "T");

			this.name = this.type.getSimpleName();

			this.primitiveType = ReflectAssist.getFieldValue(this.type,
			                                                 "TYPE",
			                                                 Class.class);
		}

		/**
		 * @param type
		 */
		public TypeDependAdapter(Class<T> type) {
			this.type = type;
			this.primitiveType = null;
			this.name = type.getSimpleName();
		}

		/**
		 *
		 */
		@Override
		public boolean isFor(Class<?> ref) {
			if (this.type.isAssignableFrom(ref)) {
				return true;
			}

			// check primitive type
			return this.primitiveType != null && this.primitiveType.equals(ref);
		}

		/**
		 *
		 */
		@Override
		public boolean isMatch(Object instance) {
			return this.type.isInstance(instance);
		}

		/**
		 * @param instance
		 * @return
		 */
		protected final T cast(Object instance) {
			return this.type.cast(instance);
		}

		/**
		 * @return
		 */
		protected final T newInstance() {
			return ReflectAssist.createInstance(this.type);
		}
	}

	// ---------------------------------------------------------------------
	// Section generic defines
	// ---------------------------------------------------------------------
	abstract class GenericDefine {
		/**
		 *
		 */
		@Getter
		final Type type;

		/**
		 *
		 */
		@Getter
		final GenericDefineType defineType;

		/**
		 *
		 */
		String boundName = null;

		/**
		 * @param type
		 */
		public GenericDefine(final Type type,
		                     final GenericDefineType defineType) {
			this.type = type;
			this.defineType = defineType;
		}

		public final int length() {
			if (this.getDefineType() != GenericDefineType.PARAMETER) {
				return 0;
			}

			final GenericDefineForParameterizedType paramType = (GenericDefineForParameterizedType) this;

			return paramType.getGeneric().length;
		}

		public final Class<?> getParameterType(int index) {
			if (this.getDefineType() != GenericDefineType.PARAMETER) {
				return null;
			}

			final GenericDefineForParameterizedType paramType = (GenericDefineForParameterizedType) this;

			final GenericDefine[] defines = paramType.getGeneric();

			if (defines.length > index) {

				switch (defines[index].getDefineType()) {
					case RAW: {
						final GenericDefineForRawType rawType = (GenericDefineForRawType) defines[index];
						return rawType.getClazz();
					}

					case PARAMETER: {
						// TODO : 중첩된 generic 결과 반환방법은?
						final GenericDefineForParameterizedType pType = (GenericDefineForParameterizedType) defines[index];

						return pType.getClazz();
					}

					default: {
						break;
					}
				}
			}

			return null;
		}

		/**
		 * @return
		 */
		public final Class<?>[] getParameterTypes() {
			final GenericDefine tv = this;

			if (tv.getDefineType() != GenericDefineType.PARAMETER) {
				return new Class<?>[0];
			}

			final GenericDefineForParameterizedType paramType = (GenericDefineForParameterizedType) tv;

			final GenericDefine[] defines = paramType.getGeneric();

			final Class<?>[] classes = new Class[defines.length];

			for (int i = 0; i < defines.length; i++) {
				GenericDefine define = defines[i];
				switch (define.getDefineType()) {
					case RAW: {
						final GenericDefineForRawType rawType = (GenericDefineForRawType) define;
						classes[i] = rawType.getClazz();
						break;
					}

					case PARAMETER: {
						final GenericDefineForParameterizedType pType = (GenericDefineForParameterizedType) define;

						classes[i] = pType.getClazz();
						break;
					}

					case WILD_CARD: {
						final GenericDefineForWildcardType wType = (GenericDefineForWildcardType) define;
						classes[i] = wType.getRawType();
						break;
					}

					default: {
						classes[i] = null;
						break;
					}
				}
			}

			return classes;
		}

		/**
		 *
		 */
		@Override
		public final String toString() {
			StringBuilder buffer = new StringBuilder();

			this.appendDescription(buffer);

			return buffer.toString();
		}

		protected abstract void appendDescription(StringBuilder buffer);

		public abstract Class<?> getRawType();
	}

	class GenericDefineForGenericArrayType
			extends GenericDefine {

		public GenericDefineForGenericArrayType(GenericArrayType type) {
			super(type,
			      GenericDefineType.GENERIC_ARRAY);
		}

		@Override
		protected void appendDescription(StringBuilder buffer) {
			buffer.append("GENERIC_ARRAY");
		}

		@Override
		public Class<?> getRawType() {
			return null;
		}
	}

	class GenericDefineForParameterizedType
			extends GenericDefineOfFixed {

		@Getter
		final GenericDefine[] generic;

		GenericDefineForParameterizedType(ParameterizedType type) {
			super(type,
			      GenericDefineType.PARAMETER);

			final Type rawType = type.getRawType();
			this.clazz = (Class<?>) rawType;
			this.generic = getGenericDefines(type.getActualTypeArguments());
		}

		@Override
		protected void appendDescription(StringBuilder buffer) {
			buffer.append("PARAMETER : ");
			buffer.append(this.getClazz()
			                  .getName());
			buffer.append(" with <");
			for (GenericDefine define : this.generic) {
				buffer.append(' ');
				define.appendDescription(buffer);
				buffer.append(' ');
			}
			buffer.append('>');
		}
	}

	class GenericDefineForRawType
			extends GenericDefineOfFixed {
		GenericDefineForRawType(Class<?> type) {
			super(type,
			      GenericDefineType.RAW);

			this.setClazz(type);
		}

		@Override
		protected void appendDescription(StringBuilder buffer) {
			buffer.append(this.getClazz()
			                  .getName());
		}
	}

	class GenericDefineForTypeVariable
			extends GenericDefine {
		@Getter
		final String name;

		@Getter
		final GenericDefine[] bounds;

		public GenericDefineForTypeVariable(TypeVariable<?> type) {
			super(type,
			      GenericDefineType.TYPE_VARIABLE);

			this.name = type.getName();
			this.bounds = getGenericDefines(type.getBounds());
		}

		@Override
		protected void appendDescription(StringBuilder buffer) {

			buffer.append("TYPE_VARIABLE : ");
			buffer.append(this.name);
			buffer.append(" [");
			for (GenericDefine define : this.bounds) {
				buffer.append(' ');
				define.appendDescription(buffer);
				buffer.append(' ');
			}
			buffer.append(']');
		}

		@Override
		public Class<?> getRawType() {
			return null;
		}
	}

	class GenericDefineForWildcardType
			extends GenericDefine {
		@Getter
		final GenericDefine[] upper;

		@Getter
		final GenericDefine[] lower;

		public GenericDefineForWildcardType(WildcardType type) {
			super(type,
			      GenericDefineType.WILD_CARD);
			this.upper = getGenericDefines(type.getUpperBounds());
			this.lower = getGenericDefines(type.getLowerBounds());
		}

		@Override
		protected void appendDescription(StringBuilder buffer) {
			buffer.append("WILD_CARD : upper [");
			for (GenericDefine define : this.upper) {
				buffer.append(' ');
				define.appendDescription(buffer);
				buffer.append(' ');
			}
			buffer.append("] lower [");
			for (GenericDefine define : this.lower) {
				buffer.append(' ');
				define.appendDescription(buffer);
				buffer.append(' ');
			}
			buffer.append(']');
		}

		@Override
		public Class<?> getRawType() {
			return null;
		}
	}

	abstract class GenericDefineOfFixed
			extends GenericDefine {
		/**
		 *
		 */
		@Getter
		@Setter(AccessLevel.PACKAGE)
		Class<?> clazz = null;

		/**
		 * @param type
		 * @param defineType
		 */
		GenericDefineOfFixed(Type type,
		                     GenericDefineType defineType) {
			super(type,
			      defineType);
		}

		@Override
		public Class<?> getRawType() {
			return this.getClazz();
		}
	}

	abstract class GenericClassDetector<T>
			implements PrivilegedAction<Class<T>> {
		final Type rootType;

		final int index;

		public GenericClassDetector(final Type rootType) {
			this(rootType,
			     0);
		}

		public GenericClassDetector(final Type rootType,
		                            final int index) {
			super();
			this.rootType = rootType;
			this.index = index;
		}

		@SuppressWarnings("unchecked")
		protected final Class<T> lookup(final Type type) {
			if (type == null || type == Object.class) {
				throw new Error(StringAssist.format("invalid type depends type ! [%s]",
				                                    this.rootType.toString()));
			}
			final GenericDefine tv = getGenericDefine(type);

			switch (tv.getDefineType()) {
				case RAW: {
					final GenericDefineForRawType rawType = (GenericDefineForRawType) tv;

					if (rawType.getClazz()
					           .isInterface()) {
						Optional<GenericDefine> detect = Arrays.stream(rawType.getClazz()
						                                                      .getGenericInterfaces())
						                                       .map(GenericSpec::getGenericDefine)
						                                       .filter(define -> define.getDefineType() == GenericDefineType.PARAMETER)
						                                       .findFirst();

						Class<?>[] types = detect.map(GenericDefine::getParameterTypes)
						                         .orElse(ConstValues.NULL_CLASS);

						if (types.length > this.index) {
							return (Class<T>) types[this.index];
						}
					} else {
						return this.lookup(rawType.getClazz()
						                          .getGenericSuperclass());
					}
					break;
				}

				case PARAMETER: {
					final GenericDefineForParameterizedType paramType = (GenericDefineForParameterizedType) tv;
					final GenericDefine[] defines = paramType.getGeneric();

					if (defines == null || defines.length < 1) {
						return this.lookup(paramType.getClazz()
						                            .getGenericSuperclass());
					}

					if (defines.length > this.index && GenericDefineType.RAW.isFor(defines[this.index])) {
						final GenericDefineForRawType rawType = (GenericDefineForRawType) defines[this.index];

						return (Class<T>) rawType.getClazz();
					}
					if (defines.length > this.index && GenericDefineType.PARAMETER.isFor(defines[this.index])) {
						final GenericDefineForParameterizedType pType = (GenericDefineForParameterizedType) defines[this.index];

						return (Class<T>) pType.getClazz();
					}
					break;
				}

				default: {
					throw new Error(StringAssist.format("invalid type depends type ! [%s] @ [%s]",
					                                    tv,
					                                    this.rootType));
				}
			}

			throw new Error(StringAssist.format("invalid type depends type ! [%s]",
			                                    this.rootType));
		}
	}

	/**
	 * detect generic super type
	 *
	 * @param <T>
	 * @author dragon
	 * @since 2020. 12. 2.
	 */
	class GenericClassTypeAction<T>
			extends GenericClassDetector<T> {
		final Class<?> rootClass;

		/**
		 * @param rootClass rootClass
		 */
		public GenericClassTypeAction(final Class<?> rootClass) {
			this(rootClass,
			     0);
		}

		/**
		 * @param rootClass
		 */
		public GenericClassTypeAction(final Class<?> rootClass,
		                              final int index) {
			super(rootClass,
			      index);
			this.rootClass = rootClass;
		}

		/**
		 *
		 */
		@Override
		public Class<T> run() {
			final Type superType = this.rootClass.getGenericSuperclass();
			return this.lookup(superType);
		}
	}

	/**
	 * detectect generic type
	 *
	 * @param <T>
	 * @author dragon
	 * @since 2020. 12. 2.
	 */
	class GenericClassTypeDetect<T>
			extends GenericClassDetector<T> {
		/**
		 * @param rootType
		 */
		public GenericClassTypeDetect(final Type rootType) {
			super(rootType,
			      0);
		}

		/**
		 * @param rootType
		 */
		public GenericClassTypeDetect(final Type rootType,
		                              final int index) {
			super(rootType,
			      index);
		}

		/**
		 *
		 */
		@Override
		public Class<T> run() {
			return this.lookup(this.rootType);
		}
	}

	// ---------------------------------------------------------------------
	// Section
	// ---------------------------------------------------------------------

	static boolean isBeanPatternMethod(Method method) {
		return isGetMethod(method) || isSetMethod(method);
	}

	/**
	 * @param method
	 * @return
	 */
	static boolean isGetMethod(Method method) {
		return getMethodAttributeNameOfGet(method) != null;
	}

	/**
	 * @param method
	 * @return
	 */
	static boolean isSetMethod(Method method) {
		return getMethodAttributeNameOfSet(method) != null;
	}

	/**
	 * @param method
	 * @return
	 */
	static boolean isMethodAttributeNameOfGet(MethodMeta method) {
		return getMethodAttributeNameOfGet(method) != null;
	}

	static String getMethodAttributeNameOfGet(MethodMeta method) {
		return getMethodAttributeNameOfGet(method.getName(),
		                                   method.getParameterTypes(),
		                                   method.getReturnType());
	}

	static String getMethodAttributeNameOfGet(Method method) {
		return getMethodAttributeNameOfGet(method.getName(),
		                                   method.getParameterTypes(),
		                                   method.getReturnType());
	}

	/**
	 * @return
	 */
	static String getMethodAttributeNameOfGet(final String methodName,
	                                          final Class<?>[] args,
	                                          final Class<?> valueType) {
		if (valueType.equals(Void.TYPE) || args.length > 0) {
			return null;
		}

		if (methodName.length() > 3 && methodName.startsWith("get")) {
			return StringAssist.format("%s%s",
			                           Character.toLowerCase(methodName.charAt(3)),
			                           methodName.substring(4));
		}
		if (methodName.length() > 2 && methodName.startsWith("is") && valueType == boolean.class) {
			return StringAssist.format("%s%s",
			                           Character.toLowerCase(methodName.charAt(2)),
			                           methodName.substring(3));
		}

		return null;
	}

	/**
	 * @param method
	 * @return
	 */
	static boolean isMethodAttributeNameOfSet(MethodMeta method) {
		return getMethodAttributeNameOfSet(method) != null;
	}

	static String getMethodAttributeNameOfSet(MethodMeta method) {
		return getMethodAttributeNameOfSet(method.getName(),
		                                   method.getParameterTypes(),
		                                   method.getReturnType());
	}

	static String getMethodAttributeNameOfSet(Method method) {
		return getMethodAttributeNameOfSet(method.getName(),
		                                   method.getParameterTypes(),
		                                   method.getReturnType());
	}

	/**
	 * @return
	 */
	static String getMethodAttributeNameOfSet(final String methodName,
	                                          final Class<?>[] args,
	                                          final Class<?> valueType) {
		if (!valueType.equals(Void.TYPE) || args.length != 1) {
			return null;
		}

		if (methodName.length() > 3 && methodName.startsWith("set")) {
			return StringAssist.format("%s%s",
			                           Character.toLowerCase(methodName.charAt(3)),
			                           methodName.substring(4));
		}

		return null;
	}

	/**
	 * @param type
	 * @return
	 */
	static boolean isVoidType(Type type) {
		GenericDefine define = getGenericDefine(type);

		if (define.getDefineType() == GenericDefineType.RAW) {
			GenericDefineForRawType df = (GenericDefineForRawType) define;
			return df.getClazz() == void.class || df.getClazz() == Void.class;
		}
		return false;
	}

	/**
	 * @param type
	 * @return
	 */
	static GenericDefine getGenericDefine(final Type type) {
		if (type instanceof ParameterizedType) {
			return new GenericDefineForParameterizedType((ParameterizedType) type);

		}
		if (type instanceof TypeVariable) {
			return new GenericDefineForTypeVariable((TypeVariable<?>) type);
		}

		if (type instanceof WildcardType) {
			return new GenericDefineForWildcardType((WildcardType) type);
		}

		if (type instanceof GenericArrayType) {
			return new GenericDefineForGenericArrayType((GenericArrayType) type);
		}

		if (type instanceof Class) {
			Class<?> clazz = (Class<?>) type;

			GenericDefineProxy defineProxy = clazz.getAnnotation(GenericDefineProxy.class);

			return defineProxy == null
			       ? new GenericDefineForRawType(clazz)
			       : new GenericDefineForParameterizedType(new ParameterizedTypeProxy(type,
			                                                                          defineProxy));
		}

		return null;
	}

	/**
	 * @param type
	 * @param index
	 * @return
	 */
	static Class<?> getParameterType(Type type,
	                                 int index) {
		final GenericDefine tv = getGenericDefine(type);

		return tv.getParameterType(index);
	}

	static Class<?>[] getParameterTypes(Type type) {
		final GenericDefine tv = getGenericDefine(type);

		return tv.getParameterTypes();
	}

	static GenericDefine[] getGenericDefines(final Type[] types) {
		final int length = types == null
		                   ? 0
		                   : types.length;

		GenericDefine[] generic = new GenericDefine[length];
		for (int i = 0; i < length; i++) {
			generic[i] = getGenericDefine(types[i]);
		}

		return generic;
	}
}
