package gaia.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import lombok.Getter;

import gaia.ConstValues.CrashedException;
import gaia.common.CharsetSpec.CharSets;
import gaia.common.ConcurrentAssist.ConditionedLock;
import gaia.common.GWCommon.AttributeAccessorContainer;
import gaia.common.GWCommon.AttributeContextAccessor;

/**
 * @author dragon
 * @since 2022. 02. 09.
 */
public interface PrimitiveTypes {
	Logger log = LoggerFactory.getLogger(PrimitiveTypes.class);

	ConcurrentHashMap<Class<?>, ObjectType> typeCache = new ConcurrentHashMap<>();
	ConcurrentHashMap<String, ObjectType> nameCache = new ConcurrentHashMap<>();

	DecimalFormatter DEFAULT_DECIMAL_FORMATTER = new DecimalFormatter() {
		@Override
		public Number parse(String source) {
			throw new Error("CRASH!");
		}

		@Override
		public String format(Object number) {
			return number.toString();
		}
	};

	ConcurrentHashMap<String, DecimalFormatter> numberFormatCache = new ConcurrentHashMap<>();

	Pattern FLOAT_DIGIT_CHECKER = Pattern.compile("[^0-9.Ee+\\-]");

	Pattern PURE_DIGIT_CHECKER = Pattern.compile("^[+\\-]?[0-9]+$");

	@Target({ElementType.TYPE,
	         ElementType.FIELD})
	@Retention(RetentionPolicy.RUNTIME)
	@interface Casting {
		Class<? extends CastingSupport> value();
	}

	static CastingSupport detectCastingSupport(Casting casting,
	                                           Function<Class<? extends CastingSupport>, CastingSupport> function) {
		return function.apply(null == casting
		                      ? null
		                      : casting.value());
	}

	interface CastingSupport {
		<T> T cast(Object obj,
		           Class<T> type);

		<T> T decode(String obj,
		             Class<T> type);

		<T> String encode(Object obj,
		                  Class<T> type);
	}

	interface CastingCodec {
		Class<?> getType();

		Class<?> getPrimitiveType();

		default CastingCodec typeFor(Class<?> type) {
			return this.getType() == type || (this.getPrimitiveType() != null && this.getPrimitiveType() == type)
			       ? this
			       : null;
		}

		Object cast(Object obj);

		Object decode(String string);

		String encode(Object obj);
	}

	@SuppressWarnings("unchecked")
	abstract class CastingCodecBase<T>
			implements CastingCodec {
		@Getter
		final Class<T> type;

		@Getter
		final Class<?> primitiveType;

		protected CastingCodecBase() {
			super();
			this.type = (Class<T>) ReflectAssist.getGenericParameter(this.getClass(),
			                                                         CastingCodecBase.class,
			                                                         "T");
			this.primitiveType = ReflectAssist.getFieldValue(this.type,
			                                                 "TYPE",
			                                                 Class.class);
		}

		@Override
		public Object decode(String obj) {
			return this.cast(obj);
		}

		@Override
		public String encode(Object obj) {
			return obj.toString();
		}
	}

	class CharsetCastingCodec
			extends CastingCodecBase<Charset> {
		@Override
		public Object cast(Object obj) {
			return Charset.forName(String.valueOf(obj));
		}
	}

	enum ObjectCategory {
		bool,
		string,
		integer,
		numeric,
		date,
		collection,
		dictionary,
		object,
		anonymous
	}

