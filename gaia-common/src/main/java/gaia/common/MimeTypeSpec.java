package gaia.common;


import static gaia.common.FunctionAssist.supply;
import static gaia.common.ResourceAssist.stringComposer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import gaia.ConstValues;
import gaia.common.CharsetSpec.CharSets;
import gaia.common.CollectionSpec.ExtendList;
import gaia.common.ConcurrentAssist.ConditionedLock;
import gaia.common.ConcurrentAssist.SuppliedReference;
import gaia.common.GWCommon.Priority;
import gaia.common.GWCommon.PriorityType;
import gaia.common.JacksonAssist.JacksonMapper;
import gaia.common.StringAssist.NamePattern;
import gaia.common.StringAssist.SplitPattern;

/**
 * @author dragon
 * @since 2022. 02. 24.
 */
public interface MimeTypeSpec {
	Logger log = LoggerFactory.getLogger(MimeTypeSpec.class);

	String DEFAULT_RESOURCE_PATH = "META-INF/default.mime.xml";

	ConditionedLock lock = new ConditionedLock();

	Map<String, String> loaded = supply(() -> {
		Map<String, String> created = new ConcurrentHashMap<>();

		final Document doc = Dom4jAssist.readDocument(() -> MimeTypeSpec.class.getClassLoader()
		                                                                      .getResourceAsStream(DEFAULT_RESOURCE_PATH));

		Dom4jAssist.iterateElement(doc.getRootElement(),
		                           "mime-mapping",
		                           (elm) -> {
			                           String ext = elm.elementText("extension");

			                           String mimeType = elm.elementText("mime-type");

			                           created.put(elm.elementText("extension"),
			                                       elm.elementText("mime-type"));

			                           if (log.isTraceEnabled())
				                           log.trace(StringAssist.format("default mime type added [%s : %s]",
				                                                         ext,
				                                                         mimeType));
		                           });

		return created;
	});

	@Getter
	enum ContentMainType {
		application,
		image,
		text,
		multipart,
		audio,
		video,
		x_world("x-world"),
		all("*");
		final String publicName;

		ContentMainType() {
			this.publicName = this.name();
		}

		ContentMainType(String name) {
			this.publicName = name;
		}

		static final ConditionedLock lock = new ConditionedLock();
		static final HashMap<String, ContentMainType> cache = new HashMap<>();

		public static ContentMainType of(String pn) {
			return lock.tran(() -> cache.computeIfAbsent(pn,
			                                             key -> {
				                                             for (ContentMainType value : ContentMainType.values()) {
					                                             if (value.publicName.equalsIgnoreCase(key)) {
						                                             return value;
					                                             }
				                                             }
				                                             return ContentMainType.all;
			                                             }));
		}
	}

