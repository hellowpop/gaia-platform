package gaia.common;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.Supplier;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.codehaus.stax2.XMLStreamLocation2;
import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.XMLStreamWriter2;
import org.codehaus.stax2.typed.Base64Variant;
import org.codehaus.stax2.validation.ValidationProblemHandler;
import org.codehaus.stax2.validation.XMLValidationSchema;
import org.codehaus.stax2.validation.XMLValidator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public interface JacksonOverrides {

	static XMLStreamWriter wrapXMLStreamWriter(XMLStreamWriter writer) {
		return writer instanceof XMLStreamWriter2
		       ? new CDataXmlStreamWriter2((XMLStreamWriter2) writer)
		       : new CDataXmlStreamWriter(writer);
	}

	class CDataXmlStreamWriter
			implements XMLStreamWriter {

		private final XMLStreamWriter w;

		CDataXmlStreamWriter(XMLStreamWriter w) {
			this.w = w;
		}

		@Override
		public void close() throws XMLStreamException {
			this.w.close();
		}

		@Override
		public void flush() throws XMLStreamException {
			this.w.flush();
		}

		@Override
		public NamespaceContext getNamespaceContext() {
			return this.w.getNamespaceContext();
		}

		@Override
		public String getPrefix(String uri) throws XMLStreamException {
			return this.w.getPrefix(uri);
		}

		@Override
		public Object getProperty(String name) throws IllegalArgumentException {
			return this.w.getProperty(name);
		}

		@Override
		public void setDefaultNamespace(String uri) throws XMLStreamException {
			this.w.setDefaultNamespace(uri);
		}

		@Override
		public void setNamespaceContext(NamespaceContext context) throws XMLStreamException {
			this.w.setNamespaceContext(context);
		}

		@Override
		public void setPrefix(String prefix,
		                      String uri) throws XMLStreamException {
			this.w.setPrefix(prefix,
			                 uri);
		}

		@Override
		public void writeAttribute(String prefix,
		                           String namespaceURI,
		                           String localName,
		                           String value) throws XMLStreamException {
			this.w.writeAttribute(prefix,
			                      namespaceURI,
			                      localName,
			                      value);
		}

		@Override
		public void writeAttribute(String namespaceURI,
		                           String localName,
		                           String value) throws XMLStreamException {
			this.w.writeAttribute(namespaceURI,
			                      localName,
			                      value);
		}

		@Override
		public void writeAttribute(String localName,
		                           String value) throws XMLStreamException {
			this.w.writeAttribute(localName,
			                      value);
		}

		@Override
		public void writeCData(String data) throws XMLStreamException {
			this.w.writeCData(data);
		}

		@Override
		public void writeCharacters(char[] text,
		                            int start,
		                            int len) throws XMLStreamException {
			this.w.writeCharacters(text,
			                       start,
			                       len);
		}

		// All this code just to override this method
		@Override
		public void writeCharacters(String text) throws XMLStreamException {
			if (text.indexOf('\n') > 0) {
				this.w.writeCData(text);
			} else {
				this.w.writeCharacters(text);
			}
		}

		@Override
		public void writeComment(String data) throws XMLStreamException {
			this.w.writeComment(data);
		}

		@Override
		public void writeDTD(String dtd) throws XMLStreamException {
			this.w.writeDTD(dtd);
		}

		@Override
		public void writeDefaultNamespace(String namespaceURI) throws XMLStreamException {
			this.w.writeDefaultNamespace(namespaceURI);
		}

		@Override
		public void writeEmptyElement(String prefix,
		                              String localName,
		                              String namespaceURI) throws XMLStreamException {
			this.w.writeEmptyElement(prefix,
			                         localName,
			                         namespaceURI);
		}

		@Override
		public void writeEmptyElement(String namespaceURI,
		                              String localName) throws XMLStreamException {
			this.w.writeEmptyElement(namespaceURI,
			                         localName);
		}

		@Override
		public void writeEmptyElement(String localName) throws XMLStreamException {
			this.w.writeEmptyElement(localName);
		}

		@Override
		public void writeEndDocument() throws XMLStreamException {
			this.w.writeEndDocument();
		}

		@Override
		public void writeEndElement() throws XMLStreamException {
			this.w.writeEndElement();
		}

		@Override
		public void writeEntityRef(String name) throws XMLStreamException {
			this.w.writeEntityRef(name);
		}

		@Override
		public void writeNamespace(String prefix,
		                           String namespaceURI) throws XMLStreamException {
			this.w.writeNamespace(prefix,
			                      namespaceURI);
		}

		@Override
		public void writeProcessingInstruction(String target,
		                                       String data) throws XMLStreamException {
			this.w.writeProcessingInstruction(target,
			                                  data);
		}

		@Override
		public void writeProcessingInstruction(String target) throws XMLStreamException {
			this.w.writeProcessingInstruction(target);
		}

		@Override
		public void writeStartDocument() throws XMLStreamException {
			this.w.writeStartDocument();
		}

		@Override
		public void writeStartDocument(String encoding,
		                               String version) throws XMLStreamException {
			this.w.writeStartDocument(encoding,
			                          version);
		}

		@Override
		public void writeStartDocument(String version) throws XMLStreamException {
			this.w.writeStartDocument(version);
		}

		@Override
		public void writeStartElement(String prefix,
		                              String localName,
		                              String namespaceURI) throws XMLStreamException {
			this.w.writeStartElement(prefix,
			                         localName,
			                         namespaceURI);
		}

		@Override
		public void writeStartElement(String namespaceURI,
		                              String localName) throws XMLStreamException {
			this.w.writeStartElement(namespaceURI,
			                         localName);
		}

		@Override
		public void writeStartElement(String localName) throws XMLStreamException {
			this.w.writeStartElement(localName);
		}
	}

	class CDataXmlStreamWriter2
			extends CDataXmlStreamWriter
			implements XMLStreamWriter2 {

		private final XMLStreamWriter2 w;

		CDataXmlStreamWriter2(XMLStreamWriter2 w) {
			super(w);
			this.w = w;
		}

		// writer 2

		@Override
		public boolean isPropertySupported(String name) {
			return this.w.isPropertySupported(name);
		}

		@Override
		public boolean setProperty(String name,
		                           Object value) {
			return this.w.setProperty(name,
			                          value);
		}

		@Override
		public XMLStreamLocation2 getLocation() {
			return this.w.getLocation();
		}

		@Override
		public String getEncoding() {
			return this.w.getEncoding();
		}

		@Override
		public void writeCData(char[] text,
		                       int start,
		                       int len) throws XMLStreamException {
			this.w.writeCData(text,
			                  start,
			                  len);
		}

		@Override
		public void writeDTD(String rootName,
		                     String systemId,
		                     String publicId,
		                     String internalSubset) throws XMLStreamException {
			this.w.writeDTD(rootName,
			                systemId,
			                publicId,
			                internalSubset);
		}

		@Override
		public void writeFullEndElement() throws XMLStreamException {
			this.w.writeFullEndElement();
		}

		@Override
		public void writeStartDocument(String version,
		                               String encoding,
		                               boolean standAlone) throws XMLStreamException {
			this.w.writeStartDocument(version,
			                          encoding,
			                          standAlone);
		}

		@Override
		public void writeSpace(String text) throws XMLStreamException {
			this.w.writeSpace(text);
		}

		@Override
		public void writeSpace(char[] text,
		                       int offset,
		                       int length) throws XMLStreamException {
			this.w.writeSpace(text,
			                  offset,
			                  length);
		}

		@Override
		public void writeRaw(String text) throws XMLStreamException {
			this.w.writeRaw(text);
		}

		@Override
		public void writeRaw(String text,
		                     int offset,
		                     int length) throws XMLStreamException {
			this.w.writeRaw(text,
			                offset,
			                length);
		}

		@Override
		public void writeRaw(char[] text,
		                     int offset,
		                     int length) throws XMLStreamException {
			this.w.writeRaw(text,
			                offset,
			                length);
		}

		@Override
		public void copyEventFromReader(XMLStreamReader2 r,
		                                boolean preserveEventData) throws XMLStreamException {
			this.w.copyEventFromReader(r,
			                           preserveEventData);
		}

		@Override
		public void closeCompletely() throws XMLStreamException {
			this.w.closeCompletely();
		}

		@Override
		public void writeBoolean(boolean value) throws XMLStreamException {
			this.w.writeBoolean(value);
		}

		@Override
		public void writeInt(int value) throws XMLStreamException {
			this.w.writeInt(value);
		}

		@Override
		public void writeLong(long value) throws XMLStreamException {
			this.w.writeLong(value);
		}

		@Override
		public void writeFloat(float value) throws XMLStreamException {
			this.w.writeFloat(value);
		}

		@Override
		public void writeDouble(double value) throws XMLStreamException {
			this.w.writeDouble(value);
		}

		@Override
		public void writeInteger(BigInteger value) throws XMLStreamException {
			this.w.writeInteger(value);
		}

		@Override
		public void writeDecimal(BigDecimal value) throws XMLStreamException {
			this.w.writeDecimal(value);
		}

		@Override
		public void writeQName(QName value) throws XMLStreamException {
			this.w.writeQName(value);
		}

		@Override
		public void writeBinary(byte[] value,
		                        int from,
		                        int length) throws XMLStreamException {
			this.w.writeBinary(value,
			                   from,
			                   length);
		}

		@Override
		public void writeBinary(Base64Variant variant,
		                        byte[] value,
		                        int from,
		                        int length) throws XMLStreamException {
			this.w.writeBinary(variant,
			                   value,
			                   from,
			                   length);
		}

		@Override
		public void writeIntArray(int[] value,
		                          int from,
		                          int length) throws XMLStreamException {
			this.w.writeIntArray(value,
			                     from,
			                     length);
		}

		@Override
		public void writeLongArray(long[] value,
		                           int from,
		                           int length) throws XMLStreamException {
			this.w.writeLongArray(value,
			                      from,
			                      length);
		}

		@Override
		public void writeFloatArray(float[] value,
		                            int from,
		                            int length) throws XMLStreamException {
			this.w.writeFloatArray(value,
			                       from,
			                       length);
		}

		@Override
		public void writeDoubleArray(double[] value,
		                             int from,
		                             int length) throws XMLStreamException {
			this.w.writeDoubleArray(value,
			                        from,
			                        length);
		}

		@Override
		public void writeBooleanAttribute(String prefix,
		                                  String namespaceURI,
		                                  String localName,
		                                  boolean value) throws XMLStreamException {
			this.w.writeBooleanAttribute(prefix,
			                             namespaceURI,
			                             localName,
			                             value);
		}

		@Override
		public void writeIntAttribute(String prefix,
		                              String namespaceURI,
		                              String localName,
		                              int value) throws XMLStreamException {
			this.w.writeIntAttribute(prefix,
			                         namespaceURI,
			                         localName,
			                         value);
		}

		@Override
		public void writeLongAttribute(String prefix,
		                               String namespaceURI,
		                               String localName,
		                               long value) throws XMLStreamException {
			this.w.writeLongAttribute(prefix,
			                          namespaceURI,
			                          localName,
			                          value);
		}

		@Override
		public void writeFloatAttribute(String prefix,
		                                String namespaceURI,
		                                String localName,
		                                float value) throws XMLStreamException {
			this.w.writeFloatAttribute(prefix,
			                           namespaceURI,
			                           localName,
			                           value);
		}

		@Override
		public void writeDoubleAttribute(String prefix,
		                                 String namespaceURI,
		                                 String localName,
		                                 double value) throws XMLStreamException {
			this.w.writeDoubleAttribute(prefix,
			                            namespaceURI,
			                            localName,
			                            value);
		}

		@Override
		public void writeIntegerAttribute(String prefix,
		                                  String namespaceURI,
		                                  String localName,
		                                  BigInteger value) throws XMLStreamException {
			this.w.writeIntegerAttribute(prefix,
			                             namespaceURI,
			                             localName,
			                             value);
		}

		@Override
		public void writeDecimalAttribute(String prefix,
		                                  String namespaceURI,
		                                  String localName,
		                                  BigDecimal value) throws XMLStreamException {
			this.w.writeDecimalAttribute(prefix,
			                             namespaceURI,
			                             localName,
			                             value);
		}

		@Override
		public void writeQNameAttribute(String prefix,
		                                String namespaceURI,
		                                String localName,
		                                QName value) throws XMLStreamException {
			this.w.writeQNameAttribute(prefix,
			                           namespaceURI,
			                           localName,
			                           value);
		}

		@Override
		public void writeBinaryAttribute(String prefix,
		                                 String namespaceURI,
		                                 String localName,
		                                 byte[] value) throws XMLStreamException {
			this.w.writeBinaryAttribute(prefix,
			                            namespaceURI,
			                            localName,
			                            value);
		}

		@Override
		public void writeBinaryAttribute(Base64Variant variant,
		                                 String prefix,
		                                 String namespaceURI,
		                                 String localName,
		                                 byte[] value) throws XMLStreamException {
			this.w.writeBinaryAttribute(variant,
			                            prefix,
			                            namespaceURI,
			                            localName,
			                            value);
		}

		@Override
		public void writeIntArrayAttribute(String prefix,
		                                   String namespaceURI,
		                                   String localName,
		                                   int[] value) throws XMLStreamException {
			this.w.writeIntArrayAttribute(prefix,
			                              namespaceURI,
			                              localName,
			                              value);
		}

		@Override
		public void writeLongArrayAttribute(String prefix,
		                                    String namespaceURI,
		                                    String localName,
		                                    long[] value) throws XMLStreamException {
			this.w.writeLongArrayAttribute(prefix,
			                               namespaceURI,
			                               localName,
			                               value);
		}

		@Override
		public void writeFloatArrayAttribute(String prefix,
		                                     String namespaceURI,
		                                     String localName,
		                                     float[] value) throws XMLStreamException {
			this.w.writeFloatArrayAttribute(prefix,
			                                namespaceURI,
			                                localName,
			                                value);
		}

		@Override
		public void writeDoubleArrayAttribute(String prefix,
		                                      String namespaceURI,
		                                      String localName,
		                                      double[] value) throws XMLStreamException {
			this.w.writeDoubleArrayAttribute(prefix,
			                                 namespaceURI,
			                                 localName,
			                                 value);
		}

		@Override
		public XMLValidator validateAgainst(XMLValidationSchema schema) throws XMLStreamException {
			return this.w.validateAgainst(schema);
		}

		@Override
		public XMLValidator stopValidatingAgainst(XMLValidationSchema schema) throws XMLStreamException {
			return this.w.stopValidatingAgainst(schema);
		}

		@Override
		public XMLValidator stopValidatingAgainst(XMLValidator validator) throws XMLStreamException {
			return this.w.stopValidatingAgainst(validator);
		}

		@Override
		public ValidationProblemHandler setValidationProblemHandler(ValidationProblemHandler h) {
			return this.w.setValidationProblemHandler(h);
		}
	}

	Supplier<ObjectMapper> XML_MAPPER = () -> XmlMapper.xmlBuilder()
	                                                   .configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION,
	                                                              true)
	                                                   .configure(MapperFeature.PROPAGATE_TRANSIENT_MARKER,
	                                                              true)
	                                                   .build();

	Supplier<ObjectMapper> JSON_MAPPER = () -> JsonMapper.builder()
	                                                     .configure(MapperFeature.PROPAGATE_TRANSIENT_MARKER,
	                                                                true)
	                                                     .build();

	Supplier<ObjectMapper> YML_MAPPER = () -> YAMLMapper.builder()
	                                                    .configure(MapperFeature.PROPAGATE_TRANSIENT_MARKER,
	                                                               true)
	                                                    .disable(SerializationFeature.INDENT_OUTPUT)
	                                                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

	                                                    .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
	                                                    .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
	                                                    .enable(YAMLGenerator.Feature.SPLIT_LINES)
	                                                    .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
	                                                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
	                                                    .build();

	class MapTypeReference
			extends TypeReference<LinkedHashMap<String, Object>> {
	}

	class ListTypeReference
			extends TypeReference<ArrayList<Object>> {
	}
}
