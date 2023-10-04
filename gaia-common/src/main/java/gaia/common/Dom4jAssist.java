package gaia.common;

import static gaia.common.FunctionAssist.supply;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Attribute;
import org.dom4j.Branch;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.DOMReader;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonPointer;

import gaia.common.CharsetSpec.ByteArrayOutputStreamExt;
import gaia.common.FunctionAssist.ErrorHandleSupplier;


/**
 * @author sklee
 */
public interface Dom4jAssist {
	Logger log = LoggerFactory.getLogger(Dom4jAssist.class);
	OutputFormat pretty = OutputFormat.createPrettyPrint();
	OutputFormat compact = OutputFormat.createCompactFormat();

	static Document createEmptyDocument() {
		return DocumentHelper.createDocument();
	}

	static void iterateElement(final Element element,
	                           final String elementName,
	                           final Consumer<Element> listener) {
		List<Element> elms = element.elements(elementName);

		for (Element subElm : elms) {//
			listener.accept(subElm);
		}
	}

	static Document convertSource(Object source) throws Exception {
		if (source instanceof File) {
			InputStream in = null;
			try {
				in = new FileInputStream((File) source);
				return new SAXReader().read(in);
			}
			finally {
				ObjectFinalizer.close(in);
			}
		}

		if (source instanceof InputStream) {
			return new SAXReader().read((InputStream) source);
		}

		throw new RuntimeException(StringAssist.format("unsupported input source type [%s]",
		                                               source.getClass()
		                                                     .getName()));
	}

	/**
	 *
	 */
	static Document readDocument(File file) {
		InputStream in = null;
		try {
			in = new FileInputStream(file);
			return readDocument(in);
		}
		catch (Exception ioe) {
			throw new RuntimeException("document read error!",
			                           ioe);
		}
		finally {
			ObjectFinalizer.close(in);
		}

	}

	static Document readDocument(ErrorHandleSupplier<InputStream> supplier) {
		InputStream in = null;
		try {
			in = supply(supplier);

			assert null != in
					: "input stream cannot be null!";

			return readDocument(in);
		}
		finally {
			ObjectFinalizer.close(in);
		}
	}

	/**
	 *
	 */
	static Document readDocument(InputStream in) {
		Document document = null;
		SAXReader reader = null;
		try {
			reader = new SAXReader();
			document = reader.read(in);
		}
		catch (DocumentException e) {
			throw new RuntimeException("document read error!",
			                           e);
		}

		return document;
	}

	static Document readDocument(String content) throws DocumentException {
		return DocumentHelper.parseText(content);
	}

	static String dump(Document document,
	                   Charset cs) throws IOException {
		ByteArrayOutputStreamExt bout = new ByteArrayOutputStreamExt();

		write(document,
		      bout);

		return bout.toString(cs);
	}

	static void dump(Document document,
	                 OutputStream out) throws IOException {
		write(document,
		      out,
		      pretty);
	}

	static void write(Document document,
	                  OutputStream out) throws IOException {
		write(document,
		      out,
		      compact);
	}

	/**
	 *
	 */
	static void write(Document document,
	                  OutputStream out,
	                  OutputFormat format) throws IOException {
		XMLWriter writer = null;
		writer = new XMLWriter(out,
		                       format);
		writer.write(document);
	}

	/**
	 *
	 */
	static void write(Document document,
	                  Writer out,
	                  OutputFormat format) throws IOException {
		XMLWriter writer = null;
		writer = new XMLWriter(out,
		                       format);
		writer.write(document);
	}

	static Map<String, Object> asMap(Document document) {
		Map<String, Object> writer = null;
		writer = new HashMap<>();
		write(document,
		      writer);
		return writer;
	}

	static void write(Document document,
	                  Map<String, Object> map) {
		Element rootElement = document.getRootElement();

		append(rootElement,
		       map);
	}

	static void append(Element element,
	                   Map<String, Object> map) {
		Object value = extract(element);
		map.put(element.getName(),
		        value);
	}

	static Object extract(Element element) {
		// check single element
		final List<Attribute> attributes = element.attributes();
		final List<Element> elements = element.elements();

		final int attributeCount = attributes == null
		                           ? 0
		                           : attributes.size();
		final int elementCount = elements == null
		                         ? 0
		                         : elements.size();

		if (attributeCount == 0 && elementCount == 0) {
			// case single elements
			String elementText = element.getTextTrim();

			return StringUtils.isEmpty(elementText)
			       ? null
			       : elementText;

		}

		final Map<String, Object> map = new LinkedHashMap<>();

		if (attributes != null) {
			attributes.forEach(attribute -> {
				String name = attribute.getName();
				String value = attribute.getText();

				map.put(name,
				        value);
			});
		}

		if (elements != null) {
			elements.forEach(candidate -> {
				Object value = extract(candidate);
				map.put(candidate.getName(),
				        value);
			});
		}

		return map;
	}

	static String transfer(Document document) {
		return transfer(document,
		                compact);
	}

	static String dump(Document document) {
		return transfer(document,
		                pretty);
	}

	static String transfer(Document document,
	                       OutputFormat format) {

		StringWriter writer = null;
		XMLWriter xmlWriter = null;
		try {
			writer = new StringWriter();
			xmlWriter = new XMLWriter(writer,
			                          format);
			xmlWriter.write(document);

			return writer.toString();
		}
		catch (IOException ioe) {
			throw ExceptionAssist.wrap(ioe);
		}
		finally {
			ObjectFinalizer.close(writer);
		}
	}

