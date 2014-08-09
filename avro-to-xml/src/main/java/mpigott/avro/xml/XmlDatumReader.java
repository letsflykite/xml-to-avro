/**
 * Copyright 2014 Mike Pigott
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mpigott.avro.xml;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;

import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Reads an XML {@link Document} from a {@link Decoder}.
 * If the {@link Schema} can be conformed to, it will be.
 *
 * @author  Mike Pigott
 */
public class XmlDatumReader implements DatumReader<Document> {

  private static class AvroRecordName implements Comparable<AvroRecordName> {

    AvroRecordName(QName xmlQName) throws URISyntaxException {
      recordNamespace = Utils.getAvroNamespaceFor(xmlQName.getNamespaceURI());
      recordName = xmlQName.getLocalPart();
    }

    /**
     * Generates a hash code representing this <code>AvroRecordName</code>.
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result
          + ((recordName == null) ? 0 : recordName.hashCode());
      result = prime * result
          + ((recordNamespace == null) ? 0 : recordNamespace.hashCode());
      return result;
    }

    /**
     * Compares this <code>AvroRecordName</code> to another one for equality.
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (!(obj instanceof AvroRecordName)) {
        return false;
      }
      AvroRecordName other = (AvroRecordName) obj;
      if (recordName == null) {
        if (other.recordName != null) {
          return false;
        }
      } else if (!recordName.equals(other.recordName)) {
        return false;
      }
      if (recordNamespace == null) {
        if (other.recordNamespace != null) {
          return false;
        }
      } else if (!recordNamespace.equals(other.recordNamespace)) {
        return false;
      }
      return true;
    }

    /**
     * Compares this <code>AvroRecordName</code> to another for
     * relative ordering.  Namespaces are compared first, followed
     * by names.
     *
     * @param o The other record to compare against.
     *
     * @return A number less than zero if this entry is the lesser,
     *         a number greater than zero if this entry is the greater,
     *         or zero if the two are equivalent.
     *
     * @see Comparable#compareTo(Object)
     */
    @Override
    public int compareTo(AvroRecordName o) {

      // 1. Compare Namespaces.
      if ((recordNamespace == null) && (o.recordNamespace != null)) {
        return -1;
      } else if ((recordNamespace != null) && (o.recordNamespace == null)) {
        return 1;
      } else if ((recordNamespace != null) && (o.recordNamespace != null)) {
        final int nsCompare = recordNamespace.compareTo(o.recordNamespace);
        if (nsCompare != 0) {
          return nsCompare;
        }
      }

      // Either both namespaces are null, or they are equal to each other.

      // 2. Compare Names.
      if ((recordName == null) && (o.recordName != null)) {
        return -1;
      } else if ((recordName != null) && (o.recordName == null)) {
        return 1;
      } else if ((recordName != null) && (o.recordName != null)) {
        final int nmCompare = recordName.compareTo(o.recordName);
        if (nmCompare != 0) {
          return nmCompare;
        }
      }

      // Either both names are null, or both are equal.

      return 0;
    }

    private String recordName;
    private String recordNamespace;
  }

  private static class AvroAttribute {

    AvroAttribute(
        String namespace,
        String localName,
        String qualName,
        String val) {

      qName = new QName(namespace, localName);
      qualifiedName = qualName;
      value = val;
    }

    final QName qName;
    final String qualifiedName;
    final String value;
  }

  private static class AvroAttributes implements Attributes {

    AvroAttributes() throws SAXException {
      attributes = new ArrayList<AvroAttribute>();
      attrsByQualifiedName = new HashMap<String, AvroAttribute>();
      attrsByQName = new HashMap<QName, AvroAttribute>();

      indexByQualifiedName = new HashMap<String, Integer>();
      indexByQName = new HashMap<QName, Integer>();
    }

    void addAttribute(AvroAttribute attr) {
      attrsByQualifiedName.put(attr.qualifiedName, attr);
      attrsByQName.put(attr.qName, attr);

      indexByQualifiedName.put(attr.qualifiedName, attributes.size());
      indexByQName.put(attr.qName, attributes.size());

      attributes.add(attr);
    }

    @Override
    public int getLength() {
      return attributes.size();
    }

    @Override
    public String getURI(int index) {
      if (attributes.size() <= index) {
        return null;
      } else {
        return attributes.get(index).qName.getNamespaceURI();
      }
    }

    @Override
    public String getLocalName(int index) {
      if (attributes.size() <= index) {
        return null;
      } else {
        return attributes.get(index).qName.getLocalPart();
      }
    }

