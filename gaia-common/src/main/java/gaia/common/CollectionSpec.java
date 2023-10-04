package gaia.common;

import static gaia.common.FunctionAssist.consume;
import static gaia.common.FunctionAssist.supply;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.Spliterators.AbstractSpliterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Getter;

import gaia.common.CharsetSpec.CharSets;
import gaia.common.ConcurrentAssist.ConditionedLock;
import gaia.common.FunctionAssist.ErrorHandleConsumer;
import gaia.common.JacksonAssist.JacksonMapper;

/**
 * @author dragon
 * @since 2020. 11. 30.
 */
public interface CollectionSpec {
	Logger log = LoggerFactory.getLogger(CollectionSpec.class);

	Function<Collection<?>, String> collection_serialize = collection -> collection.stream()
	                                                                               .filter(Objects::nonNull)
	                                                                               .map(Object::toString)
	                                                                               .collect(Collectors.joining(","));
	Function<Object, NoSuchElementException> no_such_element_exception = key -> new NoSuchElementException(StringAssist.format("[%s] not found",
	                                                                                                                           key));
	int DEFAULT_CAPACITY = 10;
	int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
	Predicate<Object> isIterable = object -> object instanceof Iterable;
	Predicate<Object> isIterator = object -> object instanceof Iterator;
	Predicate<Object> isArray = anonymous -> anonymous != null && anonymous.getClass()
	                                                                       .isArray();
	Predicate<Object> canIterate = isIterable.or(isIterator)
	                                         .or(isArray);
	Predicate<Object> emptyObject = object -> {
		if (null == object)
			return true;

		if (object instanceof Collection) {
			return ((Collection<?>) object).isEmpty();
		}

		if (object instanceof Map) {
			return ((Map<?, ?>) object).isEmpty();
		}

		if (object.getClass()
		          .isArray()) {
			return 0 == Array.getLength(object);
		}

		return false;
	};
	Predicate<Object> validObject = emptyObject.negate();
	Predicate<Collection<?>> predicate_empty_collection = collection -> null == collection || collection.isEmpty();
	Predicate<Collection<?>> predicate_valid_collection = predicate_empty_collection.negate();
	Predicate<Map<?, ?>> predicate_empty_map = map -> null == map || map.isEmpty();
	Predicate<Map<?, ?>> predicate_valid_map = predicate_empty_map.negate();
	Predicate<Object> errorResult = object -> object instanceof Throwable;
	Predicate<Object> successResult = errorResult.negate();

	static int hugeCapacity(int minCapacity) {
		if (minCapacity < 0) // overflow
			throw new OutOfMemoryError();
		return (minCapacity > MAX_ARRAY_SIZE)
		       ? Integer.MAX_VALUE
		       : MAX_ARRAY_SIZE;
	}

	static MapBuilder<String, Object> mapBuilder() {
		return new MapBuilder<>(String.class,
		                        Object.class,
		                        false);
	}

	static <V> MapBuilder<String, V> mapBuilder(Class<V> valueType) {
		return new MapBuilder<>(String.class,
		                        valueType,
		                        false);
	}

	static <K, V> MapBuilder<K, V> mapBuilder(Class<K> keyType,
	                                          Class<V> valueType) {
		return new MapBuilder<>(keyType,
		                        valueType,
		                        false);
	}

	static <K, V> MapBuilder<K, V> mapBuilder(K key,
	                                          V value) {
		return new MapBuilder<>(key,
		                        value,
		                        false);
	}

	static <K, V> MapBuilder<K, V> orderedMapBuilder(K key,
	                                                 V value) {
		return new MapBuilder<>(key,
		                        value,
		                        true);
	}

	static <T> ListBuilder<T> listBuilder(T first) {
		return new ListBuilder<>(first);
	}

	@SafeVarargs
	static <T> List<T> toList(T... values) {
		if (null == values || values.length == 0) {
			return Collections.emptyList();
		}
		return Stream.of(values)
		             .collect(Collectors.toList());
	}

	static <T> StreamBuilder<T> streamBuilder(T first) {
		return new StreamBuilder<>(first);
	}

	static <T> Stream<T> convert(Enumeration<T> enumeration) {
		EnumerationSpliterator<T> spliterator = new EnumerationSpliterator<>(Long.MAX_VALUE,
		                                                                     Spliterator.ORDERED,
		                                                                     enumeration);

		return StreamSupport.stream(spliterator,
		                            false);
	}

	static Object getValue(final Map<String, Object> header,
	                       final String key) {
		Object value = header.get(key);

		if (value != null) {
			return value;
		}

		for (Map.Entry<String, Object> entry : header.entrySet()) {
			if (entry.getKey()
			         .equalsIgnoreCase(key)) {
				return entry.getValue();
			}
		}

		return null;
	}

	static <T> List<T> asList(Iterable<T> iterable) {
		if (iterable == null) {
			return null;
		}

		List<T> list = new ArrayList<>();

		for (T elm : iterable) {
			list.add(elm);
		}

		return list;
	}

	static <T> List<T> asList(Iterator<T> iterator) {
		if (iterator == null) {
			return null;
		}

		final List<T> list = new ArrayList<>();

		iterator.forEachRemaining(list::add);

		return list;
	}

	static <T> void splitList(Iterator<T> iterator,
	                          final int limit,
	                          Consumer<List<T>> consumer) {
		if (iterator == null) {
			return;
		}

		final List<T> list = new ArrayList<>();

		iterator.forEachRemaining(elm -> {
			list.add(elm);
			if (list.size() >= limit) {
				consumer.accept(list);
				list.clear();
			}
		});

		if (list.size() > 0) {
			consumer.accept(list);
		}
	}

	static <K, V> void forEach(Map<K, V> map,
	                           BiConsumer<K, V> consumer) {
		if (map == null) {
			return;
		}

		map.forEach(consumer);
	}

	static <K, V> Stream<K> keyStream(Map<K, V> map) {
		if (map == null || map.size() == 0) {
			return Stream.empty();
		}

		return new ArrayList<>(map.keySet()).stream();
	}

	@SuppressWarnings("unchecked")
	static <T> T[] toArray(Collection<T> collection,
	                       Class<T> type) {
		if (collection.size() == 0) {
			return (T[]) Array.newInstance(type,
			                               0);
		}

		T[] array = (T[]) Array.newInstance(type,
		                                    collection.size());

		return collection.toArray(array);
	}

	static <E> void removeIf(Collection<E> collection,
	                         Predicate<E> filter,
	                         Consumer<E> removed) {
		Objects.requireNonNull(filter);
		final Iterator<E> each = collection.iterator();
		while (each.hasNext()) {
			E element = each.next();
			if (filter.test(element)) {
				each.remove();

				removed.accept(element);
			}
		}
	}

