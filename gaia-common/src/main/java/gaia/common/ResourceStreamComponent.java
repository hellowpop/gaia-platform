package gaia.common;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import lombok.Getter;
import lombok.Setter;

import gaia.common.ASMEngineeringComponent.ASMAssist;
import gaia.common.CharsetSpec.CharSets;
import gaia.common.CompressAssist.ArchiveElement;
import gaia.common.CompressAssist.ArchiveEntryIterator;
import gaia.common.CompressAssist.ArchiveHandler;
import gaia.common.CompressAssist.ArchiveHandlers;
import gaia.common.ConcurrentAssist.ConditionedLock;
import gaia.common.GWCommon.Priority;
import gaia.common.IOAssist.JarArchiveEntry;
import gaia.common.PredicateAssist.PredicateBuilder;
import gaia.common.ResourceStreamModel.ArchiveElementResourceStream;
import gaia.common.ResourceStreamModel.JavaFileResourceStream;
import gaia.common.ResourceStreamModel.ResourceStream;
import gaia.common.ResourceStreamModel.URIResourceStream;
import gaia.common.StringAssist.SplitPattern;

public interface ResourceStreamComponent {
	Logger log = LoggerFactory.getLogger(ResourceStreamComponent.class);

	ResourceStreamHelper<Object> unknown_helper = new ResourceStreamHelper<Object>() {
		@Override
		public String name(Object source) {
			return null;
		}

		@Override
		public InputStream inputStream(Object source) {
			return null;
		}

		@Override
		public OutputStream outputStream(Object source) {
			return null;
		}

		@Override
		public long length(Object source) {
			return 0;
		}
	};