	enum ObjectType
			implements CastingCodec {
		BOOLEAN("boolean",
		        ObjectCategory.bool,
		        Boolean.class,
		        1,
		        false) {
			@Override
			Object cast0(Object value) {
				return getBoolean(value);
			}
		},
		CHAR("char",
		     ObjectCategory.string,
		     Character.class,
		     -1,
		     '\0') {
			@Override
			Object cast0(Object value) {
				return getCharacter(value);
			}

			@Override
			String encode0(Object target) {
				Character character = (Character) target;

				if (character == '\0') {
					return null;
				}

				return target.toString();
			}

			@Override
			Object decode0(String target) {
				return target.length() == 1
				       ? target.charAt(0)
				       : (char) Integer.parseInt(target);
			}

			@Override
			int size0(Object value) {
				Character character = (Character) value;

				if (character == '\0') {
					return 0;
				}

				return primitiveEventAccessor.CHARSETS_ACCESSOR.reference()
				                                               .orElse(CharSets.SYS_DEPEND)
				                                               .getBytes(value.toString()).length;
			}
		},
		BYTE("byte",
		     ObjectCategory.integer,
		     Byte.class,
		     1,
		     0) {
			@Override
			Object cast0(Object value) {
				return numberOf(value,
				                Byte.class).byteValue();

			}
		},
		SHORT("short",
		      ObjectCategory.integer,
		      Short.class,
		      2,
		      0) {
			@Override
			Object cast0(Object value) {
				return numberOf(value,
				                Short.class).shortValue();
			}
		},
		INTEGER("int",
		        ObjectCategory.integer,
		        Integer.class,
		        4,
		        0) {
			@Override
			Object cast0(Object value) {
				return numberOf(value,
				                Integer.class).intValue();
			}
		},
		LONG("long",
		     ObjectCategory.integer,
		     Long.class,
		     8,
		     0) {
			@Override
			Object cast0(Object value) {
				return numberOf(value,
				                Long.class).longValue();
			}
		},
		FLOAT("float",
		      ObjectCategory.numeric,
		      Float.class,
		      4,
		      0) {
			@Override
			Object cast0(Object value) {
				return numberOf(value,
				                Float.class).floatValue();
			}
		},
		DOUBLE("double",
		       ObjectCategory.numeric,
		       Double.class,
		       8,
		       0) {
			@Override
			Object cast0(Object value) {
				return numberOf(value,
				                Double.class).doubleValue();
			}
		},
		BIG_DECIMAL("decimal",
		            ObjectCategory.numeric,
		            BigDecimal.class,
		            -1,
		            null) {
			@Override
			Object cast0(Object value) {
				return numberOf(checkFloatDigit(value.toString()),
				                BigDecimal.class);
			}
		},
		BIG_INTEGER("numeric",
		            ObjectCategory.integer,
		            BigInteger.class,
		            -1,
		            null) {
			@Override
			Object cast0(Object value) {
				return numberOf(checkPureDigit(value.toString()),
				                BigInteger.class);
			}
		},
		STRING("string",
		       ObjectCategory.string,
		       String.class,
		       -1,
		       null) {
			@Override
			Object cast0(Object value) {
				return value.toString();
			}

			@Override
			int size0(Object value) {
				return primitiveEventAccessor.CHARSETS_ACCESSOR.reference()
				                                               .orElse(CharSets.SYS_DEPEND)
				                                               .getBytes(value.toString()).length;
			}
		},
		DATE("date",
		     ObjectCategory.date,
		     Date.class,
		     8,
		     null) {
			@Override
			Object cast0(Object value) {
				return DateTimeAssist.dateOf(value);
			}
		},
		SQL_TIMESTAMP("timestamp",
		              ObjectCategory.date,
		              Timestamp.class,
		              8,
		              null) {
			@Override
			Object cast0(Object value) {
				return Optional.ofNullable(DateTimeAssist.dateOf(value))
				               .map(date -> new Timestamp(date.getTime()))
				               .orElse(null);
			}
		},
		SQL_DATE("sql_date",
		         ObjectCategory.date,
		         java.sql.Date.class,
		         8,
		         null) {
			@Override
			Object cast0(Object value) {
				return Optional.ofNullable(DateTimeAssist.dateOf(value))
				               .map(date -> new java.sql.Date(date.getTime()))
				               .orElse(null);
			}
		},
		SQL_TIME("sql_time",
		         ObjectCategory.date,
		         java.sql.Time.class,
		         8,
		         null) {
			@Override
			Object cast0(Object value) {
				return Optional.ofNullable(DateTimeAssist.dateOf(value))
				               .map(date -> new java.sql.Time(date.getTime()))
				               .orElse(null);
			}
		},
		ZONED_DATE("zoned_date",
		           ObjectCategory.date,
		           ZonedDateTime.class,
		           8,
		           null) {
			@Override
			Object cast0(Object value) {
				return DateTimeAssist.zonedDate(value);
			}
		},
		DURATION("duration",
		         ObjectCategory.date,
		         Duration.class,
		         8,
		         null) {
			@Override
			Object cast0(Object value) {
				return durationOf(value);
			}
		},
		COLLECTION("collection",
		           ObjectCategory.collection,
		           Collection.class,
		           -1,
		           null) {
			@Override
			Object cast0(Object value) {
				return CollectionSpec.toCollection(value);
			}
		},
		DICTIONARY("dictionary",
		           ObjectCategory.dictionary,
		           Map.class,
		           -1,
		           null) {
			@Override
			Object cast0(Object value) {
				return CollectionSpec.toMap(value);
			}
		},
		VOID_TYPE("void",
		          ObjectCategory.object,
		          Void.class,
		          -1,
		          null) {
			@Override
			Object cast0(Object value) {
				throw new Error("Crash!!");
			}
		},
		ANONYMOUS_TYPE("anonymous",
		               ObjectCategory.anonymous,
		               Void.class,
		               -1,
		               null) {
			@Override
			Object cast0(Object value) {
				return value;
			}
		},
		NULL_TYPE("null",
		          ObjectCategory.object,
		          Void.class,
		          -1,
		          null) {
			@Override
			Object cast0(Object value) {
				throw new Error("Crash!!");
			}
		};

		@Getter
		final Class<?> type;

		@Getter
		final Class<?> primitiveType;

		@Getter
		final String shortName;

		@Getter
		final ObjectCategory category;

		@Getter
		final int dataLength;

		@Getter
		final Object nullValue;

		/**
		 * @param shortName
		 * @param type
		 * @param dataLength
		 * @param nullValue
		 */
		ObjectType(String shortName,
		           ObjectCategory category,
		           Class<?> type,
		           int dataLength,
		           Object nullValue) {
			this.shortName = shortName;
			this.category = category;
			this.type = type;
			this.dataLength = dataLength;
			this.nullValue = nullValue;
			this.primitiveType = ReflectAssist.getFieldValue(type,
			                                                 "TYPE",
			                                                 Class.class);
		}

		@Override
		public final Object cast(final Object value) {
			if (value == null) {
				return this.nullValue;
			}
			if (this.type.isInstance(value)) {
				return value;
			}

			final Class<?> filteredType = this.primitiveType == null
			                              ? this.type
			                              : this.primitiveType;

			final Object filtered = value instanceof Iterable
			                        ? CollectionSpec.toArrayObject((Iterable<?>) value,
			                                                       Object.class)
			                        : value;
			if (filtered.getClass()
			            .isArray()) {
				final int length = Array.getLength(filtered);

				final Optional<Object> detector = IntStream.range(0,
				                                                  length)
				                                           .mapToObj(index -> Array.get(filtered,
				                                                                        index))
				                                           .filter(candidate -> !this.type.isInstance(candidate))
				                                           .findFirst();
				// scan and need convert
				if (detector.isPresent()) {
					Object arrayInstance = Array.newInstance(filteredType,
					                                         length);
					for (int i = 0; i < length; i++) {
						Object element = Array.get(filtered,
						                           i);

						Array.set(arrayInstance,
						          i,
						          this.cast0(element));
					}

					return arrayInstance;
				}
				return filtered;
			}

			return this.type.isInstance(value)
			       ? value
			       : this.cast0(value);
		}

		/**
		 * @param value
		 * @return
		 */
		abstract Object cast0(Object value);

		/**
		 * stringify source object
		 *
		 * @param source stringify target
		 * @return stringify result
		 */
		@Override
		public final String encode(Object source) {
			// filter null source
			return null == source
			       ? null
			       : this.encode0(source);
		}

		String encode0(Object source) {
			// case of number or not
			return source instanceof Number
			       ? numberFormat(source)
			       : source.toString();
		}

		/**
		 * load object from string
		 *
		 * @param source source string
		 * @return
		 */
		@Override
		public final Object decode(String source) {
			return StringUtils.isEmpty(source)
			       ? this.nullValue
			       : this.decode0(source);
		}

		Object decode0(String source) {
			return this.cast(source);
		}

		public final int size(Object value) {
			return Objects.isNull(value)
			       ? 0
			       : this.size0(value);
		}

		int size0(Object value) {
			if (this.dataLength < 0) {
				throw new Error("");
			}

			return this.dataLength;
		}

		boolean isFor(Class<?> target) {
			return target.isArray()
			       ? this.isFor0(target.getComponentType())
			       : this.isFor0(target);
		}

		boolean isFor(String name) {
			return StringUtils.equalsIgnoreCase(this.shortName,
			                                    name);
		}

		boolean isAssignableFrom(Class<?> target) {
			return target.isArray()
			       ? this.isAssignableFrom0(target.getComponentType())
			       : this.isAssignableFrom0(target);
		}

		/**
		 * @param target
		 * @return
		 */
		boolean isAssignableFrom0(Class<?> target) {
			return this.type.isAssignableFrom(target) || (this.primitiveType != null && this.primitiveType.isAssignableFrom(target));
		}

		boolean isFor0(Class<?> target) {
			return (this.type == target || this.type.isAssignableFrom(target)) || (this.primitiveType != null && this.primitiveType == target);
		}
	}

