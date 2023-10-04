package gaia.common;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gaia.common.ASMEngineeringComponent.ASMClassScanContext;
import gaia.common.PredicateAssist.PredicateBuilder;
import gaia.common.ResourceStreamModel.JavaClassResourceStream;
import gaia.common.ResourceStreamModel.ResourceStream;
import gaia.common.URIHandleSpec.URIResource;


public interface ClassScanComponent {
	Logger log = LoggerFactory.getLogger(ClassScanComponent.class);
	Logger logger = LoggerFactory.getLogger(ClassScanComponent.class);

	enum EngineeringType {
		asm {
			@Override
			public ClassScanContext of(InputStream content) {
				return ASMClassScanContext.of(content);
			}
		},
		javassist {
			@Override
			public ClassScanContext of(InputStream content) {
				return null;
			}
		};


		public abstract ClassScanContext of(InputStream content);
	}

	abstract class ClassScanContext {
		public static ClassScanContext of(Class<?> type) {
			if (type.getPackage()
			        .getName()
			        .startsWith("java")) {
				throw new IllegalArgumentException("java native scan context?");
			}
			return of(new JavaClassResourceStream(type));
		}

		public static ClassScanContext of(ResourceStream stream) {
			return of(FunctionAssist.supply(stream::openInputStream));
		}

		public static ClassScanContext of(URIResource resource) {
			try (InputStream in = resource.openInputStream()) {
				return of(in);
			}
			catch (IOException ioe) {
				throw ExceptionAssist.wrap("resource handle error",
				                           ioe);
			}
		}

		public static ClassScanContext of(InputStream in) {
			return of(EngineeringType.asm,
			          in);
		}

		public static ClassScanContext of(EngineeringType engineeringType,
		                                  InputStream content) {
			return engineeringType.of(content);
		}

		protected List<ClassAnnotationInfo> classAnnotationInfos;

		public abstract String getClassName();

		public final String getPackageName() {
			final String name = this.getClassName();
			final int index = name.lastIndexOf('/');
			return index < 0
			       ? null
			       : name.substring(0,
			                        index);
		}

		public abstract String getName();

		public abstract String getNestHost();

		public Optional<ClassAnnotationInfo> getAnnotation(final Class<? extends Annotation> annotationType) {
			return this.getAnnotation(annotationType.getName());
		}

		public Optional<ClassAnnotationInfo> getAnnotation(String name) {
			return null == this.classAnnotationInfos
			       ? Optional.empty()
			       : this.classAnnotationInfos.stream()
			                                  .filter(info -> info.isMatch(name))
			                                  .findFirst();
		}

		public final boolean annotationPresent(final Class<? extends Annotation>[] annotationType) {
			return FunctionAssist.supply(() -> {
				if (null == this.classAnnotationInfos) {
					return false;
				}

				PredicateBuilder<ClassAnnotationInfo> builder = new PredicateBuilder<>();

				for (Class<? extends Annotation> aClass : annotationType) {
					builder.or(info -> info.isMatch(aClass.getName()));
				}

				return this.classAnnotationInfos.stream()
				                                .anyMatch(builder.build());
			});
		}

		public final boolean filterByAnnotation(final Predicate<ClassAnnotationInfo> predicate,
		                                        final Class<? extends Annotation>[] annotationTypes) {
			for (Class<? extends Annotation> annotationType : annotationTypes) {
				if (this.getAnnotation(annotationType)
				        .filter(predicate)
				        .isPresent()) {
					return true;
				}
			}

			return false;
		}
	}

	interface ClassFieldInfo {

	}

	interface ClassAnnotationInfo {
		boolean isMatch(String name);

		Optional<Object> getParam(String name);

		void addParam(String name,
		              Object value);
	}

	static Class<?> forName(final String className) {
		return forName(className,
		               true);
	}

	static Class<?> forName(final String className,
	                        final boolean robust) {
		return forName(Thread.currentThread()
		                     .getContextClassLoader(),
		               className,
		               robust);
	}

	static Class<?> forName(final ClassLoader loader,
	                        final String className,
	                        final boolean robust) {
		return FunctionAssist.supply(() -> loader.loadClass(className),
		                             throwable -> {
			                             if (log.isTraceEnabled()) {
				                             log.trace(StringAssist.format("[%s] find error by [%s]",
				                                                           className,
				                                                           throwable.getMessage()));
			                             }
			                             if (robust) {
				                             throw ExceptionAssist.wrap(StringAssist.format("[%s] detect fail",
				                                                                            className),
				                                                        throwable);
			                             }
			                             return null;
		                             });
	}
}