	class ResourceStreamContext
			implements AutoCloseable {
		final ConditionedLock lock = new ConditionedLock();
		final List<ResourceStreamHelper<?>> helpers = new ArrayList<>();
		final Map<Class<?>, ResourceStreamHelper<?>> aliases = new HashMap<>();
		final AtomicBoolean dirtyHelpers = new AtomicBoolean(true);
		final AtomicReference<List<ResourceStreamHelper<?>>> helperHolder = new AtomicReference<>(Collections.emptyList());

		public Stream<ResourceStreamHelper<?>> helperStream() {
			return this.lock.tran(this::helperStream0);
		}

		public ResourceStreamContext() {
			super();
			ReflectAssist.streamMemberTypes(ResourceStreamComponent.class)
			             .filter(ReflectAssist.predicateOf(ResourceStreamHelper.class))
			             .map(type -> ReflectAssist.createInstance(type,
			                                                       ResourceStreamHelper.class))
			             .forEach(this.helpers::add);
		}

		public void scan(Stream<File> stream,
		                 String prefix,
		                 String suffix,
		                 Consumer<ResourceStream> consumer) {
			stream.forEach(file -> {
				if (file.isDirectory()) {
					if (log.isTraceEnabled()) {
						log.trace(StringAssist.format("scan as directory ==> [%s]",
						                              file.getPath()));
					}
					SplitPattern.common.stream(prefix)
					                   .forEach(path -> {
						                   // check target directory exist
						                   final File sub = new File(file,
						                                             path);
						                   if (sub.exists()) {
							                   navigateDirectory(file,
							                                     sub,
							                                     suffix,
							                                     consumer);
						                   }
					                   });
				} else {
					if (log.isTraceEnabled()) {
						log.trace(StringAssist.format("scan as archive ==> [%s]",
						                              file.getPath()));
					}
					FunctionAssist.execute(() -> navigateArchive(file,
					                                             prefix,
					                                             suffix,
					                                             consumer));
				}
			});
		}

		static void navigateArchive(File archive,
		                            String prefix,
		                            String suffix,
		                            Consumer<ResourceStream> consumer) throws IOException {
			final PredicateBuilder<ArchiveEntryIterator<?, ?>> archivePredicate = new PredicateBuilder<>();
			final PredicateBuilder<ArchiveElement<?, ?>> elementPredicate = new PredicateBuilder<>();

			SplitPattern.common.stream(prefix)
			                   .forEach(path -> {
				                   archivePredicate.or(iterator -> iterator.hasEntry(path));
				                   elementPredicate.or(element -> {
					                   final String name = element.name();
					                   return name.startsWith(path) && name.endsWith(suffix);
				                   });
			                   });

			ArchiveHandlers.zip.stream(archive,
			                           archivePredicate.build(),
			                           stream -> stream.filter(elementPredicate.build())
			                                           .forEach(element -> consumer.accept(new ArchiveElementResourceStream(element))));
		}

		static void navigateDirectory(final File root,
		                              final File current,
		                              final String suffix,
		                              final Consumer<ResourceStream> consumer) {
			Optional.ofNullable(current.listFiles())
			        .map(Stream::of)
			        .orElse(Stream.empty())
			        .forEach(file -> {
				        if (file.isDirectory()) {
					        navigateDirectory(root,
					                          file,
					                          suffix,
					                          consumer);

				        } else if (file.getName()
				                       .endsWith(suffix)) {
					        consumer.accept(new JavaFileResourceStream(file));
				        }
			        });
		}

		@Override
		public void close() {
			// release resources
			this.lock.execute(() -> this.helpers.forEach(helper -> FunctionAssist.safeExecute(helper::close)));
		}

		Stream<ResourceStreamHelper<?>> helperStream0() {
			if (this.dirtyHelpers.compareAndSet(true,
			                                    false)) {
				this.helperHolder.set(new ArrayList<>(this.helpers));
			}
			return this.helperHolder.get()
			                        .stream();
		}

		public ResourceStreamHelper<?> forType(final Class<?> type) {
			final ResourceStreamHelper<?> helper = this.lock.tran(() -> this.aliases.computeIfAbsent(type,
			                                                                                         key -> this.helperStream0()
			                                                                                                    .filter(candidate -> candidate.isFor(key))
			                                                                                                    .findFirst()
			                                                                                                    .orElse(unknown_helper)));
			if (unknown_helper == helper) throw new IllegalArgumentException(StringAssist.format("cannot find suitable handler for [%s]",
			                                                                                     type.getName()));

			return helper;
		}

		public void register(ResourceStreamHelper<?> source) {
			this.lock.execute(() -> this.register0(source));
		}

		private void register0(ResourceStreamHelper<?> source) {
			final Class<?> targetType = source.getType();

			if (targetType == Void.class) return;

			// register original list
			this.helpers.add(source);
			// register alias list
			this.aliases.clear();
			// mark as helper dirty
			this.dirtyHelpers.set(true);

			if (log.isTraceEnabled()) log.trace(StringAssist.format("[INIT] binary source register [%s]",
			                                                        targetType.getName()));
		}

		public ResourceStreamHelper<?> lookup(Object instance) {
			return this.lookup(instance.getClass());
		}

		public ResourceStreamHelper<?> lookup(Class<?> type) {
			return this.forType(type);
		}

		public final Optional<ResourceStream> resourceOf(String uriOrPath) {
			final URI uri = URIAssist.uri(uriOrPath);
			if (uriOrPath.indexOf(':') > 0) {
				final ResourceStream stream = new URIResourceStream(FunctionAssist.supply(() -> new URI(uriOrPath)));
				return stream.exists()
				       ? Optional.of(stream)
				       : Optional.empty();
			}
			final File file = new File(uriOrPath);

			return file.exists()
			       ? Optional.of(new JavaFileResourceStream(file))
			       : Optional.empty();
		}

		public final Stream<ResourceStream> streamOf(String uriOrPath) {
			final URI uri = FunctionAssist.supply(() -> new URI(uriOrPath));
			final List<ResourceStream> list = new ArrayList<>();
			switch (URIAssist.schemeOf(uri)) {
				case "file": {
					scanByPattern(URIAssist.pathOf(uri),
					              list::add);
					break;
				}

				case "classpath": {
					scanByPattern(ASMAssist.classpath(),
					              URIAssist.pathOf(uri),
					              list::add);
					break;
				}
			}
			return list.stream();
		}

		public final byte[] read(final Object source) throws IOException {
			return StreamAssist.read(() -> this.lookup(source)
			                                   .openInputStream(source));
		}

		public final String read(final Object source,
		                         CharSets charset) throws IOException {
			return charset.toString(this.read(source));
		}

		public final String getName(Object source) {
			return this.lookup(source)
			           .getName(source);
		}

		public final InputStream openInputStream(Object source) throws IOException {
			return this.lookup(source)
			           .openInputStream(source);
		}

		public final OutputStream openOutputStream(Object source) throws IOException {
			return this.lookup(source)
			           .openOutputStream(source);
		}

		public final long getLength(Object source) throws IOException {
			return this.lookup(source)
			           .getLength(source);
		}

		public final BinaryDescription describe(Object source) {
			return new BinaryDescription(this.lookup(source),
			                             source);
		}
	}

