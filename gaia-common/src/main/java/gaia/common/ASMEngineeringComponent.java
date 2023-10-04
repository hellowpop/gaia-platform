package gaia.common;


import static gaia.common.ResourceAssist.stringComposer;

import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.util.Printer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import gaia.common.ClassScanComponent.ClassAnnotationInfo;
import gaia.common.ClassScanComponent.ClassFieldInfo;
import gaia.common.ClassScanComponent.ClassScanContext;
import gaia.common.ResourceAssist.StringComposer;
import gaia.common.StringAssist.SplitPattern;

public interface ASMEngineeringComponent {
	Logger log = LoggerFactory.getLogger(ASMEngineeringComponent.class);
	Map<AccessorCategory, List<AccessorType>> categories = FunctionAssist.supply(() -> {
		Map<AccessorCategory, List<AccessorType>> map = new HashMap<>();

		for (AccessorType value : AccessorType.values()) {
			for (AccessorCategory accessorCategory : value.accessorCategories) {
				map.computeIfAbsent(accessorCategory,
				                    key -> new ArrayList<>())
				   .add(value);
			}
		}

		return Collections.unmodifiableMap(map);
	});

	static boolean appendClassLoaderHierarchy(ClassLoader loader,
	                                          StringComposer composer) {
		if (loader == null) {
			return false;
		}

		if (appendClassLoaderHierarchy(loader.getParent(),
		                               composer)) {
			composer.add('>');
		}
		composer.add(loader.getClass()
		                   .getName());
		return true;
	}

	enum ClassType {
		annotation_type,
		interface_type,
		class_type,
		enum_type,

	}

	enum AccessorCategory {
		class_type,
		field_type,
		method_type,
		parameter_type,
		module_type
	}

	// ---------------------------------------------------------------------
	// Section scan & print
	// ---------------------------------------------------------------------
	enum AccessorType {
		PUBLIC(0x0001,
		       AccessorCategory.class_type,
		       AccessorCategory.field_type,
		       AccessorCategory.method_type), // class, field, method
		PRIVATE(0x0002,
		        AccessorCategory.class_type,
		        AccessorCategory.field_type,
		        AccessorCategory.method_type), // class, field, method
		PROTECTED(0x0004,
		          AccessorCategory.class_type,
		          AccessorCategory.field_type,
		          AccessorCategory.method_type), // class, field, method
		STATIC(0x0008,
		       AccessorCategory.field_type,
		       AccessorCategory.method_type), // field, method
		FINAL(0x0010,
		      AccessorCategory.class_type,
		      AccessorCategory.field_type,
		      AccessorCategory.method_type,
		      AccessorCategory.parameter_type), // class, field, method, parameter
		SUPER(0x0020,
		      AccessorCategory.class_type), // class
		SYNCHRONIZED(0x0020,
		             AccessorCategory.method_type), // method
		OPEN(0x0020,
		     AccessorCategory.module_type), // module
		TRANSITIVE(0x0020,
		           AccessorCategory.module_type), // module requires
		VOLATILE(0x0040,
		         AccessorCategory.field_type), // field
		BRIDGE(0x0040,
		       AccessorCategory.method_type), // method
		STATIC_PHASE(0x0040,
		             AccessorCategory.module_type), // module requires
		VARARGS(0x0080,
		        AccessorCategory.method_type), // method
		TRANSIENT(0x0080,
		          AccessorCategory.field_type), // field
		NATIVE(0x0100,
		       AccessorCategory.method_type), // method
		INTERFACE(0x0200,
		          AccessorCategory.class_type), // class
		ABSTRACT(0x0400,
		         AccessorCategory.class_type,
		         AccessorCategory.method_type), // class, method
		STRICT(0x0800,
		       AccessorCategory.method_type), // method
		SYNTHETIC(0x1000,
		          AccessorCategory.class_type,
		          AccessorCategory.field_type,
		          AccessorCategory.method_type,
		          AccessorCategory.parameter_type,
		          AccessorCategory.module_type), // class, field, method, parameter, module *
		ANNOTATION(0x2000,
		           AccessorCategory.class_type), // class
		ENUM(0x4000,
		     AccessorCategory.class_type), // class(?) field inner
		MANDATED(0x8000,
		         AccessorCategory.field_type,
		         AccessorCategory.method_type,
		         AccessorCategory.parameter_type,
		         AccessorCategory.module_type), // field, method, parameter, module, module *
		MODULE(0x8000,
		       AccessorCategory.class_type), // class
		RECORD(0x10000,
		       AccessorCategory.class_type), // class
		DEPRECATED(0x20000,
		           AccessorCategory.class_type,
		           AccessorCategory.field_type,
		           AccessorCategory.method_type); // class, field, method

		final int code;
		final String index;
		final AccessorCategory[] accessorCategories;

		AccessorType(int code,
		             AccessorCategory... accessorCategories) {
			this.code = code;
			this.index = this.name()
			                 .toLowerCase();
			this.accessorCategories = accessorCategories;
		}

		public static String description(AccessorCategory category,
		                                 int access) {
			return stringComposer().self(builder -> categories.get(category)
			                                                  .stream()
			                                                  .map(elm -> elm.index(access))
			                                                  .filter(Objects::nonNull)
			                                                  .forEach(index -> builder.appendWith(index,
			                                                                                       " ")))
			                       .build();
		}

		public boolean match(int access) {
			return (access & this.code) != 0;
		}

		public String index(int access) {
			return this.match(access)
			       ? this.index
			       : null;
		}
	}

	interface InbodyNotifierSupport {

		/**
		 * Generates a human readable representation of this attribute.
		 *
		 * @param outputBuilder where the human representation of this attribute must be appended.
		 * @param labelNames    the human readable names of the labels.
		 */
		void textify(StringBuilder outputBuilder,
		             Map<Label, String> labelNames);
	}

	abstract class ASMAssist {
		public static Stream<File> classpath() {
			ClassLoader loader = Thread.currentThread()
			                           .getContextClassLoader();
			if (loader instanceof URLClassLoader) {
				final URL[] urls = ((URLClassLoader) loader).getURLs();
				if (log.isTraceEnabled()) {
					log.trace(StringAssist.format("classpath class loader as url loader [%s] urls [%n%s]",
					                              stringComposer().self(composer -> appendClassLoaderHierarchy(loader,
					                                                                                           composer))
					                                              .build(),
					                              stringComposer().self(composer -> Stream.of(urls)
					                                                                      .forEach(url -> composer.line(url.toString())))
					                                              .build()));
				}

				return Stream.of(urls)
				             .map(url -> new File(url.getFile()));
			}
			if (log.isTraceEnabled()) {
				log.trace(StringAssist.format("classpath class loader as system classpath iterator [%s]",
				                              loader.getClass()
				                                    .getName()));
			}
			final String classpath = GWEnvironment.getProperty("java.class.path");

			if (StringUtils.isEmpty(classpath)) {
				throw new Error("property [java.class.path] is empty?");
			}

			return SplitPattern.path.stream(classpath)
			                        .map(File::new);
		}
	}

	@Setter
	@Getter
	@NoArgsConstructor
	@JsonInclude(Include.NON_NULL)
	class ASMClassScanContext
			extends ClassScanContext {
		transient ClassReader reader;
		transient ClassWriter writer;
		ClassType elementType;
		String name;
		String className;
		String superName;
		int access;
		int majorVersion;
		int minorVersion;
		boolean deprecated;
		boolean record;
		String signature;
		String signatureReturnType;
		String signatureDeclaration;
		String signatureExceptions;
		String[] interfaces;
		String nestHost;
		OuterClassInfo outerClass;
		List<String> nestMemberList;
		List<String> permittedSubclassList;
		List<Attribute> attributes;
		List<InnerClassInfo> innerClassInfos;
		List<RecordComponentInfo> recordComponentInfos;
		List<ASMFieldInfo> ASMFieldInfos;
		List<MethodInfo> methodInfos;

		public ASMClassScanContext(ClassReader reader,
		                           ClassWriter writer) {
			this.reader = reader;
			this.writer = writer;
		}

		public static ClassScanContext of(InputStream stream) {
			ClassReader reader = FunctionAssist.supply(() -> new ClassReader(stream));
			ClassWriter writer = new ClassWriter(reader,
			                                     0);

			return new ASMClassScanContext(reader,
			                               writer).load();
		}

		public ASMClassScanContext load() {
			final ClassScanContextVisitor visitor = new ClassScanContextVisitor(this);
			this.className = this.reader.getClassName();
			this.reader.accept(visitor,
			                   0);
			return this;
		}

		void addAttribute(Attribute info) {
			if (null == this.attributes) {
				this.attributes = new ArrayList<>();
			}
			this.attributes.add(info);
		}

		void addNestMember(final String nestMember) {
			if (null == this.nestMemberList) {
				this.nestMemberList = new ArrayList<>();
			}
			this.nestMemberList.add(nestMember);
		}

		void addPermittedSubclass(final String permittedSubclass) {
			if (null == this.permittedSubclassList) {
				this.permittedSubclassList = new ArrayList<>();
			}
			this.permittedSubclassList.add(permittedSubclass);
		}

		void addInnerClassInfo(InnerClassInfo info) {
			if (null == this.innerClassInfos) {
				this.innerClassInfos = new ArrayList<>();
			}
			this.innerClassInfos.add(info);
		}

		void addRecordComponentInfo(RecordComponentInfo info) {
			if (null == this.recordComponentInfos) {
				this.recordComponentInfos = new ArrayList<>();
			}
			this.recordComponentInfos.add(info);
		}

		void addFieldInfo(ASMFieldInfo info) {
			if (null == this.ASMFieldInfos) {
				this.ASMFieldInfos = new ArrayList<>();
			}
			this.ASMFieldInfos.add(info);
		}

		void addMethodInfo(MethodInfo info) {
			if (null == this.methodInfos) {
				this.methodInfos = new ArrayList<>();
			}
			this.methodInfos.add(info);
		}

		void addAnnotationInfo(ASMClassAnnotationInfo info) {
			if (null == this.classAnnotationInfos) {
				this.classAnnotationInfos = new ArrayList<>();
			}
			this.classAnnotationInfos.add(info);
		}

		@Override
		public Optional<ClassAnnotationInfo> getAnnotation(final String name) {
			return null == this.classAnnotationInfos
			       ? Optional.empty()
			       : this.classAnnotationInfos.stream()
			                                  .filter(info -> info.isMatch(name))
			                                  .findFirst();
		}

		@Override
		public String toString() {
			return StringAssist.format("ASMClassScanContext[%s]",
			                           this.className);
		}
	}

	@Setter
	@Getter
	@NoArgsConstructor
	@JsonInclude(Include.NON_NULL)
	class AccessMember {
		private int access;

		private String accessDescription;


		AccessMember(final AccessorCategory category,
		             final int access) {
			this.access = access;
			this.accessDescription = AccessorType.description(category,
			                                                  access);
		}
	}

	@Setter
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonInclude(Include.NON_NULL)
	class OuterClassInfo {
		String owner;
		String name;
		String descriptor;
	}

	@Setter
	@Getter
	@NoArgsConstructor
	@JsonInclude(Include.NON_NULL)
	class InnerClassInfo
			extends AccessMember {
		String name;
		String outerName;
		String innerName;

		InnerClassInfo(String name,
		               String outerName,
		               String innerName,
		               int access) {
			super(AccessorCategory.class_type,
			      access);
			this.name = name;
			this.outerName = outerName;
			this.innerName = innerName;
		}
	}

	@Setter
	@Getter
	@NoArgsConstructor
	@JsonInclude(Include.NON_NULL)
	class MethodInfo
			extends AccessMember {
		String name;
		String descriptor;
		String signature;
		String[] exceptions;
		Integer annotableParameterCount;
		Boolean annotableParameterCountVisible;
		List<Attribute> attributes;
		List<ClassAnnotationInfo> classAnnotationInfos;
		List<MethodParamInfo> methodParamInfos;
		Map<Integer, List<ClassAnnotationInfo>> paramAnnotation;

		MethodInfo(int access,
		           String name,
		           String descriptor,
		           String signature,
		           String[] exceptions) {
			super(AccessorCategory.method_type,
			      access);
			this.name = name;
			this.descriptor = descriptor;
			this.signature = signature;
			this.exceptions = exceptions;
		}

		MethodVisitor of(MethodVisitor parent) {
			return new MethodVisitorImpl(this,
			                             parent);
		}

		void addAttribute(Attribute info) {
			if (null == this.attributes) {
				this.attributes = new ArrayList<>();
			}
			this.attributes.add(info);
		}

		void addAnnotationInfo(ClassAnnotationInfo info) {
			if (null == this.classAnnotationInfos) {
				this.classAnnotationInfos = new ArrayList<>();
			}
			this.classAnnotationInfos.add(info);
		}

		void addMethodParamInfo(MethodParamInfo info) {
			if (null == this.methodParamInfos) {
				this.methodParamInfos = new ArrayList<>();
			}
			this.methodParamInfos.add(info);
		}

		void addParameterAnnotation(final int parameter,
		                            ClassAnnotationInfo info) {
			if (null == this.paramAnnotation) {
				this.paramAnnotation = new HashMap<>();
			}

			this.paramAnnotation.computeIfAbsent(parameter,
			                                     index -> new ArrayList<>())
			                    .add(info);
		}
	}

	@Setter
	@Getter
	@NoArgsConstructor
	@JsonInclude(Include.NON_NULL)
	class MethodParamInfo
			extends AccessMember {
		String name;

		MethodParamInfo(String name,
		                int access) {
			super(AccessorCategory.parameter_type,
			      access);
			this.name = name;
		}
	}

