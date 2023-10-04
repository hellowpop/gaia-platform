package gaia.common;

import static gaia.common.FunctionAssist.execute;
import static gaia.common.FunctionAssist.supply;
import static gaia.common.ResourceAssist.stringComposer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.ResolvableDeserializer;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.FloatNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import com.fasterxml.jackson.databind.node.ShortNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.ResolvableSerializer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import gaia.common.CharsetSpec.ByteArrayOutputStreamExt;
import gaia.common.CharsetSpec.CharSets;
import gaia.common.ConcurrentAssist.ConditionedLock;
import gaia.common.ConcurrentAssist.SuppliedReference;
import gaia.common.FunctionAssist.ErrorHandleConsumer;
import gaia.common.JacksonOverrides.ListTypeReference;
import gaia.common.JacksonOverrides.MapTypeReference;
import gaia.common.PrimitiveTypes.ObjectType;
import gaia.common.StringAssist.NamePattern;

public interface JacksonAssist {
	Logger log = LoggerFactory.getLogger(JacksonAssist.class);

	interface Packable {
		default void pack() {
		}

		default void packed() {
		}

		default void unpacked() {
		}
	}

	class PackableSerializer
			extends JsonSerializer<Packable>
			implements ResolvableSerializer {
		private final JsonSerializer<Object> origin;

		PackableSerializer(JsonSerializer<Object> origin) {
			this.origin = origin;
		}

		@Override
		public void resolve(SerializerProvider serializerProvider) throws JsonMappingException {
			if (this.origin instanceof ResolvableSerializer) {
				((ResolvableSerializer) this.origin).resolve(serializerProvider);
			}
		}

		@Override
		public void serialize(Packable value,
		                      JsonGenerator gen,
		                      SerializerProvider serializers) throws IOException {
			value.pack();
			this.origin.serialize(value,
			                      gen,
			                      serializers);
			value.packed();
		}
	}

	class PackableDeserializer
			extends JsonDeserializer<Packable>
			implements ResolvableDeserializer {
		final JsonDeserializer<Object> origin;

		PackableDeserializer(JsonDeserializer<Object> origin) {
			this.origin = origin;
		}

		@Override
		public Packable deserialize(JsonParser p,
		                            DeserializationContext context) throws IOException {
			Packable packable = (Packable) this.origin.deserialize(p,
			                                                       context);

			packable.unpacked();

			return packable;
		}

		@Override
		public void resolve(DeserializationContext context) throws JsonMappingException {
			if (this.origin instanceof ResolvableDeserializer) {
				((ResolvableDeserializer) this.origin).resolve(context);
			}
		}
	}

	class BeanSerializerModifierImpl
			extends BeanSerializerModifier {
		@Override
		@SuppressWarnings("unchecked")
		public JsonSerializer<?> modifySerializer(SerializationConfig config,
		                                          BeanDescription beanDesc,
		                                          JsonSerializer<?> serializer) {
			if (Packable.class.isAssignableFrom(beanDesc.getBeanClass())) {
				return new PackableSerializer((JsonSerializer<Object>) serializer);
			}
			return super.modifySerializer(config,
			                              beanDesc,
			                              serializer);
		}
	}

	BeanSerializerModifierImpl beanSerializerModifierImpl = new BeanSerializerModifierImpl();


