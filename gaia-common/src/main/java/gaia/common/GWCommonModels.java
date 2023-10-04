package gaia.common;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import gaia.ConstValues.CrashedException;

public interface GWCommonModels {
	NamespacePackageTypeReference namespacePackageTypeReference = new NamespacePackageTypeReference();

	enum MandatoryPolicy {
		not_null {
			@Override
			public boolean matcher(Object target) {
				return Objects.isNull(target);
			}
		},
		not_empty {
			@Override
			public boolean matcher(Object target) {
				if (Objects.isNull(target)) {
					return true;
				}

				if (target instanceof CharSequence) {
					return ((CharSequence) target).length() == 0;
				}

				if (target instanceof Collection) {
					return ((Collection<?>) target).isEmpty();
				}

				if (target instanceof Map) {
					return ((Map<?, ?>) target).isEmpty();
				}

				return true;
			}
		},
		not_blank {
			@Override
			public boolean matcher(Object target) {
				if (Objects.isNull(target)) {
					return true;
				}
				if (target instanceof CharSequence) {
					return ((CharSequence) target).toString()
					                              .trim()
					                              .length() == 0;
				}
				return true;
			}
		};

		public abstract boolean matcher(Object target);
	}

	@Target(FIELD)
	@Retention(RUNTIME)
	@interface FieldName {
		String value();
	}

	@Target(FIELD)
	@Retention(RUNTIME)
	@interface FieldDescription {
		String value();
	}

	@Target(FIELD)
	@Retention(RUNTIME)
	@interface FieldDefault {
		String value() default "";

		String env() default "";

		FieldReflect[] reflect() default {};

		boolean singleton() default true;

		boolean create() default false;
	}

	@Target({FIELD,
	         ANNOTATION_TYPE})
	@Retention(RUNTIME)
	@interface FieldReflect {
		Class<?> c();

		String method() default "";

		String field() default "";
	}

	@Target({FIELD,
	         ANNOTATION_TYPE})
	@Retention(RUNTIME)
	@interface ValueMandatory {
		MandatoryPolicy policy();

		String message();
	}

	@Target({FIELD,
	         ANNOTATION_TYPE})
	@Retention(RUNTIME)
	@ValueMandatory(policy = MandatoryPolicy.not_null,
	                message = "{name} NULL 이 허용되지 않습니다.")
	@interface ValueNotNull {
	}

	@Target({FIELD,
	         ANNOTATION_TYPE})
	@Retention(RUNTIME)
	@ValueMandatory(policy = MandatoryPolicy.not_blank,
	                message = "{name} 공백 문자열은 허용되지 않습니다.")
	@interface ValueNotEmpty {
	}

	@Target({FIELD,
	         ANNOTATION_TYPE})
	@Retention(RUNTIME)
	@Repeatable(ValueRegExpMatchList.class)
	@interface ValueRegExpMatch {
		String regexp();

		String message();

		boolean match() default false;
	}

	@Target({FIELD,
	         ANNOTATION_TYPE})
	@Retention(RUNTIME)
	@ValueRegExpMatch(regexp = "\\s+",
	                  message = "{name} 공백은 허용되지 않습니다.",
	                  match = true)
	@interface BlankNotAllowed {
	}

	@Target({FIELD,
	         ANNOTATION_TYPE})
	@Retention(RUNTIME)
	@ValueRegExpMatch(regexp = "[A-Z]+",
	                  message = "{name} 대분자열은 포함되어야합니다.")
	@interface ContainUpperCase {
	}

	@Target({FIELD,
	         ANNOTATION_TYPE})
	@Retention(RUNTIME)
	@ValueRegExpMatch(regexp = "[a-z]+",
	                  message = "{name} 소분자열은 포함되어야합니다.")
	@interface ContainLowerCase {
	}

	@Target({FIELD,
	         ANNOTATION_TYPE})
	@Retention(RUNTIME)
	@ValueRegExpMatch(regexp = "[0-9]+",
	                  message = "{name} 숫자열은 포함되어야합니다.")
	@interface ContainDigit {
	}

	@Target(FIELD)
	@Retention(RUNTIME)
	@interface ValueRegExpMatchList {
		ValueRegExpMatch[] value();
	}