	@Setter
	@Getter
	@NoArgsConstructor
	@JsonInclude(Include.NON_NULL)
	class ASMClassAnnotationInfo
			implements ClassAnnotationInfo {
		String descriptor;
		boolean visible;
		TypeReference typeReference;
		TypePath typePath;
		Map<String, Object> parameter;
		boolean defaultAnnotation;
		TypeInfo typeInfo;

		ASMClassAnnotationInfo(boolean defaultAnnotation) {
			super();
			this.defaultAnnotation = defaultAnnotation;
		}

		ASMClassAnnotationInfo(String descriptor) {
			super();
			this.descriptor = descriptor;
			this.typeInfo = new TypeInfo(descriptor);
		}

		ASMClassAnnotationInfo(String descriptor,
		                       boolean visible) {
			this(descriptor);
			this.visible = visible;
		}

		ASMClassAnnotationInfo(final int typeRef,
		                       final TypePath typePath,
		                       final String descriptor,
		                       final boolean visible) {
			this(descriptor);
			this.visible = visible;
			this.typePath = typePath;
			this.typeReference = new TypeReference(typeRef);
		}

		AnnotationVisitor of(AnnotationVisitor parent) {
			return new AnnotationVisitorSingleImpl(this,
			                                       parent);
		}

		@Override
		public boolean isMatch(String name) {
			return null != this.typeInfo && StringUtils.equals(this.typeInfo.getClassName(),
			                                                   name);
		}

		@Override
		public Optional<Object> getParam(String name) {
			return null == this.parameter
			       ? Optional.empty()
			       : Optional.ofNullable(this.parameter.get(name));
		}

		@Override
		public void addParam(String name,
		                     Object value) {
			if (null == this.parameter) {
				this.parameter = new HashMap<>();
			}
			if (value instanceof Type) {
				this.parameter.put(name,
				                   new TypeInfo((Type) value));
			} else {
				this.parameter.put(name,
				                   value);
			}
		}
	}

	@Setter
	@Getter
	@NoArgsConstructor
	@JsonInclude(Include.NON_NULL)
	class ASMFieldInfo
			extends AccessMember
			implements ClassFieldInfo {
		String name;
		String descriptor;
		String signature;
		Object value;
		List<ClassAnnotationInfo> classAnnotationInfos;
		List<Attribute> attributeList;

		ASMFieldInfo(int access,
		             String name,
		             String descriptor,
		             String signature,
		             Object value) {
			super(AccessorCategory.field_type,
			      access);
			this.name = name;
			this.descriptor = descriptor;
			this.signature = signature;
			this.value = value;
		}

		FieldVisitor of(FieldVisitor parent) {
			return new FieldVisitorImpl(this,
			                            parent);
		}

		void addAnnotation(ClassAnnotationInfo classAnnotationInfo) {
			if (null == this.classAnnotationInfos) {
				this.classAnnotationInfos = new ArrayList<>();
			}
			this.classAnnotationInfos.add(classAnnotationInfo);
		}

		void addAttribute(Attribute attribute) {
			if (null == this.attributeList) {
				this.attributeList = new ArrayList<>();
			}
			this.attributeList.add(attribute);
		}
	}

	// ---------------------------------------------------------------------
	// Section visitors
	// ---------------------------------------------------------------------

	@Setter
	@Getter
	@NoArgsConstructor
	@JsonInclude(Include.NON_NULL)
	class RecordComponentInfo {
		String name;
		String descriptor;
		String signature;
		List<ClassAnnotationInfo> annotationList;
		List<Attribute> attributeList;

		RecordComponentInfo(String name,
		                    String descriptor,
		                    String signature) {
			this.name = name;
			this.descriptor = descriptor;
			this.signature = signature;
		}

		RecordComponentVisitor of(final RecordComponentVisitor visitor) {
			return new RecordComponentVisitorImpl(this,
			                                      visitor);
		}

		void addAnnotationInfo(ClassAnnotationInfo info) {
			if (null == this.annotationList) {
				this.annotationList = new ArrayList<>();
			}
			this.annotationList.add(info);
		}

		void addAttribute(Attribute attribute) {
			if (null == this.attributeList) {
				this.attributeList = new ArrayList<>();
			}
			this.attributeList.add(attribute);
		}
	}

	class ArrayInfo
			extends ArrayList<Object> {
		AnnotationVisitor of(AnnotationVisitor parent) {
			return new AnnotationVisitorArrayImpl(this,
			                                      parent);
		}
	}

	@Setter
	@Getter
	@NoArgsConstructor
	@JsonInclude(Include.NON_NULL)
	class TypeInfo {
		String descriptor;
		String className;

		TypeInfo(String descriptor) {
			super();
			this.descriptor = descriptor;
			this.className = Type.getType(descriptor)
			                     .getClassName();
		}

		TypeInfo(Type type) {
			super();
			this.descriptor = type.getDescriptor();
			this.className = type.getClassName();
		}
	}

