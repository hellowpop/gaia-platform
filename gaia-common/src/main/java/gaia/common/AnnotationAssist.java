package gaia.common;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gaia.ConstValues.CrashedException;
import gaia.common.GenericSpec.ClassMeta;
import gaia.common.PrimitiveTypes.ObjectType;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;

/**
 * @author sklee
 */
public interface AnnotationAssist {
	Logger log = LoggerFactory.getLogger(AnnotationAssist.class);

	abstract class AnnotationAssistMethod {
		private AnnotationAssistMethod() {

		}

		static Optional<Class<?>> hasClass0(final AnnotatedElement element) {
			if (element instanceof Class) {
				return Optional.of((Class<?>) element);
			}

			if (element instanceof ClassMeta) {
				return Optional.of(((ClassMeta) element).getOrigin());
			}
			return Optional.empty();
		}

		static <T extends Annotation> T scan1(final AnnotatedElement element,
		                                      final Class<T> type,
		                                      final Set<Annotation> hash) {
			if (element == Object.class) {
				return null;
			}

			Optional<T> find = Arrays.stream(element.getAnnotations())
			                         // for duplicate check
			                         .filter(annotation -> {
				                         if (hash.contains(annotation)) {
					                         return false;
				                         }
				                         hash.add(annotation);
				                         return true;
			                         })
			                         .map(annotation -> {
				                         if (type.isInstance(annotation)) {
					                         return type.cast(annotation);
				                         }
				                         return scan1(annotation.annotationType(),
				                                      type,
				                                      hash);
			                         })
			                         .filter(Objects::nonNull)
			                         .findFirst();

			return find.orElseGet(() -> hasClass0(element).map(clazz -> {
				                                              for (Class<?> interfaceType : clazz.getInterfaces()) {
					                                              T superValue = scan1(interfaceType,
					                                                                   type,
					                                                                   hash);
					                                              if (null != superValue) {
						                                              return superValue;
					                                              }
				                                              }

				                                              Class<?> superType = clazz.getSuperclass();

				                                              if (Objects.nonNull(superType) && superType != Object.class) {
					                                              return scan1(superType,
					                                                           type,
					                                                           hash);
				                                              }
				                                              return null;
			                                              })
			                                              .orElse(null));
		}

		static <T extends Annotation> void scan0(final AnnotatedElement element,
		                                         final Class<T> type,
		                                         final Set<Annotation> hash,
		                                         final Consumer<T> consumer) {
			if (element == Object.class) {
				return;
			}

			// scan for annotation type
			Arrays.stream(element.getAnnotations())
			      // for duplicate check
			      .filter(annotation -> {
				      if (hash.contains(annotation)) {
					      return false;
				      }
				      hash.add(annotation);

				      scan0(annotation.annotationType(),
				            type,
				            hash,
				            consumer);

				      return true;
			      })
			      // check target type
			      .filter(type::isInstance)
			      // map target type
			      .map(type::cast)
			      .forEach(consumer);

			hasClass0(element).ifPresent(clazz -> {
				Stream.of(clazz.getInterfaces())
				      .forEach(interfaceType -> scan0(interfaceType,
				                                      type,
				                                      hash,
				                                      consumer));

				Optional.ofNullable(clazz.getSuperclass())
				        .filter(map -> !map.equals(Object.class))
				        .ifPresent(candidate -> scan0(candidate,
				                                      type,
				                                      hash,
				                                      consumer));
			});
		}

		static boolean findInAnnotation0(final AnnotatedElement element,
		                                 final Class<? extends Annotation> type,
		                                 final Set<Annotation> hash) {
			Annotation[] methodAnnotation = element.getAnnotations();

			for (final Annotation candidate : methodAnnotation) {
				if (type.isInstance(candidate)) {
					return true;
				} else {
					hash.clear();
					if (scan1(candidate.annotationType(),
					          type,
					          hash) != null) {
						return true;
					}
				}
			}

			return false;
		}

