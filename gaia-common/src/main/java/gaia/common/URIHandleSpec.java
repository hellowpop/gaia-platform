package gaia.common;


import static gaia.common.FunctionAssist.supply;
import static gaia.common.URIAssist.isWildcardPattern;
import static gaia.common.URIHandlers.accessorJarURIController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import gaia.common.CharsetSpec.CharSets;
import gaia.common.EventAssist.TypedEventListener;
import gaia.common.GWCommon.AttributeAccessorContainer;
import gaia.common.GWCommon.AttributeContext;
import gaia.common.GWCommon.AttributeContextAccessor;
import gaia.common.GWCommon.Disposable;
import gaia.common.GWCommon.EntryNotFoundException;
import gaia.common.GWCommon.Priority;
import gaia.common.PromiseAssist.PromiseBase;
import gaia.common.PromiseAssist.PromiseEvent;
import gaia.common.URIHandleComponent.URIControllerAdapter;
import gaia.common.URIHandleComponent.URIHandleContextDefault;
import gaia.common.URIHandleComponent.URIRemoteDefineRegistry;

public interface URIHandleSpec {
	Logger log = LoggerFactory.getLogger(URIHandleSpec.class);

	@Getter
	enum URIHandleEvent
			implements PromiseEvent<URIHandlePromise> {
		handler_found,
		handler_not_found(true),
		handler_handle_error(true),
		bind_prepared,
		bind,
		bind_reused,
		bind_error(true),
		bind_timeout(true),
		request,
		request_error(true),
		request_cancelled(true),
		requested,
		received(true,
		         true),
		receive_timeout(true),
		receive_not_found(true),
		receive_error(true),
		abnormal_terminated(true);

		final boolean complete;
		final boolean success;

		URIHandleEvent() {
			this(false,
			     false);
		}

		URIHandleEvent(boolean complete) {
			this(complete,
			     false);
		}

		URIHandleEvent(boolean complete,
		               boolean success) {
			this.complete = complete;
			this.success = success;
		}

		public void handle(URIHandlePromise promise) {
			promise.event(this,
			              promise);
		}
	}

	interface URIHandleEventListener
			extends TypedEventListener<URIHandleEvent> {
		@Override
		default Class<URIHandleEvent> getEventType() {
			return URIHandleEvent.class;
		}
	}

	URIHandleEventListener NONE = (event, context) -> {

	};

	URIHandleEventListener TRACE = new URIHandleEventListener() {
		@Override
		public void doHandleEvent(URIHandleEvent event,
		                          AttributeContext context) {

			switch (event) {
				case received: {
					log.info(StringAssist.format("uri [%s] receive [%s]",
					                             accessorURIHandle.uri.take(context),
					                             accessorURIHandle.promise.take(context)
					                                                      .getValue()));
					break;
				}

				default: {
					log.info(StringAssist.format("uri [%s] event [%s]",
					                             accessorURIHandle.uri.take(context),
					                             event.name()));
					break;
				}
			}
		}
	};

	class URIHandleAccessor
			extends AttributeAccessorContainer {
		public AttributeContextAccessor<URIHandlePromise> promise;
		public AttributeContextAccessor<Throwable> cause;
		public AttributeContextAccessor<URIPromiseHandler> handler;
		public AttributeContextAccessor<URIPromiseController> controller;
		public AttributeContextAccessor<URI> uri;
		public AttributeContextAccessor<Map<String, String>> options;
		public AttributeContextAccessor<URIHandler> uriHandler;
		public AttributeContextAccessor<Object> payload;
		public AttributeContextAccessor<Object> bindObject;

		private URIHandleAccessor() {
		}
	}

	URIHandleAccessor accessorURIHandle = new URIHandleAccessor();

	enum URIContentType {
		stream,
		pojo,
		mime
	}