	class ClassScanContextVisitor
			extends ClassVisitor {
		final ASMClassScanContext context;

		ClassScanContextVisitor(final ASMClassScanContext scanContext) {
			super(Opcodes.ASM9,
			      scanContext.writer);
			this.context = scanContext;
		}

		@Override
		public void visit(final int version,
		                  final int access,
		                  final String name,
		                  final String signature,
		                  final String superName,
		                  final String[] interfaces) {
			this.context.name = name;
			this.context.superName = superName;
			this.context.access = access;
			this.context.majorVersion = version & 0xFFFF;
			this.context.minorVersion = version >>> 16;
			this.context.deprecated = (access & Opcodes.ACC_DEPRECATED) != 0;
			this.context.record = (access & Opcodes.ACC_RECORD) != 0;
			this.context.signature = signature;
			this.context.interfaces = interfaces;

			if (null != signature) {
				SignatureScanVisitor traceSignatureVisitor = new SignatureScanVisitor(access);
				new SignatureReader(signature).accept(traceSignatureVisitor);

				this.context.signatureReturnType = traceSignatureVisitor.getReturnType();
				this.context.signatureDeclaration = traceSignatureVisitor.getDeclaration();
				this.context.signatureExceptions = traceSignatureVisitor.getExceptions();
			}

			if ((access & Opcodes.ACC_ANNOTATION) != 0) {
				this.context.elementType = ClassType.annotation_type;
			} else if ((access & Opcodes.ACC_INTERFACE) != 0) {
				this.context.elementType = ClassType.interface_type;
			} else if ((access & Opcodes.ACC_ENUM) != 0) {
				this.context.elementType = ClassType.enum_type;
			} else {
				this.context.elementType = ClassType.class_type;
			}

			super.visit(version,
			            access,
			            name,
			            signature,
			            superName,
			            interfaces);
		}

		@Override
		public void visitNestHost(final String nestHost) {
			this.context.nestHost = nestHost;
			super.visitNestHost(nestHost);
		}

		@Override
		public void visitOuterClass(final String owner,
		                            final String name,
		                            final String descriptor) {
			this.context.outerClass = new OuterClassInfo(owner,
			                                             name,
			                                             descriptor);
			super.visitOuterClass(owner,
			                      name,
			                      descriptor);
		}

		@Override
		public AnnotationVisitor visitAnnotation(final String descriptor,
		                                         final boolean visible) {
			final ASMClassAnnotationInfo annotationInfo = new ASMClassAnnotationInfo(descriptor,
			                                                                         visible);
			this.context.addAnnotationInfo(annotationInfo);
			return annotationInfo.of(super.visitAnnotation(descriptor,
			                                               visible));
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(final int typeRef,
		                                             final TypePath typePath,
		                                             final String descriptor,
		                                             final boolean visible) {
			final ASMClassAnnotationInfo annotationInfo = new ASMClassAnnotationInfo(typeRef,
			                                                                         typePath,
			                                                                         descriptor,
			                                                                         visible);

			this.context.addAnnotationInfo(annotationInfo);

			return annotationInfo.of(super.visitTypeAnnotation(typeRef,
			                                                   typePath,
			                                                   descriptor,
			                                                   visible));
		}

		@Override
		public void visitAttribute(final Attribute attribute) {
			this.context.addAttribute(attribute);
			super.visitAttribute(attribute);
		}

		@Override
		public void visitNestMember(final String nestMember) {
			this.context.addNestMember(nestMember);
			super.visitNestMember(nestMember);
		}

		@Override
		public void visitPermittedSubclass(final String permittedSubclass) {
			this.context.addPermittedSubclass(permittedSubclass);
			super.visitPermittedSubclass(permittedSubclass);
		}

		@Override
		public void visitInnerClass(final String name,
		                            final String outerName,
		                            final String innerName,
		                            final int access) {
			this.context.addInnerClassInfo(new InnerClassInfo(name,
			                                                  outerName,
			                                                  innerName,
			                                                  access));
			super.visitInnerClass(name,
			                      outerName,
			                      innerName,
			                      access);
		}

		@Override
		public RecordComponentVisitor visitRecordComponent(final String name,
		                                                   final String descriptor,
		                                                   final String signature) {
			RecordComponentInfo info = new RecordComponentInfo(name,
			                                                   descriptor,
			                                                   signature);
			this.context.addRecordComponentInfo(info);

			return info.of(super.visitRecordComponent(name,
			                                          descriptor,
			                                          signature));
		}

		@Override
		public FieldVisitor visitField(final int access,
		                               final String name,
		                               final String descriptor,
		                               final String signature,
		                               final Object value) {
			ASMFieldInfo ASMFieldInfo = new ASMFieldInfo(access,
			                                             name,
			                                             descriptor,
			                                             signature,
			                                             value);
			this.context.addFieldInfo(ASMFieldInfo);

			return ASMFieldInfo.of(super.visitField(access,
			                                        name,
			                                        descriptor,
			                                        signature,
			                                        value));
		}

		@Override
		public MethodVisitor visitMethod(final int access,
		                                 final String name,
		                                 final String descriptor,
		                                 final String signature,
		                                 final String[] exceptions) {
			MethodInfo methodInfo = new MethodInfo(access,
			                                       name,
			                                       descriptor,
			                                       signature,
			                                       exceptions);
			this.context.addMethodInfo(methodInfo);

			return methodInfo.of(super.visitMethod(access,
			                                       name,
			                                       descriptor,
			                                       signature,
			                                       exceptions));
		}
	}

	class MethodVisitorImpl
			extends MethodVisitor {
		final MethodInfo methodInfo;

		MethodVisitorImpl(final MethodInfo methodInfo,
		                  final MethodVisitor visitor) {
			super(Opcodes.ASM9,
			      visitor);
			this.methodInfo = methodInfo;
		}

		@Override
		public void visitParameter(final String name,
		                           final int access) {
			this.methodInfo.addMethodParamInfo(new MethodParamInfo(name,
			                                                       access));
			super.visitParameter(name,
			                     access);
		}

		@Override
		public AnnotationVisitor visitAnnotation(final String descriptor,
		                                         final boolean visible) {
			final ASMClassAnnotationInfo annotationInfo = new ASMClassAnnotationInfo(descriptor,
			                                                                         visible);

			this.methodInfo.addAnnotationInfo(annotationInfo);

			return annotationInfo.of(super.visitAnnotation(descriptor,
			                                               visible));
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(final int typeRef,
		                                             final TypePath typePath,
		                                             final String descriptor,
		                                             final boolean visible) {
			ASMClassAnnotationInfo annotationInfo = new ASMClassAnnotationInfo(typeRef,
			                                                                   typePath,
			                                                                   descriptor,
			                                                                   visible);
			this.methodInfo.addAnnotationInfo(annotationInfo);

			return annotationInfo.of(super.visitTypeAnnotation(typeRef,
			                                                   typePath,
			                                                   descriptor,
			                                                   visible));
		}

		@Override
		public void visitAttribute(final Attribute attribute) {
			this.methodInfo.addAttribute(attribute);
			super.visitAttribute(attribute);
		}

		@Override
		public AnnotationVisitor visitAnnotationDefault() {
			ASMClassAnnotationInfo annotationInfo = new ASMClassAnnotationInfo(true);
			return annotationInfo.of(super.visitAnnotationDefault());
		}

		@Override
		public void visitAnnotableParameterCount(final int parameterCount,
		                                         final boolean visible) {
			this.methodInfo.annotableParameterCount = parameterCount;
			this.methodInfo.annotableParameterCountVisible = visible;
			super.visitAnnotableParameterCount(parameterCount,
			                                   visible);
		}

		@Override
		public AnnotationVisitor visitParameterAnnotation(final int parameter,
		                                                  final String descriptor,
		                                                  final boolean visible) {
			final ASMClassAnnotationInfo annotationInfo = new ASMClassAnnotationInfo(descriptor,
			                                                                         visible);
			this.methodInfo.addParameterAnnotation(parameter,
			                                       annotationInfo);
			return annotationInfo.of(super.visitParameterAnnotation(parameter,
			                                                        descriptor,
			                                                        visible));
		}
	}

	class FieldVisitorImpl
			extends FieldVisitor {
		final ASMFieldInfo ASMFieldInfo;

		FieldVisitorImpl(final ASMFieldInfo ASMFieldInfo,
		                 final FieldVisitor fieldVisitor) {
			super(Opcodes.ASM9,
			      fieldVisitor);
			this.ASMFieldInfo = ASMFieldInfo;
		}

		@Override
		public AnnotationVisitor visitAnnotation(final String descriptor,
		                                         final boolean visible) {
			final ASMClassAnnotationInfo annotationInfo = new ASMClassAnnotationInfo(descriptor,
			                                                                         visible);

			this.ASMFieldInfo.addAnnotation(annotationInfo);

			return annotationInfo.of(super.visitAnnotation(descriptor,
			                                               visible));
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(final int typeRef,
		                                             final TypePath typePath,
		                                             final String descriptor,
		                                             final boolean visible) {
			ASMClassAnnotationInfo annotationInfo = new ASMClassAnnotationInfo(typeRef,
			                                                                   typePath,
			                                                                   descriptor,
			                                                                   visible);
			this.ASMFieldInfo.addAnnotation(annotationInfo);

			return annotationInfo.of(super.visitTypeAnnotation(typeRef,
			                                                   typePath,
			                                                   descriptor,
			                                                   visible));
		}

		@Override
		public void visitAttribute(final Attribute attribute) {
			this.ASMFieldInfo.addAttribute(attribute);
			super.visitAttribute(attribute);
		}
	}

	class RecordComponentVisitorImpl
			extends RecordComponentVisitor {
		final RecordComponentInfo componentInfo;

		public RecordComponentVisitorImpl(final RecordComponentInfo info,
		                                  final RecordComponentVisitor visitor) {
			super(Opcodes.ASM9,
			      visitor);
			this.componentInfo = info;
		}

		@Override
		public AnnotationVisitor visitAnnotation(final String descriptor,
		                                         final boolean visible) {
			ASMClassAnnotationInfo annotationInfo = new ASMClassAnnotationInfo(descriptor,
			                                                                   visible);
			this.componentInfo.addAnnotationInfo(annotationInfo);
			return annotationInfo.of(super.visitAnnotation(descriptor,
			                                               visible));
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(final int typeRef,
		                                             final TypePath typePath,
		                                             final String descriptor,
		                                             final boolean visible) {
			ASMClassAnnotationInfo annotationInfo = new ASMClassAnnotationInfo(descriptor,
			                                                                   visible);
			annotationInfo.typePath = typePath;
			annotationInfo.typeReference = new TypeReference(typeRef);

			this.componentInfo.addAnnotationInfo(annotationInfo);

			return annotationInfo.of(super.visitTypeAnnotation(typeRef,
			                                                   typePath,
			                                                   descriptor,
			                                                   visible));
		}

		@Override
		public void visitAttribute(final Attribute attribute) {
			this.componentInfo.addAttribute(attribute);
			super.visitAttribute(attribute);
		}
	}

	class AnnotationVisitorSingleImpl
			extends AnnotationVisitor {
		final ClassAnnotationInfo classAnnotationInfo;

		AnnotationVisitorSingleImpl(final ClassAnnotationInfo classAnnotationInfo,
		                            final AnnotationVisitor visitor) {
			super(Opcodes.ASM9,
			      visitor);
			this.classAnnotationInfo = classAnnotationInfo;
		}


		@Override
		public void visit(final String name,
		                  final Object value) {
			this.classAnnotationInfo.addParam(name,
			                                  value);
			super.visit(name,
			            value);
		}

		@Override
		public void visitEnum(final String name,
		                      final String descriptor,
		                      final String value) {
			this.classAnnotationInfo.addParam(name,
			                                  StringAssist.format("%s.%s",
			                                                      descriptor,
			                                                      value));
			super.visitEnum(name,
			                descriptor,
			                value);
		}

		@Override
		public AnnotationVisitor visitAnnotation(final String name,
		                                         final String descriptor) {
			final ASMClassAnnotationInfo annotationInfo = new ASMClassAnnotationInfo(descriptor);


			this.classAnnotationInfo.addParam(name,
			                                  annotationInfo);

			return annotationInfo.of(super.visitAnnotation(name,
			                                               descriptor));
		}

		@Override
		public AnnotationVisitor visitArray(final String name) {
			ArrayInfo arrayInfo = new ArrayInfo();

			this.classAnnotationInfo.addParam(name,
			                                  arrayInfo);

			return arrayInfo.of(super.visitArray(name));
		}
	}

	class AnnotationVisitorArrayImpl
			extends AnnotationVisitor {
		final ArrayInfo arrayInfo;

		AnnotationVisitorArrayImpl(final ArrayInfo arrayInfo,
		                           final AnnotationVisitor visitor) {
			super(Opcodes.ASM9,
			      visitor);
			this.arrayInfo = arrayInfo;
		}


		@Override
		public void visit(final String name,
		                  final Object value) {
			this.arrayInfo.add(value);
			super.visit(name,
			            value);
		}

		@Override
		public void visitEnum(final String name,
		                      final String descriptor,
		                      final String value) {
			this.arrayInfo.add(StringAssist.format("%s.%s",
			                                       descriptor,
			                                       value));
			super.visitEnum(name,
			                descriptor,
			                value);
		}

		@Override
		public AnnotationVisitor visitAnnotation(final String name,
		                                         final String descriptor) {
			final ASMClassAnnotationInfo annotationInfo = new ASMClassAnnotationInfo(descriptor);

			this.arrayInfo.add(annotationInfo);

			return annotationInfo.of(super.visitAnnotation(name,
			                                               descriptor));
		}

		@Override
		public AnnotationVisitor visitArray(final String name) {
			ArrayInfo arrayInfo = new ArrayInfo();

			this.arrayInfo.add(arrayInfo);

			return arrayInfo.of(super.visitArray(name));
		}
	}

	class ClassScanVisitor
			extends ClassVisitor {
		final PrintWriter printWriter;
		final InbodyNotifier p;

		public ClassScanVisitor(final ClassVisitor classVisitor,
		                        final PrintWriter printWriter) {
			this(classVisitor,
			     new InbodyNotifier(),
			     printWriter);
		}

		public ClassScanVisitor(final ClassVisitor classVisitor,
		                        final InbodyNotifier printer,
		                        final PrintWriter printWriter) {
			super(Opcodes.ASM9,
			      classVisitor);
			this.printWriter = printWriter;
			this.p = printer;
		}

		@Override
		public void visit(final int version,
		                  final int access,
		                  final String name,
		                  final String signature,
		                  final String superName,
		                  final String[] interfaces) {
			this.p.visit(version,
			             access,
			             name,
			             signature,
			             superName,
			             interfaces);
			super.visit(version,
			            access,
			            name,
			            signature,
			            superName,
			            interfaces);
		}

		@Override
		public void visitSource(final String file,
		                        final String debug) {
			this.p.visitSource(file,
			                   debug);
			super.visitSource(file,
			                  debug);
		}

		@Override
		public ModuleVisitor visitModule(final String name,
		                                 final int flags,
		                                 final String version) {
			InbodyNotifier modulePrinter = this.p.visitModule(name,
			                                                  flags,
			                                                  version);
			return new ModuleScanVisitor(super.visitModule(name,
			                                               flags,
			                                               version),
			                             modulePrinter);
		}

		@Override
		public void visitNestHost(final String nestHost) {
			this.p.visitNestHost(nestHost);
			super.visitNestHost(nestHost);
		}

		@Override
		public void visitOuterClass(final String owner,
		                            final String name,
		                            final String descriptor) {
			this.p.visitOuterClass(owner,
			                       name,
			                       descriptor);
			super.visitOuterClass(owner,
			                      name,
			                      descriptor);
		}

		@Override
		public AnnotationVisitor visitAnnotation(final String descriptor,
		                                         final boolean visible) {
			InbodyNotifier annotationPrinter = this.p.visitClassAnnotation(descriptor,
			                                                               visible);
			return new AnnotationScanVisitor(super.visitAnnotation(descriptor,
			                                                       visible),
			                                 annotationPrinter);
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(final int typeRef,
		                                             final TypePath typePath,
		                                             final String descriptor,
		                                             final boolean visible) {
			InbodyNotifier annotationPrinter = this.p.visitClassTypeAnnotation(typeRef,
			                                                                   typePath,
			                                                                   descriptor,
			                                                                   visible);
			return new AnnotationScanVisitor(super.visitTypeAnnotation(typeRef,
			                                                           typePath,
			                                                           descriptor,
			                                                           visible),
			                                 annotationPrinter);
		}

		@Override
		public void visitAttribute(final Attribute attribute) {
			this.p.visitClassAttribute(attribute);
			super.visitAttribute(attribute);
		}

		@Override
		public void visitNestMember(final String nestMember) {
			this.p.visitNestMember(nestMember);
			super.visitNestMember(nestMember);
		}

		@Override
		public void visitPermittedSubclass(final String permittedSubclass) {
			this.p.visitPermittedSubclass(permittedSubclass);
			super.visitPermittedSubclass(permittedSubclass);
		}

		@Override
		public void visitInnerClass(final String name,
		                            final String outerName,
		                            final String innerName,
		                            final int access) {
			this.p.visitInnerClass(name,
			                       outerName,
			                       innerName,
			                       access);
			super.visitInnerClass(name,
			                      outerName,
			                      innerName,
			                      access);
		}

		@Override
		public RecordComponentVisitor visitRecordComponent(final String name,
		                                                   final String descriptor,
		                                                   final String signature) {
			InbodyNotifier recordComponentPrinter = this.p.visitRecordComponent(name,
			                                                                    descriptor,
			                                                                    signature);
			return new RecordComponentScanVisitor(super.visitRecordComponent(name,
			                                                                 descriptor,
			                                                                 signature),
			                                      recordComponentPrinter);
		}

		@Override
		public FieldVisitor visitField(final int access,
		                               final String name,
		                               final String descriptor,
		                               final String signature,
		                               final Object value) {
			InbodyNotifier fieldPrinter = this.p.visitField(access,
			                                                name,
			                                                descriptor,
			                                                signature,
			                                                value);
			return new FieldScanVisitor(super.visitField(access,
			                                             name,
			                                             descriptor,
			                                             signature,
			                                             value),
			                            fieldPrinter);
		}

		@Override
		public MethodVisitor visitMethod(final int access,
		                                 final String name,
		                                 final String descriptor,
		                                 final String signature,
		                                 final String[] exceptions) {
			InbodyNotifier methodPrinter = this.p.visitMethod(access,
			                                                  name,
			                                                  descriptor,
			                                                  signature,
			                                                  exceptions);
			return new MethodScanVisitor(super.visitMethod(access,
			                                               name,
			                                               descriptor,
			                                               signature,
			                                               exceptions),
			                             methodPrinter);
		}

		@Override
		public void visitEnd() {
			this.p.visitClassEnd();
			if (this.printWriter != null) {
				this.p.print(this.printWriter);
				this.printWriter.flush();
			}
			super.visitEnd();
		}
	}

	class MethodScanVisitor
			extends MethodVisitor {

		/**
		 * The printer to convert the visited method into text.
		 */
		// DontCheck(MemberName): can't be renamed (for backward binary compatibility).
		public final InbodyNotifier p;

		/**
		 * Constructs a new {@link org.objectweb.asm.util.TraceMethodVisitor}.
		 *
		 * @param methodVisitor the method visitor to which to delegate calls. May be {@literal null}.
		 * @param printer       the printer to convert the visited method into text.
		 */
		public MethodScanVisitor(final MethodVisitor methodVisitor,
		                         final InbodyNotifier printer) {
			super(/* latest api = */ Opcodes.ASM9,
			                         methodVisitor);
			this.p = printer;
		}

		@Override
		public void visitParameter(final String name,
		                           final int access) {
			this.p.visitParameter(name,
			                      access);
			super.visitParameter(name,
			                     access);
		}

		@Override
		public AnnotationVisitor visitAnnotation(final String descriptor,
		                                         final boolean visible) {
			InbodyNotifier annotationPrinter = this.p.visitMethodAnnotation(descriptor,
			                                                                visible);
			return new AnnotationScanVisitor(super.visitAnnotation(descriptor,
			                                                       visible),
			                                 annotationPrinter);
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(final int typeRef,
		                                             final TypePath typePath,
		                                             final String descriptor,
		                                             final boolean visible) {
			InbodyNotifier annotationPrinter = this.p.visitMethodTypeAnnotation(typeRef,
			                                                                    typePath,
			                                                                    descriptor,
			                                                                    visible);
			return new AnnotationScanVisitor(super.visitTypeAnnotation(typeRef,
			                                                           typePath,
			                                                           descriptor,
			                                                           visible),
			                                 annotationPrinter);
		}

		@Override
		public void visitAttribute(final Attribute attribute) {
			this.p.visitMethodAttribute(attribute);
			super.visitAttribute(attribute);
		}

		@Override
		public AnnotationVisitor visitAnnotationDefault() {
			InbodyNotifier annotationPrinter = this.p.visitAnnotationDefault();
			return new AnnotationScanVisitor(super.visitAnnotationDefault(),
			                                 annotationPrinter);
		}

		@Override
		public void visitAnnotableParameterCount(final int parameterCount,
		                                         final boolean visible) {
			this.p.visitAnnotableParameterCount(parameterCount,
			                                    visible);
			super.visitAnnotableParameterCount(parameterCount,
			                                   visible);
		}

		@Override
		public AnnotationVisitor visitParameterAnnotation(final int parameter,
		                                                  final String descriptor,
		                                                  final boolean visible) {
			InbodyNotifier annotationPrinter = this.p.visitParameterAnnotation(parameter,
			                                                                   descriptor,
			                                                                   visible);
			return new AnnotationScanVisitor(super.visitParameterAnnotation(parameter,
			                                                                descriptor,
			                                                                visible),
			                                 annotationPrinter);
		}

		@Override
		public void visitCode() {
			this.p.visitCode();
			super.visitCode();
		}

		@Override
		public void visitFrame(final int type,
		                       final int numLocal,
		                       final Object[] local,
		                       final int numStack,
		                       final Object[] stack) {
			this.p.visitFrame(type,
			                  numLocal,
			                  local,
			                  numStack,
			                  stack);
			super.visitFrame(type,
			                 numLocal,
			                 local,
			                 numStack,
			                 stack);
		}

		@Override
		public void visitInsn(final int opcode) {
			this.p.visitInsn(opcode);
			super.visitInsn(opcode);
		}

		@Override
		public void visitIntInsn(final int opcode,
		                         final int operand) {
			this.p.visitIntInsn(opcode,
			                    operand);
			super.visitIntInsn(opcode,
			                   operand);
		}

		@Override
		public void visitVarInsn(final int opcode,
		                         final int varIndex) {
			this.p.visitVarInsn(opcode,
			                    varIndex);
			super.visitVarInsn(opcode,
			                   varIndex);
		}

		@Override
		public void visitTypeInsn(final int opcode,
		                          final String type) {
			this.p.visitTypeInsn(opcode,
			                     type);
			super.visitTypeInsn(opcode,
			                    type);
		}

		@Override
		public void visitFieldInsn(final int opcode,
		                           final String owner,
		                           final String name,
		                           final String descriptor) {
			this.p.visitFieldInsn(opcode,
			                      owner,
			                      name,
			                      descriptor);
			super.visitFieldInsn(opcode,
			                     owner,
			                     name,
			                     descriptor);
		}

		@Override
		public void visitMethodInsn(final int opcode,
		                            final String owner,
		                            final String name,
		                            final String descriptor,
		                            final boolean isInterface) {
			this.p.visitMethodInsn(opcode,
			                       owner,
			                       name,
			                       descriptor,
			                       isInterface);
			if (this.mv != null) {
				this.mv.visitMethodInsn(opcode,
				                        owner,
				                        name,
				                        descriptor,
				                        isInterface);
			}
		}

		@Override
		public void visitInvokeDynamicInsn(final String name,
		                                   final String descriptor,
		                                   final Handle bootstrapMethodHandle,
		                                   final Object... bootstrapMethodArguments) {
			this.p.visitInvokeDynamicInsn(name,
			                              descriptor,
			                              bootstrapMethodHandle,
			                              bootstrapMethodArguments);
			super.visitInvokeDynamicInsn(name,
			                             descriptor,
			                             bootstrapMethodHandle,
			                             bootstrapMethodArguments);
		}

		@Override
		public void visitJumpInsn(final int opcode,
		                          final Label label) {
			this.p.visitJumpInsn(opcode,
			                     label);
			super.visitJumpInsn(opcode,
			                    label);
		}

		@Override
		public void visitLabel(final Label label) {
			this.p.visitLabel(label);
			super.visitLabel(label);
		}

		@Override
		public void visitLdcInsn(final Object value) {
			this.p.visitLdcInsn(value);
			super.visitLdcInsn(value);
		}

		@Override
		public void visitIincInsn(final int varIndex,
		                          final int increment) {
			this.p.visitIincInsn(varIndex,
			                     increment);
			super.visitIincInsn(varIndex,
			                    increment);
		}

		@Override
		public void visitTableSwitchInsn(final int min,
		                                 final int max,
		                                 final Label dflt,
		                                 final Label... labels) {
			this.p.visitTableSwitchInsn(min,
			                            max,
			                            dflt,
			                            labels);
			super.visitTableSwitchInsn(min,
			                           max,
			                           dflt,
			                           labels);
		}

		@Override
		public void visitLookupSwitchInsn(final Label dflt,
		                                  final int[] keys,
		                                  final Label[] labels) {
			this.p.visitLookupSwitchInsn(dflt,
			                             keys,
			                             labels);
			super.visitLookupSwitchInsn(dflt,
			                            keys,
			                            labels);
		}

		@Override
		public void visitMultiANewArrayInsn(final String descriptor,
		                                    final int numDimensions) {
			this.p.visitMultiANewArrayInsn(descriptor,
			                               numDimensions);
			super.visitMultiANewArrayInsn(descriptor,
			                              numDimensions);
		}

		@Override
		public AnnotationVisitor visitInsnAnnotation(final int typeRef,
		                                             final TypePath typePath,
		                                             final String descriptor,
		                                             final boolean visible) {
			InbodyNotifier annotationPrinter = this.p.visitInsnAnnotation(typeRef,
			                                                              typePath,
			                                                              descriptor,
			                                                              visible);
			return new AnnotationScanVisitor(super.visitInsnAnnotation(typeRef,
			                                                           typePath,
			                                                           descriptor,
			                                                           visible),
			                                 annotationPrinter);
		}

		@Override
		public void visitTryCatchBlock(final Label start,
		                               final Label end,
		                               final Label handler,
		                               final String type) {
			this.p.visitTryCatchBlock(start,
			                          end,
			                          handler,
			                          type);
			super.visitTryCatchBlock(start,
			                         end,
			                         handler,
			                         type);
		}

		@Override
		public AnnotationVisitor visitTryCatchAnnotation(final int typeRef,
		                                                 final TypePath typePath,
		                                                 final String descriptor,
		                                                 final boolean visible) {
			InbodyNotifier annotationPrinter = this.p.visitTryCatchAnnotation(typeRef,
			                                                                  typePath,
			                                                                  descriptor,
			                                                                  visible);
			return new AnnotationScanVisitor(super.visitTryCatchAnnotation(typeRef,
			                                                               typePath,
			                                                               descriptor,
			                                                               visible),
			                                 annotationPrinter);
		}

		@Override
		public void visitLocalVariable(final String name,
		                               final String descriptor,
		                               final String signature,
		                               final Label start,
		                               final Label end,
		                               final int index) {
			this.p.visitLocalVariable(name,
			                          descriptor,
			                          signature,
			                          start,
			                          end,
			                          index);
			super.visitLocalVariable(name,
			                         descriptor,
			                         signature,
			                         start,
			                         end,
			                         index);
		}

		@Override
		public AnnotationVisitor visitLocalVariableAnnotation(final int typeRef,
		                                                      final TypePath typePath,
		                                                      final Label[] start,
		                                                      final Label[] end,
		                                                      final int[] index,
		                                                      final String descriptor,
		                                                      final boolean visible) {
			InbodyNotifier annotationPrinter = this.p.visitLocalVariableAnnotation(typeRef,
			                                                                       typePath,
			                                                                       start,
			                                                                       end,
			                                                                       index,
			                                                                       descriptor,
			                                                                       visible);
			return new AnnotationScanVisitor(super.visitLocalVariableAnnotation(typeRef,
			                                                                    typePath,
			                                                                    start,
			                                                                    end,
			                                                                    index,
			                                                                    descriptor,
			                                                                    visible),
			                                 annotationPrinter);
		}

		@Override
		public void visitLineNumber(final int line,
		                            final Label start) {
			this.p.visitLineNumber(line,
			                       start);
			super.visitLineNumber(line,
			                      start);
		}

		@Override
		public void visitMaxs(final int maxStack,
		                      final int maxLocals) {
			this.p.visitMaxs(maxStack,
			                 maxLocals);
			super.visitMaxs(maxStack,
			                maxLocals);
		}

		@Override
		public void visitEnd() {
			this.p.visitMethodEnd();
			super.visitEnd();
		}
	}

	class FieldScanVisitor
			extends FieldVisitor {

		/**
		 * The printer to convert the visited field into text.
		 */
		// DontCheck(MemberName): can't be renamed (for backward binary compatibility).
		public final InbodyNotifier p;


		/**
		 * Constructs a new {@link org.objectweb.asm.util.TraceFieldVisitor}.
		 *
		 * @param fieldVisitor the field visitor to which to delegate calls. May be {@literal null}.
		 * @param printer      the printer to convert the visited field into text.
		 */
		public FieldScanVisitor(final FieldVisitor fieldVisitor,
		                        final InbodyNotifier printer) {
			super(/* latest api = */ Opcodes.ASM9,
			                         fieldVisitor);
			this.p = printer;
		}

		@Override
		public AnnotationVisitor visitAnnotation(final String descriptor,
		                                         final boolean visible) {
			InbodyNotifier annotationPrinter = this.p.visitFieldAnnotation(descriptor,
			                                                               visible);
			return new AnnotationScanVisitor(super.visitAnnotation(descriptor,
			                                                       visible),
			                                 annotationPrinter);
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(final int typeRef,
		                                             final TypePath typePath,
		                                             final String descriptor,
		                                             final boolean visible) {
			InbodyNotifier annotationPrinter = this.p.visitFieldTypeAnnotation(typeRef,
			                                                                   typePath,
			                                                                   descriptor,
			                                                                   visible);
			return new AnnotationScanVisitor(super.visitTypeAnnotation(typeRef,
			                                                           typePath,
			                                                           descriptor,
			                                                           visible),
			                                 annotationPrinter);
		}

		@Override
		public void visitAttribute(final Attribute attribute) {
			this.p.visitFieldAttribute(attribute);
			super.visitAttribute(attribute);
		}

		@Override
		public void visitEnd() {
			this.p.visitFieldEnd();
			super.visitEnd();
		}
	}

	class RecordComponentScanVisitor
			extends RecordComponentVisitor {

		/**
		 * The printer to convert the visited record portable into text.
		 */
		public final InbodyNotifier printer;

		/**
		 * Constructs a new {@link org.objectweb.asm.util.TraceRecordComponentVisitor}.
		 *
		 * @param recordComponentVisitor the record portable visitor to which to delegate calls. May be
		 *                               {@literal null}.
		 * @param printer                the printer to convert the visited record portable into text.
		 */
		public RecordComponentScanVisitor(final RecordComponentVisitor recordComponentVisitor,
		                                  final InbodyNotifier printer) {
			super(/* latest api ='*/ Opcodes.ASM9,
			                         recordComponentVisitor);
			this.printer = printer;
		}

		@Override
		public AnnotationVisitor visitAnnotation(final String descriptor,
		                                         final boolean visible) {
			InbodyNotifier annotationPrinter = this.printer.visitRecordComponentAnnotation(descriptor,
			                                                                               visible);
			return new AnnotationScanVisitor(super.visitAnnotation(descriptor,
			                                                       visible),
			                                 annotationPrinter);
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(final int typeRef,
		                                             final TypePath typePath,
		                                             final String descriptor,
		                                             final boolean visible) {
			InbodyNotifier annotationPrinter = this.printer.visitRecordComponentTypeAnnotation(typeRef,
			                                                                                   typePath,
			                                                                                   descriptor,
			                                                                                   visible);
			return new AnnotationScanVisitor(super.visitTypeAnnotation(typeRef,
			                                                           typePath,
			                                                           descriptor,
			                                                           visible),
			                                 annotationPrinter);
		}

		@Override
		public void visitAttribute(final Attribute attribute) {
			this.printer.visitRecordComponentAttribute(attribute);
			super.visitAttribute(attribute);
		}

		@Override
		public void visitEnd() {
			this.printer.visitRecordComponentEnd();
			super.visitEnd();
		}
	}

	class AnnotationScanVisitor
			extends AnnotationVisitor {

		private final InbodyNotifier printer;

		/**
		 * Constructs a new {@link org.objectweb.asm.util.TraceAnnotationVisitor}.
		 *
		 * @param annotationVisitor the annotation visitor to which to delegate calls. May be {@literal
		 *                          null}.
		 * @param printer           the printer to convert the visited annotation into text.
		 */
		public AnnotationScanVisitor(final AnnotationVisitor annotationVisitor,
		                             final InbodyNotifier printer) {
			super(/* latest api = */ Opcodes.ASM9,
			                         annotationVisitor);
			this.printer = printer;
		}

		@Override
		public void visit(final String name,
		                  final Object value) {
			this.printer.visit(name,
			                   value);
			super.visit(name,
			            value);
		}

		@Override
		public void visitEnum(final String name,
		                      final String descriptor,
		                      final String value) {
			this.printer.visitEnum(name,
			                       descriptor,
			                       value);
			super.visitEnum(name,
			                descriptor,
			                value);
		}

		@Override
		public AnnotationVisitor visitAnnotation(final String name,
		                                         final String descriptor) {
			InbodyNotifier annotationPrinter = this.printer.visitAnnotation(name,
			                                                                descriptor);
			return new AnnotationScanVisitor(super.visitAnnotation(name,
			                                                       descriptor),
			                                 annotationPrinter);
		}

		@Override
		public AnnotationVisitor visitArray(final String name) {
			InbodyNotifier arrayPrinter = this.printer.visitArray(name);
			return new AnnotationScanVisitor(super.visitArray(name),
			                                 arrayPrinter);
		}

		@Override
		public void visitEnd() {
			this.printer.visitAnnotationEnd();
			super.visitEnd();
		}
	}

	class ModuleScanVisitor
			extends ModuleVisitor {

		/**
		 * The printer to convert the visited module into text.
		 */
		// DontCheck(MemberName): can't be renamed (for backward binary compatibility).
		public final InbodyNotifier p;

		/**
		 * Constructs a new {@link org.objectweb.asm.util.TraceModuleVisitor}.
		 *
		 * @param moduleVisitor the module visitor to which to delegate calls. May be {@literal null}.
		 * @param printer       the printer to convert the visited module into text.
		 */
		public ModuleScanVisitor(final ModuleVisitor moduleVisitor,
		                         final InbodyNotifier printer) {
			super(/* latest api = */ Opcodes.ASM9,
			                         moduleVisitor);
			this.p = printer;
		}

		@Override
		public void visitMainClass(final String mainClass) {
			this.p.visitMainClass(mainClass);
			super.visitMainClass(mainClass);
		}

		@Override
		public void visitPackage(final String packaze) {
			this.p.visitPackage(packaze);
			super.visitPackage(packaze);
		}

		@Override
		public void visitRequire(final String module,
		                         final int access,
		                         final String version) {
			this.p.visitRequire(module,
			                    access,
			                    version);
			super.visitRequire(module,
			                   access,
			                   version);
		}

		@Override
		public void visitExport(final String packaze,
		                        final int access,
		                        final String... modules) {
			this.p.visitExport(packaze,
			                   access,
			                   modules);
			super.visitExport(packaze,
			                  access,
			                  modules);
		}

		@Override
		public void visitOpen(final String packaze,
		                      final int access,
		                      final String... modules) {
			this.p.visitOpen(packaze,
			                 access,
			                 modules);
			super.visitOpen(packaze,
			                access,
			                modules);
		}

		@Override
		public void visitUse(final String use) {
			this.p.visitUse(use);
			super.visitUse(use);
		}

		@Override
		public void visitProvide(final String service,
		                         final String... providers) {
			this.p.visitProvide(service,
			                    providers);
			super.visitProvide(service,
			                   providers);
		}

		@Override
		public void visitEnd() {
			this.p.visitModuleEnd();
			super.visitEnd();
		}
	}

	class SignatureScanVisitor
			extends SignatureVisitor {

		private static final String COMMA_SEPARATOR = ", ";
		private static final String EXTENDS_SEPARATOR = " extends ";
		private static final String IMPLEMENTS_SEPARATOR = " implements ";

		private static final Map<Character, String> BASE_TYPES;

		static {
			BASE_TYPES = CollectionSpec.mapBuilder(Character.class,
			                                       String.class)
			                           .put('Z',
			                                "boolean")
			                           .put('B',
			                                "byte")
			                           .put('C',
			                                "char")
			                           .put('S',
			                                "short")
			                           .put('I',
			                                "int")
			                           .put('J',
			                                "long")
			                           .put('F',
			                                "float")
			                           .put('D',
			                                "double")
			                           .put('V',
			                                "void")
			                           .build();
		}

		/**
		 * Whether the visited signature is a class signature of a Java interface.
		 */
		private final boolean isInterface;

		/**
		 * The Java generic type declaration corresponding to the visited signature.
		 */
		private final StringBuilder declaration;

		/**
		 * The Java generic method return type declaration corresponding to the visited signature.
		 */
		private StringBuilder returnType;

		/**
		 * The Java generic exception types declaration corresponding to the visited signature.
		 */
		private StringBuilder exceptions;

		/**
		 * Whether {@link #visitFormalTypeParameter} has been called.
		 */
		private boolean formalTypeParameterVisited;

		/**
		 * Whether {@link #visitInterfaceBound} has been called.
		 */
		private boolean interfaceBoundVisited;

		/**
		 * Whether {@link #visitParameterType} has been called.
		 */
		private boolean parameterTypeVisited;

		/**
		 * Whether {@link #visitInterface} has been called.
		 */
		private boolean interfaceVisited;

		/**
		 * The stack used to keep track of class types that have arguments. Each element of this stack is
		 * a boolean encoded in one bit. The top of the stack is the least significant bit. Pushing false
		 * = *2, pushing true = *2+1, popping = /2.
		 */
		private int argumentStack;

		/**
		 * The stack used to keep track of array class types. Each element of this stack is a boolean
		 * encoded in one bit. The top of the stack is the lowest order bit. Pushing false = *2, pushing
		 * true = *2+1, popping = /2.
		 */
		private int arrayStack;

		/**
		 * The separator to append before the next visited class or inner class type.
		 */
		private String separator = "";

		/**
		 * Constructs a new visitor
		 *
		 * @param accessFlags for class type signatures, the access flags of the class.
		 */
		public SignatureScanVisitor(final int accessFlags) {
			super(/* latest api = */ Opcodes.ASM9);
			this.isInterface = (accessFlags & Opcodes.ACC_INTERFACE) != 0;
			this.declaration = new StringBuilder();
		}

		private SignatureScanVisitor(final StringBuilder stringBuilder) {
			super(/* latest api = */ Opcodes.ASM9);
			this.isInterface = false;
			this.declaration = stringBuilder;
		}

		@Override
		public void visitFormalTypeParameter(final String name) {
			this.declaration.append(this.formalTypeParameterVisited
			                        ? COMMA_SEPARATOR
			                        : "<")
			                .append(name);
			this.formalTypeParameterVisited = true;
			this.interfaceBoundVisited = false;
		}

		@Override
		public SignatureScanVisitor visitClassBound() {
			this.separator = EXTENDS_SEPARATOR;
			this.startType();
			return this;
		}

		@Override
		public SignatureScanVisitor visitInterfaceBound() {
			this.separator = this.interfaceBoundVisited
			                 ? COMMA_SEPARATOR
			                 : EXTENDS_SEPARATOR;
			this.interfaceBoundVisited = true;
			this.startType();
			return this;
		}

		@Override
		public SignatureScanVisitor visitSuperclass() {
			this.endFormals();
			this.separator = EXTENDS_SEPARATOR;
			this.startType();
			return this;
		}

		@Override
		public SignatureScanVisitor visitInterface() {
			if (this.interfaceVisited) {
				this.separator = COMMA_SEPARATOR;
			} else {
				this.separator = this.isInterface
				                 ? EXTENDS_SEPARATOR
				                 : IMPLEMENTS_SEPARATOR;
				this.interfaceVisited = true;
			}
			this.startType();
			return this;
		}

		@Override
		public SignatureScanVisitor visitParameterType() {
			this.endFormals();
			if (this.parameterTypeVisited) {
				this.declaration.append(COMMA_SEPARATOR);
			} else {
				this.declaration.append('(');
				this.parameterTypeVisited = true;
			}
			this.startType();
			return this;
		}

		@Override
		public SignatureScanVisitor visitReturnType() {
			this.endFormals();
			if (this.parameterTypeVisited) {
				this.parameterTypeVisited = false;
			} else {
				this.declaration.append('(');
			}
			this.declaration.append(')');
			this.returnType = new StringBuilder();
			return new SignatureScanVisitor(this.returnType);
		}

		@Override
		public SignatureScanVisitor visitExceptionType() {
			if (this.exceptions == null) {
				this.exceptions = new StringBuilder();
			} else {
				this.exceptions.append(COMMA_SEPARATOR);
			}
			return new SignatureScanVisitor(this.exceptions);
		}

		@Override
		public void visitBaseType(final char descriptor) {
			String baseType = BASE_TYPES.get(descriptor);
			if (baseType == null) {
				throw new IllegalArgumentException();
			}
			this.declaration.append(baseType);
			this.endType();
		}

		@Override
		public void visitTypeVariable(final String name) {
			this.declaration.append(this.separator)
			                .append(name);
			this.separator = "";
			this.endType();
		}

		@Override
		public SignatureScanVisitor visitArrayType() {
			this.startType();
			this.arrayStack |= 1;
			return this;
		}

		@Override
		public void visitClassType(final String name) {
			if ("java/lang/Object".equals(name)) {
				// 'Map<java.lang.Object,java.util.List>' or 'abstract public V get(Object key);' should have
				// Object 'but java.lang.String extends java.lang.Object' is unnecessary.
				boolean needObjectClass = this.argumentStack % 2 != 0 || this.parameterTypeVisited;
				if (needObjectClass) {
					this.declaration.append(this.separator)
					                .append(name.replace('/',
					                                     '.'));
				}
			} else {
				this.declaration.append(this.separator)
				                .append(name.replace('/',
				                                     '.'));
			}
			this.separator = "";
			this.argumentStack *= 2;
		}

		@Override
		public void visitInnerClassType(final String name) {
			if (this.argumentStack % 2 != 0) {
				this.declaration.append('>');
			}
			this.argumentStack /= 2;
			this.declaration.append('.');
			this.declaration.append(this.separator)
			                .append(name.replace('/',
			                                     '.'));
			this.separator = "";
			this.argumentStack *= 2;
		}

		@Override
		public void visitTypeArgument() {
			if (this.argumentStack % 2 == 0) {
				++this.argumentStack;
				this.declaration.append('<');
			} else {
				this.declaration.append(COMMA_SEPARATOR);
			}
			this.declaration.append('?');
		}

		@Override
		public SignatureScanVisitor visitTypeArgument(final char tag) {
			if (this.argumentStack % 2 == 0) {
				++this.argumentStack;
				this.declaration.append('<');
			} else {
				this.declaration.append(COMMA_SEPARATOR);
			}

			if (tag == EXTENDS) {
				this.declaration.append("? extends ");
			} else if (tag == SUPER) {
				this.declaration.append("? super ");
			}

			this.startType();
			return this;
		}

		@Override
		public void visitEnd() {
			if (this.argumentStack % 2 != 0) {
				this.declaration.append('>');
			}
			this.argumentStack /= 2;
			this.endType();
		}

		// -----------------------------------------------------------------------------------------------

		/**
		 * Returns the Java generic type declaration corresponding to the visited signature.
		 *
		 * @return the Java generic type declaration corresponding to the visited signature.
		 */
		public String getDeclaration() {
			return this.declaration.toString();
		}

		/**
		 * Returns the Java generic method return type declaration corresponding to the visited signature.
		 *
		 * @return the Java generic method return type declaration corresponding to the visited signature.
		 */
		public String getReturnType() {
			return this.returnType == null
			       ? null
			       : this.returnType.toString();
		}

		/**
		 * Returns the Java generic exception types declaration corresponding to the visited signature.
		 *
		 * @return the Java generic exception types declaration corresponding to the visited signature.
		 */
		public String getExceptions() {
			return this.exceptions == null
			       ? null
			       : this.exceptions.toString();
		}

		// -----------------------------------------------------------------------------------------------

		private void endFormals() {
			if (this.formalTypeParameterVisited) {
				this.declaration.append('>');
				this.formalTypeParameterVisited = false;
			}
		}

		private void startType() {
			this.arrayStack *= 2;
		}

		private void endType() {
			if (this.arrayStack % 2 == 0) {
				this.arrayStack /= 2;
			} else {
				while (this.arrayStack % 2 != 0) {
					this.arrayStack /= 2;
					this.declaration.append("[]");
				}
			}
		}
	}

	class InbodyNotifier
			extends Printer {

		/**
		 * The type of internal names (see {@link Type#getInternalName()}). See {@link #appendDescriptor}.
		 */
		public static final int INTERNAL_NAME = 0;
		/**
		 * The type of field descriptors. See {@link #appendDescriptor}.
		 */
		public static final int FIELD_DESCRIPTOR = 1;
		/**
		 * The type of field signatures. See {@link #appendDescriptor}.
		 */
		public static final int FIELD_SIGNATURE = 2;
		/**
		 * The type of method descriptors. See {@link #appendDescriptor}.
		 */
		public static final int METHOD_DESCRIPTOR = 3;
		/**
		 * The type of method signatures. See {@link #appendDescriptor}.
		 */
		public static final int METHOD_SIGNATURE = 4;
		/**
		 * The type of class signatures. See {@link #appendDescriptor}.
		 */
		public static final int CLASS_SIGNATURE = 5;
		/**
		 * The type of method handle descriptors. See {@link #appendDescriptor}.
		 */
		public static final int HANDLE_DESCRIPTOR = 9;
		/**
		 * The help message shown when command line arguments are incorrect.
		 */
		private static final String CLASS_SUFFIX = ".class";
		private static final String DEPRECATED = "// DEPRECATED\n";
		private static final String RECORD = "// RECORD\n";
		private static final String INVISIBLE = " // invisible\n";

		private static final List<String> FRAME_TYPES = CollectionSpec.listBuilder("T")
		                                                              .add("I")
		                                                              .add("F")
		                                                              .add("D")
		                                                              .add("J")
		                                                              .add("N")
		                                                              .add("U")
		                                                              .build();

		/**
		 * The indentation of class members at depth level 1 (e.g. fields, methods).
		 */
		protected final String tab = "  ";

		/**
		 * The indentation of class elements at depth level 2 (e.g. bytecode instructions in methods).
		 */
		protected final String tab2 = "    ";

		/**
		 * The indentation of class elements at depth level 3 (e.g. switch cases in methods).
		 */
		protected final String tab3 = "      ";

		/**
		 * The indentation of labels.
		 */
		protected final String ltab = "   ";

		/**
		 * The names of the labels.
		 */
		protected Map<Label, String> labelNames;

		/**
		 * The access flags of the visited class.
		 */
		private int access;

		/**
		 * The number of annotation values visited so far.
		 */
		private int numAnnotationValues;

		public InbodyNotifier() {
			super(/* latest api = */ Opcodes.ASM9);
		}

		// -----------------------------------------------------------------------------------------------
		// Classes
		// -----------------------------------------------------------------------------------------------

		@Override
		public void visit(final int version,
		                  final int access,
		                  final String name,
		                  final String signature,
		                  final String superName,
		                  final String[] interfaces) {
			if ((access & Opcodes.ACC_MODULE) != 0) {
				// Modules are printed in visitModule.
				return;
			}
			this.access = access;
			int majorVersion = version & 0xFFFF;
			int minorVersion = version >>> 16;
			this.stringBuilder.setLength(0);
			this.stringBuilder.append("// class version ")
			                  .append(majorVersion)
			                  .append('.')
			                  .append(minorVersion)
			                  .append(" (")
			                  .append(version)
			                  .append(")\n");
			if ((access & Opcodes.ACC_DEPRECATED) != 0) {
				this.stringBuilder.append(DEPRECATED);
			}
			if ((access & Opcodes.ACC_RECORD) != 0) {
				this.stringBuilder.append(RECORD);
			}
			this.appendRawAccess(access);

			this.appendDescriptor(CLASS_SIGNATURE,
			                      signature);
			if (signature != null) {
				this.appendJavaDeclaration(name,
				                           signature);
			}

			this.appendAccess(access & ~(Opcodes.ACC_SUPER | Opcodes.ACC_MODULE));
			if ((access & Opcodes.ACC_ANNOTATION) != 0) {
				this.stringBuilder.append("@interface ");
			} else if ((access & Opcodes.ACC_INTERFACE) != 0) {
				this.stringBuilder.append("interface ");
			} else if ((access & Opcodes.ACC_ENUM) == 0) {
				this.stringBuilder.append("class ");
			}
			this.appendDescriptor(INTERNAL_NAME,
			                      name);

			if (superName != null && !"java/lang/Object".equals(superName)) {
				this.stringBuilder.append(" extends ");
				this.appendDescriptor(INTERNAL_NAME,
				                      superName);
			}
			if (interfaces != null && interfaces.length > 0) {
				this.stringBuilder.append(" implements ");
				for (int i = 0; i < interfaces.length; ++i) {
					this.appendDescriptor(INTERNAL_NAME,
					                      interfaces[i]);
					if (i != interfaces.length - 1) {
						this.stringBuilder.append(' ');
					}
				}
			}
			this.stringBuilder.append(" {\n\n");

			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitSource(final String file,
		                        final String debug) {
			this.stringBuilder.setLength(0);
			if (file != null) {
				this.stringBuilder.append(this.tab)
				                  .append("// compiled from: ")
				                  .append(file)
				                  .append('\n');
			}
			if (debug != null) {
				this.stringBuilder.append(this.tab)
				                  .append("// debug info: ")
				                  .append(debug)
				                  .append('\n');
			}
			if (this.stringBuilder.length() > 0) {
				this.text.add(this.stringBuilder.toString());
			}
		}

		@Override
		public InbodyNotifier visitModule(final String name,
		                                  final int access,
		                                  final String version) {
			this.stringBuilder.setLength(0);
			if ((access & Opcodes.ACC_OPEN) != 0) {
				this.stringBuilder.append("open ");
			}
			this.stringBuilder.append("module ")
			                  .append(name)
			                  .append(" { ")
			                  .append(version == null
			                          ? ""
			                          : "// " + version)
			                  .append("\n\n");
			this.text.add(this.stringBuilder.toString());
			return this.addNewInbodyNotifier(null);
		}

		@Override
		public void visitNestHost(final String nestHost) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab)
			                  .append("NESTHOST ");
			this.appendDescriptor(INTERNAL_NAME,
			                      nestHost);
			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitOuterClass(final String owner,
		                            final String name,
		                            final String descriptor) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab)
			                  .append("OUTERCLASS ");
			this.appendDescriptor(INTERNAL_NAME,
			                      owner);
			this.stringBuilder.append(' ');
			if (name != null) {
				this.stringBuilder.append(name)
				                  .append(' ');
			}
			this.appendDescriptor(METHOD_DESCRIPTOR,
			                      descriptor);
			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public InbodyNotifier visitClassAnnotation(final String descriptor,
		                                           final boolean visible) {
			this.text.add("\n");
			return this.visitAnnotation(descriptor,
			                            visible);
		}

		@Override
		public InbodyNotifier visitClassTypeAnnotation(final int typeRef,
		                                               final TypePath typePath,
		                                               final String descriptor,
		                                               final boolean visible) {
			this.text.add("\n");
			return this.visitTypeAnnotation(typeRef,
			                                typePath,
			                                descriptor,
			                                visible);
		}

		@Override
		public void visitClassAttribute(final Attribute attribute) {
			this.text.add("\n");
			this.visitAttribute(attribute);
		}

		@Override
		public void visitNestMember(final String nestMember) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab)
			                  .append("NESTMEMBER ");
			this.appendDescriptor(INTERNAL_NAME,
			                      nestMember);
			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitPermittedSubclass(final String permittedSubclass) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab)
			                  .append("PERMITTEDSUBCLASS ");
			this.appendDescriptor(INTERNAL_NAME,
			                      permittedSubclass);
			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitInnerClass(final String name,
		                            final String outerName,
		                            final String innerName,
		                            final int access) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab);
			this.appendRawAccess(access & ~Opcodes.ACC_SUPER);
			this.stringBuilder.append(this.tab);
			this.appendAccess(access);
			this.stringBuilder.append("INNERCLASS ");
			this.appendDescriptor(INTERNAL_NAME,
			                      name);
			this.stringBuilder.append(' ');
			this.appendDescriptor(INTERNAL_NAME,
			                      outerName);
			this.stringBuilder.append(' ');
			this.appendDescriptor(INTERNAL_NAME,
			                      innerName);
			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public InbodyNotifier visitRecordComponent(final String name,
		                                           final String descriptor,
		                                           final String signature) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab)
			                  .append("RECORDCOMPONENT ");
			if (signature != null) {
				this.stringBuilder.append(this.tab);
				this.appendDescriptor(FIELD_SIGNATURE,
				                      signature);
				this.stringBuilder.append(this.tab);
				this.appendJavaDeclaration(name,
				                           signature);
			}

			this.stringBuilder.append(this.tab);

			this.appendDescriptor(FIELD_DESCRIPTOR,
			                      descriptor);
			this.stringBuilder.append(' ')
			                  .append(name);

			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
			return this.addNewInbodyNotifier(null);
		}

		@Override
		public InbodyNotifier visitField(final int access,
		                                 final String name,
		                                 final String descriptor,
		                                 final String signature,
		                                 final Object value) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append('\n');
			if ((access & Opcodes.ACC_DEPRECATED) != 0) {
				this.stringBuilder.append(this.tab)
				                  .append(DEPRECATED);
			}
			this.stringBuilder.append(this.tab);
			this.appendRawAccess(access);
			if (signature != null) {
				this.stringBuilder.append(this.tab);
				this.appendDescriptor(FIELD_SIGNATURE,
				                      signature);
				this.stringBuilder.append(this.tab);
				this.appendJavaDeclaration(name,
				                           signature);
			}

			this.stringBuilder.append(this.tab);
			this.appendAccess(access);

			this.appendDescriptor(FIELD_DESCRIPTOR,
			                      descriptor);
			this.stringBuilder.append(' ')
			                  .append(name);
			if (value != null) {
				this.stringBuilder.append(" = ");
				if (value instanceof String) {
					this.stringBuilder.append('\"')
					                  .append(value)
					                  .append('\"');
				} else {
					this.stringBuilder.append(value);
				}
			}

			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
			return this.addNewInbodyNotifier(null);
		}

		@Override
		public InbodyNotifier visitMethod(final int access,
		                                  final String name,
		                                  final String descriptor,
		                                  final String signature,
		                                  final String[] exceptions) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append('\n');
			if ((access & Opcodes.ACC_DEPRECATED) != 0) {
				this.stringBuilder.append(this.tab)
				                  .append(DEPRECATED);
			}
			this.stringBuilder.append(this.tab);
			this.appendRawAccess(access);

			if (signature != null) {
				this.stringBuilder.append(this.tab);
				this.appendDescriptor(METHOD_SIGNATURE,
				                      signature);
				this.stringBuilder.append(this.tab);
				this.appendJavaDeclaration(name,
				                           signature);
			}

			this.stringBuilder.append(this.tab);
			this.appendAccess(access & ~(Opcodes.ACC_VOLATILE | Opcodes.ACC_TRANSIENT));
			if ((access & Opcodes.ACC_NATIVE) != 0) {
				this.stringBuilder.append("native ");
			}
			if ((access & Opcodes.ACC_VARARGS) != 0) {
				this.stringBuilder.append("varargs ");
			}
			if ((access & Opcodes.ACC_BRIDGE) != 0) {
				this.stringBuilder.append("bridge ");
			}
			if ((this.access & Opcodes.ACC_INTERFACE) != 0 && (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC)) == 0) {
				this.stringBuilder.append("default ");
			}

			this.stringBuilder.append(name);
			this.appendDescriptor(METHOD_DESCRIPTOR,
			                      descriptor);
			if (exceptions != null && exceptions.length > 0) {
				this.stringBuilder.append(" throws ");
				for (String exception : exceptions) {
					this.appendDescriptor(INTERNAL_NAME,
					                      exception);
					this.stringBuilder.append(' ');
				}
			}

			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
			return this.addNewInbodyNotifier(null);
		}