	@Getter
	enum ContentSubType {
		annodex,
		basic,
		bmp,
		css,
		flac,
		gif,
		html,
		ief,
		java,
		json,
		java_archive("java-archive"),
		javascript,
		jpeg,
		mac_binhex40("mac-binhex40"),
		mathml_xml("mathml+xml"),
		mp4,
		mpeg,
		mpeg2,
		msword,
		octet_stream("octet-stream"),
		oda,
		ogg,
		pdf,
		pict,
		plain,
		png,
		postscript,
		quicktime,
		rdf_xml("rdf+xml"),
		richtext,
		rtf,
		svg_xml("svg+xml"),
		tab_separated_values("tab-separated-values"),
		tiff,
		vnd_mozilla_xul_xml("vnd.mozilla.xul+xml"),
		vnd_ms_excel("vnd.ms-excel"),
		vnd_ms_powerpoint("vnd.ms-powerpoint"),
		vnd_oasis_opendocument_chart("vnd.oasis.opendocument.chart"),
		vnd_oasis_opendocument_database("vnd.oasis.opendocument.database"),
		vnd_oasis_opendocument_formula("vnd.oasis.opendocument.formula"),
		vnd_oasis_opendocument_graphics("vnd.oasis.opendocument.graphics"),
		vnd_oasis_opendocument_graphics_template("vnd.oasis.opendocument.graphics-template"),
		vnd_oasis_opendocument_image("vnd.oasis.opendocument.image"),
		vnd_oasis_opendocument_presentation("vnd.oasis.opendocument.presentation"),
		vnd_oasis_opendocument_presentation_template("vnd.oasis.opendocument.presentation-template"),
		vnd_oasis_opendocument_spreadsheet("vnd.oasis.opendocument.spreadsheet"),
		vnd_oasis_opendocument_spreadsheet_template("vnd.oasis.opendocument.spreadsheet-template"),
		vnd_oasis_opendocument_text("vnd.oasis.opendocument.text"),
		vnd_oasis_opendocument_text_master("vnd.oasis.opendocument.text-master"),
		vnd_oasis_opendocument_text_template("vnd.oasis.opendocument.text-template"),
		vnd_oasis_opendocument_text_web("vnd.oasis.opendocument.text-web"),
		vnd_rn_realmedia("vnd.rn-realmedia"),
		vnd_sun_j2me_app_descriptor("vnd.sun.j2me.app-descriptor"),
		vnd_wap_wbmp("vnd.wap.wbmp"),
		vnd_wap_wml("vnd.wap.wml"),
		vnd_wap_wmlc("vnd.wap.wmlc"),
		vnd_wap_wmlscript("vnd.wap.wmlscript"),
		vnd_wap_wmlscriptc("vnd.wap.wmlscriptc"),
		voicexml_xml("voicexml+xml"),
		wspolicy_xml("wspolicy+xml"),
		x_yaml("x-yaml"),
		x_aiff("x-aiff"),
		x_aim("x-aim"),
		x_bcpio("x-bcpio"),
		x_cdf("x-cdf"),
		x_cmu_raster("x-cmu-raster"),
		x_component("x-portable"),
		x_compress("x-compress"),
		x_cpio("x-cpio"),
		x_csh("x-csh"),
		x_dv("x-dv"),
		x_dvi("x-dvi"),
		x_gtar("x-gtar"),
		x_gzip("x-gzip"),
		x_hdf("x-hdf"),
		x_java_jnlp_file("x-java-jnlp-file"),
		x_jg("x-jg"),
		x_latex("x-latex"),
		x_macpaint("x-macpaint"),
		x_midi("x-midi"),
		x_mif("x-mif"),
		x_mpeg("x-mpeg"),
		x_mpegurl("x-mpegurl"),
		x_ms_asf("x-ms-asf"),
		x_ms_wmv("x-ms-wmv"),
		x_msvideo("x-msvideo"),
		x_netcdf("x-netcdf"),
		x_photoshop("x-photoshop"),
		x_portable_anymap("x-portable-anymap"),
		x_portable_bitmap("x-portable-bitmap"),
		x_portable_graymap("x-portable-graymap"),
		x_portable_pixmap("x-portable-pixmap"),
		x_quicktime("x-quicktime"),
		x_rad_screenplay("x-rad-screenplay"),
		x_rgb("x-rgb"),
		x_scpls("x-scpls"),
		x_setext("x-setext"),
		x_sgi_movie("x-sgi-movie"),
		x_sh("x-sh"),
		x_shar("x-shar"),
		x_shockwave_flash("x-shockwave-flash"),
		x_stuffit("x-stuffit"),
		x_sv4cpio("x-sv4cpio"),
		x_sv4crc("x-sv4crc"),
		x_tar("x-tar"),
		x_tcl("x-tcl"),
		x_tex("x-tex"),
		x_texinfo("x-texinfo"),
		x_troff("x-troff"),
		x_troff_man("x-troff-man"),
		x_troff_me("x-troff-me"),
		x_ustar("x-ustar"),
		x_visio("x-visio"),
		x_vrml("x-vrml"),
		x_wais_source("x-wais-source"),
		x_wav("x-wav"),
		x_x509_ca_cert("x-x509-ca-cert"),
		x_xbitmap("x-xbitmap"),
		x_xpixmap("x-xpixmap"),
		x_xwindowdump("x-xwindowdump"),
		xhtml_xml("xhtml+xml"),
		xml,
		xml_dtd("xml-dtd"),
		xslt_xml("xslt+xml"),
		xspf_xml("xspf+xml"),
		x_www_form_urlencoded("x-www-form-urlencoded"),
		form_data("form-data"),
		zip,
		mixed,
		alternative,
		related,
		all("*");
		final String publicName;

		ContentSubType() {
			this.publicName = this.name();
		}

		ContentSubType(String name) {
			this.publicName = name;
		}

		public boolean supportJackson() {
			switch (this) {
				case xml:
				case json:
				case x_yaml: {
					return true;
				}
			}
			return false;
		}

		static final ConditionedLock lock = new ConditionedLock();
		static final HashMap<String, ContentSubType> cache = new HashMap<>();

		public static ContentSubType of(String pn) {
			return lock.tran(() -> cache.computeIfAbsent(pn,
			                                             key -> {
				                                             for (ContentSubType value : ContentSubType.values()) {
					                                             if (value.publicName.equalsIgnoreCase(key)) {
						                                             return value;
					                                             }
				                                             }
				                                             return ContentSubType.all;
			                                             }));
		}
	}