	static boolean isSameType(Class<?> source,
	                          Class<?> target) {
		return determine(source) == determine(target);
	}

	static ObjectType determine(Object instance) {
		if (instance == null) {
			return ObjectType.NULL_TYPE;
		}

		return determine(instance.getClass());
	}

	static ObjectType determine(Class<?> type) {
		return typeCache.computeIfAbsent(type,
		                                 key -> key.equals(Object.class)
		                                        ? ObjectType.ANONYMOUS_TYPE
		                                        : Arrays.stream(ObjectType.values())
		                                                .filter(candidate -> candidate.isFor(key))
		                                                .findFirst()
		                                                .orElseGet(() -> Arrays.stream(ObjectType.values())
		                                                                       .filter(candidate -> candidate.isAssignableFrom(key))
		                                                                       .findFirst()
		                                                                       .orElse(ObjectType.VOID_TYPE)));
	}

	static ObjectType determine(String typeName) {
		return nameCache.computeIfAbsent(typeName,
		                                 name -> Arrays.stream(ObjectType.values())
		                                               .filter(candidate -> candidate.isFor(name))
		                                               .findFirst()
		                                               .orElse(ObjectType.VOID_TYPE));
	}

	static boolean match(Class<?> type,
	                     ObjectCategory category) {
		return determine(type).getCategory() == category;
	}

