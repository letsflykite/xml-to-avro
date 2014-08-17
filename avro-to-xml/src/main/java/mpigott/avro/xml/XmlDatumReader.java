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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;
import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;

import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.constants.Constants;
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
      namespace = Utils.getAvroNamespaceFor(xmlQName.getNamespaceURI());
      name = xmlQName.getLocalPart();
    }

    AvroRecordName(String recordNamespace, String recordName) {
      namespace = recordNamespace;
      name = recordName;
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
          + ((name == null) ? 0 : name.hashCode());
      result = prime * result
          + ((namespace == null) ? 0 : namespace.hashCode());
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
      if (name == null) {
        if (other.name != null) {
          return false;
        }
      } else if (!name.equals(other.name)) {
        return false;
      }
      if (namespace == null) {
        if (other.namespace != null) {
          return false;
        }
      } else if (!namespace.equals(other.namespace)) {
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
      if ((namespace == null) && (o.namespace != null)) {
        return -1;
      } else if ((namespace != null) && (o.namespace == null)) {
        return 1;
      } else if ((namespace != null) && (o.namespace != null)) {
        final int nsCompare = namespace.compareTo(o.namespace);
        if (nsCompare != 0) {
          return nsCompare;
        }
      }

      // Either both namespaces are null, or they are equal to each other.

      // 2. Compare Names.
      if ((name == null) && (o.name != null)) {
        return -1;
      } else if ((name != null) && (o.name == null)) {
        return 1;
      } else if ((name != null) && (o.name != null)) {
        final int nmCompare = name.compareTo(o.name);
        if (nmCompare != 0) {
          return nmCompare;
        }
      }

      // Either both names are null, or both are equal.

      return 0;
    }

    @Override
    public String toString() {
      return '{' + namespace + '}' + name;
    }

    private String name;
    private String namespace;
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

    AvroAttributes() {
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

  private static final BigDecimal MAX_UNSIGNEDLONG =
      new BigDecimal("18446744073709551615");

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
    bytesBuffer = null;
    nsContext = new XmlSchemaNamespaceContext();
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

	  JsonNode xmlSchemasNode = schema.getJsonProp("xmlSchemas");

	  if ((xmlSchemasNode == null)
	      && schema.getType().equals(Schema.Type.UNION)) {
	    /* The root node is a substitution group; the
	     * xmlSchemasNode was stored with its first child. 
	     */
	    xmlSchemasNode = schema.getTypes().get(0).getJsonProp("xmlSchemas");
	  }

	  if (xmlSchemasNode == null) {
	    throw new IllegalArgumentException(
	        "Avro schema must be created by XmlDatumWriter for it to be used"
	        + " with XmlDatumReader.");
	  }

    nsContext.clear();
    currNsNum = 0;

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

	  // 4. Build the xmlSchemaCollection and its namespace -> location mapping.

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
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Not all of the schema sources could be read from.", e);
    }

    // 6. Build the state machine.
    final XmlSchemaStateMachineGenerator stateMachineGen =
        new XmlSchemaStateMachineGenerator();

    final XmlSchemaWalker walker =
        new XmlSchemaWalker(xmlSchemaCollection, stateMachineGen);
    walker.setUserRecognizedTypes( Utils.getAvroRecognizedTypes() );

    final XmlSchemaElement rootElement =
        xmlSchemaCollection.getElementByQName(config.getRootTagName());
    walker.walk(rootElement);

    try {
      domBuilder = new DomBuilderFromSax(xmlSchemaCollection);
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("Cannot configure the DOM Builder.", e);
    }
    domBuilder.setNamespaceToLocationMapping(namespaceToLocationMapping);

    final XmlSchemaStateMachineNode stateMachine =
        stateMachineGen.getStartNode();

    /* 7. Build an AvroRecordName -> XmlSchemaStateMachineNode mapping, a
     *    QName -> XmlSchemaElement mapping, and a namespace prefix mapping.
     */
    final Map<QName, XmlSchemaStateMachineNode> stateMachineNodesByQName =
        stateMachineGen.getStateMachineNodesByQName();

    final Map<QName, XmlSchemaElement> elementsByQName =
        new HashMap<QName, XmlSchemaElement>();

    stateByAvroName = new HashMap<AvroRecordName, XmlSchemaStateMachineNode>();
    for (Map.Entry<QName, XmlSchemaStateMachineNode> entry :
      stateMachineNodesByQName.entrySet()) {

      elementsByQName.put(entry.getKey(), entry.getValue().getElement());

      try {
        stateByAvroName.put(new AvroRecordName(entry.getKey()), entry.getValue());
      } catch (URISyntaxException e) {
        throw new IllegalArgumentException(
            entry.getKey()
            + "'s namespace of \""
            + entry.getKey().getNamespaceURI()
            + "\" is not a valid URI.",
            e);
      }

      final String prefix =
          nsContext.getPrefix( entry.getKey().getNamespaceURI() );
      if (prefix == null) {
        nsContext.addNamespace(
            "ns" + currNsNum,
            entry.getKey().getNamespaceURI());
        ++currNsNum;
      }
    }

    domBuilder.setElementsByQName(elementsByQName);

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

    final String[] prefixes = nsContext.getDeclaredPrefixes();
    try {
      for (ContentHandler contentHandler : contentHandlers) {
        contentHandler.startDocument();

        for (String prefix : prefixes) {
          contentHandler.startPrefixMapping(
              prefix,
              nsContext.getNamespaceURI(prefix));
        }
      }

    } catch (Exception e) {
      throw new IOException("Unable to create the new document.", e);
    }

    /* If the root node is part of a substitution
     * group, retrieve the corresponding schema.
     */
    Schema rootSchema = inputSchema;
    if ( rootSchema.getType().equals(Schema.Type.UNION) ) {
      final int unionIndex = in.readIndex();
      rootSchema = rootSchema.getTypes().get(unionIndex);
    }

    processElement(rootSchema, in);

    try {
      for (ContentHandler contentHandler : contentHandlers) {
        for (String prefix : prefixes) {
          contentHandler.endPrefixMapping(prefix);
        }

        contentHandler.endDocument();
      }
    } catch (Exception e) {
      throw new IOException("Unable to create the new document.", e);
    }

    return domBuilder.getDocument();
  }

  private void processElement(Schema elemSchema, Decoder in)
      throws IOException {

    if ( !elemSchema.getType().equals(Schema.Type.RECORD) ) {
      throw new IllegalStateException(
          "Expected to process a RECORD, but found a \""
          + elemSchema.getType()
          + "\" instead.");
    }

    final AvroRecordName recordName =
        new AvroRecordName(elemSchema.getNamespace(), elemSchema.getName());

    final XmlSchemaStateMachineNode stateMachine =
        stateByAvroName.get(recordName);

    if (stateMachine == null) {
      throw new IllegalStateException(
          "Cannot find state machine for "
          + recordName);

    } else if (!stateMachine
                  .getNodeType()
                  .equals(XmlSchemaStateMachineNode.Type.ELEMENT) ) {

      throw new IllegalStateException(
          "State machine for "
          + recordName
          + " is of type "
          + stateMachine.getNodeType()
          + ", not ELEMENT.");
    }

    final List<XmlSchemaStateMachineNode.Attribute> expectedAttrs =
        stateMachine.getAttributes();

    final AvroAttributes attributes = new AvroAttributes();

    final List<Schema.Field> fields = elemSchema.getFields();

    // The first N-1 fields are attributes.
    for (int index = 0; index < (fields.size() - 1); ++index) {
      try {
        final AvroAttribute attr =
            createAttribute(expectedAttrs, fields.get(index), in);
        if (attr != null) {
          attributes.addAttribute(attr);
        }
      } catch (IOException ioe) {
        throw new IOException("Failed to create attribute for element " + stateMachine.getElement().getQName(), ioe);
      }
    }

    /* It is possible that the element's child is a QName in a
     * new namespace we have not seen before.  SAX requires us
     * to add all new namespaces *before* the corresponding
     * element, so if this is a simple type, we need to fetch
     * the content now and determine if we need to adjust the
     * namespace context accordingly.
     */
    final QName elemQName = stateMachine.getElement().getQName();
    final Schema.Field childField = elemSchema.getField(elemSchema.getName());
    final XmlSchemaTypeInfo elemType = stateMachine.getElementType();

    String content = null;
    switch ( elemType.getType() ) {
    case ATOMIC:
    case LIST:
    case UNION:
      {
        content = readSimpleType(childField.schema(), elemQName, elemType, in);
        break;
      }
    default:
      // Nothing to do here.
    }

    List<String> newPrefixes = null;
    if ((newlyAddedQNamesToNs != null) && !newlyAddedQNamesToNs.isEmpty()) {
      newPrefixes = new ArrayList<String>( newlyAddedQNamesToNs.size() );
      for (QName qName : newlyAddedQNamesToNs) {
        final String ns = qName.getNamespaceURI();
        final String prefix = qName.getPrefix();
        newPrefixes.add(prefix);
        for (ContentHandler contentHandler : contentHandlers) {
          try {
            contentHandler.startPrefixMapping(prefix, ns);
          } catch (Exception e) {
            throw new IOException(
                "Cannot add namespace " + ns
                + " and prefix " + prefix
                + " to content handlers.",
                e);
          }
        }
      }
      newlyAddedQNamesToNs.clear();
    }

    // Determine the namespace, local name, and qualified name.
    final String prefix = nsContext.getPrefix(elemQName.getNamespaceURI());
    String qName = null;
    if (prefix == null) {
      qName = elemQName.getLocalPart();
    } else {
      qName = prefix + ':' + elemQName.getLocalPart();
    }

    // Notify the content handlers an element has begun.
    for (ContentHandler contentHandler : contentHandlers) {
      try {
        contentHandler.startElement(
            elemQName.getNamespaceURI(),
            elemQName.getLocalPart(),
            qName,
            attributes);

      } catch (Exception e) {
        throw new IOException("Cannot start element " + elemQName + '.', e);
      }
    }

    switch ( elemType.getType() ) {
    case ATOMIC:
    case LIST:
    case UNION:
      {
        processContent(content);
        break;
      }
    case COMPLEX:
      processComplexChildren(
          childField,
          stateMachine.getElement(),
          elemType,
          in);
      break;
    default:
      throw new IllegalStateException(
          elemQName + " has an unrecognized type named " + elemType.getType());
    }

    // Notify the content handlers the element has ended.
    for (ContentHandler contentHandler : contentHandlers) {
      try {
        contentHandler.endElement(
            elemQName.getNamespaceURI(),
            elemQName.getLocalPart(),
            qName);

        if (newPrefixes != null) {
          for (String newPrefix : newPrefixes) {
            contentHandler.endPrefixMapping(newPrefix);
          }
        }

      } catch (Exception e) {
        throw new IOException("Cannot start element " + elemQName + '.', e);
      }
    }

    // Also remove any newly added prefixes from our namespace context.
    if (newPrefixes != null) {
      for (String newPrefix : newPrefixes) {
        nsContext.removeNamespace(newPrefix);
      }
    }
  }

  private AvroAttribute createAttribute(
      List<XmlSchemaStateMachineNode.Attribute> expectedAttrs,
      Schema.Field field,
      Decoder in)
  throws IOException {

    AvroAttribute attribute = null;

    for (XmlSchemaStateMachineNode.Attribute attr : expectedAttrs) {
      final QName attrQName = attr.getAttribute().getQName();
      if (field.name().equals(attrQName.getLocalPart())) {
        try {
          final String value =
              readSimpleType(field.schema(), attrQName, attr.getType(), in);
  
          if (value != null) {
            final String prefix =
                nsContext.getPrefix(attrQName.getNamespaceURI());
    
            String qualifiedName = null;
            if (prefix == null) {
              qualifiedName = attrQName.getLocalPart();
            } else {
              qualifiedName = prefix + ':' + attrQName.getLocalPart();
            }

            attribute =
                new AvroAttribute(
                    attrQName.getNamespaceURI(),
                    attrQName.getLocalPart(),
                    qualifiedName,
                    value);
          }
        } catch (Exception e) {
          throw new IOException("Cannot generate attribute " + attr.getAttribute().getQName(), e);
        }

        break;
      }
    }

    return attribute;
  }

  private void processContent(
      Schema.Field field,
      QName elemQName,
      XmlSchemaTypeInfo xmlType,
      Decoder in)
      throws IOException {

    processContent( readSimpleType(field.schema(), elemQName, xmlType, in) );
  }

  private void processContent(String content) throws IOException {
    if (content == null) {
      return;
    }

    if ((newlyAddedQNamesToNs != null) && !newlyAddedQNamesToNs.isEmpty()) {
      
    }

    final char[] chars = content.toCharArray();

    for (ContentHandler contentHandler : contentHandlers) {
      try {
        contentHandler.characters(chars, 0, chars.length);
      } catch (SAXException e) {
        throw new IOException(
            "Cannot process content \"" + content + "\".", e);
      }
    }
  }

  private String readSimpleType(
      Schema schema,
      QName typeQName,
      XmlSchemaTypeInfo xmlType,
      Decoder in)
      throws IOException {

    switch ( schema.getType() ) {
    case ARRAY:
      {
        if (!xmlType.getType().equals(XmlSchemaTypeInfo.Type.LIST)) {
          throw new IllegalStateException(
              "Avro Schema is of type ARRAY, but the XML Schema is of type "
              + xmlType.getType());
        }

        final StringBuilder result = new StringBuilder();
        final XmlSchemaTypeInfo xmlElemType = xmlType.getChildTypes().get(0);
        final Schema elemType = schema.getElementType();

        for (long arrayBlockSize = in.readArrayStart();
             arrayBlockSize > 0;
             arrayBlockSize = in.arrayNext()) {
          for (long itemNum = 0; itemNum < arrayBlockSize; ++itemNum) {
            result.append(
                readSimpleType(elemType, typeQName, xmlElemType, in));
            result.append(' ');
          }
        }
        result.delete(result.length() - 1, result.length());

        return result.toString();
      }
    case UNION:
      {
        final int unionIndex = in.readIndex();

        if (schema.getTypes().size() <= unionIndex) {
          throw new IllegalStateException("Attempted to read from union index "
              + unionIndex + " but the Avro Schema has "
              + schema.getTypes().size() + " types, and the XML Schema has "
              + xmlType.getChildTypes().size() + " types.");
        }

        final Schema elemType = schema.getTypes().get(unionIndex);

        XmlSchemaTypeInfo xmlElemType = xmlType;
        if ( xmlType.getType().equals(XmlSchemaTypeInfo.Type.UNION) ) {
          /* Utils.getAvroSchemaFor() will add a NULL type and/or a STRING
           * type on the end of the XML types to account for optional values
           * and mixed elements, respectively.
           *
           * In addition, if multiple XML Types resolve to the same Avro type,
           * the duplicates were purged.  Likewise, we need to rotate through
           * all of the XML union types, and go with the first XML type that
           * translates to the same Avro type.
           *
           * This approach works fine for the current mappings, but may show
           * poor results when date types are added to Avro.  That is because
           * there are 8 different date types in XML, but there will be only
           * one in Avro.
           */
          xmlElemType =
              Utils.chooseUnionType(xmlType, typeQName, elemType, unionIndex);

          return readSimpleType(elemType, typeQName, xmlElemType, in);

        } else {
          /* The same XML Type applies; the union
           * is for optional & mixed types. 
           */
          return readSimpleType(elemType, typeQName, xmlElemType, in);
        }
      }
    case BYTES:
      {
        bytesBuffer = in.readBytes(bytesBuffer);
        final int numBytes = bytesBuffer.remaining();
        final byte[] data = new byte[numBytes];
        bytesBuffer.get(data, 0, numBytes);

        switch ( xmlType.getBaseType() ) {
        case BIN_HEX:
          return DatatypeConverter.printHexBinary(data);

        case BIN_BASE64:
          return DatatypeConverter.printBase64Binary(data);

        default:
          throw new IllegalStateException(
              "Avro Schema is of type BYTES, but the XML Schema is of type "
              + xmlType.getBaseType() + '.');
        }
      }
    case NULL:
      {
        in.readNull();
        return null;
      }
    case BOOLEAN:
      return DatatypeConverter.printBoolean( in.readBoolean() );

    case DOUBLE:
      {
        final double value = in.readDouble();

        switch ( xmlType.getBaseType() ) {
        case DECIMAL:
          {
            BigDecimal result = new BigDecimal(value);

            if (xmlType
                  .getUserRecognizedType()
                  .equals(Constants.XSD_UNSIGNEDLONG)) {

              if (result.compareTo(MAX_UNSIGNEDLONG) > 0) {
                result = MAX_UNSIGNEDLONG;
              }
            }

            return DatatypeConverter.printDecimal(result);
          }

        case DOUBLE:
          return DatatypeConverter.printDouble(value);

        default:
          throw new IOException(
              "Avro Schema is of type DOUBLE, but the XML Schema is of type "
              + xmlType.getBaseType() + '.');
        }
      }
    case ENUM:
      return schema.getEnumSymbols().get( in.readEnum() );

    case FLOAT:
      return DatatypeConverter.printFloat( in.readFloat() );

    case INT:
      return DatatypeConverter.printInt( in.readInt() );

    case LONG:
      return DatatypeConverter.printLong( in.readLong() );

    case STRING:
      return DatatypeConverter.printString( in.readString() );

    case RECORD:
      {
        switch ( xmlType.getBaseType() ) {
        case QNAME:
          {
            final String ns = in.readString();
            final String lp = in.readString();

            QName qName = null;
            boolean isNew = false;

            if ( !ns.isEmpty() ) {
              String prefix = nsContext.getPrefix(ns);

              if (prefix == null) {
                isNew = true;
                prefix = "ns" + currNsNum;
                nsContext.addNamespace(prefix, ns);
                ++currNsNum;
              }

              qName = new QName(ns, lp, prefix);
            } else {
              qName = new QName(lp);
            }

            /* While we need to add the namespace to the context so we can
             * properly generate a qualified name, we also need to notify
             * our content handlers of the namespace declaration.
             *
             * We cannot do that here, because we cannot properly open and
             * close the scope.  newlyAddedQNamesToNs will be checked in
             * a place that can properly handle the scope requirements.
             */
            if (isNew) {
              if (newlyAddedQNamesToNs == null) {
                newlyAddedQNamesToNs = new ArrayList<QName>(1);
              }
              newlyAddedQNamesToNs.add(qName);
            }

            return DatatypeConverter.printQName(qName, nsContext);
          }
        default:
          throw new IOException(
              "Avro Schema is of type RECORD, but the XML Schema is of type "
              + xmlType.getBaseType() + '.');
        }
      }

    default:
      throw new IOException(schema.getType() + " is not a simple type.");
    }
  }

  private void processComplexChildren(
      Schema.Field field,
      XmlSchemaElement element,
      XmlSchemaTypeInfo elemType,
      Decoder in)
      throws IOException {

    final Schema fieldSchema = field.schema();

    switch (fieldSchema.getType()) {
    case NULL:
      // This element has no children.
      in.readNull();
      break;
    case STRING:
      if ( elemType.isMixed() ) {
        processContent( in.readString() );
      } else {
        throw new IllegalStateException(
            element.getQName()
            + " has textual content but is not a mixed type.");
      }
      break;
    case ARRAY:
      {
        final Schema elemSchema = field.schema().getElementType();
        if ( !elemSchema.getType().equals(Schema.Type.UNION) ) {
          throw new IOException(
              element.getQName()
              + " has a child field of ARRAY of " + elemSchema.getType()
              + " where ARRAY of UNION was expected.");
        }

        for (long arrayBlockSize = in.readArrayStart();
            arrayBlockSize > 0;
            arrayBlockSize = in.arrayNext()) {

          for (long index = 0; index < arrayBlockSize; ++index) {
            final int unionIndex = in.readIndex();
            final Schema unionSchema = elemSchema.getTypes().get(unionIndex);
            switch ( unionSchema.getType() ) {
            case MAP:
              {
                for (long mapBlockSize = in.readMapStart();
                     mapBlockSize > 0;
                     mapBlockSize = in.mapNext()) {
                  for (long mapIdx = 0; mapIdx < mapBlockSize; ++mapIdx) {
                    in.skipString(); // The key is irrelevant.

                    // MAP of UNION of RECORD or MAP of RECORD
                    final Schema valueType = unionSchema.getValueType();
                    if ( valueType.getType().equals(Schema.Type.RECORD) ) {
                      processElement(valueType, in);

                    } else if (valueType.getType().equals(Schema.Type.UNION)) {
                      final int mapUnionIndex = in.readIndex();
                      processElement(valueType.getTypes().get(mapUnionIndex), in);

                    } else {
                      throw new IOException(
                          "Received a MAP of "
                          + valueType.getType()
                          + " when either MAP of RECORD"
                          + " or MAP of UNION of RECORD was expected.");
                    }
                  }
                }
                break;
              }
            case RECORD:
              processElement(unionSchema, in);
              break;
            case STRING:
              if ( elemType.isMixed() ) {
                processContent( in.readString() );
              } else {
                throw new IOException(
                    "Received a STRING for non-mixed type element "
                    + element.getQName());
              }
              break;
            default:
              throw new IOException(
                  element.getQName()
                  + " has a child field of ARRAY of UNION with "
                  + unionSchema.getType()
                  + " where ARRAY of UNION of either MAP or RECORD was"
                  + " expected.");
            }
          }
        }
        break;
      }
    default:
      throw new IOException(
          element.getQName()
          + " has an invalid complex content of type "
          + fieldSchema.getType() + '.');
    }
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
  private XmlSchemaNamespaceContext nsContext;
  private int currNsNum;
  private ArrayList<QName> newlyAddedQNamesToNs;
  private ByteBuffer bytesBuffer;
}