	class URIHandlePromise
			extends PromiseBase<URIHandlePromise, URIHandleEvent, Object> {
		public static URIHandlePromise of(final URI uri) {
			return new URIHandlePromise(uri);
		}

		public static URIHandlePromise of(final URI uri,
		                                  final Object payload) {
			return new URIHandlePromise(uri).payload(payload);
		}

		private URIHandlePromise(final URI uri) {
			super();
			accessorURIHandle.promise.set(this,
			                              this);
			accessorURIHandle.uri.set(this,
			                          uri);

			final Map<String, String> options = URIAssist.params(uri);

			if (null != options) {
				accessorURIHandle.options.set(this,
				                              options);
			}
		}

		public URIHandlePromise controller(final URIPromiseController controller) {
			accessorURIHandle.controller.set(this,
			                                 controller);
			return this;
		}

		public URIHandlePromise handler(final URIPromiseHandler handler) {
			accessorURIHandle.handler.set(this,
			                              handler);
			return this;
		}

		public URIHandlePromise payload(final Object payload) {
			if (null != payload) {
				accessorURIHandle.payload.set(this,
				                              payload);
			}
			return this;
		}

		public URIHandlePromise handle() {
			accessorURIHandle.handler.take(this)
			                         .handle(this);
			return this;
		}

		@Override
		protected void doDispose() {
			accessorURIHandle.controller.reference(this)
			                            .ifPresent(URIPromiseController::dispose);

			super.doDispose();
		}

		public Optional<Object> response() {
			return Optional.ofNullable(this.get());
		}

		public void attachListener(URIHandleEventListener listener) {
			this.registerListener(listener);
		}

		@Override
		protected URIHandlePromise self() {
			return this;
		}

		public final URI uri() {
			return accessorURIHandle.uri.get(this);
		}

		@Override
		public String toString() {
			return StringAssist.format("uri : %s",
			                           this.uri());
		}
	}

	interface URIHandler
			extends Priority {
		URIHandleContext getHandleContext();

		void setHandleContext(URIHandleContext context);

		Set<String> getScheme();
	}

	@Getter
	abstract class URIHandlerAdapter
			implements URIHandler {
		@Setter
		URIRemoteDefineRegistry remoteRegistry;
		@Getter
		final Set<String> scheme = new HashSet<>();
		@Getter
		@Setter(AccessLevel.PROTECTED)
		int priority;
		@Getter
		@Setter
		URIHandleContext handleContext;
		@Getter
		@Setter(AccessLevel.PROTECTED)
		boolean indexed;


		protected final void applyScheme(String master,
		                                 String... alias) {
			this.scheme.add(master);
			if (null != alias) {
				Collections.addAll(this.scheme,
				                   alias);
			}
		}
	}

	interface URIControlHandler
			extends URIHandler {
		@SuppressWarnings("EmptyMethod")
		default void dispose(URIController controller) {
		}
	}

	abstract class URIControlHandlerAdapter
			extends URIHandlerAdapter
			implements URIControlHandler {
	}

	interface URIPublishHandler
			extends URIControlHandler {
		<PT> URIPublishController<PT> publisher(URI uri,
		                                        Class<PT> payloadType);
	}

	interface URISubscribeHandler
			extends URIControlHandler {
		<PT> URISubscribeController<PT> subscriber(URI uri,
		                                           Class<PT> payloadType);
	}

	interface URIRegistryHandler
			extends URIControlHandler {
		<PT> URIRegistryController<PT> register(URI uri,
		                                        Class<PT> payloadType);
	}

	interface URIPromiseHandler
			extends URIControlHandler {

		default URIPromiseController request(URI uri) {
			return this.request(uri,
			                    promise -> {
			                    });
		}

		URIPromiseController request(URI uri,
		                             Consumer<URIHandlePromise> customizer);

		default URIHandlePromise promise(URI uri,
		                                 Object payload) {
			return this.promise(uri,
			                    payload,
			                    promise -> {
			                    });

		}

		default URIHandlePromise promise(URI uri,
		                                 Consumer<URIHandlePromise> customizer) {
			return this.promise(uri,
			                    null,
			                    customizer);
		}

		default URIHandlePromise promise(URI uri,
		                                 Object payload,
		                                 Consumer<URIHandlePromise> customizer) {
			final URIHandlePromise promise = URIHandlePromise.of(uri)
			                                                 .payload(payload)
			                                                 .handler(this);

			customizer.accept(promise);

			return promise;
		}

		void handle(URIHandlePromise promise);
	}