	static Object nullValue(Class<?> type) {
		if (!type.isPrimitive()) {
			return null;
		}

		ObjectType pt = determine(type);

		return pt.nullValue;
	}

	static <T> T cast(Class<T> type,
	                  Object instance) {
		ObjectType pt = determine(type);

		return type.cast(pt.cast(instance));
	}

	// ---------------------------------------------------------------------
	// Section utilities
	// ---------------------------------------------------------------------
	static boolean getBoolean(Object value) {
		if (value == null) {
			return Boolean.FALSE;
		}

		if (value instanceof Boolean) {
			return (Boolean) value;
		}

		if (value instanceof Number) {
			return ((Number) value).intValue() != 0;
		}

		return Booleans.of(String.valueOf(value)
		                         .toUpperCase());
	}

	@Getter
	enum Booleans {
		/**
		 *
		 */
		TRUE(true),
		/**
		 *
		 */
		T(true),
		/**
		 *
		 */
		Y(true),
		/**
		 *
		 */
		YES(true),
		/**
		 *
		 */
		FALSE(false),
		/**
		 *
		 */
		F(false),
		/**
		 *
		 */
		N(false),
		/**
		 *
		 */
		NO(false);

		/**
		 *
		 */
		final boolean value;

		Booleans(final boolean bool) {
			this.value = bool;
		}

