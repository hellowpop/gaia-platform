package gaia.common;


import static gaia.common.FileAssist.normalizePath;
import static gaia.common.FunctionAssist.supply;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gaia.common.GWCommon.AttributeAccessorContainer;
import gaia.common.GWCommon.AttributeContextAccessor;
import gaia.common.GWCommon.EntryNotFoundException;
import gaia.common.GWCommon.Priority;
import gaia.common.StringMatcherSpec.AntPathMatcher;
import gaia.common.URIHandleSpec.URIResource;
import gaia.common.URIHandleSpec.URIScanHandler;
import gaia.common.URIHandleSpec.URIScanHandlerAdapter;
import gaia.common.URIResources.FileResource;
import gaia.common.URIResources.JavaPathResource;
import gaia.common.URIResources.URLResource;
import gaia.common.URIResources.ZipEntryResource;


public interface URIHandlers {
	Logger log = LoggerFactory.getLogger(URIHandlers.class);

	String FILE_SCHEME = "file";

	class FileURIHandler
			extends URIScanHandlerAdapter {
		public FileURIHandler() {
			super();
			this.applyScheme(FILE_SCHEME);
			this.setPriority(Priority.LOWEST_PRECEDENCE);
		}


		@Override
		public URIResource resource(URI uri) {
			return new FileResource(this,
			                        uri);
		}


		@Override
		public void roots(URI uri,
		                  Consumer<URI> consumer) {
			consumer.accept(uri);
		}

		@Override
		public void doFindPathMatchingFileResources(URI rootDirResource,
		                                            String subPattern,
		                                            Consumer<URIResource> consumer) {
			File rootDir = supply(() -> {
				final File file = new File(URIAssist.pathOf(rootDirResource));
				if (!file.exists()) {
					if (log.isDebugEnabled()) {
						log.debug(StringAssist.format("Skipping [%s] none exist",
						                              file));
					}
					return null;
				}
				if (log.isTraceEnabled()) {
					log.trace("directory scan [" + file.getPath() + "]");
				}
				if (!file.isDirectory()) {
					if (log.isInfoEnabled()) {
						log.info("Skipping [" + file.getAbsolutePath() + "] none directory");
					}
					return null;
				}
				if (!file.canRead()) {
					if (log.isInfoEnabled()) {
						log.info("Skipping [" + file.getAbsolutePath() + "] cannot read");
					}
					return null;
				}
				return file;
			});

			if (null == rootDir) {
				return;
			}
			this.doFindMatchingFileSystemResources(rootDir,
			                                       subPattern,
			                                       consumer);
		}

		void doFindMatchingFileSystemResources(final File rootDir,
		                                       final String subPattern,
		                                       final Consumer<URIResource> consumer) {
			if (log.isTraceEnabled()) {
				log.trace("Looking for tree [" + rootDir.getPath() + "]");
			}
			this.retrieveMatchingFiles(rootDir,
			                           subPattern,
			                           consumer);
		}

		void retrieveMatchingFiles(File rootDir,
		                           String pattern,
		                           final Consumer<URIResource> consumer) {
			if (!rootDir.exists()) {
				// Silently skip non-existing directories.
				if (log.isDebugEnabled()) {
					log.debug("Skipping [" + rootDir.getAbsolutePath() + "] none exist");
				}
				return;
			}
			if (!rootDir.isDirectory()) {
				// Complain louder if it exists but is no directory.
				if (log.isInfoEnabled()) {
					log.info("Skipping [" + rootDir.getAbsolutePath() + "] none directory");
				}
				return;
			}
			if (!rootDir.canRead()) {
				if (log.isInfoEnabled()) {
					log.info("Skipping [" + rootDir.getAbsolutePath() + "] cannot read");
				}
				return;
			}
			String fullPattern = normalizePath(rootDir);
			if (!pattern.startsWith("/")) {
				fullPattern += "/";
			}
			fullPattern = fullPattern + pattern.replace('\\',
			                                            '/');
			this.doRetrieveMatchingFiles(fullPattern,
			                             rootDir,
			                             consumer);
		}

		void doRetrieveMatchingFiles(final String fullPattern,
		                             final File dir,
		                             final Consumer<URIResource> consumer) {
			if (log.isTraceEnabled()) {
				log.trace("Searching [" + dir.getAbsolutePath() + "] by [" + fullPattern + "]");
			}
			final AntPathMatcher pathMatcher = StringMatcherSpec.matcher;
			Optional.ofNullable(dir.listFiles())
			        .ifPresent(files -> Stream.of(files)
			                                  .sorted(Comparator.comparing(File::getName))
			                                  .forEach(file -> {
				                                  String currPath = normalizePath(file);

				                                  if (file.isDirectory() && pathMatcher.matchStart(fullPattern,
				                                                                                   currPath + "/")) {
					                                  if (!file.canRead()) {
						                                  if (log.isDebugEnabled()) {
							                                  log.debug("Skipping [" + dir.getAbsolutePath() + "] cannot read");
						                                  }
					                                  } else {
						                                  if (log.isTraceEnabled()) {
							                                  log.trace("Searching doRetrieveMatchingFiles [" + file.getAbsolutePath() + "] by [" + fullPattern + "]");
						                                  }
						                                  this.doRetrieveMatchingFiles(fullPattern,
						                                                               file,
						                                                               consumer);
					                                  }
				                                  }
				                                  if (pathMatcher.match(fullPattern,
				                                                        currPath)) {
					                                  if (log.isTraceEnabled()) {
						                                  log.trace("Searching accept [" + file.getAbsolutePath() + "] by [" + fullPattern + "]");
					                                  }
					                                  consumer.accept(new FileResource(this,
					                                                                   file));
				                                  }
			                                  }));
		}
	}