	abstract class URIPromiseHandlerAdapter
			extends URIControlHandlerAdapter
			implements URIPromiseHandler {

	}

	interface URIResourceHandler
			extends URIHandler {
		URIResource resource(URI uri);
	}

	abstract class URIResourceHandlerAdapter
			extends URIHandlerAdapter
			implements URIResourceHandler {

	}

	interface URIScanHandler
			extends URIResourceHandler {
		default void scan(URI uri,
		                  Consumer<URIResource> consumer) {
			if (isWildcardPattern(uri)) {
				final URI rootURI = URIAssist.determineRootURI(uri);
				final String subPattern = uri.toString()
				                             .substring(rootURI.toString()
				                                               .length());
				this.roots(rootURI,
				           subURI -> this.getHandleContext()
				                         .scanner(subURI)
				                         .doFindPathMatchingFileResources(subURI,
				                                                          subPattern,
				                                                          consumer));

			} else {
				this.doFindPathResources(uri,
				                         consumer);
			}
		}

		void roots(final URI uri,
		           final Consumer<URI> consumer);

		void doFindPathMatchingFileResources(URI rootURI,
		                                     String subPattern,
		                                     Consumer<URIResource> consumer);

		default void doFindPathResources(URI uri,
		                                 Consumer<URIResource> consumer) {
			consumer.accept(supply(() -> this.resource(uri)));
		}
	}

	abstract class URIScanHandlerAdapter
			extends URIResourceHandlerAdapter
			implements URIScanHandler {

	}


	interface URITransformHandler
			extends URIHandler {
		URITransformController transformer(URI uri);

		<T> T transform(URI uri,
		                Object payload,
		                Class<T> responseType);
	}

	interface URIController
			extends Disposable {
		URIControlHandler getHandler();

		@Override
		default void dispose() {
			this.getHandler()
			    .dispose(this);
		}
	}

	interface URIRegistryController<PT>
			extends PayloadController<PT> {
		PT put(String identity,
		       PT payload);

		PT remove(String identity);

		PT get(String identity);

		default PT put(PT payload,
		               Supplier<String> identitySupplier) {
			return this.put(identitySupplier.get(),
			                payload);
		}

		default PT put(PT payload,
		               Function<PT, String> identityFunction) {
			return this.put(identityFunction.apply(payload),
			                payload);
		}
	}

	interface URIPublishController<PT>
			extends PayloadController<PT> {
		boolean publish(PT payload);
	}

	interface URISubscribeController<PT>
			extends PayloadController<PT> {
		void register(Consumer<PT> payloadListener);

		void listenStart();

		void listenStop();
	}

	interface URIResourceController
			extends URIController {
		URIResource resource(URI uri) throws IOException;
	}

	interface URIPromiseController
			extends URIController {
		URIHandlePromise promise(Object payload);

		default void dispose(URIHandlePromise promise) {

		}
	}

	abstract class URIPromiseControllerAdapter
			extends URIControllerAdapter
			implements URIPromiseController {

	}

	interface URITransformController
			extends URIController {
		<T> T transform(Object source,
		                Class<T> returnType);
	}

	interface PayloadController<PT>
			extends URIController {
		Class<PT> getPayloadType();
	}

	@Getter
	abstract class PayloadControllerAdapter<PT>
			extends URIControllerAdapter
			implements PayloadController<PT> {
		final Class<PT> payloadType;

		@SuppressWarnings("unchecked")
		protected PayloadControllerAdapter() {
			super();
			this.payloadType = (Class<PT>) ReflectAssist.getGenericParameter(this.getClass(),
			                                                                 PayloadControllerAdapter.class,
			                                                                 "PT");
		}

		protected PayloadControllerAdapter(Class<PT> payloadType) {
			super();
			this.payloadType = payloadType;
		}
	}