		static boolean of(String value) {
			return FunctionAssist.supply(() -> Booleans.valueOf(value).value,
			                             throwable -> false);
		}
	}

	static Character getCharacter(Object value) {
		if (value instanceof Character) return (Character) value;

		final String val = value.toString();

		switch (val.length()) {
			case 0: {
				return null;
			}

			case 1: {
				return val.charAt(0);
			}

			default: {
				throw new ClassCastException(StringAssist.format("cannot convert character [%s]",
				                                                 val));
			}
		}
	}

	class PrimitiveEventAccessor
			extends AttributeAccessorContainer {

		public AttributeContextAccessor<DecimalFormatter> DECIMAL_FORMATTER_ACCESSOR;

		public AttributeContextAccessor<CharSets> CHARSETS_ACCESSOR;

		private PrimitiveEventAccessor() {
		}
	}

	PrimitiveEventAccessor primitiveEventAccessor = new PrimitiveEventAccessor();

	static String numberFormat(final Object source) {
		if (Objects.isNull(source)) {
			throw new CrashedException("source cannot be null!");
		}

		return primitiveEventAccessor.DECIMAL_FORMATTER_ACCESSOR.reference()
		                                                        .map(formatter -> formatter.format(source.toString()))
		                                                        .orElseGet(source::toString);
	}

	static Number numberOf(final Object source,
	                       final Class<? extends Number> type) {
		if (Objects.isNull(source)) {
			throw new CrashedException("source cannot be null!");
		}

		if (source instanceof Number) {
			return (Number) source;
		}

		return primitiveEventAccessor.DECIMAL_FORMATTER_ACCESSOR.reference()
		                                                        .map(formatter -> formatter.parse(source.toString()))
		                                                        .orElseGet(() -> numberOf(source.toString(),
		                                                                                  type));
	}

	static Number numberOf(final String source,
	                       final Class<? extends Number> type) {
		assert StringUtils.isNotEmpty(source)
				: "source should have value";

		try {
			if (Byte.class == type || Short.class == type || Integer.class == type || Long.class == type) return Long.parseLong(source);

			if (Float.class == type || Double.class == type) return Double.parseDouble(source);

			if (BigDecimal.class == type) return new BigDecimal(source);
			if (BigInteger.class == type) return new BigInteger(source);
		}
		catch (Exception throwable) {
			throw new IllegalArgumentException(StringAssist.format("numeric parse error [%s]",
			                                                       source),
			                                   throwable);
		}
		throw new IllegalArgumentException(StringAssist.format("unknown numeric type [%s]",
		                                                       type.getName()));
	}

	static Duration durationOf(final Object source) {
		if (Objects.isNull(source)) {
			throw new CrashedException("source cannot be null!");
		}

		if (source instanceof Duration) {
			return (Duration) source;
		}

		return Duration.ofMillis(numberOf(source,
		                                  Long.class).longValue());
	}

	interface DecimalFormatter {
		Number parse(String source);

		String format(Object number);
	}

	class DecimalFormatterImpl
			implements DecimalFormatter {
		final ConditionedLock lock = new ConditionedLock();

		final DecimalFormat format;

		DecimalFormatterImpl(String format) {
			this.format = new DecimalFormat(format);
		}

		@Override
		public Number parse(String source) {
			return this.lock.tran(() -> this.format.parse(source));
		}

		@Override
		public String format(Object number) {
			return this.lock.tran(() -> this.format.format(number));
		}
	}

	static DecimalFormatter getNumberFormat(String format) {
		return StringUtils.isEmpty(format)
		       ? DEFAULT_DECIMAL_FORMATTER
		       : numberFormatCache.computeIfAbsent(format,
		                                           DecimalFormatterImpl::new);
	}

	static String checkFloatDigit(String source) {
		if (FLOAT_DIGIT_CHECKER.matcher(source)
		                       .find()) throw new IllegalArgumentException("invalid float type digit : " + source);

		return source;
	}

	static String checkPureDigit(String source) {
		if (isPureDigit(source)) return source;

		throw new IllegalArgumentException("invalid pure digit : " + source);

	}