	static String dump(org.w3c.dom.Document document) throws IOException {
		return dump(new DOMReader().read(document));
	}

	// NodeWalkResult
	enum NodeWalkResult {
		OK,
		SOURCE_MISSING,
		TARGET_POINTER_INVALID,
		VALUE_NODE_FOUND_AT_PATH,
		ABNORMAL_LOOP_END
	}

	Predicate<NodeWalkResult> predicate_node_walk_ok = result -> result == NodeWalkResult.OK;
	Predicate<NodeWalkResult> predicate_node_walk_fail = predicate_node_walk_ok.negate();


	interface NodeWalkerListener {
		void on(NodeWalkResult result,
		        JsonPointer pointer,
		        Node node);
	}

	static NodeWalkResult assignNodeAt(final Document root,
	                                   final JsonPointer pointer,
	                                   final Node node) {
		return walkNodeAt(root,
		                  pointer,
		                  (result, workingPointer, workingNode) -> {
			                  if (NodeWalkResult.OK == result) {
				                  switch (workingNode.getNodeType()) {
					                  case Node.DOCUMENT_NODE:
					                  case Node.ELEMENT_NODE: {
						                  final Branch branch = (Branch) workingNode;

						                  if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
							                  final Attribute attr = (Attribute) node;
							                  ((Element) branch).addAttribute(workingPointer.getMatchingProperty(),
							                                                  attr.getValue());
						                  } else if (node.getNodeType() == Node.ELEMENT_NODE) {
							                  final Element elm = (Element) node.clone();

							                  elm.setName(workingPointer.getMatchingProperty());

							                  branch.add(elm);
						                  } else {
							                  throw new Error("attr,element only!");
						                  }
						                  break;
					                  }


					                  default: {
						                  throw new Error("OK found node not a both of (OBJECT,ARRAY) ?");
					                  }
				                  }
			                  }
		                  });
	}

	static NodeWalkResult eraseNodeAt(final Document root,
	                                  final JsonPointer pointer) {
		return walkNodeAt(root,
		                  pointer,
		                  (result, workingPointer, workingNode) -> {
			                  if (NodeWalkResult.OK == result) {
				                  switch (workingNode.getNodeType()) {
					                  case Node.ELEMENT_NODE: {
						                  final Element element = (Element) workingNode;

						                  // remove element
						                  Optional.ofNullable(element.element(workingPointer.getMatchingProperty()))
						                          .ifPresent(element::remove);

						                  // remove attribute
						                  Optional.ofNullable(element.attribute(workingPointer.getMatchingProperty()))
						                          .ifPresent(element::remove);
						                  break;
					                  }

					                  default: {
						                  throw new Error("OK found node not a both of (OBJECT,ARRAY) ?");
					                  }
				                  }
			                  }
		                  });
	}

	static NodeWalkResult walkNodeAt(final Document root,
	                                 final JsonPointer pointer,
	                                 final NodeWalkerListener listener) {
		if (pointer.matches()) {
			// target pointer invalid
			listener.on(NodeWalkResult.TARGET_POINTER_INVALID,
			            pointer,
			            null);
			return NodeWalkResult.TARGET_POINTER_INVALID;
		}

		AtomicReference<Node> nodeHolder = new AtomicReference<>(root);
		AtomicReference<JsonPointer> pointerHolder = new AtomicReference<>(pointer);

		do {
			final Node workingNode = nodeHolder.get();
			final JsonPointer workingPointer = pointerHolder.get();

			// drill down next pointer
			switch (workingNode.getNodeType()) {
				case Node.DOCUMENT_NODE:
				case Node.ELEMENT_NODE: {
					if (workingPointer.tail()
					                  .matches()) {
						// find assign target
						listener.on(NodeWalkResult.OK,
						            workingPointer,
						            workingNode);
						return NodeWalkResult.OK;
					}

					final String name = workingPointer.getMatchingProperty();

					Element child = supply(() -> {
						if (workingNode instanceof Element) {
							final Element element = (Element) workingNode;
							Element sub = element.element(name);
							if (null == sub) {
								sub = element.addElement(name);
							}
							return sub;
						} else if (workingNode instanceof Document) {
							final Document document = (Document) workingNode;
							Element sub = document.getRootElement();

							if (sub == null) {
								sub = document.addElement(name);
							} else if (!StringUtils.equals(sub.getName(),
							                               name)) {
								throw new Error("root element name is different");
							}

							return sub;
						}
						throw new Error("workingNode invalid at container node : ".concat(workingNode.getClass()
						                                                                             .getSimpleName()));
					});

					nodeHolder.set(child);
					break;
				}


				default: {
					if (workingPointer.tail()
					                  .matches()) {
						listener.on(NodeWalkResult.VALUE_NODE_FOUND_AT_PATH,
						            workingPointer,
						            workingNode);
						return NodeWalkResult.VALUE_NODE_FOUND_AT_PATH;
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
				listener.on(NodeWalkResult.ABNORMAL_LOOP_END,
				            workingPointer,
				            workingNode);
				break;
			}
		} while (true);

		return NodeWalkResult.ABNORMAL_LOOP_END;
	}
}
