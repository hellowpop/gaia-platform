package gaia.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public interface GWEnvironment {
	String GW_HOME = "gw.home";
	String GW_BASE = "gw.base";
	String GW_NAME = "gw.name";
	String GW_PROPERTIES = "gw.properties";
	String GW_LOG_DIR = "gw.log.dir";

	Pattern pattern = Pattern.compile("\\$\\{([^}]*)}");

	Function<Properties, List<String>> propertyKeys = prop -> prop.keySet()
	                                                              .stream()
	                                                              .map(Object::toString)
	                                                              .sorted(String::compareTo)
	                                                              .collect(Collectors.toList());

	BiConsumer<Map<String, String>, Properties> propertyCopy = (map, prop) -> propertyKeys.apply(prop)
	                                                                                      .forEach(key -> map.put(key,
	                                                                                                              prop.getProperty(key)));


	BiConsumer<Map<String, String>, Map<String, Object>> mapCopy = (map, prop) -> prop.forEach((key, value) -> map.put(key,
	                                                                                                                   String.valueOf(value)));


	Function<Properties, Map<String, String>> propertiesToMap = prop -> {
		Map<String, String> map = new LinkedHashMap<>();

		propertyCopy.accept(map,
		                    prop);

		return map;
	};

	MappingContext environment = new MappingContext();

	static String mappingValue(final String source) {
		return environment.mappingValue(source);
	}

	final class MappingContext {
		Map<String, String> map;
		Function<String, String> mapper;
		Function<String, String> eraser;
		Predicate<String> predicate;

		private MappingContext() {
			super();
			this.refreshWithSystem();
		}

		public MappingContext(final Map<String, String> map) {
			super();
			this.refresh(map);
		}

		public void refreshWithSystem() {
			this.refresh(propertiesToMap.apply(System.getProperties()));
		}

		public void refresh(final Map<String, String> map) {
			this.map = map;
			this.mapper = source -> {
				final Matcher matcher = pattern.matcher(source);

				if (matcher.find()) {
					StringBuilder buffer = new StringBuilder();

					int index = 0;
					do {
						buffer.append(source,
						              index,
						              matcher.start());
						final String key = source.substring(matcher.start(1),
						                                    matcher.end(1));

						final int sep = key.indexOf(':');

						if (sep > 0) {
							final String filteredKey = key.substring(0,
							                                         sep);
							final String defaultValue = key.substring(sep + 1)
							                               .trim();
							final String value = map.get(filteredKey);

							buffer.append(value == null
							              ? defaultValue
							              : value);
						} else {
							final String value = map.get(key);
							if (value == null) {
								buffer.append("${");
								buffer.append(key);
								buffer.append("}");
							} else {
								buffer.append(value);
							}
						}

						index = matcher.end();
					} while (matcher.find());
					buffer.append(source.substring(index));

					return buffer.toString();
				}

				return source;
			};
			this.eraser = source -> {
				final Matcher matcher = pattern.matcher(source);

				if (matcher.find()) {
					StringBuilder buffer = new StringBuilder();

					int index = 0;
					do {
						buffer.append(source,
						              index,
						              matcher.start());
						final String key = source.substring(matcher.start(1),
						                                    matcher.end(1));

						final int sep = key.indexOf(':');

						if (sep > 0) {
							final String filteredKey = key.substring(0,
							                                         sep);
							final String defaultValue = key.substring(sep + 1)
							                               .trim();
							final String value = map.get(filteredKey);

							buffer.append(value == null
							              ? defaultValue
							              : value);
						} else {
							final String value = map.get(key);
							if (value != null) {
								buffer.append(value);
							}
						}

						index = matcher.end();
					} while (matcher.find());
					buffer.append(source.substring(index));

					return buffer.toString();
				}

				return source;
			};
			this.predicate = source -> pattern.matcher(source)
			                                  .find();

			// first regulate
			this.regulate();
		}

		public void regulate() {
			final AtomicInteger counter = new AtomicInteger();
			final AtomicInteger currentMappedCount = new AtomicInteger();
			final AtomicBoolean ignoreMissing = new AtomicBoolean();
			do {
				counter.set(0);
				this.map.entrySet()
				        .forEach(entry -> {
					        final String value = entry.getValue();

					        if (ignoreMissing.get()) {
						        if (this.isMappedValue(value)) {
							        // 선언되지 않은 값은 공백으로 바꿈.
							        entry.setValue(this.eraseValue(value));
						        }
					        } else {
						        this.mappedValue(value)
						            .ifPresent(filtered -> {
							            counter.getAndIncrement();
							            entry.setValue(filtered);
						            });
					        }


					        this.map.put(entry.getKey(),
					                     entry.getValue());
				        });

				if (ignoreMissing.get()) {
					break;
				}

				if (currentMappedCount.get() == counter.get()) {
					ignoreMissing.set(true);
				}
				currentMappedCount.set(counter.get());
			} while (currentMappedCount.get() > 0);
		}

		public Optional<String> mappedValue(String source) {
			return Optional.ofNullable(this.mapper.apply(source));
		}

		public String eraseValue(String source) {
			return this.eraser.apply(source);
		}

		public boolean isMappedValue(final String source) {
			return this.predicate.test(source);
		}

		public String mappingValue(String source) {
			return this.mappedValue(source)
			           .orElse(source);
		}

		public void loadSystemVariable() {
			this.append(System.getProperties());
		}

		public void append(MappingContext other) {
			this.map.putAll(other.map);
			this.regulate();
		}

		public void append(Map<String, Object> map) {
			mapCopy.accept(this.map,
			               map);
			this.regulate();
		}

		public void append(Properties properties) {
			propertyCopy.accept(this.map,
			                    properties);
			this.regulate();
		}

		public String getProperty(final String key) {
			return this.map.get(key);
		}

		public String getProperty(final String key,
		                          final String df) {
			return Optional.ofNullable(this.map.get(key))
			               .orElse(df);
		}

		public void setProperty(final String key,
		                        final String value) {
			this.map.put(key,
			             value);
		}

		/**
		 * @param key key
		 * @param df  default value
		 * @return value of system property by 'key' with default value
		 */
		public int getInteger(String key,
		                      int df) {
			return Optional.ofNullable(this.getProperty(key))
			               .filter(val -> !val.isEmpty())
			               .map(Integer::parseInt)
			               .orElse(df);
		}

		/**
		 * @param key key
		 * @param df  default value
		 * @return value of system property by 'key' with default value
		 */
		public long getLong(String key,
		                    long df) {
			return Optional.ofNullable(this.getProperty(key))
			               .filter(val -> !val.isEmpty())
			               .map(Long::parseLong)
			               .orElse(df);
		}

		/**
		 * @param key key
		 * @param df  default value
		 * @return value of system property by 'key' with default value
		 */
		public boolean getBoolean(String key,
		                          boolean df) {
			return Optional.ofNullable(this.getProperty(key))
			               .filter(val -> !val.isEmpty())
			               .map(PrimitiveTypes::getBoolean)
			               .orElse(df);
		}

		public void forEach(final BiConsumer<String, String> consumer) {
			this.map.forEach(consumer);
		}
	}

	static String mappedSystemValue(String source) {
		return environment.mappedValue(source)
		                  .orElse(source);
	}

	static String home() {
		return environment.getProperty(GWEnvironment.GW_HOME);
	}


	static String name() {
		return environment.getProperty(GWEnvironment.GW_NAME,
		                               "single");
	}

	static String getProperty(final String key) {
		return environment.getProperty(key);
	}

	static String getProperty(final String key,
	                          final String df) {
		return environment.getProperty(key,
		                               df);
	}

	static int getInteger(String key,
	                      int df) {
		return environment.getInteger(key,
		                              df);
	}

	static long getLong(String key,
	                    long df) {
		return environment.getLong(key,
		                           df);
	}
}
