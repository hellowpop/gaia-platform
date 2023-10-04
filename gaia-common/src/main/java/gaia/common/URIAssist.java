package gaia.common;


import static gaia.common.FunctionAssist.supply;
import static gaia.common.ResourceAssist.stringComposer;

import java.io.File;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

import gaia.ConstValues;
import gaia.common.CharsetSpec.CharSets;
import gaia.common.StringMatcherSpec.AntPathMatcher;


public interface URIAssist {
	static URI uri(String location) {
		final String filtered = location.replace('\\',
		                                         '/');
		final URI uri = supply(() -> new URI(filtered));

		if (null != uri.getScheme() && uri.getScheme()
		                                  .length() > 1) {
			return uri;
		}

		// perhaps window file system
		return supply(() -> new File(filtered).toURI());
	}

	static boolean isJarURL(URI uri) {
		switch (uri.getScheme()) {
			case "jar":
			case "war":
			case "zip": {
				return true;
			}

			default: {
				break;
			}
		}

		return false;
	}

	static boolean isWildcardPattern(URI uri) {
		return pathOf(uri).indexOf('*') >= 0;
	}

	static String schemeOf(final URI uri) {
		return Optional.ofNullable(uri.getScheme())
		               .orElse("file")
		               .toLowerCase();
	}

	static String pathOf(final URI uri) {
		return Optional.ofNullable(uri.getPath())
		               .orElseGet(uri::getSchemeSpecificPart);
	}

	static String hostOf(final URI uri) {
		return stringComposer()
		                     .add(builder -> {
			                     builder.append(uri.getHost());
			                     if (uri.getPort() > 0) {
				                     builder.append(':');
				                     builder.append(uri.getPort());
			                     }
		                     })
		                     .build();
	}

	static URI determineRootURI(final URI uri) {
		final AntPathMatcher pathMatcher = StringMatcherSpec.matcher;
		final String location = uri.toString();
		final int prefixEnd = location.indexOf(':') + 1;
		final AtomicInteger rootDirEnd = new AtomicInteger(location.length());
		while (rootDirEnd.get() > prefixEnd && pathMatcher.isPattern(location.substring(prefixEnd,
		                                                                                rootDirEnd.get()))) {
			rootDirEnd.set(location.lastIndexOf('/',
			                                    rootDirEnd.get() - 2) + 1);
		}
		if (rootDirEnd.get() == 0) {
			rootDirEnd.set(prefixEnd);
		}
		return supply(() -> new URI(location.substring(0,
		                                               rootDirEnd.get())));
	}

	static String description(URI uri) {
		return stringComposer()
		                     .add("getScheme")
		                     .add(':')
		                     .line(uri.getScheme())
		                     .add("getHost")
		                     .add(':')
		                     .line(uri.getHost())
		                     .add("getPort")
		                     .add(':')
		                     .line(String.valueOf(uri.getPort()))
		                     .add("getPath")
		                     .add(':')
		                     .line(uri.getPath())
		                     .add("getQuery")
		                     .add(':')
		                     .line(uri.getQuery())
		                     .add("getFragment")
		                     .add(':')
		                     .line(uri.getFragment())
		                     .add("getSchemeSpecificPart")
		                     .add(':')
		                     .line(uri.getSchemeSpecificPart())
		                     .build();
	}

	static String encode(String value) {
		return supply(() -> URLEncoder.encode(value,
		                                      StandardCharsets.UTF_8.name()));
	}

	static String decode(String value) {
		return decode(value,
		              StandardCharsets.UTF_8.name());
	}

	static String decode(String value,
	                     String charset) {
		return supply(() -> URLDecoder.decode(value,
		                                      charset));
	}

	static URI compose(String protocol,
	                   String host,
	                   String path,
	                   Map<String, Object> param) {
		return compose(protocol,
		               host,
		               -1,
		               path,
		               param);
	}

	static URI compose(String protocol,
	                   String host,
	                   int port,
	                   String path,
	                   Map<String, Object> param) {
		return uri(stringComposer()
		                         .add(protocol)
		                         .add("://")
		                         .add(builder -> {
			                         builder.append(host);
			                         if (port > 0) {
				                         builder.append(':');
				                         builder.append(port);
			                         }
		                         })
		                         .add('/')
		                         .add(path)
		                         .add(builder -> {
			                         if (null != param) {
				                         builder.append('?');
				                         param.forEach((key, value) -> {
					                         builder.append(encode(key));
					                         builder.append('=');
					                         builder.append(encode(String.valueOf(value)));
					                         builder.append('&');
				                         });
				                         // trim last '&'
				                         builder.setLength(builder.length() - 1);
			                         }
		                         })
		                         .build());
	}

	static Map<String, String> params(URI uri) {
		final String query = uri.getQuery();
		if (StringUtils.isEmpty(query)) {
			return null;
		}
		final Map<String, String> map = new LinkedHashMap<>();

		parseQueryString(query,
		                 map::put);

		return map;
	}

	Pattern query_separator = Pattern.compile("&");

	static int parseUri(String uri,
	                    BiConsumer<String, String> consumer) {
		return parseUri(uri,
		                CharSets.UTF_8,
		                consumer);
	}

	static int parseUri(String uri,
	                    CharSets charset,
	                    BiConsumer<String, String> consumer) {
		if (StringUtils.isEmpty(uri)) {
			return -1;
		}

		final int idx = uri.indexOf('?');

		if (idx > 0) {
			parseQueryString(uri.substring(idx + 1),
			                 charset,
			                 consumer);
		}

		return idx;
	}

	static void parseQueryString(String origin,
	                             BiConsumer<String, String> consumer) {
		parseQueryString(origin,
		                 CharSets.UTF_8,
		                 consumer);
	}

	static void parseQueryString(final String origin,
	                             final CharSets charset,
	                             final BiConsumer<String, String> consumer) {
		if (StringUtils.isEmpty(origin)) {
			return;
		}

		final String query = charset.urlDecode(origin);

		query_separator.splitAsStream(query)
		               .forEach(element -> {
			               final int index = element.indexOf('=');
			               final String key = index < 0
			                                  ? element
			                                  : element.substring(0,
			                                                      index);
			               final String value = index < 0
			                                    ? ConstValues.BLANK_STRING
			                                    : element.substring(index + 1);

			               consumer.accept(key,
			                               value);
		               });
	}

	static String fullPathOf(URI uri) {
		return stringComposer().add(uri.getRawPath())
		                       .self(builder -> {
			                       final String query = uri.getRawQuery();
			                       if (StringUtils.isNotEmpty(query)) {
				                       builder.add('?')
				                              .add(query);
			                       }


			                       final String fragment = uri.getRawFragment();
			                       if (StringUtils.isNotEmpty(fragment)) {
				                       builder.add('#')
				                              .add(fragment);
			                       }
		                       })
		                       .build();
	}
}