	@Getter
	@SuppressWarnings("unchecked")
	abstract class ResourceStreamHelper<T>
			implements Priority,
			           AutoCloseable {
		final Class<T> type;

		@Setter
		@Getter
		private int priority = 0;

		public ResourceStreamHelper() {
			super();
			this.type = (Class<T>) ReflectAssist.getGenericParameter(this.getClass(),
			                                                         ResourceStreamHelper.class,
			                                                         "T");
		}

		public boolean isFor(Class<?> targetType) {
			return this.type.isAssignableFrom(targetType);
		}

		public final String getName(Object source) {
			return this.name(this.type.cast(source));
		}

		public final InputStream openInputStream(Object source) throws IOException {
			return this.inputStream(this.type.cast(source));
		}

		public final OutputStream openOutputStream(Object source) throws IOException {
			return this.outputStream(this.type.cast(source));
		}

		public final long getLength(Object source) throws IOException {
			return this.length(this.type.cast(source));
		}

		public final Stream<?> getChild(Object source) {
			return this.child(this.type.cast(source));
		}

		public final boolean hasChild(Object source) {
			return this.childPresent(this.type.cast(source));
		}

		public final boolean destroy(Object source) {
			return this.delete(this.type.cast(source));
		}

		public abstract String name(T source);

		public abstract InputStream inputStream(T source) throws IOException;

		public abstract OutputStream outputStream(T source) throws IOException;

		public Stream<?> child(T source) {
			return Stream.empty();
		}

		@SuppressWarnings({"unused",
		                   "SameReturnValue"})
		public boolean childPresent(T source) {
			// customize
			return false;
		}

		@SuppressWarnings({"unused",
		                   "SameReturnValue"})
		public boolean delete(T source) {
			return false;
		}

		public abstract long length(T source);

		@Override
		public void close() {
			// default do nothing
		}
	}

	class ByteArrayResourceStreamHelper
			extends ResourceStreamHelper<byte[]> {
		@Override
		public String name(byte[] source) {
			return "byte-array";
		}

		@Override
		public InputStream inputStream(byte[] source) {
			return new ByteArrayInputStream(source);
		}

		@Override
		public OutputStream outputStream(byte[] source) {
			return null;
		}

		@Override
		public long length(byte[] source) {
			return null == source
			       ? 0
			       : source.length;
		}
	}

	class URLResourceStreamHelper
			extends ResourceStreamHelper<URL> {
		@Override
		public String name(URL source) {
			return source.getFile();
		}

		@Override
		public InputStream inputStream(URL source) throws IOException {
			return source.openStream();
		}

		@Override
		public OutputStream outputStream(URL source) {
			return null;
		}

		@Override
		public long length(URL source) {
			return StreamAssist.count(() -> this.inputStream(source));
		}
	}

	class FileResourceStreamHelper
			extends ResourceStreamHelper<File> {
		@Override
		public String name(File source) {
			return source.getName();
		}

		@Override
		public InputStream inputStream(File source) throws IOException {
			return Files.newInputStream(source.toPath());
		}

		@Override
		public OutputStream outputStream(File source) throws IOException {
			return Files.newOutputStream(source.toPath());
		}

		@Override
		public long length(File source) {
			return source.length();
		}
	}

	class JarArchiveEntryResourceStreamHelper
			extends ResourceStreamHelper<JarArchiveEntry> {
		@Override
		public String name(JarArchiveEntry source) {
			return source.getJarEntry()
			             .getName();
		}

		@Override
		public InputStream inputStream(JarArchiveEntry source) throws IOException {
			return source.open();
		}

		@Override
		public OutputStream outputStream(JarArchiveEntry source) {
			return null;
		}

		@Override
		public long length(JarArchiveEntry source) {
			return source.getJarEntry()
			             .getSize();
		}
	}

	class ResourceResourceStreamHelper
			extends ResourceStreamHelper<ResourceStream> {
		@Override
		public String name(ResourceStream source) {
			return source.getName();
		}

		@Override
		public InputStream inputStream(ResourceStream source) throws IOException {
			if (!source.isReadable()) {
				throw new IOException(StringAssist.format("[%s] cannot read target source",
				                                          source));
			}
			return source.openInputStream();
		}

		@Override
		public OutputStream outputStream(ResourceStream source) throws IOException {
			if (!source.isWritable()) {
				throw new IOException(StringAssist.format("[%s] cannot read target source",
				                                          source));
			}
			return source.openOutputStream();
		}

		@Override
		public long length(ResourceStream source) {
			return source.contentLength();
		}
	}

	@Getter
	class BinaryDescription {
		final ResourceStreamHelper<?> helper;

		@Getter
		final Object source;

		final AtomicReference<String> string;

		public BinaryDescription(ResourceStreamHelper<?> helper,
		                         Object source) {
			this.helper = helper;
			this.source = source;
			this.string = new AtomicReference<>();
		}