	static void iterateCollection(final Object source,
	                              final Consumer<Object> consumer) {
		if (null == source) {
			return;
		}
		if (source instanceof Iterable) {
			final Iterable<?> iterable = (Iterable<?>) source;
			iterable.forEach(consumer);
			return;
		}
		if (source instanceof CharSequence) {
			JacksonMapper.OBJECT.readASList(source.toString())
			                    .forEach(consumer);
			return;
		}

		if (source.getClass()
		          .isArray()) {
			IntStream.range(0,
			                Array.getLength(source))
			         .mapToObj(index -> Array.get(source,
			                                      index))
			         .forEach(consumer);
			return;
		}
		if (source instanceof Iterator) {
			((Iterator<?>) source).forEachRemaining(consumer);
			return;
		}
		consumer.accept(source);
	}

	static Collection<?> toCollection(final Object source) {
		if (null == source) {
			return Collections.emptyList();
		}
		if (source instanceof Collection) {
			return (Collection<?>) source;
		}
		if (source instanceof CharSequence) {
			return JacksonMapper.OBJECT.readASList(source.toString());
		}

		final List<Object> list = new ArrayList<>();

		if (source.getClass()
		          .isArray()) {
			IntStream.range(0,
			                Array.getLength(source))
			         .mapToObj(index -> Array.get(source,
			                                      index))
			         .forEach(list::add);
		} else if (source instanceof Iterable) {
			((Iterable<?>) source).forEach(list::add);
		} else if (source instanceof Iterator) {
			((Iterator<?>) source).forEachRemaining(list::add);
		} else {
			list.add(source);
		}

		return list;
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> toMap(final Object source) {
		if (null == source) {
			return Collections.emptyMap();
		}
		if (source instanceof Map) {
			return (Map<String, Object>) source;
		}


		final String content = source instanceof CharSequence
		                       ? source.toString()
		                       : supply(() -> JacksonMapper.OBJECT.marshal(source),
		                                throwable -> {
			                                throw new IllegalArgumentException(StringAssist.format("[%s] marshal error",
			                                                                                       source.getClass()
			                                                                                             .getSimpleName()),
			                                                                   throwable);
		                                });

		return supply(() -> JacksonMapper.OBJECT.readASMap(content),
		              throwable -> {
			              throw new IllegalArgumentException(StringAssist.format("[%s] unmarshal error [%n%s%n]",
			                                                                     source.getClass()
			                                                                           .getSimpleName(),
			                                                                     content),
			                                                 throwable);
		              });
	}

	static <T> Stream<T> toStream(Enumeration<T> enumeration) {
		if (null == enumeration) {
			return Stream.empty();
		}
		List<T> list = new ArrayList<>();

		while (enumeration.hasMoreElements())
			list.add(enumeration.nextElement());

		return list.stream();
	}

	static <T> Stream<T> toStream(Iterator<T> iterator) {
		if (null == iterator) {
			return Stream.empty();
		}
		return StreamSupport.stream(Spliterators.spliterator(iterator,
		                                                     Long.MAX_VALUE,
		                                                     0),
		                            false);
	}

	static <T> Stream<T> toStream(Collection<T> collection) {
		if (null == collection || collection.isEmpty()) {
			return Stream.empty();
		}
		return collection.stream();
	}

	static Object toArrayObject(Iterable<?> iterable,
	                            Class<?> type) {
		List<Object> list = new ArrayList<>();
		for (Object element : iterable) {
			list.add(element);
		}

		final int length = list.size();

		Object array = Array.newInstance(type,
		                                 length);

		for (int i = 0; i < length; i++) {
			Array.set(array,
			          i,
			          list.get(i));
		}

		return array;
	}

	static void iterateAnonymous(Object anonymous,
	                             ErrorHandleConsumer<Object> consumer) {
		if (anonymous instanceof Iterable) {
			for (Object elm : (Iterable<?>) anonymous) {
				consume(elm,
				        consumer);
			}
		} else if (anonymous instanceof Iterator) {
			final Iterator<?> iterator = (Iterator<?>) anonymous;

			while (iterator.hasNext()) {
				consume(iterator.next(),
				        consumer);
			}
		} else if (anonymous.getClass()
		                    .isArray()) {
			final int length = Array.getLength(anonymous);
			for (int i = 0; i < length; i++) {
				final Object elm = Array.get(anonymous,
				                             i);
				consume(elm,
				        consumer);
			}
		} else {
			throw new IllegalArgumentException(StringAssist.format("[%s] is not able to iterate",
			                                                       anonymous.getClass()));
		}
	}

	enum Padding {
		LEFT {
			@Override
			void append(StringBuilder builder,
			            String source,
			            char padding,
			            int delta) {
				// append padding
				IntStream.range(0,
				                delta)
				         .forEach(i -> builder.append(padding));
				// append body
				builder.append(source);
			}

			@Override
			void append(OutputStream builder,
			            byte[] bytes,
			            char padding,
			            int delta) throws IOException {
				// append padding
				for (int i = 0; i < delta; i++) {
					builder.write(padding);
				}
				// append body
				builder.write(bytes);
			}

			@Override
			String read(byte[] packet,
			            CharSets cs,
			            int length,
			            char padding) {
				int from = 0;
				// detect padding
				for (int i = 0; i < length; i++) {
					if (packet[i] != padding) {
						from = i;
						break;
					}
				}
				return cs.toString(packet,
				                   from,
				                   length - from);
			}
		},
		RIGHT {
			@Override
			void append(StringBuilder builder,
			            String source,
			            char padding,
			            int delta) {
				// append body
				builder.append(source);
				// append padding
				IntStream.range(0,
				                delta)
				         .forEach(i -> builder.append(padding));
			}

			@Override
			void append(OutputStream builder,
			            byte[] bytes,
			            char padding,
			            int delta) throws IOException {
				// append body
				builder.write(bytes);
				// append padding
				for (int i = 0; i < delta; i++) {
					builder.write(padding);
				}
			}

			@Override
			String read(byte[] packet,
			            CharSets cs,
			            int length,
			            char padding) {
				int from = 0;
				int to = length;
				// detect padding
				for (int i = length - 1; i > 0; i--) {
					if (packet[i] != padding) {
						to = i + 1;
						break;
					}
				}
				return cs.toString(packet,
				                   from,
				                   to - from);
			}
		};

		public String getPadding(String source,
		                         CharSets cs,
		                         int length,
		                         char padding) {
			byte[] bytes = cs.getBytes(source);

			final int delta = length - bytes.length;

			if (delta < 0) {
				return cs.toString(bytes,
				                   0,
				                   length);
			}

			if (delta == 0) {
				return source;
			}

			return ResourceAssist.stringComposer()
			                     .add(builder -> this.append(builder,
			                                                 source,
			                                                 padding,
			                                                 delta))
			                     .build();
		}

		public void appendPadding(StringBuilder builder,
		                          String source,
		                          CharSets cs,
		                          int length,
		                          char padding) {
			byte[] bytes = cs.getBytes(source);

			final int delta = length - bytes.length;

			if (delta < 0) {
				builder.append(cs.toString(bytes,
				                           0,
				                           length));
				return;
			}

			if (delta == 0) {
				builder.append(source);
				return;
			}

			this.append(builder,
			            source,
			            padding,
			            delta);
		}

		@SuppressWarnings("unused")
		public void appendPadding(OutputStream builder,
		                          String source,
		                          CharSets cs,
		                          int length,
		                          char padding) throws IOException {
			byte[] bytes = cs.getBytes(source);

			final int delta = length - bytes.length;

			if (delta < 0) {
				builder.write(bytes,
				              0,
				              length);
				return;
			}

			if (delta == 0) {
				builder.write(bytes);
				return;
			}

			this.append(builder,
			            bytes,
			            padding,
			            delta);
		}

		public String read(InputStream stream,
		                   CharSets cs,
		                   int length,
		                   char padding) throws IOException {
			byte[] packet = new byte[length];

			StreamAssist.readFully(stream,
			                       packet);

			return this.read(packet,
			                 cs,
			                 length,
			                 padding);
		}

		abstract void append(StringBuilder builder,
		                     String source,
		                     char padding,
		                     int delta);

		abstract void append(OutputStream builder,
		                     byte[] bytes,
		                     char padding,
		                     int delta) throws IOException;

		abstract String read(byte[] packet,
		                     CharSets cs,
		                     int length,
		                     char padding);

	}

	interface CompareTo {
		boolean compare(Object object);
	}

	interface ObjectListIndexer {
		int find(int base,
		         List<?> beans,
		         Class<?> type);
	}

	// ---------------------------------------------------------------------
	// Section HashedBlockingQueue
	// ---------------------------------------------------------------------
	interface QueueStatusListener<T> {
		default void duplicateInstance(HashedBlockingQueue<T> queue,
		                               T instance) {
			// implements case
		}

		default void offered(HashedBlockingQueue<T> queue,
		                     T instance) {

		}

		default void pollResultNull(HashedBlockingQueue<T> queue) {

		}

		default void polled(HashedBlockingQueue<T> queue,
		                    T instance) {

		}

		default void removed(HashedBlockingQueue<T> queue,
		                     T instance) {

		}
	}

	class TraceNode {
		Object value;

		Throwable stack;

		TraceNode() {
			super();
		}

		TraceNode(Object value) {
			this();
			this.set(value);
		}

		void set(Object value) {
			this.value = value;
			this.stack = new Throwable("stack");
		}

		@Override
		public boolean equals(Object obj) {
			if (null == this.value) {
				return false;
			}
			return this.value.equals(obj);
		}
	}

	class TraceableList<E>
			extends AbstractList<E>
			implements List<E>,
			           RandomAccess {
		transient TraceNode[] elementData;

		private int size;

		public TraceableList() {
			this(DEFAULT_CAPACITY);
		}

		public TraceableList(int initialCapacity) {
			this.elementData = new TraceNode[initialCapacity];
		}

		/**
		 * for internal use
		 *
		 * @return mod count
		 */
		int getModCount() {
			return this.modCount;
		}

		public Throwable stackOf(Object o) {
			final int index = this.indexOf(o);

			if (index < 0) {
				return null;
			}

			return this.elementData[index].stack;
		}

		/**
		 * Increases the capacity of this <tt>ArrayList</tt> instance, if
		 * necessary, to ensure that it can hold at least the number of elements
		 * specified by the minimum capacity argument.
		 *
		 * @param minCapacity the desired minimum capacity
		 */
		public void ensureCapacity(int minCapacity) {
			// overflow-conscious code
			if (minCapacity - this.elementData.length > 0) {
				this.modCount++;
				this.grow(minCapacity);
			}
		}

		@Override
		public int size() {
			return this.size;
		}

		@Override
		public boolean isEmpty() {
			return this.size == 0;
		}

		@Override
		public boolean contains(Object o) {
			if (o == null) {
				for (int i = 0; i < this.size; i++) {
					if (this.elementData[i] == null) {
						return true;
					}
				}
			} else {
				for (int i = 0; i < this.size; i++) {
					if (this.elementData[i] != null && this.elementData[i].equals(o)) {
						return true;
					}
				}
			}
			return false;
		}

		@Override
		public int indexOf(Object o) {
			if (o == null) {
				for (int i = 0; i < this.size; i++) {
					if (this.elementData[i] == null) {
						return i;
					}
				}
			} else {
				for (int i = 0; i < this.size; i++) {
					if (this.elementData[i] != null && this.elementData[i].equals(o)) {
						return i;
					}
				}
			}
			return -1;
		}

		@Override
		public int lastIndexOf(Object o) {
			if (o == null) {
				for (int i = this.size - 1; i >= 0; i--) {
					if (this.elementData[i] == null) {
						return i;
					}
				}
			} else {
				for (int i = this.size - 1; i >= 0; i--) {
					if (this.elementData[i].equals(o)) {
						return i;
					}
				}
			}
			return -1;
		}

		@Override
		public Object[] toArray() {
			Object[] array = new Object[this.size];
			for (int i = 0; i < this.size; i++) {
				array[i] = this.elementData[i].value;
			}
			return array;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> T[] toArray(T[] a) {
			final int length = a.length;

			if (a.length < this.size) {
				final T[] created = (T[]) Array.newInstance(a.getClass()
				                                             .getComponentType(),
				                                            this.size);
				// Make a new array of a's runtime type, but my contents:
				for (int i = 0; i < this.size; i++) {
					created[i] = (T) this.elementData[i].value;
				}

				return created;
			}

			for (int i = 0; i < length; i++) {
				a[i] = i < this.size
				       ? (T) this.elementData[i].value
				       : null;
			}

			return a;
		}

		@Override
		public E get(int index) {
			this.rangeCheck(index);

			return this.getElementData(index);
		}

		@Override
		public E set(int index,
		             E element) {
			this.rangeCheck(index);

			E oldValue = this.getElementData(index);
			this.elementData[index].value = element;
			return oldValue;
		}

		@Override
		public boolean add(E e) {
			this.ensureCapacity(this.size + 1); // Increments modCount!!
			this.setData(this.size++,
			             e);
			return true;
		}

		@Override
		public void add(int index,
		                E element) {
			this.rangeCheckForAdd(index);

			this.ensureCapacity(this.size + 1); // Increments modCount!!

			System.arraycopy(this.elementData,
			                 index,
			                 this.elementData,
			                 index + 1,
			                 this.size - index);

			this.elementData[index] = new TraceNode(element);
			this.size++;
		}

		@Override
		public E remove(int index) {
			this.rangeCheck(index);

			this.modCount++;
			E oldValue = this.getElementData(index);

			int numMoved = this.size - index - 1;
			if (numMoved > 0)
				System.arraycopy(this.elementData,
				                 index + 1,
				                 this.elementData,
				                 index,
				                 numMoved);
			this.elementData[--this.size] = null; // clear to let GC do its work

			return oldValue;
		}

		@Override
		public boolean remove(Object o) {
			assert null != o
					: "null object not supported";

			final int index = this.indexOf(o);

			if (index < 0) {
				return false;
			}

			this.fastRemove(index);

			return true;
		}

		@Override
		public void clear() {
			this.modCount++;

			// clear to let GC do its work
			for (int i = 0; i < this.size; i++)
				this.elementData[i] = null;

			this.size = 0;
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			if (index < 0 || index > this.size)
				throw new IndexOutOfBoundsException("Index: " + index);
			return new TraceableListIterator<>(this,
			                                   index);
		}

		@Override
		public ListIterator<E> listIterator() {
			return this.listIterator(0);
		}

		@Override
		public Iterator<E> iterator() {
			return new TraceableIterator<>(this);
		}

		@Override
		@SuppressWarnings("unchecked")
		public void forEach(Consumer<? super E> action) {
			Objects.requireNonNull(action);
			final int expectedModCount = this.modCount;
			final TraceNode[] elementData = this.elementData;
			final int size = this.size;
			for (int i = 0; this.modCount == expectedModCount && i < size; i++) {
				action.accept((E) elementData[i].value);
			}
			if (this.modCount != expectedModCount) {
				throw new ConcurrentModificationException();
			}
		}

		@Override
		public void sort(Comparator<? super E> c) {
			throw new UnsupportedOperationException("unsupported method");
		}

		@Override
		public void replaceAll(UnaryOperator<E> operator) {
			throw new UnsupportedOperationException("unsupported method");
		}

		@Override
		public boolean removeIf(Predicate<? super E> filter) {
			throw new UnsupportedOperationException("unsupported method");
		}

		@Override
		public Spliterator<E> spliterator() {
			throw new UnsupportedOperationException("unsupported method");
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			throw new UnsupportedOperationException("unsupported method");
		}

		@Override
		public boolean addAll(int index,
		                      Collection<? extends E> c) {
			throw new UnsupportedOperationException("unsupported method");
		}

		@Override
		protected void removeRange(int fromIndex,
		                           int toIndex) {
			throw new UnsupportedOperationException("unsupported method");
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			throw new UnsupportedOperationException("unsupported method");
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException("unsupported method");
		}

		@Override
		public List<E> subList(int fromIndex,
		                       int toIndex) {
			throw new UnsupportedOperationException("unsupported method");
		}

		@SuppressWarnings("unchecked")
		E getElementData(int index) {
			if (this.elementData[index] == null) {
				this.elementData[index] = new TraceNode();
			}
			return (E) this.elementData[index].value;
		}

		void setData(int index,
		             E e) {
			if (this.elementData[index] == null) {
				this.elementData[index] = new TraceNode(e);
			} else {
				this.elementData[index].set(e);
			}
		}

		private void fastRemove(int index) {
			this.modCount++;
			int numMoved = this.size - index - 1;
			if (numMoved > 0)
				System.arraycopy(this.elementData,
				                 index + 1,
				                 this.elementData,
				                 index,
				                 numMoved);
			this.elementData[--this.size] = null; // clear to let GC do its work
		}

		private void rangeCheckForAdd(int index) {
			if (index > this.size || index < 0)
				throw new IndexOutOfBoundsException(this.outOfBoundsMsg(index));
		}

		private void rangeCheck(int index) {
			if (index >= this.size)
				throw new IndexOutOfBoundsException(this.outOfBoundsMsg(index));
		}

		private String outOfBoundsMsg(int index) {
			return "Index: " + index + ", Size: " + this.size;
		}

		private void grow(int minCapacity) {
			// overflow-conscious code
			int oldCapacity = this.elementData.length;
			int newCapacity = oldCapacity + (oldCapacity >> 1);
			if (newCapacity - minCapacity < 0)
				newCapacity = minCapacity;
			if (newCapacity - MAX_ARRAY_SIZE > 0)
				newCapacity = hugeCapacity(minCapacity);
			// minCapacity is usually close to size, so this is a win:
			this.elementData = Arrays.copyOf(this.elementData,
			                                 newCapacity);
		}
	}

	class TraceableIterator<E>
			implements Iterator<E> {
		final TraceableList<E> list;

		int cursor; // index of next element to return

		int lastRet = -1; // index of last element returned; -1 if no such

		int expectedModCount;

		TraceableIterator(TraceableList<E> list) {
			this.list = list;
			this.expectedModCount = list.getModCount();
		}

		@Override
		public boolean hasNext() {
			return this.cursor != this.list.size;
		}

		@Override
		@SuppressWarnings("unchecked")
		public E next() {
			this.checkForModification();
			int i = this.cursor;
			if (i >= this.list.size)
				throw new NoSuchElementException();
			TraceNode[] elementData = this.list.elementData;
			if (i >= elementData.length)
				throw new ConcurrentModificationException();
			this.cursor = i + 1;
			return (E) elementData[this.lastRet = i].value;
		}

		@Override
		public void remove() {
			if (this.lastRet < 0)
				throw new IllegalStateException();
			this.checkForModification();

			try {
				this.list.remove(this.lastRet);
				this.cursor = this.lastRet;
				this.lastRet = -1;
				this.expectedModCount = this.list.getModCount();
			}
			catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}

		@Override
		@SuppressWarnings("unchecked")
		public void forEachRemaining(Consumer<? super E> consumer) {
			Objects.requireNonNull(consumer);
			final int size = this.list.size;
			int i = this.cursor;
			if (i >= size) {
				return;
			}
			final TraceNode[] elementData = this.list.elementData;
			if (i >= elementData.length) {
				throw new ConcurrentModificationException();
			}
			while (i != size && this.list.getModCount() == this.expectedModCount) {
				consumer.accept((E) elementData[i++]);
			}
			// update once at end of iteration to reduce heap write traffic
			this.cursor = i;
			this.lastRet = i - 1;
			this.checkForModification();
		}

		final void checkForModification() {
			if (this.list.getModCount() != this.expectedModCount)
				throw new ConcurrentModificationException();
		}
	}

	class TraceableListIterator<E>
			extends TraceableIterator<E>
			implements ListIterator<E> {
		TraceableListIterator(TraceableList<E> list,
		                      int index) {
			super(list);
			this.cursor = index;
		}

		@Override
		public boolean hasPrevious() {
			return this.cursor != 0;
		}

		@Override
		public int nextIndex() {
			return this.cursor;
		}

		@Override
		public int previousIndex() {
			return this.cursor - 1;
		}

		@Override
		@SuppressWarnings("unchecked")
		public E previous() {
			this.checkForModification();
			int i = this.cursor - 1;
			if (i < 0)
				throw new NoSuchElementException();
			TraceNode[] elementData = this.list.elementData;
			if (i >= elementData.length)
				throw new ConcurrentModificationException();
			this.cursor = i;
			return (E) elementData[this.lastRet = i].value;
		}

		@Override
		public void set(E e) {
			if (this.lastRet < 0)
				throw new IllegalStateException();
			this.checkForModification();

			try {
				this.list.set(this.lastRet,
				              e);
			}
			catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}

		@Override
		public void add(E e) {
			this.checkForModification();

			try {
				int i = this.cursor;
				this.list.add(i,
				              e);
				this.cursor = i + 1;
				this.lastRet = -1;
				this.expectedModCount = this.list.getModCount();
			}
			catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}
	}

	class ArraySet<E>
			extends AbstractSet<E>
			implements Serializable {
		private static final long serialVersionUID = -1609657509314984935L;

		ArrayList<E> list = null;

		public ArraySet(Collection<E> collection) {
			this();
			this.list.addAll(collection);
		}

		public ArraySet() {
			super();
			this.list = new ArrayList<>();
		}

		@Override
		public Iterator<E> iterator() {
			return this.list.iterator();
		}

		@Override
		public int size() {
			return this.list.size();
		}

		@Override
		public boolean contains(Object o) {
			return this.list.contains(o);
		}

		@Override
		public boolean add(E o) {
			return this.list.add(o);
		}

		@Override
		public boolean remove(Object o) {
			return this.list.remove(o);
		}

		@Override
		public void clear() {
			this.list.clear();
		}
	}

	class UniqueListMap<K, V>
			extends HashMap<K, List<V>>
			implements Map<K, List<V>> {
		private static final long serialVersionUID = 838933032942353795L;

		public void add(K key,
		                V value) {
			this.computeIfAbsent(key,
			                     candidate -> new UniqueList<>())
			    .add(value);
		}
	}

	class UniqueList<T>
			extends ArrayList<T> {
		private static final long serialVersionUID = 6218185165672833648L;

		@Override
		public boolean add(T t) {
			if (super.contains(t)) {
				return false;
			}
			return super.add(t);
		}

		@Override
		public boolean addAll(Collection<? extends T> collection) {
			for (T t : collection) {
				this.add(t);
			}
			return true;
		}

		@Override
		public boolean addAll(int index,
		                      Collection<? extends T> c) {
			throw new UnsupportedOperationException("indexed addAll not supported!");
		}

		public boolean missing(T object) {
			return !super.contains(object);
		}
	}

	class ExtendList<T>
			extends UniqueList<T> {
		private static final long serialVersionUID = -8347625155560008793L;
		final Comparator<? super T> comparator;

		public ExtendList(Comparator<? super T> comparator) {
			super();
			this.comparator = comparator;
		}

		@Override
		public boolean add(T t) {
			if (super.add(t)) {
				super.sort(this.comparator);
				return true;
			}

			return false;
		}
	}

	class SmartArrayList<T>
			extends ArrayList<T> {
		private static final long serialVersionUID = 2394229260874096692L;
		final Comparator<? super T> comparator;
		@Getter
		private final ConditionedLock lock = new ConditionedLock();

		public SmartArrayList() {
			this(null);
		}

		public SmartArrayList(Comparator<? super T> comparator) {
			super();
			this.comparator = Objects.isNull(comparator)
			                  ? (o1, o2) -> 0
			                  : comparator;
		}

		@Override
		public boolean add(T t) {
			return this.lock.tran(() -> {
				boolean ret = super.add(t);
				super.sort(this.comparator);
				return ret;
			});
		}

		@Override
		public T remove(int index) {
			return this.lock.tran(() -> {
				T ret = super.remove(index);
				super.sort(this.comparator);
				return ret;
			});
		}

		@Override
		public boolean remove(Object o) {
			return this.lock.tran(() -> {
				boolean ret = super.remove(o);
				super.sort(this.comparator);
				return ret;
			});
		}

		@Override
		public T get(int index) {
			return this.lock.tran(() -> super.get(index));
		}
	}

	abstract class LRUCacheMap<K, V>
			extends LinkedHashMap<K, V> {
		private static final long serialVersionUID = -6505390229006928135L;
		final Class<K> keyType;
		private final ConditionedLock lock;
		private final int limit;

		public LRUCacheMap() {
			this(16,
			     1024,
			     true);
		}

		/**
		 * @param limit
		 * @param fair
		 */
		@SuppressWarnings("unchecked")
		public LRUCacheMap(final int initial,
		                   final int limit,
		                   final boolean fair) {
			super(initial,
			      0.75f,
			      true);
			this.limit = limit;
			this.lock = new ConditionedLock(fair);
			this.keyType = (Class<K>) ReflectAssist.getGenericParameter(this.getClass(),
			                                                            LRUCacheMap.class,
			                                                            "K");
		}

		private V ref(K key) {
			V value = super.get(key);
			if (value != null) {
				super.remove(key);
				super.put(key,
				          value);
			} else {
				value = this.generate(key);
				if (value != null) {
					super.put(key,
					          value);
				}
			}
			return value;
		}

		@Override
		public V computeIfAbsent(K key,
		                         Function<? super K, ? extends V> mappingFunction) {
			return this.lock.tran(() -> super.computeIfAbsent(key,
			                                                  mappingFunction));
		}

		/**
		 * @param key
		 * @return
		 */
		public final V getSilent(K key) {
			return this.lock.tran(() -> this.ref(key));
		}

		/**
		 *
		 */
		@Override
		public final V get(Object key) {
			return this.lock.tran(() -> this.ref(this.keyType.cast(key)));
		}

		/**
		 *
		 */
		@Override
		protected final boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
			final boolean flag = super.size() > this.limit;
			if (flag) {
				this.onRemoveEldestEntry(eldest);
			}
			return flag;
		}

		protected void onRemoveEldestEntry(final Map.Entry<K, V> eldest) {

		}

		/**
		 * @param key
		 * @return
		 */
		protected abstract V generate(K key);

		/**
		 *
		 */
		@Override
		public final int size() {
			return this.lock.tran(super::size);
		}

		/**
		 *
		 */
		@Override
		public final V put(K key,
		                   V value) {
			return this.lock.tran(() -> super.put(key,
			                                      value));
		}

		/**
		 *
		 */
		@Override
		public final V remove(Object key) {
			return this.lock.tran(() -> super.remove(key));
		}

		/**
		 *
		 */
		@Override
		public final String toString() {
			return this.lock.tran(super::toString);
		}
	}

	class MapEntry<K, V>
			implements Map.Entry<K, V> {
		static MapEntry<?, ?>[] NULL = new MapEntry[]{};

		final K key;

		final AtomicReference<V> value = new AtomicReference<>();

		public MapEntry(K key) {
			this.key = key;
		}

		public MapEntry(K key,
		                V value) {
			this.key = key;
			this.value.set(value);
		}

		@Override
		public K getKey() {
			return this.key;
		}

		@Override
		public V getValue() {
			return this.value.get();
		}

		@Override
		public V setValue(V value) {
			return this.value.getAndSet(value);
		}

		@Override
		public String toString() {
			return StringAssist.format("%s=%s",
			                           this.key,
			                           this.value);
		}
	}

	class MapBuilder<K, V> {
		final Map<K, V> map;

		MapBuilder(boolean ordered) {
			super();
			this.map = ordered
			           ? new LinkedHashMap<>()
			           : new HashMap<>();
		}

		MapBuilder(final K key,
		           final V value,
		           boolean ordered) {
			this(ordered);
			this.map.put(key,
			             value);
		}

		MapBuilder(final Class<K> keyType,
		           final Class<V> valueType,
		           boolean ordered) {
			this(ordered);
		}

		public MapBuilder<K, V> put(K key,
		                            V value) {
			this.map.put(key,
			             value);
			return this;
		}

		public Map<K, V> build() {
			return this.map;
		}
	}

	class ListBuilder<T> {
		final List<T> list = new ArrayList<>();

		ListBuilder(T first) {
			this.list.add(first);
		}

		public ListBuilder<T> add(T instance) {
			this.list.add(instance);
			return this;
		}

		public List<T> build() {
			return this.list;
		}
	}

	class StreamBuilder<T> {
		final List<T> list = new ArrayList<>();

		StreamBuilder(T first) {
			this.list.add(first);
		}

		public StreamBuilder<T> add(T instance) {
			this.list.add(instance);
			return this;
		}

		public Stream<T> build() {
			return this.list.stream();
		}
	}

	class EnumerationSpliterator<T>
			extends AbstractSpliterator<T> {

		private final Enumeration<T> enumeration;

		public EnumerationSpliterator(long est,
		                              int additionalCharacteristics,
		                              Enumeration<T> enumeration) {
			super(est,
			      additionalCharacteristics);
			this.enumeration = enumeration;
		}

		@Override
		public boolean tryAdvance(Consumer<? super T> action) {
			if (this.enumeration.hasMoreElements()) {
				action.accept(this.enumeration.nextElement());
				return true;
			}
			return false;
		}

		@Override
		public void forEachRemaining(Consumer<? super T> action) {
			while (this.enumeration.hasMoreElements())
				action.accept(this.enumeration.nextElement());
		}
	}

	class IntStreamCount {
		public static void main(String[] args) {
			IntStream.range(0,
			                10)
			         .forEach(value -> log.info(String.valueOf(value)));
		}
	}

	final class NullListener<M>
			implements QueueStatusListener<M> {
	}

	@Getter
	final class HashedBlockingQueue<T> {
		final String name;
		final LinkedList<T> queue;
		final QueueStatusListener<T> listener;
		final ConditionedLock lock = new ConditionedLock();

		public HashedBlockingQueue() {
			this(null,
			     null);
		}

		public HashedBlockingQueue(final String name) {
			this(name,
			     null);
		}

		public HashedBlockingQueue(final String name,
		                           final QueueStatusListener<T> listener) {
			super();
			this.name = name == null
			            ? "UNDEFINED"
			            : name;
			this.queue = new LinkedList<>();
			this.listener = listener == null
			                ? new NullListener<>()
			                : listener;
		}

		public void drainTo(Collection<T> collection) {
			this.lock.execute(() -> {
				collection.addAll(this.queue);
				this.queue.clear();
			});
		}

		public void remove(final T e) {
			this.lock.execute(() -> {
				if (this.queue.remove(e)) {
					this.listener.removed(this,
					                      e);
					this.lock.notifyFor();
				}
			});
		}

		/**
		 * @param timeout
		 * @param unit
		 * @return
		 */
		public T poll(final long timeout,
		              final TimeUnit unit) {
			final ConditionedLock lock = this.lock;

			final long limit = System.currentTimeMillis() + unit.toMillis(timeout);

			long remain = unit.toMillis(timeout);

			while (remain > 0) {
				if (this.queue.size() > 0) {
					T elm = lock.tran(() -> {
						                  if (this.queue.size() > 0) {
							                  final T e = this.queue.removeFirst();

							                  this.listener.polled(this,
							                                       e);

							                  return e;
						                  }
						                  return null;
					                  },
					                  thw -> {
						                  log.error(StringAssist.format("[%s] poll error [%s]",
						                                                this.name,
						                                                ExceptionAssist.getStackMessage(thw)),
						                            thw);
						                  return null;
					                  });

					if (null != elm) {
						return elm;
					}
				}

				remain = limit - System.currentTimeMillis();

				if (remain > 0) {
					lock.waitFor(remain,
					             TimeUnit.MILLISECONDS,
					             this.queue::isEmpty);
				}
			}

			this.listener.pollResultNull(this);

			return null;
		}

		/**
		 * @param e
		 */
		public boolean offer(final T e) {
			return Boolean.TRUE.equals(this.lock.tran(() -> {
				                                          if (this.queue.contains(e)) {
					                                          log.warn(StringAssist.format("already registered queue object [%s]",
					                                                                       e.toString()));
					                                          this.listener.duplicateInstance(this,
					                                                                          e);
					                                          return false;
				                                          }
				                                          this.queue.addLast(e);
				                                          this.listener.offered(this,
				                                                                e);
				                                          return true;
			                                          },
			                                          thw -> {
				                                          log.error(StringAssist.format("queue offer error [%s]",
				                                                                        StringAssist.valueOf(e)),
				                                                    thw);
				                                          return false;
			                                          }));
		}

		/**
		 * @param e
		 * @return
		 */
		public boolean contains(final T e) {
			return this.lock.tran(() -> this.queue.contains(e));
		}

		/**
		 * @return
		 */
		public int size() {
			return this.lock.tran(this.queue::size);
		}
	}

	abstract class HashedObjectContainer<K, V>
			implements Closeable {

		private final HashMap<K, V> map = new HashMap<>();

		private final HashMap<K, Error> invalid = new HashMap<>();

		private final boolean nullable;

		private final ConditionedLock lock = new ConditionedLock();

		V defaultInstance = null;

		protected HashedObjectContainer() {
			this(false);
		}

		protected HashedObjectContainer(boolean flag) {
			super();
			this.nullable = flag;
		}

		/**
		 * TODO : change reference
		 *
		 * @return
		 */
		public final Stream<V> valueStream() {
			return this.lock.tran(() -> new ArrayList<>(this.map.values()))
			                .stream();
		}

		/**
		 * @return
		 */
		public final Set<K> keySet() {
			return this.map.keySet();
		}

		/**
		 * @param key
		 * @return
		 */
		public final boolean remove(K key) {
			final boolean flag = this.remove0(key);
			this.remove1(key);
			return flag;
		}

		/**
		 * @param key
		 */
		protected final boolean remove0(final K key) {
			return this.lock.tran(() -> {
				V value = this.map.remove(key);
				return value != null;
			});
		}

		@SuppressWarnings("EmptyMethod")
		protected void remove1(final K key) {
			// customize when key is removed
		}

		/**
		 * @param key
		 */
		public final boolean isValidKey(final K key) {
			try {
				final V value = this.getInstance(key);
				return value != null;
			}
			catch (RuntimeException re) {
				return false;
			}
		}

		/**
		 * @param key
		 * @return
		 */
		public V getInstance(K key) {
			if (key == null) {
				return this.getDefaultInstance();
			}
			return this.lookup(key);
		}

		/**
		 * @return the defaultInstance
		 */
		public final V getDefaultInstance() {
			if (this.defaultInstance == null) {
				this.defaultInstance = this.makeDefaultInstance();
			}

			if (this.defaultInstance == null) {
				throw new RuntimeException("lookup.key.is.null.but.default.did.not.assign");
			}

			return this.defaultInstance;
		}

		/**
		 * @param defaultInstance the defaultInstance to set
		 */
		public final void setDefaultInstance(V defaultInstance) {
			this.defaultInstance = defaultInstance;
		}

		private V lookup(K key) {
			return this.lock.tran(() -> {
				                      final V cached = this.get(key);

				                      if (cached != null) {
					                      return cached;
				                      }

				                      final Error re = this.invalid.get(key);

				                      if (re != null) {
					                      if (this.nullable) {
						                      return null;
					                      }
					                      throw re;
				                      }

				                      try {
					                      final V created = this.make(key);

					                      if (created != null) {
						                      this.map.put(key,
						                                   created);
						                      return created;
					                      }
				                      }
				                      catch (Throwable thw) {
					                      log.error(StringAssist.format("instance create error [%s][%s]",
					                                                    key,
					                                                    thw.getMessage()),
					                                thw);
					                      if (thw instanceof RuntimeException) {
						                      if (this.nullable) {
							                      return null;
						                      }
						                      throw (RuntimeException) thw;
					                      }

					                      final Error e = thw instanceof Error
					                                      ? (Error) thw
					                                      : new Error("instance create error",
					                                                  thw);
					                      this.invalid.put(key,
					                                       e);
					                      if (this.nullable) {
						                      return null;
					                      }
					                      throw e;
				                      }

				                      this.invalid.put(key,
				                                       new Error("instance is null"));

				                      return null;
			                      },
			                      throwable -> {
				                      if (throwable instanceof Error) {
					                      throw (Error) throwable;
				                      }
				                      if (throwable instanceof RuntimeException) {
					                      throw (RuntimeException) throwable;
				                      }
				                      throw new RuntimeException("hashed object evaluate error",
				                                                 throwable);
			                      });
		}

		@SuppressWarnings("SameReturnValue")
		protected V makeDefaultInstance() {
			// customize entry point
			return null;
		}

		/**
		 * @param key
		 * @return
		 */
		public final V get(final K key) {
			return this.map.get(key);
		}

		/**
		 * @param key
		 * @return
		 */
		protected abstract V make(K key) throws Throwable;

		/**
		 * @param key
		 * @return
		 */
		public final Error getInvalidReason(final K key) {
			this.getInstance(key);

			return this.invalid.get(key);
		}

		/**
		 * @param key
		 * @param value
		 */
		public final boolean register(final K key,
		                              final V value,
		                              final boolean force) {
			return this.lock.tran(() -> {
				if (force || !this.map.containsKey(key)) {
					this.map.put(key,
					             value);
					return true;
				}
				return false;
			});
		}

		/**
		 * @return
		 */
		public final List<V> getValues() {
			List<V> values = new ArrayList<>();
			this.iterate(values);
			return values;
		}

		/**
		 * @param list
		 */
		public final void iterate(final List<V> list) {
			this.iterate(list::add);
		}

		/**
		 * @param listener
		 */
		public final void iterate(final Consumer<V> listener) {
			this.lock.execute(() -> this.map.forEach((key, value) -> listener.accept(value)));
		}

		/**
		 * @param key
		 * @return
		 */
		public boolean contain(K key) {
			return this.map.containsKey(key);
		}

		@Override
		public void close() {
			this.clear();
		}

		public final void clear() {
			this.lock.execute(() -> {
				this.map.clear();
				this.invalid.clear();
			});
		}
	}

	class CaseInsensitiveMap<V>
			implements Map<String, V>,
			           Serializable {
		private final LinkedHashMap<String, V> targetMap;
		private final HashMap<String, String> caseInsensitiveKeys;
		@Getter
		private final Locale locale;
		private transient volatile Set<String> keySet;
		private transient volatile Collection<V> values;
		private transient volatile Set<Entry<String, V>> entrySet;

		public CaseInsensitiveMap() {
			this((Locale) null);
		}

		public CaseInsensitiveMap(Locale locale) {
			this(12,
			     locale);
		}

		public CaseInsensitiveMap(int expectedSize) {
			this(expectedSize,
			     (Locale) null);
		}

		public CaseInsensitiveMap(int expectedSize,
		                          Locale locale) {
			this.targetMap = new LinkedHashMap<String, V>((int) ((float) expectedSize / 0.75F),
			                                              0.75F) {
				public boolean containsKey(Object key) {
					return CaseInsensitiveMap.this.containsKey(key);
				}

				protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
					boolean doRemove = CaseInsensitiveMap.this.removeEldestEntry(eldest);
					if (doRemove) {
						CaseInsensitiveMap.this.removeCaseInsensitiveKey(eldest.getKey());
					}

					return doRemove;
				}
			};
			this.caseInsensitiveKeys = newHashMap(expectedSize);
			this.locale = locale != null
			              ? locale
			              : Locale.getDefault();
		}

		public int size() {
			return this.targetMap.size();
		}

		public boolean isEmpty() {
			return this.targetMap.isEmpty();
		}

		public boolean containsKey(Object key) {
			return key instanceof String && this.caseInsensitiveKeys.containsKey(this.convertKey((String) key));
		}

		public boolean containsValue(Object value) {
			return this.targetMap.containsValue(value);
		}


		public V get(Object key) {
			if (key instanceof String) {
				final String caseInsensitiveKey = this.caseInsensitiveKeys.get(this.convertKey((String) key));
				if (caseInsensitiveKey != null) {
					return this.targetMap.get(caseInsensitiveKey);
				}
			}

			return null;
		}


		public V getOrDefault(Object key,
		                      V defaultValue) {
			if (key instanceof String) {
				String caseInsensitiveKey = this.caseInsensitiveKeys.get(this.convertKey((String) key));
				if (caseInsensitiveKey != null) {
					return this.targetMap.get(caseInsensitiveKey);
				}
			}

			return defaultValue;
		}


		public V put(String key,
		             V value) {
			String oldKey = this.caseInsensitiveKeys.put(this.convertKey(key),
			                                             key);
			V oldKeyValue = null;
			if (oldKey != null && !oldKey.equals(key)) {
				oldKeyValue = this.targetMap.remove(oldKey);
			}

			V oldValue = this.targetMap.put(key,
			                                value);
			return oldKeyValue != null
			       ? oldKeyValue
			       : oldValue;
		}

		public void putAll(Map<? extends String, ? extends V> map) {
			if (!map.isEmpty()) {
				map.forEach(this::put);
			}
		}

		private String filterKey(String key) {
			return this.caseInsensitiveKeys.putIfAbsent(this.convertKey(key),
			                                            key);
		}

		public V putIfAbsent(String key,
		                     V value) {
			String oldKey = this.filterKey(key);

			if (oldKey != null) {
				V oldKeyValue = this.targetMap.get(oldKey);
				if (oldKeyValue != null) {
					return oldKeyValue;
				}

				key = oldKey;
			}

			return this.targetMap.putIfAbsent(key,
			                                  value);
		}


		public V computeIfAbsent(String key,
		                         Function<? super String, ? extends V> mappingFunction) {
			String oldKey = this.filterKey(key);
			if (oldKey != null) {
				V oldKeyValue = this.targetMap.get(oldKey);
				if (oldKeyValue != null) {
					return oldKeyValue;
				}

				key = oldKey;
			}

			return this.targetMap.computeIfAbsent(key,
			                                      mappingFunction);
		}


		public V remove(Object key) {
			if (key instanceof String) {
				String caseInsensitiveKey = this.removeCaseInsensitiveKey((String) key);
				if (caseInsensitiveKey != null) {
					return this.targetMap.remove(caseInsensitiveKey);
				}
			}

			return null;
		}

		public void clear() {
			this.caseInsensitiveKeys.clear();
			this.targetMap.clear();
		}

		public Set<String> keySet() {
			Set<String> keySet = this.keySet;
			if (keySet == null) {
				keySet = new KeySet(this.targetMap.keySet());
				this.keySet = keySet;
			}

			return keySet;
		}

		public Collection<V> values() {
			Collection<V> values = this.values;
			if (values == null) {
				values = new Values(this.targetMap.values());
				this.values = values;
			}

			return values;
		}

		public Set<Entry<String, V>> entrySet() {
			Set<Entry<String, V>> entrySet = this.entrySet;
			if (entrySet == null) {
				entrySet = new EntrySet(this.targetMap.entrySet());
				this.entrySet = entrySet;
			}

			return entrySet;
		}

		public boolean equals(Object other) {
			if (!(other instanceof Map)) {
				return false;
			}
			return this == other || this.targetMap.equals(other);
		}

		public int hashCode() {
			return this.targetMap.hashCode();
		}

		public String toString() {
			return this.targetMap.toString();
		}

		protected String convertKey(String key) {
			return key.toLowerCase(this.getLocale());
		}

		protected boolean removeEldestEntry(Entry<String, V> eldest) {
			return false;
		}


		private String removeCaseInsensitiveKey(String key) {
			return this.caseInsensitiveKeys.remove(this.convertKey(key));
		}

		private class EntrySetIterator
				extends EntryIterator<Entry<String, V>> {
			private EntrySetIterator() {
				super();
			}

			public Entry<String, V> next() {
				return this.nextEntry();
			}
		}

		private class ValuesIterator
				extends EntryIterator<V> {
			private ValuesIterator() {
				super();
			}

			public V next() {
				return this.nextEntry()
				           .getValue();
			}
		}

		private class KeySetIterator
				extends EntryIterator<String> {
			private KeySetIterator() {
				super();
			}

			public String next() {
				return (String) this.nextEntry()
				                    .getKey();
			}
		}

		private abstract class EntryIterator<T>
				implements Iterator<T> {
			private final Iterator<Entry<String, V>> delegate;

			private Entry<String, V> last;

			public EntryIterator() {
				this.delegate = CaseInsensitiveMap.this.targetMap.entrySet()
				                                                 .iterator();
			}

			protected Entry<String, V> nextEntry() {
				Entry<String, V> entry = this.delegate.next();
				this.last = entry;
				return entry;
			}

			public boolean hasNext() {
				return this.delegate.hasNext();
			}

			public void remove() {
				this.delegate.remove();
				if (this.last != null) {
					CaseInsensitiveMap.this.removeCaseInsensitiveKey(this.last.getKey());
					this.last = null;
				}

			}
		}

		private class EntrySet
				extends AbstractSet<Entry<String, V>> {
			private final Set<Entry<String, V>> delegate;

			public EntrySet(Set<Entry<String, V>> delegate) {
				this.delegate = delegate;
			}

			public int size() {
				return this.delegate.size();
			}

			public boolean contains(Object o) {
				return this.delegate.contains(o);
			}

			public Iterator<Entry<String, V>> iterator() {
				return CaseInsensitiveMap.this.new EntrySetIterator();
			}

			@SuppressWarnings("unchecked")
			public boolean remove(Object o) {
				if (this.delegate.remove(o)) {
					CaseInsensitiveMap.this.removeCaseInsensitiveKey(((Entry<String, V>) o).getKey());
					return true;
				} else {
					return false;
				}
			}

			public void clear() {
				this.delegate.clear();
				CaseInsensitiveMap.this.caseInsensitiveKeys.clear();
			}

			public Spliterator<Entry<String, V>> spliterator() {
				return this.delegate.spliterator();
			}

			public void forEach(Consumer<? super Entry<String, V>> action) {
				this.delegate.forEach(action);
			}
		}

		private class Values
				extends AbstractCollection<V> {
			private final Collection<V> delegate;

			Values(Collection<V> delegate) {
				this.delegate = delegate;
			}

			public int size() {
				return this.delegate.size();
			}

			public boolean contains(Object o) {
				return this.delegate.contains(o);
			}

			public Iterator<V> iterator() {
				return CaseInsensitiveMap.this.new ValuesIterator();
			}

			public void clear() {
				CaseInsensitiveMap.this.clear();
			}

			public Spliterator<V> spliterator() {
				return this.delegate.spliterator();
			}

			public void forEach(Consumer<? super V> action) {
				this.delegate.forEach(action);
			}
		}

		private class KeySet
				extends AbstractSet<String> {
			private final Set<String> delegate;

			KeySet(Set<String> delegate) {
				this.delegate = delegate;
			}

			public int size() {
				return this.delegate.size();
			}

			public boolean contains(Object o) {
				return this.delegate.contains(o);
			}

			public Iterator<String> iterator() {
				return CaseInsensitiveMap.this.new KeySetIterator();
			}

			public boolean remove(Object o) {
				return CaseInsensitiveMap.this.remove(o) != null;
			}

			public void clear() {
				CaseInsensitiveMap.this.clear();
			}

			public Spliterator<String> spliterator() {
				return this.delegate.spliterator();
			}

			public void forEach(Consumer<? super String> action) {
				this.delegate.forEach(action);
			}
		}
	}

	static <K, V> HashMap<K, V> newHashMap(int expectedSize) {
		return new HashMap<>(computeMapInitialCapacity(expectedSize),
		                     0.75F);
	}

	static int computeMapInitialCapacity(int expectedSize) {
		return (int) Math.ceil((double) expectedSize / 0.75);
	}
}