	static boolean isPureDigit(String source) {
		return StringUtils.isNotEmpty(source) && PURE_DIGIT_CHECKER.matcher(source)
		                                                           .find();
	}

	// ---------------------------------------------------------------------
	// Section numeric conversion
	// ---------------------------------------------------------------------
	DecimalFormat DEFAULT_INTEGER_FORMAT = new DecimalFormat("#");

	static int parseInteger(final Object source,
	                        final int df) {
		final Number val = parse(source,
		                         Integer.class);
		return val == null
		       ? df
		       : val.intValue();
	}

	static Float getFloat(Object value) {
		final Number val = parse(value,
		                         Float.class);
		return val == null
		       ? null
		       : val.floatValue();
	}

	static Number parse(final Object source,
	                    final Class<? extends Number> type) {
		if (source == null) {
			return null;
		}

		// first number
		if (source instanceof Number) {
			if (checkRange((Number) source,
			               type)) {
				return (Number) source;
			}

			throw new NumberFormatException(StringAssist.format("%s is exceed %s",
			                                                    source,
			                                                    type));
		}

		final String sSource = StringUtils.trim(source.toString());

		if (StringUtils.isEmpty(sSource)) {
			return null;
		}
		// first type parse

		final Number nb = primitiveEventAccessor.DECIMAL_FORMATTER_ACCESSOR.reference()
		                                                                   .map(formatter -> formatter.parse(sSource))
		                                                                   .orElseGet(() -> valueOf(sSource,
		                                                                                            type));
		if (checkRange(nb,
		               type)) {
			return nb;
		}

		throw new NumberFormatException(StringAssist.format("invalid [%s].[%s]",
		                                                    source,
		                                                    type));
	}

	static Number valueOf(final String source,
	                      final Class<? extends Number> type) {
		if (Byte.class == type) {
			return Byte.valueOf(source);
		}
		if (Short.class == type) {
			return Short.valueOf(source);
		}
		if (Integer.class == type) {
			return Integer.valueOf(source);
		}
		if (Long.class == type) {
			return Long.valueOf(source);
		}
		if (Float.class == type) {
			return Float.valueOf(source);
		}
		if (Double.class == type) {
			return Double.valueOf(source);
		}

		throw new IllegalArgumentException(StringAssist.format("unknown numeric type [%s]",
		                                                       type.getName()));
	}

	static boolean checkRange(final Number source,
	                          final Class<? extends Number> type) {
		if (source instanceof BigDecimal || source instanceof BigInteger) {
			return true;
		}

		if (Double.isInfinite(source.doubleValue()) || Double.isNaN(source.doubleValue())) {
			throw new NumberFormatException(StringAssist.format("invalid [%s].[%s]",
			                                                    source,
			                                                    type));
		}

		if (source.getClass() == type) {
			return true;
		}
		if (Byte.class == type) {
			return source.longValue() >= Byte.MIN_VALUE && source.longValue() <= Byte.MAX_VALUE;
		}
		if (Short.class == type) {
			return source.longValue() >= Short.MIN_VALUE && source.longValue() <= Short.MAX_VALUE;
		}
		if (Integer.class == type) {
			return source.longValue() >= Integer.MIN_VALUE && source.longValue() <= Integer.MAX_VALUE;
		}
		if (Long.class == type) {
			try {
				Long.parseLong(DEFAULT_INTEGER_FORMAT.format(source.doubleValue()));
			}
			catch (NumberFormatException nfe) {
				throw new IllegalArgumentException(StringAssist.format("unknown numeric type [%s]",
				                                                       type.getName()));
			}
			return true;
		}
		// TODO : Float, Double 검증방법 구현
		if (Float.class == type) {
			return true;
		}

		if (Double.class == type) {
			return true;
		}

		if (BigInteger.class == type) {
			return true;
		}

		if (BigDecimal.class == type) {
			return true;
		}

		throw new IllegalArgumentException(StringAssist.format("unknown numeric type [%s]",
		                                                       type.getName()));

	}
}
