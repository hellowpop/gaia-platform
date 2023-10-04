package gaia.common;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;


import lombok.Getter;

import gaia.ConstValues.CrashedException;
import gaia.common.ConcurrentAssist.ConditionedLock;
import gaia.common.GWCommon.AttributeAccessorContainer;
import gaia.common.GWCommon.AttributeContextAccessor;

public interface DateTimeAssist {
	// ---------------------------------------------------------------------
	// Section useful methods
	// ---------------------------------------------------------------------
	static ZonedDateTime longToDate(Long time) {
		return null == time
		       ? null
		       : Instant.ofEpochMilli(time)
		                .atZone(ZoneId.systemDefault());
	}

	static Long dateToLong(ZonedDateTime time) {
		return null == time
		       ? null
		       : time.toInstant()
		             .toEpochMilli();
	}

	static ZonedDateTime timeOf(final long amountToAdd,
	                            final TemporalUnit unitToAdd) {
		return timeOf(amountToAdd,
		              unitToAdd,
		              null);
	}

	static ZonedDateTime timeOf(final long amountToAdd,
	                            final TemporalUnit unitToAdd,
	                            final TemporalUnit unitToTruncate) {
		final AtomicReference<ZonedDateTime> reference = new AtomicReference<>(ZonedDateTime.now());

		if (amountToAdd > 0) {
			reference.updateAndGet(time -> time.plus(amountToAdd,
			                                         unitToAdd));
		}
		if (amountToAdd < 0) {
			reference.updateAndGet(time -> time.minus(Math.abs(amountToAdd),
			                                          unitToAdd));
		}

		Optional.ofNullable(unitToTruncate)
		        .ifPresent(unit -> reference.updateAndGet(time -> time.truncatedTo(unit)));

		return reference.get();
	}

	static int secondsOf() {
		return (int) ZonedDateTime.now()
		                          .toEpochSecond();
	}

	static int secondsOf(final long amountToAdd,
	                     final TemporalUnit unitToAdd) {
		return secondsOf(amountToAdd,
		                 unitToAdd,
		                 null);
	}

	static ZonedDateTime secondOf(long seconds) {
		return secondOf(seconds,
		                ZoneId.systemDefault());
	}

	static ZonedDateTime secondOf(long seconds,
	                              ZoneId zone) {
		return ZonedDateTime.ofInstant(Instant.ofEpochSecond(seconds),
		                               ZoneId.systemDefault());
	}

	static int secondsOf(final long amountToAdd,
	                     final TemporalUnit unitToAdd,
	                     final TemporalUnit unitToTruncate) {
		return (int) timeOf(amountToAdd,
		                    unitToAdd,
		                    unitToTruncate).toEpochSecond();
	}

	static long millisOf(final long amountToAdd,
	                     final TemporalUnit unitToAdd,
	                     final TemporalUnit unitToTruncate) {
		return timeOf(amountToAdd,
		              unitToAdd,
		              unitToTruncate).toInstant()
		                             .toEpochMilli();
	}
	// ---------------------------------------------------------------------
	// Section useful classes
	// ---------------------------------------------------------------------

	class DateFormatAccessor
			extends AttributeAccessorContainer {
		public final Locale DEFAULT_LOCALE = Locale.getDefault();
		final ConcurrentHashMap<String, SimpleDateFormatter> dateFormatCache = new ConcurrentHashMap<>();
		public AttributeContextAccessor<SimpleDateFormatter> DATE_FORMATTER_ACCESSOR;

		public SimpleDateFormatter getDateFormat(String format) {
			return this.dateFormatCache.computeIfAbsent(format,
			                                            SimpleDateFormatter::new);
		}

		private DateFormatAccessor() {
		}
	}

	DateFormatAccessor dateFormatAccessor = new DateFormatAccessor();

	interface DateFormatter {
		String getTextFormat();

		String getFormatterFormat();

		Date parse(String source);

		default String anonymous(Object source) {
			if (source instanceof Number) {
				return this.format(((Number) source).longValue());
			}

			if (source instanceof Date) {
				return this.format((Date) source);
			}

			if (source instanceof ZonedDateTime) {
				return this.format((ZonedDateTime) source);
			}

			throw new IllegalArgumentException("unable to format source:" + source.getClass());
		}

		default String format(long time) {
			return this.format(new Date(time));
		}