	@NoArgsConstructor
	class MediaType {
		@Getter
		private ContentMainType mainType;
		@Getter
		private ContentSubType subType;
		@Getter
		private CharSets charset;
		@Getter
		private String boundary;
		@Getter
		private String publicName;
		@Getter
		private boolean normal;
		@Getter
		Predicate<MediaType> matched;
		@Getter
		Predicate<MediaType> unmatched;
		SuppliedReference<byte[]> publicBytes;
		private Map<String, String> options;
		private final AtomicBoolean freeze = new AtomicBoolean(false);

		public MediaType freeze() {
			if (this.freeze.compareAndSet(false,
			                              true)) {
				this.matched = other -> other.mainType == this.mainType && other.subType == this.subType;
				this.unmatched = this.matched.negate();
				this.publicBytes = new SuppliedReference<>(() -> this.publicName.getBytes(StandardCharsets.UTF_8));
				return this;
			}
			throw new IllegalStateException("freeze twice?");
		}

		private void checkFreeze() {
			if (this.freeze.get()) {
				throw new IllegalStateException("already freeze media type");
			}
		}

		public MediaType mainType(ContentMainType value) {
			this.checkFreeze();
			this.mainType = value;
			this.touch();
			return this;
		}

		public MediaType subType(ContentSubType value) {
			this.checkFreeze();
			this.subType = value;
			this.touch();
			return this;
		}

		public MediaType charset(CharSets value) {
			this.checkFreeze();
			this.charset = value;
			this.touch();
			return this;
		}

		public void boundary(String value) {
			this.checkFreeze();
			this.boundary = value;
			this.abnormal();
		}

		public void options(final String key,
		                    final String value) {
			this.checkFreeze();
			if (Objects.isNull(this.options)) {
				this.options = new LinkedHashMap<>();
			}
			this.options.put(key,
			                 value);
		}

		public CharSets charset() {
			return this.charset == null
			       ? CharSets.UTF_8
			       : this.charset;
		}

		public CharSets getCharset(CharSets other) {
			return this.charset == null
			       ? other
			       : this.charset;
		}

		public boolean matchType(MediaType other) {
			return this.mainType == other.mainType && this.subType == other.subType;
		}

		public byte[] publicNameAsBytes() {
			return this.publicBytes.supplied();
		}

		private void abnormal() {
			this.publicName = null;
			this.normal = false;
		}

		private void touch() {
			if (null == this.mainType || null == this.subType) {
				return;
			}
			this.normal = true;
			this.publicName = stringComposer().add(this.mainType.getPublicName())
			                                  .add('/')
			                                  .add(this.subType.getPublicName())
			                                  .add(builder -> {
				                                  if (null != this.charset) {
					                                  builder.append(';')
					                                         .append(" charset=")
					                                         .append(this.charset.getPublicName());
				                                  }
			                                  })
			                                  .build();
		}

		public boolean supportJackson() {
			return this.subType.supportJackson();
		}

		public JacksonMapper objectMapper() {
			return this.objectMapper(NamePattern.camel);
		}

		public JacksonMapper objectMapper(NamePattern pattern) {
			switch (this.subType) {
				case json: {
					switch (pattern) {
						case camel: {
							return JacksonMapper.JSON_MAP;
						}
						case kebab: {
							return JacksonMapper.JSON_KEBAB_MAP;
						}
						case snake: {
							return JacksonMapper.JSON_SNAKE_MAP;
						}
					}
					break;
				}

				case xml: {
					switch (pattern) {
						case camel: {
							return JacksonMapper.XML_MAP;
						}
						case kebab: {
							return JacksonMapper.XML_KEBAB_MAP;
						}
						case snake: {
							return JacksonMapper.XML_SNAKE_MAP;
						}
					}
					break;

				}

				case x_yaml: {
					switch (pattern) {
						case camel: {
							return JacksonMapper.YML;
						}
						case kebab: {
							return JacksonMapper.YML_KEBAB;
						}
						case snake: {
							return JacksonMapper.YML_SNAKE;
						}
					}
					break;
				}
			}

			return null;
		}

		@Override
		public String toString() {
			return this.publicName;
		}

		static final ConditionedLock lock = new ConditionedLock();
		static final HashMap<String, MediaType> cache = new HashMap<>();
		static final HashMap<String, MediaType> extCache = new HashMap<>();
		public static final MediaType all = new MediaType().mainType(ContentMainType.all)
		                                                   .subType(ContentSubType.all)
		                                                   .freeze();
		public static final MediaType stream = new MediaType().mainType(ContentMainType.application)
		                                                      .subType(ContentSubType.octet_stream)
		                                                      .freeze();
		public static final MediaType url_encode = new MediaType().mainType(ContentMainType.application)
		                                                          .subType(ContentSubType.x_www_form_urlencoded)
		                                                          .charset(CharSets.UTF_8)
		                                                          .freeze();