		static Field scanField0(final Class<?> clazz,
		                        final Class<? extends Annotation> type,
		                        final Set<Annotation> hash,
		                        final Consumer<Field> listener) {
			Field[] fields = clazz.getDeclaredFields();
			for (final Field field : fields) {
				if (findInAnnotation0(field,
				                      type,
				                      hash)) {
					if (listener == null) {
						return field;
					}
					listener.accept(field);
				}
			}

			Class<?> superClass = clazz.getSuperclass();

			if (superClass != null && superClass != Object.class) {
				Field value = scanField0(superClass,
				                         type,
				                         hash,
				                         listener);
				if (listener == null) {
					return value;
				}
			}

			return null;
		}

		static Method scanMethod0(final Class<?> clazz,
		                          final Class<? extends Annotation> type,
		                          final Set<Annotation> hash,
		                          final Consumer<Method> listener) {
			final Method[] methods = clazz.getDeclaredMethods();
			for (final Method method : methods) {
				if (findInAnnotation0(method,
				                      type,
				                      hash)) {
					if (listener == null) {
						return method;
					}
					listener.accept(method);
				}
			}

			Class<?> subClazz = clazz.getSuperclass();

			if (subClazz != null) {
				Method value = scanMemberMethod(subClazz,
				                                type,
				                                listener);
				if (listener == null) {
					return value;
				}
			}

			return null;
		}
	}

	static <T extends Annotation> void scanAnnotation(final AnnotatedElement element,
	                                                  final Class<T> type,
	                                                  final Consumer<T> consumer) {
		AnnotationAssistMethod.scan0(element,
		                             type,
		                             new HashSet<>(),
		                             consumer);

		Optional.ofNullable(type.getAnnotation(Repeatable.class))
		        .map(Repeatable::value)
		        .ifPresent(repeat -> Optional.ofNullable(FunctionAssist.supply(() -> repeat.getMethod("value"),
		                                                                       throwable -> null))
		                                     .ifPresent(method -> AnnotationAssistMethod.scan0(element,
		                                                                                       repeat,
		                                                                                       new HashSet<>(),
		                                                                                       composite -> {
			                                                                                       Object compositeValue = FunctionAssist.supply(() -> method.invoke(composite));

			                                                                                       CollectionSpec.iterateAnonymous(compositeValue,
			                                                                                                                       value -> {
				                                                                                                                       if (type.isInstance(value)) consumer.accept(type.cast(value));
			                                                                                                                       });
		                                                                                       })));
	}

	static void scanAnnotation(final AnnotatedElement element,
	                           final Consumer<Annotation> consumer) {
		final List<Annotation> annotations = new ArrayList<>();
		AnnotationAssistMethod.scan0(element,
		                             Annotation.class,
		                             new HashSet<>(),
		                             annotations::add);

		annotations.forEach(annotation -> {
			// find repeatable
			final Class<?> annotationType = annotation.annotationType();

			final Method method = FunctionAssist.supply(() -> annotationType.getMethod("value"),
			                                            throwable -> null);

			if (null != method) {
				final Class<?> methodReturnType = method.getReturnType();
				final Class<?> methodComponentType = methodReturnType.getComponentType();

				if (methodReturnType.isArray() && Annotation.class.isAssignableFrom(methodComponentType)) {
					final Repeatable repeatable = methodComponentType.getAnnotation(Repeatable.class);
					if (null != repeatable && repeatable.value()
					                                    .equals(annotationType)) {
						Object compositeValue = FunctionAssist.supply(() -> method.invoke(annotation));

						CollectionSpec.iterateAnonymous(compositeValue,
						                                value -> {
							                                if (value instanceof Annotation) consumer.accept((Annotation) value);
						                                });
						return;
					}
				}
			}


			consumer.accept(annotation);
		});
	}

	static boolean isPresent(final AnnotatedElement element,
	                         final Class<? extends Annotation> type) {
		return AnnotationAssistMethod.scan1(element,
		                                    type,
		                                    new HashSet<>()) != null;
	}

	static <T extends Annotation> T getAnnotation(final AnnotatedElement element,
	                                              final Class<T> type) {
		return AnnotationAssistMethod.scan1(element,
		                                    type,
		                                    new HashSet<>());
	}

	static <T extends Annotation> Optional<T> refAnnotation(final AnnotatedElement element,
	                                                        final Class<T> type) {
		return Optional.ofNullable(getAnnotation(element,
		                                         type));
	}