		default String format(Date date) {
			return this.format(date,
			                   dateFormatAccessor.DEFAULT_LOCALE);
		}

		String format(Date date,
		              Locale locale);

		default String format(TemporalAccessor date) {
			return this.format(date,
			                   dateFormatAccessor.DEFAULT_LOCALE);
		}

		default String format() {
			return this.format(ZonedDateTime.now());
		}

		String format(TemporalAccessor date,
		              Locale locale);
	}

	class SimpleDateFormatter
			implements DateFormatter {
		final ConditionedLock lock = new ConditionedLock();

		final ConcurrentHashMap<Locale, SimpleDateFormat> textLocale = new ConcurrentHashMap<>();

		final ConcurrentHashMap<Locale, DateTimeFormatter> formatLocale = new ConcurrentHashMap<>();

		@Getter
		final String formatterFormat;

		@Getter
		final String textFormat;

		SimpleDateFormatter(final String format) {
			this(format,
			     format);
		}

		SimpleDateFormatter(final String formatterFormat,
		                    final String textFormat) {
			this.formatterFormat = formatterFormat;
			this.textFormat = textFormat;
		}

		private SimpleDateFormat testFor(Locale locale) {
			return this.textLocale.computeIfAbsent(locale,
			                                       key -> new SimpleDateFormat(this.textFormat,
			                                                                   key));
		}

		private DateTimeFormatter formatFor(Locale locale) {
			return this.formatLocale.computeIfAbsent(locale,
			                                         key -> DateTimeFormatter.ofPattern(this.formatterFormat,
			                                                                            key));
		}

		@Override
		public Date parse(final String source) {
			return this.lock.tran(() -> this.testFor(Locale.getDefault())
			                                .parse(source));
		}

		@Override
		public String format(Date date,
		                     Locale locale) {
			return this.lock.tran(() -> this.testFor(locale)
			                                .format(date));
		}

		@Override
		public String format(TemporalAccessor date,
		                     Locale locale) {
			return ResourceAssist.stringComposer()
			                     .add(builder -> this.formatFor(locale)
			                                         .formatTo(date,
			                                                   builder))
			                     .build();
		}
	}

	enum DateFormatters
			implements DateFormatter {
		CUSTOM_001("yyyy-mm-dd a KK:mm:ss",
		           "[0-9]{4,4}-[0-9]{2,2}-[0-9]{2,2} ((오전)|(오후)|(AM)|(PM)) [0-9]{1,2}:[0-9]{2,2}:[0-9]{2,2}"),
		CUSTOM_002("yyyy-MM-dd HH:mm:ss.SSS Z",
		           "[0-9]{4,4}-[0-9]{2,2}-[0-9]{2,2} [0-9]{2,2}:[0-9]{2,2}:[0-9]{2,2}\\.[0-9]{1,3} \\+[0-9]{4,4}"),
		STANDARD("yyyy-MM-dd'T'HH:mm:ssZ",
		         "[0-9]{4,4}-[0-9]{2,2}-[0-9]{2,2}T[0-9]{2,2}:[0-9]{2,2}:[0-9]{2,2}\\+[0-9]{4,4}"),
		YYYY_MM_DD("yyyy-MM-dd",
		           "[0-9]{4,4}-[0-9]{2,2}-[0-9]{2,2}"),
		SERIAL_TIME("yyyyMMddHHmmss",
		            "[0-9]{4,4}[0-9]{2,2}[0-9]{2,2}[0-9]{2,2}[0-9]{2,2}[0-9]{2,2}"),
		FULL_SERIAL("yyyyMMddHHmmssSSS",
		            "[0-9]{4,4}[0-9]{2,2}[0-9]{2,2}[0-9]{2,2}[0-9]{2,2}[0-9]{2,2}[0-9]{1,3}"),
		COMMON_TIME("yyyy-MM-dd HH:mm:ss",
		            "[0-9]{4,4}-[0-9]{2,2}-[0-9]{2,2} [0-9]{2,2}:[0-9]{2,2}:[0-9]{2,2}"),
		FULL_TIME("yyyy-MM-dd HH:mm:ss.SSS",
		          "[0-9]{4,4}-[0-9]{2,2}-[0-9]{2,2} [0-9]{2,2}:[0-9]{2,2}:[0-9]{2,2}\\.[0-9]{1,3}"),
		FULL_TIME_ZONE("yyyy-MM-dd HH:mm:ss.SSS z",
		               "[0-9]{4,4}-[0-9]{2,2}-[0-9]{2,2} [0-9]{2,2}:[0-9]{2,2}:[0-9]{2,2}\\.[0-9]{1,3} [a-zA-Z]{3,5}");

		final Pattern pattern;

		final SimpleDateFormatter formatter;

		DateFormatters(String format,
		               String pattern) {
			this(format,
			     format,
			     pattern);
		}

		DateFormatters(String formatterFormat,
		               String textFormat,
		               String pattern) {
			this.pattern = Pattern.compile(StringAssist.format("^%s$",
			                                                   pattern));
			this.formatter = new SimpleDateFormatter(formatterFormat,
			                                         textFormat);
		}

		@Override
		public String getTextFormat() {
			return this.formatter.getTextFormat();
		}

		@Override
		public String getFormatterFormat() {
			return this.formatter.getFormatterFormat();
		}

		@Override
		public Date parse(String source) {
			return this.formatter.parse(source);
		}

		@Override
		public String format(Date date,
		                     Locale locale) {
			return this.formatter.format(date,
			                             locale);
		}

		@Override
		public String format(TemporalAccessor date,
		                     Locale locale) {
			return this.formatter.format(date,
			                             locale);
		}

		Date match(final String src) {
			return this.pattern.matcher(src)
			                   .find()
			       ? this.formatter.parse(src)
			       : null;
		}
	}