		@Override
		public void visitClassEnd() {
			this.text.add("}\n");
		}

		// -----------------------------------------------------------------------------------------------
		// Modules
		// -----------------------------------------------------------------------------------------------

		@Override
		public void visitMainClass(final String mainClass) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append("  // main class ")
			                  .append(mainClass)
			                  .append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitPackage(final String packaze) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append("  // package ")
			                  .append(packaze)
			                  .append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitRequire(final String require,
		                         final int access,
		                         final String version) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab)
			                  .append("requires ");
			if ((access & Opcodes.ACC_TRANSITIVE) != 0) {
				this.stringBuilder.append("transitive ");
			}
			if ((access & Opcodes.ACC_STATIC_PHASE) != 0) {
				this.stringBuilder.append("static ");
			}
			this.stringBuilder.append(require)
			                  .append(';');
			this.appendRawAccess(access);
			if (version != null) {
				this.stringBuilder.append("  // version ")
				                  .append(version)
				                  .append('\n');
			}
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitExport(final String packaze,
		                        final int access,
		                        final String... modules) {
			this.visitExportOrOpen("exports ",
			                       packaze,
			                       access,
			                       modules);
		}

		@Override
		public void visitOpen(final String packaze,
		                      final int access,
		                      final String... modules) {
			this.visitExportOrOpen("opens ",
			                       packaze,
			                       access,
			                       modules);
		}

		private void visitExportOrOpen(final String method,
		                               final String packaze,
		                               final int access,
		                               final String... modules) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab)
			                  .append(method);
			this.stringBuilder.append(packaze);
			if (modules != null && modules.length > 0) {
				this.stringBuilder.append(" to");
			} else {
				this.stringBuilder.append(';');
			}
			this.appendRawAccess(access);
			if (modules != null && modules.length > 0) {
				for (int i = 0; i < modules.length; ++i) {
					this.stringBuilder.append(this.tab2)
					                  .append(modules[i]);
					this.stringBuilder.append(i != modules.length - 1
					                          ? ",\n"
					                          : ";\n");
				}
			}
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitUse(final String use) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab)
			                  .append("uses ");
			this.appendDescriptor(INTERNAL_NAME,
			                      use);
			this.stringBuilder.append(";\n");
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitProvide(final String provide,
		                         final String... providers) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab)
			                  .append("provides ");
			this.appendDescriptor(INTERNAL_NAME,
			                      provide);
			this.stringBuilder.append(" with\n");
			for (int i = 0; i < providers.length; ++i) {
				this.stringBuilder.append(this.tab2);
				this.appendDescriptor(INTERNAL_NAME,
				                      providers[i]);
				this.stringBuilder.append(i != providers.length - 1
				                          ? ",\n"
				                          : ";\n");
			}
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitModuleEnd() {
			// Nothing to do.
		}

		// -----------------------------------------------------------------------------------------------
		// Annotations
		// -----------------------------------------------------------------------------------------------

		// DontCheck(OverloadMethodsDeclarationOrder): overloads are semantically different.
		@Override
		public void visit(final String name,
		                  final Object value) {
			this.visitAnnotationValue(name);
			if (value instanceof String) {
				this.visitString((String) value);
			} else if (value instanceof Type) {
				this.visitType((Type) value);
			} else if (value instanceof Byte) {
				this.visitByte((Byte) value);
			} else if (value instanceof Boolean) {
				this.visitBoolean((Boolean) value);
			} else if (value instanceof Short) {
				this.visitShort((Short) value);
			} else if (value instanceof Character) {
				this.visitChar((Character) value);
			} else if (value instanceof Integer) {
				this.visitInt((Integer) value);
			} else if (value instanceof Float) {
				this.visitFloat((Float) value);
			} else if (value instanceof Long) {
				this.visitLong((Long) value);
			} else if (value instanceof Double) {
				this.visitDouble((Double) value);
			} else if (value.getClass()
			                .isArray()) {
				this.stringBuilder.append('{');
				if (value instanceof byte[]) {
					byte[] byteArray = (byte[]) value;
					for (int i = 0; i < byteArray.length; i++) {
						this.maybeAppendComma(i);
						this.visitByte(byteArray[i]);
					}
				} else if (value instanceof boolean[]) {
					boolean[] booleanArray = (boolean[]) value;
					for (int i = 0; i < booleanArray.length; i++) {
						this.maybeAppendComma(i);
						this.visitBoolean(booleanArray[i]);
					}
				} else if (value instanceof short[]) {
					short[] shortArray = (short[]) value;
					for (int i = 0; i < shortArray.length; i++) {
						this.maybeAppendComma(i);
						this.visitShort(shortArray[i]);
					}
				} else if (value instanceof char[]) {
					char[] charArray = (char[]) value;
					for (int i = 0; i < charArray.length; i++) {
						this.maybeAppendComma(i);
						this.visitChar(charArray[i]);
					}
				} else if (value instanceof int[]) {
					int[] intArray = (int[]) value;
					for (int i = 0; i < intArray.length; i++) {
						this.maybeAppendComma(i);
						this.visitInt(intArray[i]);
					}
				} else if (value instanceof long[]) {
					long[] longArray = (long[]) value;
					for (int i = 0; i < longArray.length; i++) {
						this.maybeAppendComma(i);
						this.visitLong(longArray[i]);
					}
				} else if (value instanceof float[]) {
					float[] floatArray = (float[]) value;
					for (int i = 0; i < floatArray.length; i++) {
						this.maybeAppendComma(i);
						this.visitFloat(floatArray[i]);
					}
				} else if (value instanceof double[]) {
					double[] doubleArray = (double[]) value;
					for (int i = 0; i < doubleArray.length; i++) {
						this.maybeAppendComma(i);
						this.visitDouble(doubleArray[i]);
					}
				}
				this.stringBuilder.append('}');
			}
			this.text.add(this.stringBuilder.toString());
		}

		private void visitInt(final int value) {
			this.stringBuilder.append(value);
		}

		private void visitLong(final long value) {
			this.stringBuilder.append(value)
			                  .append('L');
		}

		private void visitFloat(final float value) {
			this.stringBuilder.append(value)
			                  .append('F');
		}

		private void visitDouble(final double value) {
			this.stringBuilder.append(value)
			                  .append('D');
		}

		private void visitChar(final char value) {
			this.stringBuilder.append("(char)")
			                  .append((int) value);
		}

		private void visitShort(final short value) {
			this.stringBuilder.append("(short)")
			                  .append(value);
		}

		private void visitByte(final byte value) {
			this.stringBuilder.append("(byte)")
			                  .append(value);
		}

		private void visitBoolean(final boolean value) {
			this.stringBuilder.append(value);
		}

		private void visitString(final String value) {
			appendString(this.stringBuilder,
			             value);
		}

		private void visitType(final Type value) {
			this.stringBuilder.append(value.getClassName())
			                  .append(CLASS_SUFFIX);
		}

		@Override
		public void visitEnum(final String name,
		                      final String descriptor,
		                      final String value) {
			this.visitAnnotationValue(name);
			this.appendDescriptor(FIELD_DESCRIPTOR,
			                      descriptor);
			this.stringBuilder.append('.')
			                  .append(value);
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public InbodyNotifier visitAnnotation(final String name,
		                                      final String descriptor) {
			this.visitAnnotationValue(name);
			this.stringBuilder.append('@');
			this.appendDescriptor(FIELD_DESCRIPTOR,
			                      descriptor);
			this.stringBuilder.append('(');
			this.text.add(this.stringBuilder.toString());
			return this.addNewInbodyNotifier(")");
		}

		@Override
		public InbodyNotifier visitArray(final String name) {
			this.visitAnnotationValue(name);
			this.stringBuilder.append('{');
			this.text.add(this.stringBuilder.toString());
			return this.addNewInbodyNotifier("}");
		}

		@Override
		public void visitAnnotationEnd() {
			// Nothing to do.
		}

		private void visitAnnotationValue(final String name) {
			this.stringBuilder.setLength(0);
			this.maybeAppendComma(this.numAnnotationValues++);
			if (name != null) {
				this.stringBuilder.append(name)
				                  .append('=');
			}
		}

		// -----------------------------------------------------------------------------------------------
		// Record components
		// -----------------------------------------------------------------------------------------------

		@Override
		public InbodyNotifier visitRecordComponentAnnotation(final String descriptor,
		                                                     final boolean visible) {
			return this.visitAnnotation(descriptor,
			                            visible);
		}

		@Override
		public InbodyNotifier visitRecordComponentTypeAnnotation(final int typeRef,
		                                                         final TypePath typePath,
		                                                         final String descriptor,
		                                                         final boolean visible) {
			return this.visitTypeAnnotation(typeRef,
			                                typePath,
			                                descriptor,
			                                visible);
		}

		@Override
		public void visitRecordComponentAttribute(final Attribute attribute) {
			this.visitAttribute(attribute);
		}

		@Override
		public void visitRecordComponentEnd() {
			// Nothing to do.
		}

		// -----------------------------------------------------------------------------------------------
		// Fields
		// -----------------------------------------------------------------------------------------------

		@Override
		public InbodyNotifier visitFieldAnnotation(final String descriptor,
		                                           final boolean visible) {
			return this.visitAnnotation(descriptor,
			                            visible);
		}

		@Override
		public InbodyNotifier visitFieldTypeAnnotation(final int typeRef,
		                                               final TypePath typePath,
		                                               final String descriptor,
		                                               final boolean visible) {
			return this.visitTypeAnnotation(typeRef,
			                                typePath,
			                                descriptor,
			                                visible);
		}

		@Override
		public void visitFieldAttribute(final Attribute attribute) {
			this.visitAttribute(attribute);
		}

		@Override
		public void visitFieldEnd() {
			// Nothing to do.
		}

		// -----------------------------------------------------------------------------------------------
		// Methods
		// -----------------------------------------------------------------------------------------------

		@Override
		public void visitParameter(final String name,
		                           final int access) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append("// parameter ");
			this.appendAccess(access);
			this.stringBuilder.append(' ')
			                  .append((name == null)
			                          ? "<no name>"
			                          : name)
			                  .append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public InbodyNotifier visitAnnotationDefault() {
			this.text.add(this.tab2 + "default=");
			return this.addNewInbodyNotifier("\n");
		}

		@Override
		public InbodyNotifier visitMethodAnnotation(final String descriptor,
		                                            final boolean visible) {
			return this.visitAnnotation(descriptor,
			                            visible);
		}

		@Override
		public InbodyNotifier visitMethodTypeAnnotation(final int typeRef,
		                                                final TypePath typePath,
		                                                final String descriptor,
		                                                final boolean visible) {
			return this.visitTypeAnnotation(typeRef,
			                                typePath,
			                                descriptor,
			                                visible);
		}

		@Override
		public InbodyNotifier visitAnnotableParameterCount(final int parameterCount,
		                                                   final boolean visible) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append("// annotable parameter count: ");
			this.stringBuilder.append(parameterCount);
			this.stringBuilder.append(visible
			                          ? " (visible)\n"
			                          : " (invisible)\n");
			this.text.add(this.stringBuilder.toString());
			return this;
		}

		@Override
		public InbodyNotifier visitParameterAnnotation(final int parameter,
		                                               final String descriptor,
		                                               final boolean visible) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append('@');
			this.appendDescriptor(FIELD_DESCRIPTOR,
			                      descriptor);
			this.stringBuilder.append('(');
			this.text.add(this.stringBuilder.toString());

			this.stringBuilder.setLength(0);
			this.stringBuilder.append(visible
			                          ? ") // parameter "
			                          : ") // invisible, parameter ")
			                  .append(parameter)
			                  .append('\n');
			return this.addNewInbodyNotifier(this.stringBuilder.toString());
		}

		@Override
		public void visitMethodAttribute(final Attribute attribute) {
			this.visitAttribute(attribute);
		}

		@Override
		public void visitCode() {
			// Nothing to do.
		}

		@Override
		public void visitFrame(final int type,
		                       final int numLocal,
		                       final Object[] local,
		                       final int numStack,
		                       final Object[] stack) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.ltab);
			this.stringBuilder.append("FRAME ");
			switch (type) {
				case Opcodes.F_NEW:
				case Opcodes.F_FULL:
					this.stringBuilder.append("FULL [");
					this.appendFrameTypes(numLocal,
					                      local);
					this.stringBuilder.append("] [");
					this.appendFrameTypes(numStack,
					                      stack);
					this.stringBuilder.append(']');
					break;
				case Opcodes.F_APPEND:
					this.stringBuilder.append("APPEND [");
					this.appendFrameTypes(numLocal,
					                      local);
					this.stringBuilder.append(']');
					break;
				case Opcodes.F_CHOP:
					this.stringBuilder.append("CHOP ")
					                  .append(numLocal);
					break;
				case Opcodes.F_SAME:
					this.stringBuilder.append("SAME");
					break;
				case Opcodes.F_SAME1:
					this.stringBuilder.append("SAME1 ");
					this.appendFrameTypes(1,
					                      stack);
					break;
				default:
					throw new IllegalArgumentException();
			}
			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitInsn(final int opcode) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append(OPCODES[opcode])
			                  .append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitIntInsn(final int opcode,
		                         final int operand) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append(OPCODES[opcode])
			                  .append(' ')
			                  .append(opcode == Opcodes.NEWARRAY
			                          ? TYPES[operand]
			                          : Integer.toString(operand))
			                  .append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitVarInsn(final int opcode,
		                         final int varIndex) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append(OPCODES[opcode])
			                  .append(' ')
			                  .append(varIndex)
			                  .append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitTypeInsn(final int opcode,
		                          final String type) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append(OPCODES[opcode])
			                  .append(' ');
			this.appendDescriptor(INTERNAL_NAME,
			                      type);
			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitFieldInsn(final int opcode,
		                           final String owner,
		                           final String name,
		                           final String descriptor) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append(OPCODES[opcode])
			                  .append(' ');
			this.appendDescriptor(INTERNAL_NAME,
			                      owner);
			this.stringBuilder.append('.')
			                  .append(name)
			                  .append(" : ");
			this.appendDescriptor(FIELD_DESCRIPTOR,
			                      descriptor);
			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitMethodInsn(final int opcode,
		                            final String owner,
		                            final String name,
		                            final String descriptor,
		                            final boolean isInterface) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append(OPCODES[opcode])
			                  .append(' ');
			this.appendDescriptor(INTERNAL_NAME,
			                      owner);
			this.stringBuilder.append('.')
			                  .append(name)
			                  .append(' ');
			this.appendDescriptor(METHOD_DESCRIPTOR,
			                      descriptor);
			if (isInterface) {
				this.stringBuilder.append(" (itf)");
			}
			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitInvokeDynamicInsn(final String name,
		                                   final String descriptor,
		                                   final Handle bootstrapMethodHandle,
		                                   final Object... bootstrapMethodArguments) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append("INVOKEDYNAMIC")
			                  .append(' ');
			this.stringBuilder.append(name);
			this.appendDescriptor(METHOD_DESCRIPTOR,
			                      descriptor);
			this.stringBuilder.append(" [");
			this.stringBuilder.append('\n');
			this.stringBuilder.append(this.tab3);
			this.appendHandle(bootstrapMethodHandle);
			this.stringBuilder.append('\n');
			this.stringBuilder.append(this.tab3)
			                  .append("// arguments:");
			if (bootstrapMethodArguments.length == 0) {
				this.stringBuilder.append(" none");
			} else {
				this.stringBuilder.append('\n');
				for (Object value : bootstrapMethodArguments) {
					this.stringBuilder.append(this.tab3);
					if (value instanceof String) {
						Printer.appendString(this.stringBuilder,
						                     (String) value);
					} else if (value instanceof Type) {
						Type type = (Type) value;
						if (type.getSort() == Type.METHOD) {
							this.appendDescriptor(METHOD_DESCRIPTOR,
							                      type.getDescriptor());
						} else {
							this.visitType(type);
						}
					} else if (value instanceof Handle) {
						this.appendHandle((Handle) value);
					} else {
						this.stringBuilder.append(value);
					}
					this.stringBuilder.append(", \n");
				}
				this.stringBuilder.setLength(this.stringBuilder.length() - 3);
			}
			this.stringBuilder.append('\n');
			this.stringBuilder.append(this.tab2)
			                  .append("]\n");
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitJumpInsn(final int opcode,
		                          final Label label) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append(OPCODES[opcode])
			                  .append(' ');
			this.appendLabel(label);
			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitLabel(final Label label) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.ltab);
			this.appendLabel(label);
			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitLdcInsn(final Object value) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append("LDC ");
			if (value instanceof String) {
				Printer.appendString(this.stringBuilder,
				                     (String) value);
			} else if (value instanceof Type) {
				this.stringBuilder.append(((Type) value).getDescriptor())
				                  .append(CLASS_SUFFIX);
			} else {
				this.stringBuilder.append(value);
			}
			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitIincInsn(final int varIndex,
		                          final int increment) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append("IINC ")
			                  .append(varIndex)
			                  .append(' ')
			                  .append(increment)
			                  .append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitTableSwitchInsn(final int min,
		                                 final int max,
		                                 final Label dflt,
		                                 final Label... labels) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append("TABLESWITCH\n");
			for (int i = 0; i < labels.length; ++i) {
				this.stringBuilder.append(this.tab3)
				                  .append(min + i)
				                  .append(": ");
				this.appendLabel(labels[i]);
				this.stringBuilder.append('\n');
			}
			this.stringBuilder.append(this.tab3)
			                  .append("default: ");
			this.appendLabel(dflt);
			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitLookupSwitchInsn(final Label dflt,
		                                  final int[] keys,
		                                  final Label[] labels) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append("LOOKUPSWITCH\n");
			for (int i = 0; i < labels.length; ++i) {
				this.stringBuilder.append(this.tab3)
				                  .append(keys[i])
				                  .append(": ");
				this.appendLabel(labels[i]);
				this.stringBuilder.append('\n');
			}
			this.stringBuilder.append(this.tab3)
			                  .append("default: ");
			this.appendLabel(dflt);
			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitMultiANewArrayInsn(final String descriptor,
		                                    final int numDimensions) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append("MULTIANEWARRAY ");
			this.appendDescriptor(FIELD_DESCRIPTOR,
			                      descriptor);
			this.stringBuilder.append(' ')
			                  .append(numDimensions)
			                  .append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public InbodyNotifier visitInsnAnnotation(final int typeRef,
		                                          final TypePath typePath,
		                                          final String descriptor,
		                                          final boolean visible) {
			return this.visitTypeAnnotation(typeRef,
			                                typePath,
			                                descriptor,
			                                visible);
		}

		@Override
		public void visitTryCatchBlock(final Label start,
		                               final Label end,
		                               final Label handler,
		                               final String type) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append("TRYCATCHBLOCK ");
			this.appendLabel(start);
			this.stringBuilder.append(' ');
			this.appendLabel(end);
			this.stringBuilder.append(' ');
			this.appendLabel(handler);
			this.stringBuilder.append(' ');
			this.appendDescriptor(INTERNAL_NAME,
			                      type);
			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public InbodyNotifier visitTryCatchAnnotation(final int typeRef,
		                                              final TypePath typePath,
		                                              final String descriptor,
		                                              final boolean visible) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append("TRYCATCHBLOCK @");
			this.appendDescriptor(FIELD_DESCRIPTOR,
			                      descriptor);
			this.stringBuilder.append('(');
			this.text.add(this.stringBuilder.toString());

			this.stringBuilder.setLength(0);
			this.stringBuilder.append(") : ");
			this.appendTypeReference(typeRef);
			this.stringBuilder.append(", ")
			                  .append(typePath);
			this.stringBuilder.append(visible
			                          ? "\n"
			                          : INVISIBLE);
			return this.addNewInbodyNotifier(this.stringBuilder.toString());
		}

		@Override
		public void visitLocalVariable(final String name,
		                               final String descriptor,
		                               final String signature,
		                               final Label start,
		                               final Label end,
		                               final int index) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append("LOCALVARIABLE ")
			                  .append(name)
			                  .append(' ');
			this.appendDescriptor(FIELD_DESCRIPTOR,
			                      descriptor);
			this.stringBuilder.append(' ');
			this.appendLabel(start);
			this.stringBuilder.append(' ');
			this.appendLabel(end);
			this.stringBuilder.append(' ')
			                  .append(index)
			                  .append('\n');

			if (signature != null) {
				this.stringBuilder.append(this.tab2);
				this.appendDescriptor(FIELD_SIGNATURE,
				                      signature);
				this.stringBuilder.append(this.tab2);
				this.appendJavaDeclaration(name,
				                           signature);
			}
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public InbodyNotifier visitLocalVariableAnnotation(final int typeRef,
		                                                   final TypePath typePath,
		                                                   final Label[] start,
		                                                   final Label[] end,
		                                                   final int[] index,
		                                                   final String descriptor,
		                                                   final boolean visible) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append("LOCALVARIABLE @");
			this.appendDescriptor(FIELD_DESCRIPTOR,
			                      descriptor);
			this.stringBuilder.append('(');
			this.text.add(this.stringBuilder.toString());

			this.stringBuilder.setLength(0);
			this.stringBuilder.append(") : ");
			this.appendTypeReference(typeRef);
			this.stringBuilder.append(", ")
			                  .append(typePath);
			for (int i = 0; i < start.length; ++i) {
				this.stringBuilder.append(" [ ");
				this.appendLabel(start[i]);
				this.stringBuilder.append(" - ");
				this.appendLabel(end[i]);
				this.stringBuilder.append(" - ")
				                  .append(index[i])
				                  .append(" ]");
			}
			this.stringBuilder.append(visible
			                          ? "\n"
			                          : INVISIBLE);
			return this.addNewInbodyNotifier(this.stringBuilder.toString());
		}

		@Override
		public void visitLineNumber(final int line,
		                            final Label start) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append("LINENUMBER ")
			                  .append(line)
			                  .append(' ');
			this.appendLabel(start);
			this.stringBuilder.append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitMaxs(final int maxStack,
		                      final int maxLocals) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append("MAXSTACK = ")
			                  .append(maxStack)
			                  .append('\n');
			this.text.add(this.stringBuilder.toString());

			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab2)
			                  .append("MAXLOCALS = ")
			                  .append(maxLocals)
			                  .append('\n');
			this.text.add(this.stringBuilder.toString());
		}

		@Override
		public void visitMethodEnd() {
			// Nothing to do.
		}

		// -----------------------------------------------------------------------------------------------
		// Common methods
		// -----------------------------------------------------------------------------------------------

		/**
		 * Prints a disassembled view of the given annotation.
		 *
		 * @param descriptor the class descriptor of the annotation class.
		 * @param visible    {@literal true} if the annotation is visible at runtime.
		 * @return a visitor to visit the annotation values.
		 */
		// DontCheck(OverloadMethodsDeclarationOrder): overloads are semantically different.
		public InbodyNotifier visitAnnotation(final String descriptor,
		                                      final boolean visible) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab)
			                  .append('@');
			this.appendDescriptor(FIELD_DESCRIPTOR,
			                      descriptor);
			this.stringBuilder.append('(');
			this.text.add(this.stringBuilder.toString());
			return this.addNewInbodyNotifier(visible
			                                 ? ")\n"
			                                 : ") // invisible\n");
		}

		/**
		 * Prints a disassembled view of the given type annotation.
		 *
		 * @param typeRef    a reference to the annotated type. See {@link TypeReference}.
		 * @param typePath   the path to the annotated type argument, wildcard bound, array element type, or
		 *                   static inner type within 'typeRef'. May be {@literal null} if the annotation targets
		 *                   'typeRef' as a whole.
		 * @param descriptor the class descriptor of the annotation class.
		 * @param visible    {@literal true} if the annotation is visible at runtime.
		 * @return a visitor to visit the annotation values.
		 */
		public InbodyNotifier visitTypeAnnotation(final int typeRef,
		                                          final TypePath typePath,
		                                          final String descriptor,
		                                          final boolean visible) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab)
			                  .append('@');
			this.appendDescriptor(FIELD_DESCRIPTOR,
			                      descriptor);
			this.stringBuilder.append('(');
			this.text.add(this.stringBuilder.toString());

			this.stringBuilder.setLength(0);
			this.stringBuilder.append(") : ");
			this.appendTypeReference(typeRef);
			this.stringBuilder.append(", ")
			                  .append(typePath);
			this.stringBuilder.append(visible
			                          ? "\n"
			                          : INVISIBLE);
			return this.addNewInbodyNotifier(this.stringBuilder.toString());
		}

		/**
		 * Prints a disassembled view of the given attribute.
		 *
		 * @param attribute an attribute.
		 */
		public void visitAttribute(final Attribute attribute) {
			this.stringBuilder.setLength(0);
			this.stringBuilder.append(this.tab)
			                  .append("ATTRIBUTE ");
			this.appendDescriptor(-1,
			                      attribute.type);

			if (attribute instanceof InbodyNotifierSupport) {
				if (this.labelNames == null) {
					this.labelNames = new HashMap<>();
				}
				((InbodyNotifierSupport) attribute).textify(this.stringBuilder,
				                                            this.labelNames);
			} else {
				this.stringBuilder.append(" : unknown\n");
			}

			this.text.add(this.stringBuilder.toString());
		}

		// -----------------------------------------------------------------------------------------------
		// Utility methods
		// -----------------------------------------------------------------------------------------------

		/**
		 * Appends a string representation of the given access flags to {@link #stringBuilder}.
		 *
		 * @param accessFlags some access flags.
		 */
		private void appendAccess(final int accessFlags) {
			if ((accessFlags & Opcodes.ACC_PUBLIC) != 0) {
				this.stringBuilder.append("public ");
			}
			if ((accessFlags & Opcodes.ACC_PRIVATE) != 0) {
				this.stringBuilder.append("private ");
			}
			if ((accessFlags & Opcodes.ACC_PROTECTED) != 0) {
				this.stringBuilder.append("protected ");
			}
			if ((accessFlags & Opcodes.ACC_FINAL) != 0) {
				this.stringBuilder.append("final ");
			}
			if ((accessFlags & Opcodes.ACC_STATIC) != 0) {
				this.stringBuilder.append("static ");
			}
			if ((accessFlags & Opcodes.ACC_SYNCHRONIZED) != 0) {
				this.stringBuilder.append("synchronized ");
			}
			if ((accessFlags & Opcodes.ACC_VOLATILE) != 0) {
				this.stringBuilder.append("volatile ");
			}
			if ((accessFlags & Opcodes.ACC_TRANSIENT) != 0) {
				this.stringBuilder.append("transient ");
			}
			if ((accessFlags & Opcodes.ACC_ABSTRACT) != 0) {
				this.stringBuilder.append("abstract ");
			}
			if ((accessFlags & Opcodes.ACC_STRICT) != 0) {
				this.stringBuilder.append("strictfp ");
			}
			if ((accessFlags & Opcodes.ACC_SYNTHETIC) != 0) {
				this.stringBuilder.append("synthetic ");
			}
			if ((accessFlags & Opcodes.ACC_MANDATED) != 0) {
				this.stringBuilder.append("mandated ");
			}
			if ((accessFlags & Opcodes.ACC_ENUM) != 0) {
				this.stringBuilder.append("enum ");
			}
		}

		/**
		 * Appends the hexadecimal value of the given access flags to {@link #stringBuilder}.
		 *
		 * @param accessFlags some access flags.
		 */
		private void appendRawAccess(final int accessFlags) {
			this.stringBuilder.append("// access flags 0x")
			                  .append(Integer.toHexString(accessFlags)
			                                 .toUpperCase())
			                  .append('\n');
		}

		/**
		 * Appends an internal name, a type descriptor or a type signature to {@link #stringBuilder}.
		 *
		 * @param type  the type of 'value'. Must be one of {@link #INTERNAL_NAME}, {@link
		 *              #FIELD_DESCRIPTOR}, {@link #FIELD_SIGNATURE}, {@link #METHOD_DESCRIPTOR}, {@link
		 *              #METHOD_SIGNATURE}, {@link #CLASS_SIGNATURE} or {@link #HANDLE_DESCRIPTOR}.
		 * @param value an internal name (see {@link Type#getInternalName()}), type descriptor or a type
		 *              signature. May be {@literal null}.
		 */
		protected void appendDescriptor(final int type,
		                                final String value) {
			if (type == CLASS_SIGNATURE || type == FIELD_SIGNATURE || type == METHOD_SIGNATURE) {
				if (value != null) {
					this.stringBuilder.append("// signature ")
					                  .append(value)
					                  .append('\n');
				}
			} else {
				this.stringBuilder.append(value);
			}
		}

		/**
		 * Appends the Java generic type declaration corresponding to the given signature.
		 *
		 * @param name      a class, field or method name.
		 * @param signature a class, field or method signature.
		 */
		private void appendJavaDeclaration(final String name,
		                                   final String signature) {
			SignatureScanVisitor traceSignatureVisitor = new SignatureScanVisitor(this.access);
			new SignatureReader(signature).accept(traceSignatureVisitor);
			this.stringBuilder.append("// declaration: ");
			if (traceSignatureVisitor.getReturnType() != null) {
				this.stringBuilder.append(traceSignatureVisitor.getReturnType());
				this.stringBuilder.append(' ');
			}
			this.stringBuilder.append(name);
			this.stringBuilder.append(traceSignatureVisitor.getDeclaration());
			if (traceSignatureVisitor.getExceptions() != null) {
				this.stringBuilder.append(" throws ")
				                  .append(traceSignatureVisitor.getExceptions());
			}
			this.stringBuilder.append('\n');
		}

		/**
		 * Appends the name of the given label to {@link #stringBuilder}. Constructs a new label name if
		 * the given label does not yet have one.
		 *
		 * @param label a label.
		 */
		protected void appendLabel(final Label label) {
			if (this.labelNames == null) {
				this.labelNames = new HashMap<>();
			}
			String name = this.labelNames.computeIfAbsent(label,
			                                              key -> "L" + this.labelNames.size());
			this.stringBuilder.append(name);
		}

		/**
		 * Appends a string representation of the given handle to {@link #stringBuilder}.
		 *
		 * @param handle a handle.
		 */
		protected void appendHandle(final Handle handle) {
			int tag = handle.getTag();
			this.stringBuilder.append("// handle kind 0x")
			                  .append(Integer.toHexString(tag))
			                  .append(" : ");
			boolean isMethodHandle = false;
			switch (tag) {
				case Opcodes.H_GETFIELD:
					this.stringBuilder.append("GETFIELD");
					break;
				case Opcodes.H_GETSTATIC:
					this.stringBuilder.append("GETSTATIC");
					break;
				case Opcodes.H_PUTFIELD:
					this.stringBuilder.append("PUTFIELD");
					break;
				case Opcodes.H_PUTSTATIC:
					this.stringBuilder.append("PUTSTATIC");
					break;
				case Opcodes.H_INVOKEINTERFACE:
					this.stringBuilder.append("INVOKEINTERFACE");
					isMethodHandle = true;
					break;
				case Opcodes.H_INVOKESPECIAL:
					this.stringBuilder.append("INVOKESPECIAL");
					isMethodHandle = true;
					break;
				case Opcodes.H_INVOKESTATIC:
					this.stringBuilder.append("INVOKESTATIC");
					isMethodHandle = true;
					break;
				case Opcodes.H_INVOKEVIRTUAL:
					this.stringBuilder.append("INVOKEVIRTUAL");
					isMethodHandle = true;
					break;
				case Opcodes.H_NEWINVOKESPECIAL:
					this.stringBuilder.append("NEWINVOKESPECIAL");
					isMethodHandle = true;
					break;
				default:
					throw new IllegalArgumentException();
			}
			this.stringBuilder.append('\n');
			this.stringBuilder.append(this.tab3);
			this.appendDescriptor(INTERNAL_NAME,
			                      handle.getOwner());
			this.stringBuilder.append('.');
			this.stringBuilder.append(handle.getName());
			if (!isMethodHandle) {
				this.stringBuilder.append('(');
			}
			this.appendDescriptor(HANDLE_DESCRIPTOR,
			                      handle.getDesc());
			if (!isMethodHandle) {
				this.stringBuilder.append(')');
			}
			if (handle.isInterface()) {
				this.stringBuilder.append(" itf");
			}
		}

		/**
		 * Appends a comma to {@link #stringBuilder} if the given number is strictly positive.
		 *
		 * @param numValues a number of 'values visited so far', for instance the number of annotation
		 *                  values visited so far in an annotation visitor.
		 */
		private void maybeAppendComma(final int numValues) {
			if (numValues > 0) {
				this.stringBuilder.append(", ");
			}
		}

		/**
		 * Appends a string representation of the given type reference to {@link #stringBuilder}.
		 *
		 * @param typeRef a type reference. See {@link TypeReference}.
		 */
		private void appendTypeReference(final int typeRef) {
			TypeReference typeReference = new TypeReference(typeRef);
			switch (typeReference.getSort()) {
				case TypeReference.CLASS_TYPE_PARAMETER:
					this.stringBuilder.append("CLASS_TYPE_PARAMETER ")
					                  .append(typeReference.getTypeParameterIndex());
					break;
				case TypeReference.METHOD_TYPE_PARAMETER:
					this.stringBuilder.append("METHOD_TYPE_PARAMETER ")
					                  .append(typeReference.getTypeParameterIndex());
					break;
				case TypeReference.CLASS_EXTENDS:
					this.stringBuilder.append("CLASS_EXTENDS ")
					                  .append(typeReference.getSuperTypeIndex());
					break;
				case TypeReference.CLASS_TYPE_PARAMETER_BOUND:
					this.stringBuilder.append("CLASS_TYPE_PARAMETER_BOUND ")
					                  .append(typeReference.getTypeParameterIndex())
					                  .append(", ")
					                  .append(typeReference.getTypeParameterBoundIndex());
					break;
				case TypeReference.METHOD_TYPE_PARAMETER_BOUND:
					this.stringBuilder.append("METHOD_TYPE_PARAMETER_BOUND ")
					                  .append(typeReference.getTypeParameterIndex())
					                  .append(", ")
					                  .append(typeReference.getTypeParameterBoundIndex());
					break;
				case TypeReference.FIELD:
					this.stringBuilder.append("FIELD");
					break;
				case TypeReference.METHOD_RETURN:
					this.stringBuilder.append("METHOD_RETURN");
					break;
				case TypeReference.METHOD_RECEIVER:
					this.stringBuilder.append("METHOD_RECEIVER");
					break;
				case TypeReference.METHOD_FORMAL_PARAMETER:
					this.stringBuilder.append("METHOD_FORMAL_PARAMETER ")
					                  .append(typeReference.getFormalParameterIndex());
					break;
				case TypeReference.THROWS:
					this.stringBuilder.append("THROWS ")
					                  .append(typeReference.getExceptionIndex());
					break;
				case TypeReference.LOCAL_VARIABLE:
					this.stringBuilder.append("LOCAL_VARIABLE");
					break;
				case TypeReference.RESOURCE_VARIABLE:
					this.stringBuilder.append("RESOURCE_VARIABLE");
					break;
				case TypeReference.EXCEPTION_PARAMETER:
					this.stringBuilder.append("EXCEPTION_PARAMETER ")
					                  .append(typeReference.getTryCatchBlockIndex());
					break;
				case TypeReference.INSTANCEOF:
					this.stringBuilder.append("INSTANCEOF");
					break;
				case TypeReference.NEW:
					this.stringBuilder.append("NEW");
					break;
				case TypeReference.CONSTRUCTOR_REFERENCE:
					this.stringBuilder.append("CONSTRUCTOR_REFERENCE");
					break;
				case TypeReference.METHOD_REFERENCE:
					this.stringBuilder.append("METHOD_REFERENCE");
					break;
				case TypeReference.CAST:
					this.stringBuilder.append("CAST ")
					                  .append(typeReference.getTypeArgumentIndex());
					break;
				case TypeReference.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT:
					this.stringBuilder.append("CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT ")
					                  .append(typeReference.getTypeArgumentIndex());
					break;
				case TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT:
					this.stringBuilder.append("METHOD_INVOCATION_TYPE_ARGUMENT ")
					                  .append(typeReference.getTypeArgumentIndex());
					break;
				case TypeReference.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT:
					this.stringBuilder.append("CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT ")
					                  .append(typeReference.getTypeArgumentIndex());
					break;
				case TypeReference.METHOD_REFERENCE_TYPE_ARGUMENT:
					this.stringBuilder.append("METHOD_REFERENCE_TYPE_ARGUMENT ")
					                  .append(typeReference.getTypeArgumentIndex());
					break;
				default:
					throw new IllegalArgumentException();
			}
		}

		/**
		 * Appends the given stack map frame types to {@link #stringBuilder}.
		 *
		 * @param numTypes   the number of stack map frame types in 'frameTypes'.
		 * @param frameTypes an array of stack map frame types, in the format described in {@link
		 *                   MethodVisitor#visitFrame}.
		 */
		private void appendFrameTypes(final int numTypes,
		                              final Object[] frameTypes) {
			for (int i = 0; i < numTypes; ++i) {
				if (i > 0) {
					this.stringBuilder.append(' ');
				}
				if (frameTypes[i] instanceof String) {
					String descriptor = (String) frameTypes[i];
					if (descriptor.charAt(0) == '[') {
						this.appendDescriptor(FIELD_DESCRIPTOR,
						                      descriptor);
					} else {
						this.appendDescriptor(INTERNAL_NAME,
						                      descriptor);
					}
				} else if (frameTypes[i] instanceof Integer) {
					this.stringBuilder.append(FRAME_TYPES.get((Integer) frameTypes[i]));
				} else {
					this.appendLabel((Label) frameTypes[i]);
				}
			}
		}

		/**
		 * Creates and adds to {@link #text} a new {@link InbodyNotifier}, followed by the given string.
		 *
		 * @param endText the text to add to {@link #text} after the textifier. May be {@literal null}.
		 * @return the newly created {@link InbodyNotifier}.
		 */
		private InbodyNotifier addNewInbodyNotifier(final String endText) {
			InbodyNotifier textifier = this.createInbodyNotifier();
			this.text.add(textifier.getText());
			if (endText != null) {
				this.text.add(endText);
			}
			return textifier;
		}

		/**
		 * Creates a new {@link InbodyNotifier}.
		 *
		 * @return a new {@link InbodyNotifier}.
		 */
		protected InbodyNotifier createInbodyNotifier() {
			return new InbodyNotifier();
		}
	}
}