	String CLASSPATH_SCHEME = "classpath";

	class ClasspathURIHandler
			extends URIScanHandlerAdapter {

		public ClasspathURIHandler() {
			super();
			this.applyScheme(CLASSPATH_SCHEME);
			this.setPriority(Priority.LOWEST_PRECEDENCE);
		}


		@Override
		public URIResource resource(URI uri) {
			return new JavaPathResource(this,
			                            uri);
		}

		@Override
		public void roots(URI uri,
		                  Consumer<URI> consumer) {
			final String location = URIAssist.pathOf(uri);
			final String path = location.startsWith("/")
			                    ? location.substring(1)
			                    : location;
			final ClassLoader loader = Thread.currentThread()
			                                 .getContextClassLoader();
			CollectionSpec.convert(supply(() -> loader.getResources(path)))
			              .forEach(url -> {
				              if (log.isTraceEnabled()) {
					              log.trace(StringAssist.format("iterate url start [%s]",
					                                            url));
				              }
				              final URI subURI = supply(url::toURI);
				              final URIScanHandler handler = this.getHandleContext()
				                                                 .lookup(subURI,
				                                                         URIScanHandler.class)
				                                                 .findFirst()
				                                                 .orElseThrow(() -> new EntryNotFoundException("",
				                                                                                               ""));
				              handler.roots(subURI,
				                            consumer);
			              });
		}

		@Override
		public void doFindPathResources(URI uri,
		                                Consumer<URIResource> consumer) {
			final String location = URIAssist.pathOf(uri);
			final String path = location.startsWith("/")
			                    ? location.substring(1)
			                    : location;
			final ClassLoader loader = Thread.currentThread()
			                                 .getContextClassLoader();
			CollectionSpec.convert(supply(() -> loader.getResources(path)))
			              .forEach(url -> {
				              if (log.isTraceEnabled()) {
					              log.trace(StringAssist.format("iterate url start [%s]",
					                                            url));
				              }
				              consumer.accept(new URLResource(this,
				                                              url));
			              });
		}

		@Override
		public void doFindPathMatchingFileResources(URI rootDirResource,
		                                            String subPattern,
		                                            Consumer<URIResource> consumer) {
		}
	}

	String WAR_URL_SEPARATOR = "*/";
	String JAR_URL_SEPARATOR = "!/";
	String FILE_URL_PREFIX = "file:";
	String JAR_SCHEME = "jar";

	class JarURIControllerAccessor
			extends AttributeAccessorContainer {
		public AttributeContextAccessor<Map<String, JarFile>> jarCache;

		private JarURIControllerAccessor() {
			super();
		}
	}

	JarURIControllerAccessor accessorJarURIController = new JarURIControllerAccessor();