    @Override
    public String getQName(int index) {
      if (attributes.size() <= index) {
        return null;
      } else {
        return attributes.get(index).qualifiedName;
      }
    }

    @Override
    public String getType(int index) {
      if (attributes.size() <= index) {
        return null;
      } else {
        return "CDATA"; // We do not know the type information.
      }
    }

    @Override
    public String getValue(int index) {
      if (attributes.size() <= index) {
        return null;
      } else {
        return attributes.get(index).value;
      }
    }

    @Override
    public int getIndex(String uri, String localName) {
      if ((uri == null) || (localName == null)) {
        return -1;
      }

      final QName qName = new QName(uri, localName);
      final Integer index = indexByQName.get(qName);

      if (index == null) {
        return -1;
      } else {
        return index;
      }
    }

    @Override
    public int getIndex(String qName) {
      if (qName == null) {
        return -1;
      }

      final Integer index = indexByQualifiedName.get(qName);
      if (index == null) {
        return -1;
      } else {
        return index;
      }
    }

    @Override
    public String getType(String uri, String localName) {
      if ((uri == null) || (localName == null)) {
        return null;
      } else {
        final AvroAttribute attr =
            attrsByQName.get( new QName(uri, localName) );
        return (attr == null) ? null : "CDATA";
      }
    }

    @Override
    public String getType(String qName) {
      if (qName == null) {
        return null;
      } else {
        final AvroAttribute attr = attrsByQualifiedName.get(qName);
        return (attr == null) ? null : "CDATA";
      }
    }

    @Override
    public String getValue(String uri, String localName) {
      if ((uri == null) || (localName == null)) {
        return null;
      } else {
        final AvroAttribute attr =
            attrsByQName.get( new QName(uri, localName) );
        return (attr == null) ? null : attr.value;
      }
    }

    @Override
    public String getValue(String qName) {
      if (qName == null) {
        return null;
      } else {
        final AvroAttribute attr = attrsByQualifiedName.get(qName);
        return (attr == null) ? null : attr.value;
      }
    }

    private final List<AvroAttribute> attributes;
    private final Map<String, AvroAttribute> attrsByQualifiedName;
    private final Map<QName, AvroAttribute> attrsByQName;
    private final Map<String, Integer> indexByQualifiedName;
    private final Map<QName, Integer> indexByQName;
  }

  /**
   * Creates an {@link XmlDatumReader} with the {@link XmlSchemaCollection}
   * to use when decoding XML {@link Document}s from {@link Decoder}s.
   */
  public XmlDatumReader() {
    inputSchema = null;
    xmlSchemaCollection = null;
    namespaceToLocationMapping = null;
    contentHandlers = null;
    domBuilder = null;
  }