	interface URIResource
			extends Disposable {
		URIResourceHandler getHandler();

		URIContentType getType();

		URI getUri();

		void input(Consumer<InputStream> consumer) throws IOException;

		InputStream openInputStream() throws IOException;

		void output(Consumer<OutputStream> consumer) throws IOException;

		OutputStream openOutputStream() throws IOException;

		byte[] raw() throws IOException;

		default boolean exists() {
			return null != this.getUri();
		}

		@Override
		default void dispose() {
			// do nothing
		}

		URIResource NOT_FOUND = new URIResource() {
			@Override
			public URIResourceHandler getHandler() {
				return null;
			}

			@Override
			public URIContentType getType() {
				return null;
			}

			@Override
			public URI getUri() {
				return null;
			}

			@Override
			public void input(Consumer<InputStream> consumer) throws IOException {

			}

			@Override
			public InputStream openInputStream() throws IOException {
				return null;
			}

			@Override
			public void output(Consumer<OutputStream> consumer) throws IOException {

			}

			@Override
			public OutputStream openOutputStream() throws IOException {
				return null;
			}

			@Override
			public byte[] raw() throws IOException {
				return new byte[0];
			}
		};
	}

	interface URIHandleContext {
		void registerHandler(URIHandler handler);

		void deregisterHandler(URIHandler handler);

		Stream<URIHandler> lookup(URI uri);

		default <T extends URIHandler> Stream<T> lookup(URI uri,
		                                                Class<T> handlerType) {
			return this.lookup(uri)
			           .filter(handlerType::isInstance)
			           .map(handlerType::cast);
		}

		default <PT> URIPublishController<PT> publisher(URI uri,
		                                                Class<PT> payloadType) {
			return this.lookup(uri,
			                   URIPublishHandler.class)
			           .map(handler -> handler.publisher(uri,
			                                             payloadType))
			           .filter(Objects::nonNull)
			           .findFirst()
			           .orElseThrow(() -> new EntryNotFoundException("",
			                                                         ""));
		}

		default <PT> URISubscribeController<PT> subscriber(URI uri,
		                                                   Class<PT> payloadType) {
			return this.lookup(uri,
			                   URISubscribeHandler.class)
			           .map(handler -> handler.subscriber(uri,
			                                              payloadType))
			           .filter(Objects::nonNull)
			           .findFirst()
			           .orElseThrow(() -> new EntryNotFoundException("",
			                                                         ""));
		}

		default <PT> URIRegistryController<PT> registry(URI uri,
		                                                Class<PT> payloadType) {
			return this.lookup(uri,
			                   URIRegistryHandler.class)
			           .map(handler -> handler.register(uri,
			                                            payloadType))
			           .filter(Objects::nonNull)
			           .findFirst()
			           .orElseThrow(() -> new EntryNotFoundException("",
			                                                         ""));
		}

		default URITransformController transformer(URI uri) {
			return this.lookup(uri,
			                   URITransformHandler.class)
			           .map(handler -> handler.transformer(uri))
			           .filter(Objects::nonNull)
			           .findFirst()
			           .orElseThrow(() -> new EntryNotFoundException("",
			                                                         ""));
		}

		default <T> Optional<T> transform(URI uri,
		                                  Object payload,
		                                  Class<T> responseType) {
			return this.lookup(uri,
			                   URITransformHandler.class)
			           .map(handler -> handler.transform(uri,
			                                             payload,
			                                             responseType))
			           .filter(Objects::nonNull)
			           .findFirst();
		}

		default URIScanHandler scanner(URI uri) {
			return this.lookup(uri,
			                   URIScanHandler.class)
			           .findFirst()
			           .orElseThrow(() -> new EntryNotFoundException("",
			                                                         ""));
		}

		default URIResource resource(URI uri) {
			return this.lookup(uri)
			           .filter(candidate -> candidate instanceof URIResourceHandler)
			           .map(candidate -> (URIResourceHandler) candidate)
			           .map(candidate -> candidate.resource(uri))
			           .filter(Objects::nonNull)
			           .findFirst()
			           .orElse(URIResource.NOT_FOUND);
		}

		default URIResource resource(String path) {
			if (StringUtils.isEmpty(path)) {
				throw new IllegalArgumentException("path must have content!");
			}
			return supply(() -> this.resource(URIAssist.uri(path)));
		}