	class JarURIHandler
			extends URIScanHandlerAdapter {

		public JarURIHandler() {
			super();
			this.applyScheme(JAR_SCHEME,
			                 "zip",
			                 "war");
			this.setPriority(Priority.LOWEST_PRECEDENCE);
		}


		@Override
		public URIResource resource(URI uri) {
			return new URLResource(this,
			                       supply(uri::toURL));
		}

		@Override
		public void roots(URI uri,
		                  Consumer<URI> consumer) {
			consumer.accept(uri);
		}

		@Override
		@SuppressWarnings("resource")
		public void doFindPathMatchingFileResources(final URI uri,
		                                            final String subPattern,
		                                            final Consumer<URIResource> consumer) {
			final URL rootDirURL = supply(uri::toURL);
			final AtomicReference<String> rootEntryPath = new AtomicReference<>();
			final AtomicReference<String> jarFileUrl = new AtomicReference<>();
			final Map<String, JarFile> jarCache = accessorJarURIController.jarCache.take();

			final JarFile jarFile = jarCache.computeIfAbsent(rootDirURL.toString(),
			                                                 key -> {
				                                                 try {
					                                                 URLConnection con = rootDirURL.openConnection();

					                                                 if (con instanceof JarURLConnection) {
						                                                 // Should usually be the case for traditional JAR files.
						                                                 JarURLConnection jarCon = (JarURLConnection) con;
						                                                 jarFileUrl.set(jarCon.getJarFileURL()
						                                                                      .toExternalForm());
						                                                 JarEntry jarEntry = jarCon.getJarEntry();
						                                                 rootEntryPath.set((jarEntry != null
						                                                                    ? jarEntry.getName()
						                                                                    : ""));
						                                                 return jarCon.getJarFile();
					                                                 } else {
						                                                 // No JarURLConnection -> need to resort to URL file parsing.
						                                                 // We'll assume URLs of the format "jar:path!/entry", with the protocol
						                                                 // being arbitrary as long as following the entry format.
						                                                 // We'll also handle paths with and without leading "file:" prefix.
						                                                 String urlFile = rootDirURL.getFile();
						                                                 int separatorIndex = urlFile.indexOf(WAR_URL_SEPARATOR);
						                                                 if (separatorIndex == -1) {
							                                                 separatorIndex = urlFile.indexOf(JAR_URL_SEPARATOR);
						                                                 }
						                                                 if (separatorIndex != -1) {
							                                                 jarFileUrl.set(urlFile.substring(0,
							                                                                                  separatorIndex));
							                                                 rootEntryPath.set(urlFile.substring(separatorIndex + 2));  // both separators are 2 chars
							                                                 return getJarFile(jarFileUrl.get());
						                                                 } else {
							                                                 jarFileUrl.set(urlFile);
							                                                 rootEntryPath.set("");
							                                                 return new JarFile(urlFile);
						                                                 }
					                                                 }
				                                                 }
				                                                 catch (Throwable throwable) {
					                                                 if (log.isDebugEnabled()) {
						                                                 log.debug("Skipping invalid jar classpath entry [" + key + "]");
					                                                 }
				                                                 }
				                                                 return null;
			                                                 });


			if (null == jarFile) {
				return;
			}

			String searchPath = rootEntryPath.get();
			final AntPathMatcher pathMatcher = StringMatcherSpec.matcher;

			if (log.isTraceEnabled()) {
				log.trace("Looking for matching resources in jar file [" + jarFileUrl + "]");
			}
			if (StringUtils.isNotEmpty(searchPath) && !searchPath.endsWith("/")) {
				// Root entry path must end with slash to allow for proper matching.
				// The Sun JRE does not return a slash here, but BEA JRockit does.
				searchPath = searchPath + "/";
			}

			final String searchRootPath = searchPath;

			CollectionSpec.convert(jarFile.entries())
			              .forEach(entry -> {
				              final String entryPath = entry.getName();
				              if (entryPath.startsWith(searchRootPath)) {
					              final String relativePath = entryPath.substring(searchRootPath.length());

					              if (pathMatcher.match(subPattern,
					                                    relativePath)) {
						              consumer.accept(new ZipEntryResource(this,
						                                                   uri,
						                                                   jarFile,
						                                                   entry));
					              }
				              }
			              });
		}

		static JarFile getJarFile(String jarFileUrl) throws IOException {
			if (jarFileUrl.startsWith(FILE_URL_PREFIX)) {
				return new JarFile(URIAssist.pathOf(URIAssist.uri(jarFileUrl)));
			} else {
				return new JarFile(jarFileUrl);
			}
		}
	}
}
