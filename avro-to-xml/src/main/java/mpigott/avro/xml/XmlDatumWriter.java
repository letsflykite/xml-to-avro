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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.bind.DatatypeConverter;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;

import org.apache.avro.Schema;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.ws.commons.schema.XmlSchemaAttribute;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Reads an XML {@link Document} and writes it to an {@link Encoder}.
   * <p>
   * Generates an Avro {@link Schema} on the fly from the XML Schema itself. 
   * That {@link Schema can be retrieved by calling {@link #getSchema()}.
   * </p>
   *
 *
 * @author  Mike Pigott
 */
public class XmlDatumWriter implements DatumWriter<Document> {

  private static final QName NIL_ATTR =
      new QName("http://www.w3.org/2001/XMLSchema-instance", "nil");

  private static class StackEntry {
    StackEntry(XmlSchemaDocumentNode<AvroRecordInfo> docNode) {
      this.docNode = docNode;
      this.receivedContent = false;
    }

    XmlSchemaDocumentNode<AvroRecordInfo> docNode;
    boolean receivedContent;
  }

  private static class Writer extends DefaultHandler {
    Writer(XmlSchemaPathNode path, Encoder out) {
      this.path = path;
      this.out = out;

      stack = new ArrayList<StackEntry>();
      currLocation = null;
      content = null;
      currAnyElem = null;
    }

    @Override
    public void startDocument() throws SAXException {
      currLocation = path;
    }

    @Override
    public void startElement(
        String uri,
        String localName,
        String qName,
        Attributes atts) throws SAXException {

      if (currAnyElem != null) {
        // We are inside an any element and not processing this one.
        return;
      }

      final QName elemName = new QName(uri, localName);
      walkToElement(elemName);

      if (!currLocation
            .getDirection()
            .equals(XmlSchemaPathNode.Direction.CHILD)
          && !currLocation
               .getDirection()
               .equals(XmlSchemaPathNode.Direction.SIBLING)) {
        throw new IllegalStateException("We are starting an element, so our path node direction should be to a CHILD or SIBLING, not " + currLocation.getDirection());
      }

      if (currLocation
            .getStateMachineNode()
            .getNodeType()
            .equals(XmlSchemaStateMachineNode.Type.ANY)) {

        // This is an any element; we are not processing it.
        currAnyElem = elemName;
        return;
      }

      try {
        final XmlSchemaDocumentNode<AvroRecordInfo> doc =
            currLocation.getDocumentNode();
        final AvroRecordInfo recordInfo = doc.getUserDefinedContent();
        final Schema avroSchema = recordInfo.getAvroSchema();

        final List<XmlSchemaStateMachineNode.Attribute> attributes =
            doc.getStateMachineNode().getAttributes();

        final HashMap<String, XmlSchemaTypeInfo> attrTypes =
            new HashMap<String, XmlSchemaTypeInfo>();

        final HashMap<String, XmlSchemaAttribute> schemaAttrs =
            new HashMap<String, XmlSchemaAttribute>();

        for (XmlSchemaStateMachineNode.Attribute attribute : attributes) {
          attrTypes.put(attribute.getAttribute().getName(), attribute.getType());

          schemaAttrs.put(
              attribute.getAttribute().getName(),
              attribute.getAttribute());
        }


        if ( !stack.isEmpty() ) {
          out.startItem();
        }
        if (recordInfo.getUnionIndex() >= 0) {
          out.writeIndex( recordInfo.getUnionIndex() );
        }

        /* The last element in the set of fields is the children.  We want
         * to process the children separately as they require future calls
         * to characters() and/or startElement().
         */
        for (int fieldIndex = 0;
            fieldIndex < avroSchema.getFields().size() - 1;
            ++fieldIndex) {

          final Schema.Field field = avroSchema.getFields().get(fieldIndex);
          if (field.name().equals(elemName.getLocalPart())) {
            // We reached the children field early ... not supposed to happen!
            throw new IllegalStateException("The children field is indexed at " + fieldIndex + " when it was expected to be the last element, or " + (avroSchema.getFields().size() - 1) + ".");
          }

          final XmlSchemaTypeInfo typeInfo = attrTypes.get( field.name() );

          /* Attributes in XML Schema each have their own namespace, which
           * is not supported in Avro.  So, we will see if we can find the
           * attribute using the existing namespace, and if not, we will
           * walk all of them to see which one has the same name.
           */
          String value =
              atts.getValue(elemName.getNamespaceURI(), field.name());

          if (value == null) {
            for (int attrIndex = 0;
                attrIndex < atts.getLength();
                ++attrIndex) {
              if (atts.getLocalName(attrIndex).equals( field.name() )) {
                value = atts.getValue(attrIndex);
                break;
              }
            }
          }

          if (value == null) {
            // See if there is a default or fixed value instead.
            final XmlSchemaAttribute schemaAttr =
                schemaAttrs.get( field.name() );

            value = schemaAttr.getDefaultValue();
            if (value == null) {
              value = schemaAttr.getFixedValue();
            }
          }

          try {
            write(typeInfo.getBaseType(), field.schema(), value);
          } catch (Exception ioe) {
            throw new RuntimeException("Could not write " + field.name() + " in " + field.schema().toString() + " to the output stream for element " + elemName, ioe);
          }
        }

        // If there are children, we want to start an array and end it later.
        final StackEntry entry =
            new StackEntry(currLocation.getDocumentNode());

        if (recordInfo
              .getAvroSchema() // TODO: Handle MAPs.
              .getField( elemName.getLocalPart() )
              .schema()
              .getType()
              .equals(Schema.Type.ARRAY)) {
          out.writeArrayStart();

          if (recordInfo.getNumChildren() > 0) {
            out.setItemCount( recordInfo.getNumChildren() );
          } else {
            out.setItemCount(0);
          }

          /* We expect to receive child elements; no need to look
           * for a default or fixed value once this element exits.
           */
          entry.receivedContent = true;

        } else if (avroSchema
                     .getField( elemName.getLocalPart() )
                     .schema()
                     .getType().equals(Schema.Type.NULL) ) {
          out.writeNull();
          entry.receivedContent = true;

        } else {
          final int nilIndex =
              atts.getIndex(
                  NIL_ATTR.getNamespaceURI(),
                  NIL_ATTR.getLocalPart()); 

          if ((nilIndex >= 0)
              && Boolean.parseBoolean(atts.getValue(nilIndex))) {

            write(doc.getStateMachineNode().getElementType().getBaseType(),
                  avroSchema.getField( elemName.getLocalPart() ).schema(),
                  null);
            entry.receivedContent = true;
          }
        }

        stack.add(entry);

      } catch (Exception e) {
        throw new RuntimeException("Unable to write " + elemName + " to the output stream.", e);
      }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
      if (currAnyElem != null) {
        // We do not process any elements.
        return;
      }

      if (stack.isEmpty()) {
        throw new SAXException("We are processing content, but the element stack is empty!");
      }

      currLocation = currLocation.getNext();
      if ((currLocation == null)
          || !currLocation
                .getDirection()
                .equals(XmlSchemaPathNode.Direction.CONTENT)) {
        String str = new String(ch, start, length).trim();
        if (str.isEmpty()) {
          currLocation = currLocation.getPrevious();
          return;
        } else {
          throw new SAXException("We are processing characters for " + stack.get(stack.size() - 1).docNode.getStateMachineNode().getElement().getQName() + " but the current direction is " + currLocation.getDirection() + ", not CONTENT.");
        }

      } else if (currLocation.getNext() == null) {
        throw new SAXException("We are processing characters for " + stack.get(stack.size() - 1).docNode.getStateMachineNode().getElement().getQName() + " but somehow the path ends here!");
      }

      /* If characters() will be called multiple times, we want to collect
       * all of them in the "content" StringBuilder, then process it all
       * once the last bit of content has been collected.
       *
       * If this is the last content node, we'll just write it all out here.
       */
      final boolean moreContentComing =
          currLocation
            .getNext()
            .getDirection()
            .equals(XmlSchemaPathNode.Direction.CONTENT);

      String result = null;
      if (moreContentComing
          || ((content != null) && (content.length() > 0))) {

        if (content == null) {
          content = new StringBuilder();
        }
        content.append(ch, start, length);

        if (!moreContentComing) {
          // If this is the last node, process the content.
          result = content.toString();
          content.delete(0, content.length());
        }
      } else {
        // This is the only content node - just write it.
        result = new String(ch, start, length);
      }

      if (result != null) {
        final StackEntry entry = stack.get(stack.size() - 1);
        final XmlSchemaDocumentNode<AvroRecordInfo> docNode = entry.docNode;

        final XmlSchemaBaseSimpleType baseType =
            docNode.getStateMachineNode().getElementType().getBaseType();

        final Schema avroSchema =
           docNode
             .getUserDefinedContent()
             .getAvroSchema()
             .getField(
                 docNode
                   .getStateMachineNode()
                   .getElement()
                   .getQName()
                   .getLocalPart())
             .schema();

        try {
          write(baseType, avroSchema, result);
          entry.receivedContent = true;
        } catch (Exception ioe) {
          final QName elemQName =
              docNode
                .getStateMachineNode()
                .getElement()
                .getQName();
          throw new RuntimeException("Unable to write the content \"" + result + "\" for " + elemQName + "", ioe);
        }
      }
    }

    @Override
    public void endElement(
        String uri,
        String localName,
        String qName)
        throws SAXException
    {
      final QName elemName = new QName(uri, localName);

      if ((currAnyElem != null) && currAnyElem.equals(elemName)) {
        // We are exiting an any element; prepare for the next one!
        currAnyElem = null;
        return;
      }

      final StackEntry entry = stack.remove(stack.size() - 1);
      final XmlSchemaDocumentNode<AvroRecordInfo> docNode = entry.docNode;

      if (!entry.receivedContent) {

        /* Look for either the default value
         * or fixed value and apply it, if any.
         */
        String value =
            docNode.getStateMachineNode().getElement().getDefaultValue();

        if (value == null) {
          value = docNode.getStateMachineNode().getElement().getFixedValue();
        }

        final XmlSchemaBaseSimpleType baseType =
            docNode.getStateMachineNode().getElementType().getBaseType();

        final Schema avroSchema =
            docNode
              .getUserDefinedContent()
              .getAvroSchema()
              .getField(localName)
              .schema();

        try {
          write(baseType, avroSchema, value);
        } catch (IOException e) {
          throw new RuntimeException("Attempted to write a default value of \"" + value + "\" for " + elemName + " and failed.", e);
        }
      }

      final QName stackElemName =
          docNode
            .getStateMachineNode()
            .getElement()
            .getQName();

      if (!stackElemName.equals(elemName)) {
        throw new IllegalStateException("We are leaving " + elemName + " but the element on the stack is " + stackElemName + ".");
      }

      if (docNode
            .getUserDefinedContent()
            .getAvroSchema() // TODO: Handle MAPs.
            .getField( elemName.getLocalPart() )
            .schema()
            .getType()
            .equals(Schema.Type.ARRAY)) {
        try {
          out.writeArrayEnd();
        } catch (IOException ioe) {
          throw new RuntimeException("Unable to end the array for " + elemName, ioe);
        }
      }
    }

    @Override
    public void endDocument() throws SAXException {
      if (currLocation.getNext() != null) {
        currLocation = currLocation.getNext();
        while (currLocation != null) {
          if (!currLocation
                 .getDirection()
                 .equals(XmlSchemaPathNode.Direction.PARENT)) {
            throw new IllegalStateException("Reached the end of the document, but the path has more nodes: " + currLocation.getStateMachineNode());
          }
          currLocation = currLocation.getNext();
        }
      }
    }

    private void walkToElement(QName elemName) {
      if (stack.isEmpty()
          && currLocation
               .getStateMachineNode()
               .getNodeType()
               .equals(XmlSchemaStateMachineNode.Type.ELEMENT)
          && currLocation
               .getStateMachineNode()
               .getElement()
               .getQName()
               .equals(elemName)) {
        return;
      }

      do {
        currLocation = currLocation.getNext();
      } while ((currLocation != null)
                  && (currLocation
                        .getDirection()
                        .equals(XmlSchemaPathNode.Direction.PARENT)
                  || (!currLocation.getDirection().equals(XmlSchemaPathNode.Direction.PARENT)
                        && !currLocation
                              .getStateMachineNode()
                              .getNodeType()
                              .equals(XmlSchemaStateMachineNode.Type.ELEMENT)
                        && !currLocation
                              .getStateMachineNode()
                              .getNodeType()
                              .equals(XmlSchemaStateMachineNode.Type.ANY))));

      if (currLocation == null) {
        throw new IllegalStateException("Cannot find " + elemName + " in the path!");
      } else if (
          currLocation
            .getStateMachineNode()
            .getNodeType()
            .equals(XmlSchemaStateMachineNode.Type.ELEMENT)
          && !currLocation
                .getStateMachineNode()
                .getElement()
                .getQName()
                .equals(elemName)) {
        throw new IllegalStateException("The next element in the path is " + currLocation.getStateMachineNode().getElement().getQName() + " (" + currLocation.getDirection() + "), not " + elemName + ".");
      }
    }

    private void write(
        XmlSchemaBaseSimpleType baseType,
        Schema schema,
        String data) throws IOException {

      write(baseType, schema, data, -1);
    }

    private void write(
        XmlSchemaBaseSimpleType baseType,
        Schema schema,
        String data,
        int unionIndex)
        throws IOException {

      /* If the data is empty or null, write
       * it as a null or string, if possible.
       */
      if ((data == null) || data.isEmpty()) {
        boolean isNullable = (schema.getType().equals(Schema.Type.NULL));
        boolean isString = (schema.getType().equals(Schema.Type.STRING));
        int nullUnionIndex = -1;
        int stringIndex = -1;
        if (!isNullable
            && !isString
            && schema.getType().equals(Schema.Type.UNION)) {

          for (int typeIndex = 0;
              typeIndex < schema.getTypes().size();
              ++typeIndex) {

            final Schema.Type type =
                schema.getTypes().get(typeIndex).getType();

            if (type.equals(Schema.Type.NULL)) {
              nullUnionIndex = typeIndex;
              isNullable = true;
              break;
            } else if (type.equals(Schema.Type.STRING)) {
              isString = true;
              stringIndex = typeIndex;
            }
          }
        }
        if (isNullable) {
          if (nullUnionIndex >= 0) {
            out.writeIndex(nullUnionIndex);
          }
          out.writeNull();

        } else if (isString) {
          if (stringIndex >= 0) {
            out.writeIndex(stringIndex);
          }
          out.writeString("");
        } else {
          throw new IOException("Cannot write a null or empty string as a non-null or non-string type.");
        }

        return;
      }

      switch ( schema.getType() ) {
      case UNION:
        {
          int textIndex = -1;
          int bytesIndex = -1;

          Schema bytesType = null;

          final List<Schema> subTypes = schema.getTypes();
          boolean written = false;
          for (int subTypeIndex = 0;
              subTypeIndex < subTypes.size();
              ++subTypeIndex) {
            // Try the text types last.
            final Schema subType = subTypes.get(subTypeIndex);
            if (subType.getType().equals(Schema.Type.BYTES)) {
              bytesIndex = subTypeIndex;
              bytesType = subType;
              continue;
            } else if (subType.getType().equals(Schema.Type.STRING)) {
              textIndex = subTypeIndex;
              continue;
            }

            try {
              write(baseType, subType, data, subTypeIndex);
              written = true;
              break;
            } catch (IOException ioe) {
              /* Could not parse the value using the
               * provided type; try the next one.
               */
            }
          }

          if (!written) {
            if (bytesIndex >= 0) {
              try {
                write(baseType, bytesType, data, bytesIndex);
                written = true;
              } catch (IOException ioe) {
                // Cannot write the data as bytes either.
              }
            }
            if (!written && (textIndex >= 0)) {
              out.writeIndex(textIndex);
              out.writeString(data);

            } else if (!written) {
              throw new IOException("Cannot write \"" + data + "\" as one of the types in " + schema.toString());
            }
          }
          break;
        }
      case BYTES:
        {
          byte[] bytes = null;
          switch (baseType) {
          case BIN_BASE64:
            bytes = DatatypeConverter.parseBase64Binary(data);
            break;
          case BIN_HEX:
            bytes = DatatypeConverter.parseHexBinary(data);
            break;
          default:
            throw new IllegalArgumentException("Cannot generate bytes for data of a base type of " + baseType);
          }
          out.writeBytes(bytes);
          break;
        }
      case STRING:
        {
          if (unionIndex >= 0) {
            out.writeIndex(unionIndex);
          }
          out.writeString(data);
          break;
        }
      case ENUM:
        {
          if ( !schema.hasEnumSymbol(data) ) {
            final int numSymbols = schema.getEnumSymbols().size();

            StringBuilder errMsg = new StringBuilder("\"");
            errMsg.append(data);
            errMsg.append("\" is not a member of the symbols [\"");
            for (int symbolIndex = 0;
                symbolIndex < numSymbols - 1;
                ++symbolIndex) {
              errMsg.append( schema.getEnumSymbols().get(symbolIndex) );
              errMsg.append("\", \"");
            }
            errMsg.append( schema.getEnumSymbols().get(numSymbols - 1) );
            errMsg.append("\"].");

            throw new IOException( errMsg.toString() );
          }
          if (unionIndex >= 0) {
            out.writeIndex(unionIndex);
          }
          out.writeEnum( schema.getEnumOrdinal(data) );
          break;
        }
      case DOUBLE:
        {
          try {
            final double value = Double.parseDouble(data);
            if (unionIndex >= 0) {
              out.writeIndex(unionIndex);
            }
            out.writeDouble(value);
          } catch (NumberFormatException nfe) {
            throw new IOException("\"" + data + "\" is not a double.", nfe);
          }
          break;
        }
      case FLOAT:
        {
          try {
            final float value = Float.parseFloat(data);
            if (unionIndex >= 0) {
              out.writeIndex(unionIndex);
            }
            out.writeFloat(value);
          } catch (NumberFormatException nfe) {
            throw new IOException("\"" + data + "\" is not a float.", nfe);
          }
          break;
        }
      case LONG:
        {
          try {
            final long value = Long.parseLong(data);
            if (unionIndex >= 0) {
              out.writeIndex(unionIndex);
            }
            out.writeLong(value);
          } catch (NumberFormatException nfe) {
            throw new IOException("\"" + data + "\" is not a long.", nfe);
          }
          break;
        }
      case INT:
        {
          try {
            final int value = Integer.parseInt(data);
            if (unionIndex >= 0) {
              out.writeIndex(unionIndex);
            }
            out.writeInt(value);
          } catch (NumberFormatException nfe) {
            throw new IOException("\"" + data + "\" is not an int.", nfe);
          }
          break;
        }
      case BOOLEAN:
        {
          if (unionIndex >= 0) {
            out.writeIndex(unionIndex);
          }
          out.writeBoolean( Boolean.parseBoolean(data) );
          break;
        }
      default:
        throw new IOException("Cannot write data of type " + schema.getType());
      }
    }

    private XmlSchemaPathNode currLocation;
    private StringBuilder content;
    private QName currAnyElem;
    private ArrayList<StackEntry> stack;

    private final XmlSchemaPathNode path;
    private final Encoder out;
  }

  public XmlDatumWriter(XmlDatumConfig config, Schema avroSchema)
      throws IOException {

    if (config == null) {
      throw new IllegalArgumentException("XmlDatumConfig cannot be null.");
    }

    xmlSchemaCollection = new XmlSchemaCollection();
    xmlSchemaCollection.setSchemaResolver(new XmlSchemaMultiBaseUriResolver());
    xmlSchemaCollection.setBaseUri(config.getBaseUri());
    for (StreamSource source : config.getSources()) {
      xmlSchemaCollection.read(source);
    }

    final XmlSchemaStateMachineGenerator stateMachineGen =
        new XmlSchemaStateMachineGenerator();

    final XmlSchemaWalker walker =
        new XmlSchemaWalker(xmlSchemaCollection, stateMachineGen);
    walker.setUserRecognizedTypes( Utils.getAvroRecognizedTypes() );

    AvroSchemaGenerator avroSchemaGen = null;
    if (avroSchema == null) {
      avroSchemaGen =
          new AvroSchemaGenerator(
              config.getBaseUri(),
              config.getSchemaUrls(),
              config.getSchemaFiles());
      walker.addVisitor(avroSchemaGen);
    }

    final XmlSchemaElement rootElement =
        xmlSchemaCollection.getElementByQName(config.getRootTagName());
    walker.walk(rootElement);

    stateMachine = stateMachineGen.getStartNode();

    if (avroSchema == null) {
      schema = avroSchemaGen.getSchema();
    } else {
      schema = avroSchema;
    }
  }

  public XmlDatumWriter(XmlDatumConfig config) throws IOException {
    this(config, null);
  }

  /**
   * Returns the {@link Schema} this <code>XmlDatumWriter</code> is
   * writing against - either the one automatically generated from
   * the {@link XmlDatumConfig} or the {@link Schema} set after that.
   */
  public Schema getSchema() {
    return schema;
  }

  /**
   * Sets the schema to use when writing the XML
   * {@link Document} to the {@link Encoder}.
   *
   * @see org.apache.avro.io.DatumWriter#setSchema(org.apache.avro.Schema)
   */
  @Override
  public void setSchema(Schema schema) {
    if (schema == null) {
      throw new IllegalArgumentException("Avro schema cannot be null.");
    }
    this.schema = schema;
  }

  /**
   * Writes the {@link Document} to the {@link Encoder} in accordance
   * with the {@link Schema} set in {@link #setSchema(Schema)}.
   *
   * <p>
   * If no {@link Schema} was provided, builds one from the {@link Document}
   * and its {@link XmlSchemaCollection}.  The schema can then be retrieved
   * from {@link #getSchema()}.
   * </p>
   *
   * @see org.apache.avro.io.DatumWriter#write(java.lang.Object, org.apache.avro.io.Encoder)
   */
  @Override
  public void write(Document doc, Encoder out) throws IOException {
    // 1. Build the path through the schema that describes the document.
    XmlSchemaPathFinder pathCreator = new XmlSchemaPathFinder(stateMachine);
    SaxWalkerOverDom walker = new SaxWalkerOverDom(pathCreator);
    try {
      walker.walk(doc);
    } catch (Exception se) {
      throw new IOException("Unable to parse the document.", se);
    }
    final XmlSchemaPathNode path = pathCreator.getXmlSchemaDocumentPath();

    // 2. Apply Avro schema metadata on top of the document. 
    final AvroSchemaApplier applier = new AvroSchemaApplier(schema, false);
    applier.apply(path.getDocumentNode());

    // 3. Encode the document.
    walker.removeContentHandler(pathCreator);
    walker.addContentHandler( new Writer(path, out) );

    try {
      walker.walk(doc);
    } catch (SAXException e) {
      throw new IOException("Unable to encode the document.", e);
    }
  }

  private final XmlSchemaCollection xmlSchemaCollection;
  private final XmlSchemaStateMachineNode stateMachine;
  private Schema schema;
}
