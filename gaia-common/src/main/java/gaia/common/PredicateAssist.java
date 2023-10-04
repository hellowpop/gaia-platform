package gaia.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import gaia.common.GWCommon.AttributeContextImpl;
import gaia.common.GWCommon.EntryNotFoundException;

public interface PredicateAssist {
	Logger log = LoggerFactory.getLogger(PredicateAssist.class);

	@Target({ElementType.TYPE})
	@Retention(RetentionPolicy.RUNTIME)
	@interface PredicateKey {
		String value();
	}

	@Setter
	@Getter
	@NoArgsConstructor
	@JsonInclude(Include.NON_NULL)
	class PredicateMeta
			extends AttributeContextImpl {
		String type;
		Map<String, Object> attributes;
		List<PredicateMeta> predicates;
	}

	interface PredicateComposer<T> {
		Predicate<T> compose(PredicateMeta meta);
	}


	abstract class PredicateMetaRegistry<T> {
		final PredicateComposer<T> and = meta -> {
			if (CollectionSpec.predicate_empty_collection.test(meta.getPredicates())) {
				throw new IllegalArgumentException("target predicates is empty!");
			}

			PredicateBuilder<T> builder = predicateBuilder();

			meta.getPredicates()
			    .forEach(candidate -> builder.and(PredicateMetaRegistry.this.compile(candidate)));

			return builder.build();
		};

		final PredicateComposer<T> or = meta -> {
			if (CollectionSpec.predicate_empty_collection.test(meta.getPredicates())) {
				throw new IllegalArgumentException("target predicates is empty!");
			}

			PredicateBuilder<T> builder = predicateBuilder();

			meta.getPredicates()
			    .forEach(candidate -> builder.or(PredicateMetaRegistry.this.compile(candidate)));

			return builder.build();
		};
		protected final Class<T> targetType;
		final Map<String, PredicateComposer<T>> composers = new HashMap<>();

		@SuppressWarnings("unchecked")
		protected PredicateMetaRegistry() {
			super();
			this.targetType = (Class<T>) ReflectAssist.getGenericParameter(this.getClass(),
			                                                               PredicateMetaRegistry.class,
			                                                               "T");
		}

		protected final void registerComposer(final String name,
		                                      final PredicateComposer<T> composer) {
			Optional.ofNullable(this.composers.put(name,
			                                       composer))
			        .ifPresent(old -> log.warn(StringAssist.format("PredicateComposer [%s] change from [%s] to [%s]",
			                                                       name,
			                                                       old.getClass()
			                                                          .getName(),
			                                                       composer.getClass()
			                                                               .getName())));
		}

		public final void loadComposers(Class<?> target) {
			if (target.isInterface()) {
				ReflectAssist.streamMemberTypes(target)
				             .filter(type -> type.isAnnotationPresent(PredicateKey.class))
				             .filter(PredicateComposer.class::isAssignableFrom)
				             .filter(type -> {
					             final Class<?> predicateType = ReflectAssist.getGenericParameter(type,
					                                                                              PredicateComposer.class,
					                                                                              "T");

					             return predicateType.isAssignableFrom(this.targetType);
				             })
				             .forEach(type -> {
					             final String key = type.getAnnotation(PredicateKey.class)
					                                    .value();
					             PredicateComposer<T> composer = this.createComposer(type);

					             this.registerComposer(key,
					                                   composer);
				             });
			}
		}

		@SuppressWarnings("unchecked")
		protected PredicateComposer<T> createComposer(Class<?> composerType) {
			return (PredicateComposer<T>) ReflectAssist.createInstance(composerType,
			                                                           PredicateComposer.class);
		}

		public final Predicate<T> compile(final PredicateMeta meta) {
			if (StringUtils.isEmpty(meta.getType())) {
				throw new IllegalArgumentException("meta type undefined!");
			}

			switch (meta.getType()) {
				case "and": {
					return this.and.compose(meta);
				}

				case "or": {
					return this.or.compose(meta);
				}

				default: {
					break;
				}
			}

			return this.composerOf(meta.getType())
			           .compose(meta);
		}

		protected PredicateComposer<T> composerOf(String type) {
			return Optional.ofNullable(this.composers.get(type))
			               .orElseThrow(() -> new EntryNotFoundException(type,
			                                                             "predicate domain"));
		}
	}

	static <T> PredicateBuilder<T> predicateBuilder() {
		return new PredicateBuilder<>();
	}

	static <T> PredicateBuilder<T> predicateBuilder(Predicate<T> predicate) {
		return new PredicateBuilder<>(predicate);
	}

	class PredicateBuilder<T>
			extends AtomicReference<Predicate<T>> {
		public PredicateBuilder() {
			super();
		}

		public PredicateBuilder(Predicate<T> predicate) {
			super();
			this.and(predicate);
		}

		public PredicateBuilder<T> or(Predicate<T> predicate) {
			if (predicate != null) {
				this.updateAndGet(before -> Objects.isNull(before)
				                            ? predicate
				                            : before.or(predicate));
			}
			return this;
		}

		public PredicateBuilder<T> and(Predicate<T> predicate) {
			if (predicate != null) {
				this.updateAndGet(before -> Objects.isNull(before)
				                            ? predicate
				                            : before.and(predicate));
			}
			return this;
		}

		public PredicateBuilder<T> negateAnd(Predicate<T> predicate) {
			return this.and(predicate.negate());
		}

		public Predicate<T> build() {
			return this.updateAndGet(before -> Objects.isNull(before)
			                                   ? candidate -> true
			                                   : before);
		}
	}
}