		public String getName() {
			return this.helper.getName(this.source);
		}

		public InputStream inputStream() throws IOException {
			return this.helper.openInputStream(this.source);
		}

		public OutputStream outputStream() throws IOException {
			return this.helper.openOutputStream(this.source);
		}

		public long getLength() throws IOException {
			return this.helper.getLength(this.source);
		}

		public Stream<BinaryDescription> childOf() {
			return this.helper.getChild(this.source)
			                  .map(object -> new BinaryDescription(this.helper,
			                                                       object));
		}

		public boolean hasChild() {
			return this.helper.hasChild(this.source);
		}

		public boolean destroy() {
			return this.helper.destroy(this.source);
		}

		@Override
		public String toString() {
			return this.string.updateAndGet(before -> Objects.isNull(before)
			                                          ? ResourceAssist.stringComposer()
			                                                          .add(this.helper.getName(this.source))
			                                                          .add(" from ")
			                                                          .add(this.helper.getClass()
			                                                                          .getSimpleName())
			                                                          .build()
			                                          : before);
		}
	}

	static void scanByPattern(final String namePattern,
	                          final Consumer<ResourceStream> consumer) {
		new FileSystemNavigator(namePattern).scan(consumer);
	}

	static void scanByPattern(final Stream<File> stream,
	                          final String namePattern,
	                          final Consumer<ResourceStream> consumer) {
		new FileSystemNavigator(namePattern).scan(stream,
		                                          consumer);
	}

	class FileSystemNavigator {
		final String namePattern;
		final String prefix;
		final Pattern pattern;

		FileSystemNavigator(final String namePattern) {
			this.namePattern = namePattern;
			final int wildIndex = namePattern.indexOf('*');

			this.prefix = wildIndex > 0
			              ? namePattern.substring(0,
			                                      wildIndex)
			              : namePattern;
			this.pattern = wildIndex > 0
			               ? RegularExpressionAssist.asWildcardPattern(namePattern)
			               : null;
		}

		void scan(final Consumer<ResourceStream> consumer) {
			final File file = new File(null == this.pattern
			                           ? this.namePattern
			                           : this.prefix);
			navigateDirectory(file,
			                  file,
			                  this.pattern,
			                  consumer);
		}

		void scan(final Stream<File> stream,
		          final Consumer<ResourceStream> consumer) {
			stream.forEach(file -> {
				if (file.isDirectory()) {
					// check target directory exist
					final File sub = new File(file,
					                          this.prefix);
					if (sub.exists()) {
						navigateDirectory(file,
						                  sub,
						                  this.pattern,
						                  consumer);
					}
				} else if (ArchiveHandler.isArchive(file.getName())) {
					FunctionAssist.execute(() -> navigateArchive(file,
					                                             this.prefix,
					                                             this.pattern,
					                                             consumer));
				} else {
					consumer.accept(new JavaFileResourceStream(file));
				}
			});
		}

		static void navigateArchive(final File archive,
		                            final String prefix,
		                            final Pattern pattern,
		                            final Consumer<ResourceStream> consumer) throws IOException {
			final PredicateBuilder<ArchiveEntryIterator<?, ?>> archivePredicate = new PredicateBuilder<>();
			final PredicateBuilder<ArchiveElement<?, ?>> elementPredicate = new PredicateBuilder<>();

			SplitPattern.common.stream(prefix)
			                   .forEach(path -> {
				                   archivePredicate.or(iterator -> iterator.hasEntry(path));
				                   elementPredicate.or(element -> {
					                   final String name = element.name();
					                   return name.startsWith(path) && (null == pattern || pattern.matcher(name)
					                                                                              .find());
				                   });
			                   });

			ArchiveHandlers.zip.stream(archive,
			                           archivePredicate.build(),
			                           stream -> stream.filter(elementPredicate.build())
			                                           .forEach(element -> consumer.accept(new ArchiveElementResourceStream(element))));
		}

		static void navigateDirectory(final File root,
		                              final File current,
		                              final Pattern pattern,
		                              final Consumer<ResourceStream> consumer) {
			Optional.ofNullable(current.listFiles())
			        .map(Stream::of)
			        .orElseGet(Stream::empty)
			        .forEach(file -> {
				        if (file.isDirectory()) {
					        navigateDirectory(root,
					                          file,
					                          pattern,
					                          consumer);

				        } else {
					        final String path = file.getPath()
					                                .replace('\\',
					                                         '/');

					        if (null == pattern || pattern.matcher(path)
					                                      .find()) {
						        consumer.accept(new JavaFileResourceStream(file));
					        }
				        }
			        });
		}
	}
}