	class BeanDeserializerModifierImpl
			extends BeanDeserializerModifier {
		@Override
		@SuppressWarnings("unchecked")
		public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config,
		                                              BeanDescription beanDesc,
		                                              JsonDeserializer<?> deserializer) {
			if (Packable.class.isAssignableFrom(beanDesc.getBeanClass())) {
				return new PackableDeserializer((JsonDeserializer<Object>) deserializer);
			}
			return super.modifyDeserializer(config,
			                                beanDesc,
			                                deserializer);
		}
	}

	BeanDeserializerModifierImpl beanDeserializerModifierImpl = new BeanDeserializerModifierImpl();

	class ObjectMapperCustomizeModule
			extends SimpleModule {
		@Override
		public void setupModule(SetupContext context) {
			context.addBeanSerializerModifier(beanSerializerModifierImpl);
			context.addBeanDeserializerModifier(beanDeserializerModifierImpl);
			context.appendAnnotationIntrospector(JacksonAssist.introspector);
			super.setupModule(context);
		}
	}

	ObjectMapperCustomizeModule customizer = new ObjectMapperCustomizeModule();

	MapTypeReference MAP_TYPE = new MapTypeReference();
	ListTypeReference LIST_TYPE = new ListTypeReference();

	List<Module> modules = supply(() -> {
		final ArrayList<Module> list = new ArrayList<>();
		list.add(new JavaTimeModule());
		list.add(new Jdk8Module());
		list.add(customizer);
		return list;
	});

	JacksonAnnotationIntrospectorImpl introspector = new JacksonAnnotationIntrospectorImpl();

	@Getter
	enum MapperType {
		json("application/json; charset=UTF-8"),
		yml("application/x-yaml; charset=UTF-8"),
		xml("application/xml; charset=UTF-8");

		final String contentType;

		MapperType(String contentType) {
			this.contentType = contentType;
		}
	}

	enum NodeAction {
		get {
			@Override
			NodeActionContext invoke(final NodeActionContext context) {
				final JsonNode node = context.getRoot()
				                             .at(context.getPointer());

				if (JsonNodeType.MISSING == node.getNodeType()) {
					context.setDetection(NodeDetection.SOURCE_MISSING);
				} else {
					context.find(node);
				}

				return context;
			}
		},
		set {
			@Override
			NodeActionContext invoke(NodeActionContext context) {
				walkNodeAt(context);

				if (NodeDetection.OK == context.detection) {
					final JsonNode workingNode = context.detectedNode;
					final JsonPointer workingPointer = context.detectedPointer;
					switch (workingNode.getNodeType()) {
						case OBJECT: {
							((ObjectNode) workingNode).set(workingPointer.getMatchingProperty(),
							                               context.payload);
							break;
						}

						case ARRAY: {
							((ArrayNode) workingNode).set(workingPointer.getMatchingIndex(),
							                              context.payload);
							break;
						}

						default: {
							throw new Error("OK found node not a both of (OBJECT,ARRAY) ?");
						}
					}
				}
				return context;
			}
		},
		remove {
			@Override
			NodeActionContext invoke(NodeActionContext context) {
				walkNodeAt(context);

				if (NodeDetection.OK == context.detection) {
					final JsonNode workingNode = context.detectedNode;
					final JsonPointer workingPointer = context.detectedPointer;
					switch (workingNode.getNodeType()) {
						case OBJECT: {
							context.payload = ((ObjectNode) workingNode).without(workingPointer.getMatchingProperty());
							break;
						}

						case ARRAY: {
							context.payload = ((ArrayNode) workingNode).remove(workingPointer.getMatchingIndex());
							break;
						}

						default: {
							throw new Error("OK found node not a both of (OBJECT,ARRAY) ?");
						}
					}
				}
				return context;
			}
		},
		sort {
			@Override
			NodeActionContext invoke(NodeActionContext context) {
				walkNodeAt(context);

				if (NodeDetection.OK == context.detection) {
					final JsonNode workingNode = context.detectedNode;
					final JsonPointer workingPointer = context.detectedPointer;
					switch (workingNode.getNodeType()) {
						case OBJECT: {
							context.payload = ((ObjectNode) workingNode).without(workingPointer.getMatchingProperty());
							break;
						}

						case ARRAY: {
							context.payload = ((ArrayNode) workingNode).remove(workingPointer.getMatchingIndex());
							break;
						}

						default: {
							throw new Error("OK found node not a both of (OBJECT,ARRAY) ?");
						}
					}
				}
				return context;
			}
		};

		public JsonNode evaluate(final JsonNode root,
		                         final JsonPointer pointer) {
			return this.evaluate(root,
			                     pointer,
			                     null);
		}

		public JsonNode evaluate(final JsonNode root,
		                         final JsonPointer pointer,
		                         final JsonNode node) {
			NodeActionContext context = this.call(root,
			                                      pointer,
			                                      node);
			if (context.success()) {
				return context.getPayload();
			}

			return null;
		}

		public NodeActionContext call(final JsonNode root,
		                              final JsonPointer pointer) {
			return this.call(root,
			                 pointer,
			                 null);
		}

		public NodeActionContext call(final JsonNode root,
		                              final JsonPointer pointer,
		                              final JsonNode payload) {
			return this.invoke(NodeActionContext.builder()
			                                    .root(root)
			                                    .pointer(pointer)
			                                    .payload(payload)
			                                    .build());
		}

		abstract NodeActionContext invoke(NodeActionContext context);

		static void walkNodeAt(final NodeActionContext context) {
			final JsonNode root = context.getRoot();
			final JsonPointer pointer = context.getPointer();
			if (pointer.matches()) {
				context.setDetection(NodeDetection.TARGET_POINTER_INVALID);
				return;
			}

			AtomicReference<JsonNode> nodeHolder = new AtomicReference<>(root);
			AtomicReference<JsonPointer> pointerHolder = new AtomicReference<>(pointer);

			do {
				final JsonNode workingNode = nodeHolder.get();
				final JsonPointer workingPointer = pointerHolder.get();

				// drill down next pointer
				switch (workingNode.getNodeType()) {
					case OBJECT: {
						if (workingPointer.tail()
						                  .matches()) {
							context.detected(workingPointer,
							                 workingNode);
							return;
						}

						final ObjectNode objectNode = (ObjectNode) workingNode;
						final String name = workingPointer.getMatchingProperty();

						JsonNode child = objectNode.get(name);

						if (null == child) {
							child = JacksonMapper.createObjectNode();
							objectNode.set(name,
							               child);
						}

						nodeHolder.set(child);
						break;
					}

					case ARRAY: {
						if (workingPointer.tail()
						                  .matches()) {
							context.detected(workingPointer,
							                 workingNode);
							return;
						}
						final ArrayNode arrayNodeNode = (ArrayNode) workingNode;
						final int index = workingPointer.getMatchingIndex();
						JsonNode child = arrayNodeNode.get(workingPointer.getMatchingIndex());
						if (null == child) {
							child = JacksonMapper.createObjectNode();
							arrayNodeNode.set(index,
							                  child);
						}
						nodeHolder.set(child);
						break;
					}

					default: {
						if (workingPointer.tail()
						                  .matches()) {
							context.detected(NodeDetection.VALUE_NODE_FOUND_AT_PATH,
							                 workingPointer,
							                 workingNode);
							return;
						}
						break;
					}
				}

				pointerHolder.set(workingPointer.tail());

				// if pointer is last set value to target
				if (pointerHolder.get()
				                 .matches()) {
					if (log.isTraceEnabled()) {
						log.trace(StringAssist.format("[%s] reach abnormal loop end",
						                              pointer));
					}
					context.detected(NodeDetection.ABNORMAL_LOOP_END,
					                 workingPointer,
					                 workingNode);
					return;
				}
			} while (true);
		}
	}

	enum NodeDetection {
		OK,
		SOURCE_MISSING,
		TARGET_POINTER_INVALID,
		VALUE_NODE_FOUND_AT_PATH,
		ABNORMAL_LOOP_END
	}

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonInclude(Include.NON_NULL)
	class NodeActionContext {
		JsonNode root;
		NodeAction action;
		NodeDetection detection;
		JsonPointer pointer;
		JsonNode payload;
		JsonPointer detectedPointer;
		JsonNode detectedNode;

		public boolean success() {
			return this.detection == NodeDetection.OK;
		}

		public boolean fail() {
			return this.detection != NodeDetection.OK;
		}

		void find(JsonNode n) {
			this.detection = NodeDetection.OK;
			this.payload = n;
		}

		void detected(JsonPointer p,
		              JsonNode n) {
			this.detected(NodeDetection.OK,
			              p,
			              n);
		}

		void detected(NodeDetection d,
		              JsonPointer p,
		              JsonNode n) {
			this.detection = d;
			this.detectedNode = n;
			this.detectedPointer = p;
		}
	}

	Predicate<NodeDetection> predicate_node_action_ok = result -> result == NodeDetection.OK;
	Predicate<NodeDetection> predicate_node_action_fail = predicate_node_action_ok.negate();

	enum JacksonMapper {
		YML(MapperType.yml,
		    JacksonOverrides.YML_MAPPER,
		    mapper -> {
		    }),
		YML_KEBAB(MapperType.yml,
		          JacksonOverrides.YML_MAPPER,
		          mapper -> mapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)),
		YML_SNAKE(MapperType.yml,
		          JacksonOverrides.YML_MAPPER,
		          mapper -> mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)),
		JSON_DUMP(MapperType.json,
		          JacksonOverrides.JSON_MAPPER,
		          mapper -> mapper.enable(SerializationFeature.INDENT_OUTPUT)),
		JSON_MAP(MapperType.json,
		         JacksonOverrides.JSON_MAPPER,
		         mapper -> mapper.disable(SerializationFeature.INDENT_OUTPUT)),
		JSON_KEBAB_DUMP(MapperType.json,
		                JacksonOverrides.JSON_MAPPER,
		                mapper -> mapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
		                                .enable(SerializationFeature.INDENT_OUTPUT)),
		JSON_KEBAB_MAP(MapperType.json,
		               JacksonOverrides.JSON_MAPPER,
		               mapper -> mapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
		                               .disable(SerializationFeature.INDENT_OUTPUT)),
		JSON_SNAKE_DUMP(MapperType.json,
		                JacksonOverrides.JSON_MAPPER,
		                mapper -> mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		                                .enable(SerializationFeature.INDENT_OUTPUT)),
		JSON_SNAKE_MAP(MapperType.json,
		               JacksonOverrides.JSON_MAPPER,
		               mapper -> mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		                               .disable(SerializationFeature.INDENT_OUTPUT)),
		XML_DUMP(MapperType.xml,
		         JacksonOverrides.XML_MAPPER,
		         mapper -> mapper.enable(SerializationFeature.INDENT_OUTPUT)),
		XML_MAP(MapperType.xml,
		        JacksonOverrides.XML_MAPPER,
		        mapper -> mapper.disable(SerializationFeature.INDENT_OUTPUT)),
		XML_KEBAB_DUMP(MapperType.xml,
		               JacksonOverrides.XML_MAPPER,
		               mapper -> mapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
		                               .enable(SerializationFeature.INDENT_OUTPUT)),
		XML_KEBAB_MAP(MapperType.xml,
		              JacksonOverrides.XML_MAPPER,
		              mapper -> mapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
		                              .disable(SerializationFeature.INDENT_OUTPUT)),
		XML_SNAKE_DUMP(MapperType.xml,
		               JacksonOverrides.XML_MAPPER,
		               mapper -> mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		                               .enable(SerializationFeature.INDENT_OUTPUT)),
		XML_SNAKE_MAP(MapperType.xml,
		              JacksonOverrides.XML_MAPPER,
		              mapper -> mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		                              .disable(SerializationFeature.INDENT_OUTPUT)),
		OBJECT(MapperType.json,
		       ObjectMapper::new,
		       mapper -> mapper.disable(SerializationFeature.INDENT_OUTPUT)),
		OBJECT_KEBAB(MapperType.json,
		             ObjectMapper::new,
		             mapper -> mapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
		                             .disable(SerializationFeature.INDENT_OUTPUT));
		final SuppliedReference<ObjectMapper> holder;
		@Getter
		final MapperType mapperType;

		JacksonMapper(final MapperType mapperType,
		              final Supplier<ObjectMapper> supplier,
		              final Consumer<ObjectMapper> consumer) {
			this.mapperType = mapperType;
			this.holder = new SuppliedReference<>(() -> {
				ObjectMapper mapper = supplier.get();

				mapper.registerModules(modules);

				mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z"));

				mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
				mapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

				mapper.disable(SerializationFeature.FAIL_ON_SELF_REFERENCES);
				mapper.enable(SerializationFeature.WRITE_SELF_REFERENCES_AS_NULL);

				consumer.accept(mapper);
				return mapper;
			});
		}

		public ObjectMapper mapper() {
			return this.holder.supplied();
		}

		void reload() {
			this.holder.set(null);
		}

		public <T> T transform(final Object value,
		                       final Class<T> valueType) {
			final String content = this.marshal(value);

			return this.readAS(content,
			                   valueType);
		}

		public <T> T clone(final T origin,
		                   final Class<T> type) {
			String serialized = this.marshal(origin);

			return this.readAS(serialized,
			                   type);
		}

		public Map<String, Object> readASMap(final String content) {
			return supply(() -> this.mapper()
			                        .readValue(content,
			                                   MAP_TYPE));
		}

		public Map<String, Object> readASMap(final byte[] content) {
			return supply(() -> this.mapper()
			                        .readValue(content,
			                                   MAP_TYPE));
		}

		public Map<String, Object> readASMap(final JsonNode content) {
			final ObjectMapper mapper = this.mapper();
			return supply(() -> mapper.readValue(mapper.treeAsTokens(content),
			                                     MAP_TYPE));
		}

		public List<Object> readASList(final String content) {
			return supply(() -> this.mapper()
			                        .readValue(content,
			                                   LIST_TYPE));
		}

		public <T> List<T> readASList(final String content,
		                              final Class<T> type) {
			return this.readASList(this.contentToNode(content),
			                       type);
		}

		public <T> List<T> readASList(final JsonNode node,
		                              final Class<T> type) {
			switch (node.getNodeType()) {
				case OBJECT: {
					return Collections.singletonList(this.nodeToType(node,
					                                                 type));
				}

				case ARRAY: {
					return CollectionSpec.toStream(node.elements())
					                     .map(sub -> this.nodeToType(sub,
					                                                 type))
					                     .collect(Collectors.toList());
				}
			}
			return null;
		}

		private JsonNode contentToNode(final String content) {
			return supply(() -> this.mapper()
			                        .readTree(content));
		}

		private <T> T nodeToType(final JsonNode content,
		                         final Class<T> type) {
			final ObjectMapper mapper = this.mapper();
			return supply(() -> mapper.readValue(mapper.treeAsTokens(content),
			                                     type));
		}

		public <T> T readAS(final Reader reader,
		                    final Class<T> valueType) {
			return supply(() -> this.mapper()
			                        .readValue(reader,
			                                   valueType));
		}

		public <T> T readAS(final InputStream stream,
		                    final Class<T> valueType) {
			return supply(() -> this.mapper()
			                        .readValue(stream,
			                                   valueType));
		}

		public <T> T readAS(final String content,
		                    final TypeReference<T> valueTypeRef) {
			return supply(() -> this.mapper()
			                        .readValue(content,
			                                   valueTypeRef));
		}

		public <T> T readAS(final byte[] src,
		                    final TypeReference<T> valueTypeRef) {
			return supply(() -> this.mapper()
			                        .readValue(src,
			                                   valueTypeRef));
		}

		public <T> T readAS(final JsonNode jsonNode,
		                    final TypeReference<T> valueTypeRef) {
			final ObjectMapper mapper = this.mapper();
			return supply(() -> mapper.readValue(mapper.treeAsTokens(jsonNode),
			                                     valueTypeRef));
		}

		public <T> T readAS(final String content,
		                    final Class<T> valueType) {
			return supply(() -> this.mapper()
			                        .readValue(content,
			                                   valueType));
		}

		public <T> T readAS(final byte[] src,
		                    final Class<T> valueType) {
			return supply(() -> this.mapper()
			                        .readValue(src,
			                                   valueType));
		}

		public <T> T readAS(final JsonNode jsonNode,
		                    final Class<T> valueType) {
			return supply(() -> this.mapper()
			                        .treeToValue(jsonNode,
			                                     valueType));
		}

		public <T> T convertValue(final Object value,
		                          final Class<T> valueType) {
			return supply(() -> this.mapper()
			                        .convertValue(value,
			                                      valueType));
		}

		public JsonNode valueToTree(final Object value) {
			return supply(() -> this.mapper()
			                        .valueToTree(value));
		}

		@SuppressWarnings("unchecked")
		public Map<String, Object> asMap(final Object value) {
			if (value instanceof Map) {
				return Collections.emptyMap();
			}

			return this.cloneMap(value);
		}

		public Map<String, Object> cloneMap(final Object value) {
			if (null == value) {
				return Collections.emptyMap();
			}
			final String elmSerial = this.marshal(value);

			return this.readAS(elmSerial,
			                   MAP_TYPE);
		}

		public JsonNode readTree(final String content) {
			return supply(() -> this.mapper()
			                        .readTree(content));
		}

		public JsonNode readTree(final byte[] content) {
			return supply(() -> this.mapper()
			                        .readTree(content));
		}

		public byte[] marshal(final Object value,
		                      final CharSets charset) {
			return this.marshal(value,
			                    charset,
			                    c -> {
			                    });
		}

		public byte[] marshal(final Object value,
		                      final CharSets charset,
		                      final ErrorHandleConsumer<ByteArrayOutputStreamExt> customizer) {
			return ResourceAssist.streamComposer()
			                     .stream(stream -> {
				                     if (CharSets.UTF_8 == charset) {
					                     this.marshal(stream,
					                                  value);
				                     } else {
					                     stream.write(charset.getBytes(this.marshal(value)));
				                     }
			                     })
			                     .stream(customizer)
			                     .build();
		}

		public String marshal(final Object value) {
			return this.marshal(value,
			                    FunctionAssist::rethrow);
		}

		public String marshal(final Object value,
		                      final Consumer<Throwable> errorHandle) {
			return supply(() -> this.mapper()
			                        .writeValueAsString(value),
			              throwable -> {
				              errorHandle.accept(throwable);
				              return null;
			              });
		}

		public void marshal(OutputStream stream,
		                    Object value) {
			execute(() -> this.mapper()
			                  .writeValue(stream,
			                              value));
		}

		public Map<String, Object> flatten(String content) {
			return this.flatten(this.readASMap(content));
		}

		public Map<String, Object> flatten(final Map<String, Object> source) {
			final Map<String, Object> flatten = new LinkedHashMap<>();

			source.forEach((key, value) -> this.flatten(flatten,
			                                            key,
			                                            value));

			return flatten;
		}

		@SuppressWarnings("unchecked")
		private void flatten(final Map<String, Object> flatten,
		                     final String prefix,
		                     final Object value) {
			final ObjectType ot = PrimitiveTypes.determine(value);

			switch (ot) {
				case DICTIONARY: {
					this.flattenDICTIONARY(flatten,
					                       prefix,
					                       (Map<String, Object>) value);
					break;
				}
				case COLLECTION: {
					this.flattenCOLLECTION(flatten,
					                       prefix,
					                       (Collection<Object>) value);
					break;
				}
				case VOID_TYPE: {
					this.flattenDICTIONARY(flatten,
					                       prefix,
					                       this.asMap(value));
					break;
				}
				default: {
					flatten.put(prefix,
					            value);
					break;
				}
			}
		}

		private void flattenDICTIONARY(final Map<String, Object> flatten,
		                               final String prefix,
		                               final Map<String, Object> map) {
			map.forEach((key, value) -> this.flatten(flatten,
			                                         stringComposer().add(prefix)
			                                                         .add('.')
			                                                         .add(key)
			                                                         .build(),
			                                         value));
		}

		private void flattenCOLLECTION(final Map<String, Object> flatten,
		                               final String prefix,
		                               final Collection<Object> collection) {
			final AtomicInteger counter = new AtomicInteger();
			collection.forEach((value) -> this.flatten(flatten,
			                                           stringComposer().add(prefix)
			                                                           .add('[')
			                                                           .add(counter.getAndIncrement())
			                                                           .add(']')
			                                                           .build(),
			                                           value));
		}

		public static JsonNode wrapObject(final Object object) {
			if (null == object) {
				return NullNode.getInstance();
			}
			if (object instanceof JsonNode) {
				return (JsonNode) object;
			}

			final ObjectType ot = PrimitiveTypes.determine(object);

			switch (ot) {
				case DICTIONARY:
				case COLLECTION:
				case VOID_TYPE: {
					return JacksonMapper.OBJECT.mapper()
					                           .valueToTree(object);
				}
				case BOOLEAN: {
					return PrimitiveTypes.getBoolean(object)
					       ? BooleanNode.TRUE
					       : BooleanNode.FALSE;
				}
				case BYTE:
				case SHORT: {
					return new ShortNode(((Number) object).shortValue());
				}
				case INTEGER: {
					return new IntNode(((Number) object).intValue());
				}
				case LONG: {
					return new LongNode(((Number) object).longValue());
				}
				case BIG_INTEGER: {
					return new BigIntegerNode((BigInteger) object);
				}
				case FLOAT: {
					return new FloatNode(((Number) object).floatValue());
				}
				case DOUBLE: {
					return new DoubleNode(((Number) object).doubleValue());
				}
				case BIG_DECIMAL: {
					return new DecimalNode((BigDecimal) object);
				}
				case ANONYMOUS_TYPE: {
					// case of direct Object.class
					return new ObjectNode(JacksonMapper.OBJECT.mapper()
					                                          .getNodeFactory());
				}
				default: {
					break;
				}
			}

			return new TextNode(object.toString());
		}

		public static Object unwrapNode(final JsonNode node) {
			switch (node.getNodeType()) {
				case ARRAY: {
					ArrayList<Object> list = new ArrayList<>();

					for (JsonNode jsonNode : node) {
						list.add(unwrapNode(jsonNode));
					}

					return list;
				}

				case OBJECT: {
					Map<String, Object> map = new LinkedHashMap<>();

					node.fields()
					    .forEachRemaining(entry -> map.put(entry.getKey(),
					                                       unwrapNode(entry.getValue())));

					return map;
				}

				case BINARY: {
					return supply(node::binaryValue);
				}

				case BOOLEAN: {
					return node.booleanValue();
				}

				case NUMBER: {
					return node.numberValue();
				}

				case POJO: {
					if (node instanceof POJONode) {
						return ((POJONode) node).getPojo();
					}
					log.warn(StringAssist.format("[%s] is POJO type but not a POJONode",
					                             node.getClass()
					                                 .getName()));
					return node.asText();
				}

				case STRING: {
					return node.asText();
				}

				case MISSING:
				case NULL: {
					// skip null
					break;
				}
			}
			return null;
		}

		public static ObjectNode createObjectNode() {
			return new ObjectNode(JacksonMapper.OBJECT.mapper()
			                                          .getNodeFactory());
		}

		public static ArrayNode createArrayNode() {
			return new ArrayNode(JacksonMapper.OBJECT.mapper()
			                                         .getNodeFactory());
		}

		public static JsonNode toNode(final Object content) {
			return supply(() -> JacksonMapper.OBJECT.mapper()
			                                        .valueToTree(content));
		}


		public static Map<String, Object> nodeAsMap(JsonNode node) {
			if (node.getNodeType() != JsonNodeType.OBJECT) {
				throw new IllegalArgumentException("must object node!");
			}

			Map<String, Object> map = new LinkedHashMap<>();

			node.fields()
			    .forEachRemaining(entry -> map.put(entry.getKey(),
			                                       unwrapNode(entry.getValue())));

			return map;
		}

		public static <T> T nodeAs(JsonNode node,
		                           Class<T> type) {
			return supply(() -> JacksonMapper.OBJECT.mapper()
			                                        .treeToValue(node,
			                                                     type),
			              throwable -> {
				              log.error("node read error",
				                        throwable);
				              throw new RuntimeException("node read error",
				                                         throwable);
			              });
		}

		public static <T> T objectAs(final Object content,
		                             final Class<T> type) {
			final ObjectMapper mapper = JacksonMapper.OBJECT.mapper();
			return supply(() -> mapper.treeToValue(mapper.valueToTree(content),
			                                       type),
			              throwable -> {
				              log.error("node read error",
				                        throwable);
				              throw new RuntimeException("node read error",
				                                         throwable);
			              });
		}

		public static NodeDetection copyNode(JsonNode source,
		                                     String sourcePath,
		                                     JsonNode target,
		                                     String targetPath) {
			final NodeActionContext get = NodeAction.get.call(source,
			                                                  JsonPointer.compile(sourcePath));

			if (NodeDetection.OK != get.getDetection()) {
				return get.getDetection();
			}

			final NodeActionContext set = NodeAction.set.call(target,
			                                                  JsonPointer.compile(targetPath),
			                                                  get.getPayload());

			if (NodeDetection.OK != set.getDetection()) {
				return get.getDetection();
			}

			return NodeDetection.OK;
		}

		interface NodeWalkerListener {
			void on(NodeDetection result,
			        JsonPointer pointer,
			        JsonNode node);
		}

		public static JsonNode naming(final NamePattern pattern,
		                              final JsonNode node) {
			switch (node.getNodeType()) {
				case ARRAY: {
					node.elements()
					    .forEachRemaining(sub -> naming(pattern,
					                                    sub));
					break;
				}

				case OBJECT: {
					final ObjectNode objectNode = (ObjectNode) node;
					// copy
					final List<Entry<String, JsonNode>> list = CollectionSpec.toStream(objectNode.fields())
					                                                         .collect(Collectors.toList());
					// clear
					objectNode.removeAll();

					list.forEach(entry -> {
						final String key = pattern.encode(entry.getKey());

						objectNode.set(key,
						               naming(pattern,
						                      entry.getValue()));
					});
					break;
				}
			}
			return node;
		}
	}

	static void addIgnoreAnnotation(Class<? extends Annotation> type) {
		introspector.add(type);

		for (JacksonMapper value : JacksonMapper.values()) {
			value.reload();
		}
	}

	@SafeVarargs
	static void addIgnoreAnnotations(Class<? extends Annotation>... types) {
		for (Class<? extends Annotation> type : types) {
			introspector.add(type);
		}

		for (JacksonMapper value : JacksonMapper.values()) {
			value.reload();
		}
	}

	static void addModule(Module module) {
		modules.add(module);

		for (JacksonMapper value : JacksonMapper.values()) {
			value.reload();
		}
	}

	class JacksonAnnotationIntrospectorImpl
			extends JacksonAnnotationIntrospector {
		private static final long serialVersionUID = -4199598047225143333L;

		final ConditionedLock lock = new ConditionedLock();

		final ArrayList<Class<? extends Annotation>> annotations = new ArrayList<>();

		ArrayList<Class<? extends Annotation>> snapshot = new ArrayList<>();

		JacksonAnnotationIntrospectorImpl() {
			super();
		}

		final void add(final Class<? extends Annotation> type) {
			this.lock.execute(() -> {
				if (this.annotations.contains(type)) return;
				this.annotations.add(type);

				this.snapshot = new ArrayList<>(this.annotations);
			});
		}

		@Override
		protected boolean _isIgnorable(Annotated annotated) {
			if (super._isIgnorable(annotated)) {
				return true;
			}

			// skip transient field
			if (annotated instanceof AnnotatedField) {
				final Field field = ((AnnotatedField) annotated).getAnnotated();
				final int modifier = field.getModifiers();
				if (Modifier.isTransient(modifier) || Modifier.isFinal(modifier) || Modifier.isStatic(modifier)) {
					return true;
				}
			}

			final ArrayList<Class<? extends Annotation>> list = this.snapshot;

			return list.stream()
			           .anyMatch(annotated::hasAnnotation);

		}
	}

	static Map<String, Object> asMap(ObjectMapper mapper,
	                                 Object content) throws JsonProcessingException {
		String elmSerial = mapper.writeValueAsString(content);

		return mapper.readValue(elmSerial,
		                        MAP_TYPE);
	}

	static Map<String, String> extractAsMap(final JsonNode node) {
		final Map<String, String> map = new LinkedHashMap<>();

		node.fields()
		    .forEachRemaining(info -> {
			    final String key = info.getKey();
			    final JsonNode value = info.getValue();

			    switch (value.getNodeType()) {
				    case STRING: {
					    map.put(key,
					            value.asText());
					    break;
				    }

				    default: {
					    log.warn(StringAssist.format("[%s] is invalid header value node type",
					                                 value.getNodeType()));
					    break;
				    }
			    }

		    });

		return map;
	}

	static void appendString(String name,
	                         String value,
	                         JsonGenerator gen) {
		try {
			gen.writeStringField(name,
			                     value);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	static void appendMap(String name,
	                      Map<String, String> map,
	                      JsonGenerator gen) {
		try {
			gen.writeObjectFieldStart(name);
			map.forEach((key, value) -> {
				try {
					gen.writeStringField(key,
					                     value);
				}
				catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
			gen.writeEndObject();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