	static DateFormatter getDateFormat(String format) {
		return dateFormatAccessor.getDateFormat(format);
	}

	static Date dateOf(final Object source) {
		if (Objects.isNull(source)) {
			throw new CrashedException("source cannot be null!");
		}

		if (source instanceof Number) {
			return new Date(((Number) source).longValue());
		}

		if (source instanceof Date) {
			return (Date) source;
		}

		final String from = source.toString();

		// return null when source string is empty
		if (StringUtils.isEmpty(from) || from.equalsIgnoreCase("null")) return null;

		// convert use format
		return dateFormatAccessor.DATE_FORMATTER_ACCESSOR.reference()
		                                                 .map(formatter -> formatter.parse(from))
		                                                 .orElseGet(() -> Stream.of(DateFormatters.values())
		                                                                        .map(formatter -> formatter.match(from))
		                                                                        .filter(Objects::nonNull)
		                                                                        .findFirst()
		                                                                        .orElseThrow(() -> new IllegalArgumentException(StringAssist.format("unknown date format string [%s]",
		                                                                                                                                            from))));

		// convert use detector
	}

	static ZonedDateTime zonedDate(final Object source) {
		if (Objects.isNull(source)) {
			throw new CrashedException("source cannot be null!");
		}

		if (source instanceof TemporalAccessor) return ZonedDateTime.from((TemporalAccessor) source);

		return Optional.ofNullable(dateOf(source))
		               .map(date -> ZonedDateTime.ofInstant(date.toInstant(),
		                                                    ZoneId.systemDefault()))
		               .orElse(null);
	}

	static void updateDatePart(Map<String, Object> target,
	                           long current) {
		Calendar calendar = Calendar.getInstance();

		if (current > 0) {
			calendar.setTimeInMillis(current);
		}

		target.put("YYYY",
		           calendar.get(Calendar.YEAR));
		target.put("MM",
		           digitPadding(calendar.get(Calendar.MONTH) + 1,
		                        2));
		target.put("DD",
		           digitPadding(calendar.get(Calendar.DAY_OF_MONTH),
		                        2));
		target.put("HH",
		           digitPadding(calendar.get(Calendar.HOUR_OF_DAY),
		                        2));
		target.put("MI",
		           digitPadding(calendar.get(Calendar.MINUTE),
		                        2));
		target.put("SS",
		           digitPadding(calendar.get(Calendar.SECOND),
		                        2));
		target.put("SSS",
		           digitPadding(calendar.get(Calendar.MILLISECOND),
		                        3));
	}

	static String digitPadding(int value,
	                           int length) {
		return StringUtils.leftPad(String.valueOf(value),
		                           length,
		                           '0');
	}

	static SimpleDateFormatter formatConvertor(String format) {
		return new SimpleDateFormatter(format);
	}

	Predicate<Class<?>> predicateDate = PredicateAssist.predicateBuilder(Date.class::isAssignableFrom)
	                                                   .or(ZonedDateTime.class::isAssignableFrom)
	                                                   .build();

}