  /**
   * Sets the {@link Schema} that defines how data will be read from the
   * {@link Decoder} when {@link #read(Document, Decoder)} is called.
   *
   * <p>
   * Checks the input {@link Schema} conforms with the provided
   * {@link XmlSchemaCollection}.  If <code>schema</code> does not conform,
   * an {@link IllegalArgumentException} is thrown.
   * </p>
   *
   * @throws IllegalArgumentException if the schema is <code>null</code> or
   *                                  does not conform to the corresponding
   *                                  XML schema.
   *
   * @see org.apache.avro.io.DatumReader#setSchema(org.apache.avro.Schema)
   */
  @Override
  public void setSchema(Schema schema) {
	  if (schema == null) {
	    throw new IllegalArgumentException("Input schema cannot be null.");
	  }

	  final JsonNode xmlSchemasNode = schema.getJsonProp("xmlSchemas");
	  if (xmlSchemasNode == null) {
	    throw new IllegalArgumentException("Avro schema must be created by XmlDatumWriter for it to be used with XmlDatumReader.");
	  }

	  final JsonNode baseUriNode = xmlSchemasNode.get("baseUri");
	  final JsonNode urlsNode    = xmlSchemasNode.get("urls");
	  final JsonNode filesNode   = xmlSchemasNode.get("files");
	  final JsonNode rootTagNode = xmlSchemasNode.get("rootTag");

	  XmlDatumConfig config = null;

	  // 1. Build the root tag QName.
	  QName rootTagQName = buildQNameFrom(rootTagNode);

	  // 2. Build the list of schema files.
	  if (filesNode != null) {
	    String baseUri = null;
	    if (baseUriNode != null) {
	      baseUri = baseUriNode.getTextValue();
	    }
	    if (baseUri == null) {
        throw new IllegalArgumentException("When building an XML Schema from files, a Base URI is required and must be in a text JSON node.");
	    }

	    final List<File> files = buildFileListFrom(filesNode);
	    if ( !files.isEmpty() ) {
	      config = new XmlDatumConfig(files.get(0), baseUri, rootTagQName);

	      for (int fileIndex = 1; fileIndex < files.size(); ++fileIndex) {
	        config.addSchemaFile( files.get(fileIndex) );
	      }
	    }
	  }

	  // 3. Build the list of schema URLs.
	  if (urlsNode != null) {
	    List<URL> urls = buildUrlListFrom(urlsNode);

	    if ( !urls.isEmpty() ) {
  	    int startIndex = 0;
  	    if (config == null) {
  	      startIndex = 1;
  	      config = new XmlDatumConfig(urls.get(0), rootTagQName);
  	    }

  	    for (int urlIndex = startIndex; urlIndex < urls.size(); ++urlIndex) {
  	      config.addSchemaUrl( urls.get(urlIndex) );
  	    }
	    }
	  }

	  if (config == null) {
	    throw new IllegalArgumentException("At least one XML Schema file or URL must be defined in the xmlSchemas property.");
	  }

	  /* 4. Build the xmlSchemaCollection, its namespace -> location
	   *    mapping, and an Avro Record Name -> XML QName mapping.
	   */
	  final HashMap<QName, AvroRecordName> avroNameMapping =
	      new HashMap<QName, AvroRecordName>();

	  if (namespaceToLocationMapping == null) {
	    namespaceToLocationMapping = new HashMap<String, String>();
	  } else {
	    namespaceToLocationMapping.clear();
	  }

	  xmlSchemaCollection = new XmlSchemaCollection();
    xmlSchemaCollection.setSchemaResolver(new XmlSchemaMultiBaseUriResolver());
    xmlSchemaCollection.setBaseUri(config.getBaseUri());
    try {
      for (StreamSource source : config.getSources()) {
        final XmlSchema xmlSchema = xmlSchemaCollection.read(source);
        namespaceToLocationMapping.put(
            xmlSchema.getTargetNamespace(),
            source.getSystemId());

        final Map<QName, XmlSchemaElement> elementsByQName =
            xmlSchema.getElements();

        for (QName elemQName : elementsByQName.keySet()) {
          try {
            avroNameMapping.put(elemQName, new AvroRecordName(elemQName));
          } catch (URISyntaxException e) {
            throw new IllegalStateException(elemQName + " has a namespace of \"" + elemQName.getNamespaceURI() + "\" that is not a valid URI.", e);
          }
        }
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Not all of the schema sources could be read from.", e);
    }

    // 5. Build the state machine.
    final XmlSchemaStateMachineGenerator stateMachineGen =
        new XmlSchemaStateMachineGenerator();

    final XmlSchemaWalker walker =
        new XmlSchemaWalker(xmlSchemaCollection, stateMachineGen);
    walker.setUserRecognizedTypes( Utils.getAvroRecognizedTypes() );

    final XmlSchemaElement rootElement =
        xmlSchemaCollection.getElementByQName(config.getRootTagName());
    walker.walk(rootElement);

    try {
      domBuilder = new DomBuilderFromSax();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("Cannot configure the DOM Builder.", e);
    }
    domBuilder.setNamespaceToLocationMapping(namespaceToLocationMapping);

    final XmlSchemaStateMachineNode stateMachine =
        stateMachineGen.getStartNode();

    // 6. Build an AvroRecordName -> XmlSchemaStateMachineNode mapping.
    final Map<QName, XmlSchemaStateMachineNode> stateMachineNodesByQName =
        stateMachineGen.getStateMachineNodesByQName();

    stateByAvroName = new HashMap<AvroRecordName, XmlSchemaStateMachineNode>();
    for (Map.Entry<QName, XmlSchemaStateMachineNode> entry :
      stateMachineNodesByQName.entrySet()) {

      final AvroRecordName recordName = avroNameMapping.get(entry.getKey());
      stateByAvroName.put(recordName, entry.getValue());
    }

    inputSchema = schema;

	  contentHandlers = new ArrayList<ContentHandler>(2);
	  contentHandlers.add( new XmlSchemaPathFinder(stateMachine) );
	  contentHandlers.add(domBuilder);
  }

  /**
   * Reads the XML {@link Document} from the input {@link Decoder} and
   * returns it, transformed.  The <code>reuse</code> {@link Document}
   * will not be used, as {@link Document} re-use is difficult.
   *
   * @see DatumReader#read(Object, Decoder)
   */
  @Override
  public Document read(Document reuse, Decoder in) throws IOException {
    if ((inputSchema == null)
        || (xmlSchemaCollection == null)
        || (domBuilder == null)
        || (contentHandlers == null)) {
      throw new IllegalStateException(
          "The Avro and XML Schemas must be defined before reading from an "
          + "Avro Decoder.  Please call XmlDatumReader.setSchema(Schema) "
          + "before calling this function.");
    }

    try {
      for (ContentHandler contentHandler : contentHandlers) {
        contentHandler.startDocument();
      }
    } catch (SAXException e) {
      throw new IOException("Unable to create the new document.", e);
    }

    processElement(inputSchema);

    try {
      for (ContentHandler contentHandler : contentHandlers) {
        contentHandler.endDocument();
      }
    } catch (SAXException e) {
      throw new IOException("Unable to create the new document.", e);
    }

    return domBuilder.getDocument();
  }

  private void processElement(Schema elemSchema) {
    if ( !elemSchema.getType().equals(Schema.Type.RECORD) ) {
      throw new IllegalStateException(
          "Expected to process a RECORD, but found a \""
          + elemSchema.getType()
          + "\" instead.");
    }

    final List<Schema.Field> fields = elemSchema.getFields();
    for (int index = 0; index < (fields.size() - 1); ++index) {
      // The first N-1 fields are attributes.
      
    }
  }

  private void processContent(Schema contentSchema) {
    
  }

  private static QName buildQNameFrom(JsonNode rootTagNode) {
    if (rootTagNode == null) {
      throw new IllegalArgumentException("The xmlSchemas property in the schema must have a root tag defined.");
    }

    final JsonNode namespaceNode = rootTagNode.get("namespace");
    if (namespaceNode == null) {
      throw new IllegalArgumentException("The rootTag object must have a namespace field.");
    }

    final JsonNode localPartNode = rootTagNode.get("localPart");
    if (localPartNode == null) {
      throw new IllegalArgumentException("The rootTag object must have a localPart field.");
    }

    final String namespace = namespaceNode.getTextValue();
    if ((namespace == null) || namespace.isEmpty()) {
      throw new IllegalArgumentException("The namespace field of the rootTag object must be a non-empty text field.");
    }

    final String localPart = localPartNode.getTextValue();
    if ((localPart == null) || localPart.isEmpty()) {
      throw new IllegalArgumentException("The localPart field of the rootTag object must be a non-empty text field.");
    }

    return new QName(namespace, localPart);
  }

  private static List<File> buildFileListFrom(JsonNode filesNode) {
    if ( !filesNode.isArray() ) {
      throw new IllegalArgumentException("The \"files\" field under xmlSchemas must be an array of file paths.");
    }
    final ArrayList<File> files = new ArrayList<File>( filesNode.size() );

    for (int fileIndex = 0; fileIndex < filesNode.size(); ++fileIndex) {
      final JsonNode fileNode = filesNode.get(fileIndex);
      final String filePath = fileNode.getTextValue();
      if ((filePath == null) || filePath.isEmpty()) {
        throw new IllegalArgumentException("The file in the files array at index " + fileIndex + " is either empty or not a string node.");
      }
      files.add( new File(filePath) );
    }

    return files;
  }

  private static List<URL> buildUrlListFrom(JsonNode urlsNode) {
    if ( !urlsNode.isArray() ) {
      throw new IllegalArgumentException("The \"urls\" field under xmlSchemas must be an array of URLs.");
    }
    final ArrayList<URL> urls = new ArrayList<URL>( urlsNode.size() );

    for (int urlIndex = 0; urlIndex < urlsNode.size(); ++urlIndex) {
      final JsonNode urlNode = urlsNode.get(urlIndex);
      final String url = urlNode.getTextValue();
      if ((url == null) || url.isEmpty()) {
        throw new IllegalArgumentException("The URL in the URLs array at index " + urlIndex + " is either empty or not a string node.");
      }
      try {
        urls.add( new URL(url) );
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException("The URL in the URLs array at index " + urlIndex + " is malformed.", e);
      }
    }

    return urls;
  }

  private Schema inputSchema;
  private XmlSchemaCollection xmlSchemaCollection;
  private HashMap<String, String> namespaceToLocationMapping;
  private List<ContentHandler> contentHandlers;
  private DomBuilderFromSax domBuilder;
  private Map<AvroRecordName, XmlSchemaStateMachineNode> stateByAvroName;
}