		public static MediaType of(final String pn) {
			final String filtered = StringUtils.isEmpty(pn)
			                        ? ConstValues.BLANK_STRING
			                        : pn.trim()
			                            .toLowerCase();
			if (StringUtils.isEmpty(filtered)) {
				return all;
			}

			return lock.tran(() -> cache.computeIfAbsent(filtered,
			                                             MediaType::parse));
		}

		static String escapeQuotation(String org) {
			final char first = org.charAt(0);

			switch (first) {
				case '\'':
				case '"': {
					return org.substring(1,
					                     org.length() - 1);
				}

				default: {
					break;
				}
			}
			return org;
		}

		static MediaType parse(final String filtered) {
			final String[] parts = StringAssist.split(filtered,
			                                          ';',
			                                          true,
			                                          true);
			final int nameSep = parts[0].indexOf('/');

			if (nameSep < 0) {
				throw new IllegalArgumentException(StringAssist.format("[%s] is malformed",
				                                                       filtered));
			}

			final String mainTypeName = parts[0].substring(0,
			                                               nameSep);
			final String subTypeName = parts[0].substring(nameSep + 1);

			final MediaType mediaType = new MediaType().mainType(mainTypeName.indexOf('*') >= 0
			                                                     ? ContentMainType.all
			                                                     : supply(() -> ContentMainType.of(mainTypeName),
			                                                              throwable -> {
				                                                              throw new IllegalArgumentException(StringAssist.format("[%s] cannot detect main type",
				                                                                                                                     filtered));
			                                                              }))
			                                           .subType(subTypeName.indexOf('*') >= 0
			                                                    ? ContentSubType.all
			                                                    : supply(() -> ContentSubType.of(subTypeName),
			                                                             throwable -> {
				                                                             throw new IllegalArgumentException(StringAssist.format("[%s] cannot detect sub type",
				                                                                                                                    filtered));
			                                                             }));
			if (parts.length > 1) {
				Arrays.stream(parts,
				              1,
				              parts.length)
				      .forEach(part -> {
					      final int sep = part.indexOf('=');

					      if (sep > 0) {
						      final String key = part.substring(0,
						                                        sep)
						                             .trim()
						                             .toLowerCase();
						      final String value = part.substring(sep + 1)
						                               .trim();

						      switch (key) {
							      case "charset": {
								      mediaType.charset(CharSets.of(escapeQuotation(value)));
								      break;
							      }

							      case "boundary": {
								      mediaType.boundary(value);
								      break;
							      }

							      default: {
								      mediaType.options(key,
								                        value);
								      break;
							      }
						      }
					      }
				      });

			}

			return mediaType.freeze();
		}

		public static MediaType byFile(File file) {
			return byName(file.getName());
		}

		public static MediaType byName(String fileName) {
			final int dot = fileName.lastIndexOf('.');

			if (dot < 0) {
				return stream;
			}

			final String ext = fileName.substring(dot + 1);


			return byExt(ext);
		}

		public static MediaType byExt(String ext) {
			if (ext.isEmpty()) {
				return stream;
			}

			return lock.tran(() -> extCache.computeIfAbsent(ext,
			                                                key -> Optional.ofNullable(loaded.get(key))
			                                                               .map(MediaType::of)
			                                                               .orElse(stream)));
		}
	}

	@Getter
	@AllArgsConstructor
	class AcceptContentTypeEntry
			implements Priority {
		final MediaType mediaType;
		final int priority;
	}

	class AcceptContentTypeSet {
		final List<AcceptContentTypeEntry> types = new ExtendList<>(PriorityType.DESC);

		void append(final MediaType mediaType,
		            final float factor) {
			this.types.add(new AcceptContentTypeEntry(mediaType,
			                                          ((int) factor * 1000)));
		}

		public MediaType master() {
			return this.types.get(0).mediaType;
		}

		public boolean needUpdate(MediaType mediaType) {
			return this.types.stream()
			                 .noneMatch(entry -> entry.mediaType.getMatched()
			                                                    .test(mediaType));
		}

		public static AcceptContentTypeSet of(String accept) {
			AcceptContentTypeSet set = new AcceptContentTypeSet();
			SplitPattern.comma.stream(accept)
			                  .forEach(elm -> {
				                  final int sep = elm.indexOf(';');
				                  final String contentTypeStr = sep > 0
				                                                ? elm.substring(0,
				                                                                sep)
				                                                : elm.trim();
				                  final String qStr = sep > 0
				                                      ? elm.substring(sep + 1)
				                                      : "q=1";

				                  final float factor = qStr.startsWith("q=")
				                                       ? Float.parseFloat(qStr.substring(2)
				                                                              .trim()
				                                                              .toLowerCase())
				                                       : 1.0f;

				                  final MediaType mediaType = MediaType.of(contentTypeStr);

				                  set.append(mediaType,
				                             factor);

			                  });
			return set;
		}
	}
}
