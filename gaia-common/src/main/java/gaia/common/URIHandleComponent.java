package gaia.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import lombok.Getter;
import lombok.Setter;

import gaia.common.CollectionSpec.ExtendList;
import gaia.common.ConcurrentAssist.ConditionedLock;
import gaia.common.GWCommon.EntryDuplicatedException;
import gaia.common.GWCommon.EntryNotFoundException;
import gaia.common.GWCommon.PriorityType;
import gaia.common.GWCommonModels.FieldName;
import gaia.common.URIHandleSpec.URIContentType;
import gaia.common.URIHandleSpec.URIControlHandler;
import gaia.common.URIHandleSpec.URIController;
import gaia.common.URIHandleSpec.URIHandleContext;
import gaia.common.URIHandleSpec.URIHandler;
import gaia.common.URIHandleSpec.URIResource;
import gaia.common.URIHandleSpec.URIResourceHandler;

public interface URIHandleComponent {
	Logger log = LoggerFactory.getLogger(URIHandleComponent.class);

	abstract class URIHandleContextAdapter
			implements URIHandleContext {
		final ConditionedLock lock = new ConditionedLock();
		protected final ExtendList<URIHandler> collection = new ExtendList<>(PriorityType.ASC);
		protected final AtomicReference<Map<String, List<URIHandler>>> holder = new AtomicReference<>(Collections.emptyMap());
		protected final AtomicBoolean dirty = new AtomicBoolean();

		protected final void loadDefault() {
			ReflectAssist.streamMemberTypes(URIHandlers.class)
			             .filter(ReflectAssist.predicateOf(URIHandler.class))
			             .map(type -> ReflectAssist.createInstance(type,
			                                                       URIHandler.class))
			             .peek(handler -> handler.setHandleContext(this))
			             .forEach(this.collection::add);
			this.dirty.set(true);
		}

		Map<String, List<URIHandler>> touch0() {
			if (this.dirty.compareAndSet(true,
			                             false)) {
				this.lock.execute(() -> {
					final Map<String, List<URIHandler>> map = new HashMap<>();
					this.collection.forEach(element -> element.getScheme()
					                                          .forEach(scheme -> map.computeIfAbsent(scheme,
					                                                                                 key -> new ExtendList<>(PriorityType.ASC))
					                                                                .add(element)));
					this.holder.set(map);
				});
			}
			return this.holder.get();
		}

		@Override
		public void registerHandler(URIHandler handler) {
			this.lock.execute(() -> {
				if (this.collection.add(handler)) {
					this.dirty.set(true);
				} else {
					throw new EntryDuplicatedException(handler.toString(),
					                                   "uri handle context");
				}
			});
		}

		@Override
		public void deregisterHandler(URIHandler handler) {
			this.lock.execute(() -> {
				if (this.collection.remove(handler)) {
					this.dirty.set(true);
				} else {
					throw new EntryNotFoundException(handler.toString(),
					                                 "uri handle context");
				}
			});
		}

		@Override
		public Stream<URIHandler> lookup(URI uri) {
			final String scheme = URIAssist.schemeOf(uri);
			return Optional.ofNullable(this.touch0()
			                               .get(scheme))
			               .map(List::stream)
			               .orElse(Stream.empty());
		}

	}

	class URIHandleContextDefault
			extends URIHandleContextAdapter {
		public URIHandleContextDefault() {
			super();
			this.loadDefault();
		}
	}


	abstract class URIControllerAdapter
			implements URIController {
		@Getter
		@Setter
		URIControlHandler handler;
	}

	abstract class URIResourceAdapter
			implements URIResource {
		@Getter
		@Setter
		URIResourceHandler handler;
		@Getter
		final URI uri;

		protected URIResourceAdapter(URIResourceHandler handler,
		                             URI uri) {
			this.handler = handler;
			this.uri = uri;
		}

		protected URIResourceAdapter(URI uri) {
			this.uri = uri;
		}

		@Override
		public byte[] raw() throws IOException {
			if (null == this.uri) {
				throw new IllegalStateException("resource not exist!");
			}
			try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				this.input(in -> StreamAssist.copy(in,
				                                   out,
				                                   -1));
				return out.toByteArray();
			}
		}