		default void scan(final String path,
		                  final Consumer<URIResource> consumer) {
			this.scan(URIAssist.uri(path),
			          consumer);
		}

		default void scan(final URI uri,
		                  final Consumer<URIResource> consumer) {
			try {
				accessorJarURIController.jarCache.set(new HashMap<>());
				this.lookup(uri)
				    .filter(candidate -> candidate instanceof URIScanHandler)
				    .map(candidate -> (URIScanHandler) candidate)
				    .forEach(handler -> handler.scan(uri,
				                                     consumer));
			}
			finally {
				accessorJarURIController.jarCache.unset()
				                                 .forEach((url, jarFile) -> ObjectFinalizer.close(jarFile));

			}
		}

		default Optional<URIHandlePromise> promise(URI uri) {
			return this.promise(uri,
			                    null,
			                    promise -> {
			                    });
		}

		default Optional<URIHandlePromise> promise(URI uri,
		                                           Object payload,
		                                           URIHandleEventListener listener) {
			return this.promise(uri,
			                    payload,
			                    promise -> promise.registerListener(listener));
		}

		default Optional<URIHandlePromise> promise(URI uri,
		                                           Consumer<URIHandlePromise> customizer) {
			return this.promise(uri,
			                    null,
			                    customizer);
		}

		default Optional<URIHandlePromise> promise(URI uri,
		                                           Object payload,
		                                           Consumer<URIHandlePromise> customizer) {
			return this.lookup(uri)
			           .filter(handler -> handler instanceof URIPromiseHandler)
			           .map(handler -> (URIPromiseHandler) handler)
			           .map(Handler -> Handler.promise(uri,
			                                           payload,
			                                           customizer))
			           .filter(Objects::nonNull)
			           .findFirst();
		}

		// ---------------------------------------------------------------------
		// Section
		// ---------------------------------------------------------------------

		default Optional<URIPromiseController> request(URI uri) {
			return this.request(uri,
			                    null,
			                    promise -> {
			                    });
		}

		default Optional<URIPromiseController> request(URI uri,
		                                               Object payload,
		                                               URIHandleEventListener listener) {
			return this.request(uri,
			                    payload,
			                    promise -> promise.registerListener(listener));
		}

		default Optional<URIPromiseController> request(URI uri,
		                                               Consumer<URIHandlePromise> customizer) {
			return this.request(uri,
			                    null,
			                    customizer);
		}

		default Optional<URIPromiseController> request(URI uri,
		                                               Object payload,
		                                               Consumer<URIHandlePromise> customizer) {
			return this.lookup(uri)
			           .filter(handler -> handler instanceof URIPromiseHandler)
			           .map(handler -> (URIPromiseHandler) handler)
			           .map(Handler -> Handler.request(uri,
			                                           customizer))
			           .filter(Objects::nonNull)
			           .findFirst();
		}

		default byte[] content(final String uri) {
			return this.content(URIAssist.uri(uri));
		}

		default byte[] content(final URI uri) {
			return supply(() -> this.resource(uri)
			                        .raw());
		}

		default String getContentOf(final URI uri) {
			return this.getContentOf(uri,
			                         CharSets.UTF_8);
		}

		default String getContentOf(final String uri,
		                            final CharSets cs) {
			return this.getContentOf(URIAssist.uri(uri),
			                         cs);
		}

		default String getContentOf(final URI uri,
		                            final CharSets cs) {
			return cs.toString(this.content(uri));
		}
	}

	URIHandleContext URI_HANDLE_CONTEXT = supply(() -> {
		final String contextBuilderType = GWEnvironment.getProperty("url.context.class.name");
		if (StringUtils.isNotEmpty(contextBuilderType)) {
			return supply(() -> {
				final Class<?> contextBuilderClass = ClassScanComponent.forName(contextBuilderType);

				if (URIHandleContext.class.isAssignableFrom(contextBuilderClass)) {
					return (URIHandleContext) contextBuilderClass.getConstructor()
					                                             .newInstance();
				}

				throw new Error(StringAssist.format("[%s] is not a RuntimeContextBuilder",
				                                    contextBuilderType));
			});
		}
		return new URIHandleContextDefault();
	});
}