	static void scanMemberField(final Class<?> clazz,
	                            final Class<? extends Annotation> type,
	                            final Consumer<Field> listener) {
		AnnotationAssistMethod.scanField0(clazz,
		                                  type,
		                                  new HashSet<>(),
		                                  listener);
	}

	static Stream<Field> streamMemberField(final Class<?> clazz,
	                                       final Class<? extends Annotation> type) {
		final List<Field> fields = new ArrayList<>();

		AnnotationAssistMethod.scanField0(clazz,
		                                  type,
		                                  new HashSet<>(),
		                                  fields::add);

		return fields.stream();
	}

	/**
	 * @param clazz
	 * @param type
	 * @param listener
	 * @return
	 */
	static Method scanMemberMethod(final Class<?> clazz,
	                               final Class<? extends Annotation> type,
	                               final Consumer<Method> listener) {
		return AnnotationAssistMethod.scanMethod0(clazz,
		                                          type,
		                                          new HashSet<>(),
		                                          listener);
	}

	static Stream<Method> streamMemberMethod(final Class<?> clazz,
	                                         final Class<? extends Annotation> type) {
		final List<Method> methods = new ArrayList<>();

		AnnotationAssistMethod.scanMethod0(clazz,
		                                   type,
		                                   new HashSet<>(),
		                                   methods::add);

		return methods.stream();
	}

	/**
	 * TODO : runtime context 로딩하면서 발생하는 meta 를 변환하여 사용하도록
	 *
	 * @param <T>
	 */
	class AnnotationProxyMeta<T extends Annotation> {
		public static <TP extends Annotation> AnnotationProxyMeta<TP> of(final Class<TP> type) {
			return new AnnotationProxyMeta<>(type);
		}

		final ProxyFactory factory = new ProxyFactory();
		final List<String> mandatory = new ArrayList<>();
		final Class<T> type;

		AnnotationProxyMeta(Class<T> type) {
			this.type = type;
			this.factory.setSuperclass(Object.class);
			this.factory.setInterfaces(new Class[]{type});

			final Method[] methods = type.getDeclaredMethods();

			for (final Method method : methods) {
				if (method.getDefaultValue() == null) {
					this.mandatory.add(method.getName());
				}
			}
		}

		public T proxy(Object values) {
			final Map<String, Object> map = CollectionSpec.toMap(values);

			try {
				return type.cast(FunctionAssist.supply(() -> this.factory.create(new Class<?>[0],
				                                                                 new Object[0],
				                                                                 new AnnotationProxyMethodHandler(map))));
			}
			catch (Throwable throwable) {
				// after exception define
				throw new CrashedException("proxy create error",
				                           throwable);
			}
		}

		final class AnnotationProxyMethodHandler
				implements MethodHandler {
			final Map<String, Object> values;

			public AnnotationProxyMethodHandler(final Map<String, Object> values) {
				super();
				this.values = values;
			}

			@Override
			public String toString() {
				return StringAssist.format("@%s(%s)",
				                           AnnotationProxyMeta.this.type.getSimpleName(),
				                           this.values.toString());
			}

			@Override
			public Object invoke(Object self,
			                     Method thisMethod,
			                     Method proceed,
			                     Object[] args) throws Throwable {
				if (thisMethod.getName()
				              .equals("annotationType")) {
					return AnnotationProxyMeta.this.type;
				}

				if (thisMethod.getDeclaringClass()
				              .equals(Object.class)) {
					return thisMethod.invoke(this,
					                         args);
				}

				final String key = thisMethod.getName();

				Object value = this.values.get(key);

				if (value == null) {
					value = thisMethod.getDefaultValue();
				}

				if (value == null) {
					// todo event broad cast
					throw new CrashedException("MANDATORY_VALUE_MISSED");
				}

				if (thisMethod.getReturnType()
				              .isInstance(value)) {
					return value;
				}

				final Class<?> returnType = thisMethod.getReturnType();

				if (returnType.isInstance(value)) {
					return value;
				}

				final ObjectType objectType = PrimitiveTypes.determine(returnType);

				return objectType.cast(value);
			}
		}
	}
}