		@Override
		public void input(Consumer<InputStream> consumer) throws IOException {
			if (this.getType() != URIContentType.stream) {
				throw new IllegalStateException("stream is unable to support!");
			}

			try (InputStream in = this.openInputStream()) {
				consumer.accept(in);
			}
		}

		@Override
		public InputStream openInputStream() throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void output(Consumer<OutputStream> consumer) throws IOException {
			if (this.getType() != URIContentType.stream) {
				throw new IllegalStateException("stream is unable to support!");
			}

			try (OutputStream out = this.openOutputStream()) {
				consumer.accept(out);
			}
		}

		@Override
		public OutputStream openOutputStream() throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public String toString() {
			return StringAssist.format("%s (%s)",
			                           this.uri.toString(),
			                           this.getClass()
			                               .getSimpleName());
		}
	}

	final class URIRemoteDefine {
		@Getter
		final String host;
		@Getter
		final int port;
		@Getter
		final String schema;
		final AtomicLong access = new AtomicLong();
		final AtomicReference<InetSocketAddress> address = new AtomicReference<>();
		final Predicate<URI> predicate;
		final String description;
		@Getter
		@Setter
		@FieldName("최대 연결")
		int maxSession = -1;
		@Getter
		@Setter
		@FieldName("최대 연결 대기시간")
		long connectTimeout = -1L;

		public URIRemoteDefine(final URI initial) {
			this.schema = initial.getScheme();
			this.host = initial.getHost();
			this.port = NetAssist.getPort(initial);
			this.access.set(System.currentTimeMillis());

			Predicate<URI> candidate = url -> StringUtils.equalsIgnoreCase(this.schema,
			                                                               url.getScheme());

			candidate = candidate.and(url -> StringUtils.equalsIgnoreCase(this.host,
			                                                              url.getHost()));

			this.predicate = candidate.and(uri -> this.port == NetAssist.getPort(uri));

			this.description = ResourceAssist.stringComposer()
			                                 .add("HttpRemoteDefine{")
			                                 .add("schema=")
			                                 .add(this.schema)
			                                 .add(", host=")
			                                 .add(this.host)
			                                 .add(", port=")
			                                 .add(this.port)
			                                 .build();

		}

		public InetSocketAddress getAddress() {
			if (System.currentTimeMillis() - this.access.get() > TimeUnit.DAYS.toMillis(1)) {
				// more than 1 day later
				this.address.set(null);
			}
			return this.address.updateAndGet(before -> null != before
			                                           ? before
			                                           : new InetSocketAddress(NetAssist.getInetAddress(this.host),
			                                                                   this.port));
		}

		public boolean test(URI uri) {
			this.access.set(System.currentTimeMillis());
			return this.predicate.test(uri);
		}

		public boolean compare(final URIRemoteDefine other) {
			if (other == this) {
				return true;
			}
			return StringUtils.equals(this.description,
			                          other.description);
		}


		@Override
		public String toString() {
			return this.description;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof URIRemoteDefine && this.compare((URIRemoteDefine) o);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.host,
			                    this.port,
			                    this.schema);
		}
	}

	class URIRemoteDefineRegistry {
		final ConditionedLock lock = new ConditionedLock();

		final Map<String, List<URIRemoteDefine>> cache = new HashMap<>();

		public final URIRemoteDefine take(final URI uri,
		                                  final Consumer<URIRemoteDefine> initializer) {
			final String host = uri.getHost();
			return this.lock.tran(() -> {
				final List<URIRemoteDefine> list = this.cache.computeIfAbsent(host,
				                                                              key -> new ArrayList<>());
				return list.stream()
				           .filter(candidate -> candidate.test(uri))
				           .findFirst()
				           .orElseGet(() -> {
					           final URIRemoteDefine target = new URIRemoteDefine(uri);

					           initializer.accept(target);
					           list.add(target);

					           return target;
				           });
			});
		}
	}
}