	@Target({FIELD,
	         ANNOTATION_TYPE})
	@Retention(RUNTIME)
	@interface MaxValue {
		long value();

		String message() default "{name}은 {value}보다 클 수 없습니다.";
	}

	@Target({FIELD,
	         ANNOTATION_TYPE})
	@Retention(RUNTIME)
	@interface MinValue {
		long value();

		String message() default "{name}은 {value}보다 작을 수 없습니다.";
	}

	@Target({FIELD,
	         ANNOTATION_TYPE})
	@Retention(RUNTIME)
	@interface MaxLength {
		int value();

		String message() default "{name} 길이가 {value}보다 클 수 없습니다.";
	}

	@Target({FIELD,
	         ANNOTATION_TYPE})
	@Retention(RUNTIME)
	@interface MinLength {
		int value();

		String message() default "{name} 길이가 {value}보다 작을 수 없습니다.";
	}

	class NamespacePackageTypeReference
			extends TypeReference<List<NamespacePackage>> {
	}

	//
	@Setter
	@Getter
	@NoArgsConstructor
	@JsonInclude(Include.NON_NULL)
	class NameSpaceDefine {
		@FieldName("서비스 namespace")
		String namespace;
		@FieldName("서비스 namespace 설명")
		String description;
		@FieldName("서비스 namespace 속성목록")
		Collection<NameSpaceProperty> properties;
	}

	@Setter
	@Getter
	@NoArgsConstructor
	@JsonInclude(Include.NON_NULL)
	class NameSpaceProperty {
		@FieldName("서비스 속성 타입")
		String type;
		@FieldName("서비스 속성 이름")
		String name;
		@FieldName("서비스 속성 타이틀")
		String title;
		@FieldName("서비스 속성 상세설명")
		String description;
		@FieldName("서비스 속성 기본값")
		String defaultValue;
	}

	@Target(ElementType.TYPE)
	@Retention(RUNTIME)
	@interface ModelNamespace {
		String value();

		String description() default "";
	}

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	class NamespacePackage {
		String name;
		List<NamespaceBundle> bundles;

		public NamespacePackage(String name) {
			this.name = name;
		}

		public void bundle(NamespaceBundle bundle) {
			if (null == this.bundles) this.bundles = new ArrayList<>();
			for (NamespaceBundle element : this.bundles) {
				if (StringUtils.equals(element.getNamespace(),
				                       bundle.getNamespace())) {
					element.merge(bundle);
					return;
				}
			}
			this.bundles.add(bundle);
		}

		public Optional<NamespaceBundle> bundle(String namespace) {
			return this.bundles == null
			       ? Optional.empty()
			       : this.bundles.stream()
			                     .filter(bundle -> StringUtils.equals(namespace,
			                                                          bundle.namespace))
			                     .findFirst();
		}

		public NamespaceBundle open(String namespace) {
			if (null == this.bundles) {
				this.bundles = new ArrayList<>();
			}
			return this.bundles.stream()
			                   .filter(bundle -> StringUtils.equals(namespace,
			                                                        bundle.namespace))
			                   .findFirst()
			                   .orElseGet(() -> {
				                   final NamespaceBundle created = new NamespaceBundle();
				                   created.setNamespace(namespace);
				                   this.bundles.add(created);
				                   return created;
			                   });
		}
	}


	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	class NamespaceBundle {
		String namespace;
		Map<String, Object> properties;

		public boolean isEmpty() {
			return null == this.properties || this.properties.isEmpty();
		}

		public void merge(NamespaceBundle other) {
			if (other.isEmpty()) {
				return;
			}
			if (null == this.properties) {
				this.properties = new HashMap<>();
			}

			this.properties.putAll(other.getProperties());
		}
	}

	interface NamespaceModel {
		default String getNamespace() {
			return namespaceValue(this.getClass());
		}

		default void validate() {
		}
	}

	static <T extends NamespaceModel> String namespaceValue(Class<T> type) {
		return Optional.ofNullable(type.getAnnotation(ModelNamespace.class))
		               .map(ModelNamespace::value)
		               .orElseThrow(() -> new CrashedException("@ModelNamespace not found"));
	}
}
