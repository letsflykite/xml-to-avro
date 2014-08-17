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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.avro.Schema;
import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaAnyAttribute;
import org.apache.ws.commons.schema.XmlSchemaAttribute;
import org.apache.ws.commons.schema.XmlSchemaAttributeGroup;
import org.apache.ws.commons.schema.XmlSchemaAttributeGroupMember;
import org.apache.ws.commons.schema.XmlSchemaAttributeGroupRef;
import org.apache.ws.commons.schema.XmlSchemaAttributeOrGroupRef;
import org.apache.ws.commons.schema.XmlSchemaComplexContent;
import org.apache.ws.commons.schema.XmlSchemaComplexContentExtension;
import org.apache.ws.commons.schema.XmlSchemaComplexContentRestriction;
import org.apache.ws.commons.schema.XmlSchemaComplexType;
import org.apache.ws.commons.schema.XmlSchemaContent;
import org.apache.ws.commons.schema.XmlSchemaContentType;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.XmlSchemaFacet;
import org.apache.ws.commons.schema.XmlSchemaFractionDigitsFacet;
import org.apache.ws.commons.schema.XmlSchemaMaxInclusiveFacet;
import org.apache.ws.commons.schema.XmlSchemaMinInclusiveFacet;
import org.apache.ws.commons.schema.XmlSchemaParticle;
import org.apache.ws.commons.schema.XmlSchemaPatternFacet;
import org.apache.ws.commons.schema.XmlSchemaSequence;
import org.apache.ws.commons.schema.XmlSchemaSequenceMember;
import org.apache.ws.commons.schema.XmlSchemaSimpleContentExtension;
import org.apache.ws.commons.schema.XmlSchemaSimpleContentRestriction;
import org.apache.ws.commons.schema.XmlSchemaSimpleType;
import org.apache.ws.commons.schema.XmlSchemaSimpleTypeContent;
import org.apache.ws.commons.schema.XmlSchemaSimpleTypeList;
import org.apache.ws.commons.schema.XmlSchemaSimpleTypeRestriction;
import org.apache.ws.commons.schema.XmlSchemaSimpleTypeUnion;
import org.apache.ws.commons.schema.XmlSchemaType;
import org.apache.ws.commons.schema.XmlSchemaUse;
import org.apache.ws.commons.schema.XmlSchemaWhiteSpaceFacet;
import org.apache.ws.commons.schema.constants.Constants;
import org.apache.ws.commons.schema.utils.XmlSchemaNamed;

/**
 * The scope represents the set of types, attributes, and
 * child groups & elements that the current type represents.
 */
final class XmlSchemaScope {

  private static final Map<QName, List<XmlSchemaFacet>> facetsOfSchemaTypes =
      new HashMap<QName, List<XmlSchemaFacet>>();

  private Map<String, XmlSchema> schemasByNamespace;
  private Map<QName, XmlSchemaScope> scopeCache;

  private XmlSchemaTypeInfo typeInfo;
  private HashMap<QName, XmlSchemaAttribute> attributes;
  private XmlSchemaParticle child;
  private XmlSchemaAnyAttribute anyAttr;
  private Set<QName> userRecognizedTypes;

  static {
    /* Until https://issues.apache.org/jira/browse/XMLSCHEMA-33
     * makes it to the next release of Apache XML Schema (2.1.1).
     */
    facetsOfSchemaTypes.put(
        Constants.XSD_DURATION,
        Arrays.asList( new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_DATETIME,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_TIME,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_DATE,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_YEARMONTH,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_YEAR,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_MONTHDAY,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_DAY,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_MONTH,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_MONTH,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_BOOLEAN,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_BASE64,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_HEXBIN,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_FLOAT,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_DOUBLE,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_ANYURI,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_QNAME,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_DECIMAL,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", true) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_INTEGER,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaFractionDigitsFacet(new Integer(0), true),
            new XmlSchemaPatternFacet("[\\-+]?[0-9]+", false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_NONPOSITIVEINTEGER,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaMaxInclusiveFacet(new Integer(0), false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_NEGATIVEINTEGER,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaMaxInclusiveFacet(new Integer(-1), false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_LONG,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaMinInclusiveFacet(
                new Long(-9223372036854775808L),
                false),
            new XmlSchemaMaxInclusiveFacet(
                new Long(9223372036854775807L),
                false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_INT,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaMinInclusiveFacet(
                new Integer(-2147483648),
                false),
            new XmlSchemaMaxInclusiveFacet(
                new Integer(2147483647),
                false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_SHORT,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaMinInclusiveFacet(new Short((short) -32768), false),
            new XmlSchemaMaxInclusiveFacet(new Short((short) 32767), false)}));

    facetsOfSchemaTypes.put(
        Constants.XSD_BYTE,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaMinInclusiveFacet(new Byte((byte) -128), false),
            new XmlSchemaMaxInclusiveFacet(new Byte((byte) 127), false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_NONNEGATIVEINTEGER,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaMinInclusiveFacet(new Integer(0), false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_POSITIVEINTEGER,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaMinInclusiveFacet(new Integer(1), false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_UNSIGNEDLONG,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaMaxInclusiveFacet(
                new BigInteger("18446744073709551615"),
                false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_UNSIGNEDINT,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaMaxInclusiveFacet(new Long(4294967295L), false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_UNSIGNEDSHORT,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaMaxInclusiveFacet(new Integer(65535), false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_UNSIGNEDBYTE,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaMaxInclusiveFacet(new Short((short) 255), false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_STRING,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("preserve", false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_NORMALIZEDSTRING,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("replace", false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_TOKEN,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaWhiteSpaceFacet("collapse", false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_LANGUAGE,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaPatternFacet(
                "[a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8})*",
                false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_NMTOKEN,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaPatternFacet("\\c+", false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_NAME,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaPatternFacet("\\i\\c*", false) }));

    facetsOfSchemaTypes.put(
        Constants.XSD_NCNAME,
        Arrays.asList(new XmlSchemaFacet[] {
            new XmlSchemaPatternFacet("[\\i-[:]][\\c-[:]]*", false) }));
  }

  /**
   * Initialization of members to be filled in during the walk.
   */
  private XmlSchemaScope() {
    typeInfo = null;
    attributes = null;
    child = null;
    anyAttr = null;
  }

  private XmlSchemaScope(XmlSchemaScope child, XmlSchemaType type) {
    this();
    this.schemasByNamespace = child.schemasByNamespace;
    this.scopeCache = child.scopeCache;
    this.userRecognizedTypes = child.userRecognizedTypes;

    walk(type);
  }

  /**
   * Initializes a new {@link XmlSchemaScope} with a base
   * {@link XmlSchemaElement}.  The element type and
   * attributes will be traversed, and attribute lists
   * and element children will be retrieved.
   *
   * @param element The base element to build the scope from.
   * @param substitutions The master list of substitution groups to pull from.
   * @param userRecognizedTypes The set of types recognized by the caller.
   */
  XmlSchemaScope(
      XmlSchemaType type,
      Map<String, XmlSchema> xmlSchemasByNamespace,
      Map<QName, XmlSchemaScope> scopeCache,
      Set<QName> userRecognizedTypes) {

    this();

    schemasByNamespace = xmlSchemasByNamespace;
    this.scopeCache = scopeCache;
    this.userRecognizedTypes = userRecognizedTypes;

    walk(type);
  }

  XmlSchemaTypeInfo getTypeInfo() {
    return typeInfo;
  }

  Collection<XmlSchemaAttribute> getAttributesInScope() {
    if (attributes == null) {
      return null;
    }
    return attributes.values();
  }

  XmlSchemaParticle getParticle() {
    return child;
  }

  XmlSchemaAnyAttribute getAnyAttribute() {
    return anyAttr;
  }

  private void walk(XmlSchemaType type) {
    if (type instanceof XmlSchemaSimpleType) {
      walk((XmlSchemaSimpleType) type);
    } else if (type instanceof XmlSchemaComplexType) {
      walk((XmlSchemaComplexType) type);
    } else {
      throw new IllegalArgumentException(
          "Unrecognized XmlSchemaType of type "
          + type.getClass().getName());
    }
  }

  private void walk(XmlSchemaSimpleType simpleType) {
    XmlSchemaSimpleTypeContent content = simpleType.getContent();

    if (content == null) {
      /* Only anyType contains no content. We
       * reached the root of the type hierarchy.
       */
      typeInfo =
          new XmlSchemaTypeInfo(XmlSchemaBaseSimpleType.ANYTYPE);

    } else if (content instanceof XmlSchemaSimpleTypeList) {
        XmlSchemaSimpleTypeList list = (XmlSchemaSimpleTypeList) content;
        XmlSchemaSimpleType listType = list.getItemType();
        if (listType == null) {
            XmlSchema schema =
                schemasByNamespace.get(
                    list.getItemTypeName().getNamespaceURI());

            listType =
                (XmlSchemaSimpleType) schema.getTypeByName(
                    list.getItemTypeName());
        }
        if (listType == null) {
            throw new IllegalArgumentException(
                "Unrecognized schema type for list "
                + getName(simpleType, "{Anonymous List Type}"));
        }

        XmlSchemaScope parentScope = getScope(listType);

        switch ( parentScope.getTypeInfo().getType() ) {
        case UNION:
        case ATOMIC:
          break;
        default:
          throw new IllegalStateException(
              "Attempted to create a list from a "
              + parentScope.getTypeInfo().getType()
              + " type.");
        }

        typeInfo = new XmlSchemaTypeInfo( parentScope.getTypeInfo() );

    } else if (content instanceof XmlSchemaSimpleTypeUnion) {
        XmlSchemaSimpleTypeUnion union = (XmlSchemaSimpleTypeUnion) content;
        QName[] namedBaseTypes = union.getMemberTypesQNames();
        List<XmlSchemaSimpleType> baseTypes = union.getBaseTypes();

        if (namedBaseTypes != null) {
          if (baseTypes == null) {
            baseTypes =
                new ArrayList<XmlSchemaSimpleType>(namedBaseTypes.length);
          }

          for (QName namedBaseType : namedBaseTypes) {
            XmlSchema schema =
                schemasByNamespace.get( namedBaseType.getNamespaceURI() );
            XmlSchemaSimpleType baseType =
                (XmlSchemaSimpleType) schema.getTypeByName(namedBaseType);
            if (baseType != null) {
                baseTypes.add(baseType);
            }
          }
        }

        /* baseTypes cannot be null at this point;
         * there must be a union of types.
         */
        if ((baseTypes == null) || baseTypes.isEmpty()) {
          throw new IllegalArgumentException(
              "Unrecognized base types for union "
              + getName(simpleType, "{Anonymous Union Type}"));
        }

        List<XmlSchemaTypeInfo> childTypes =
            new ArrayList<XmlSchemaTypeInfo>( baseTypes.size() );

        for (XmlSchemaSimpleType baseType : baseTypes) {
          XmlSchemaScope parentScope = getScope(baseType);
          if (parentScope
                .getTypeInfo()
                .getType()
                .equals(XmlSchemaTypeInfo.Type.UNION) ) {
            childTypes.addAll( parentScope.getTypeInfo().getChildTypes() );
          } else {
            childTypes.add( parentScope.getTypeInfo() );
          }
        }

        typeInfo = new XmlSchemaTypeInfo(childTypes);

    } else if (content instanceof XmlSchemaSimpleTypeRestriction) {
        final XmlSchemaSimpleTypeRestriction restr =
            (XmlSchemaSimpleTypeRestriction) content;

        List<XmlSchemaFacet> facets = restr.getFacets();
        if ((facets == null) || facets.isEmpty()) {
          // Needed until XMLSCHEMA-33 becomes available.
          facets = facetsOfSchemaTypes.get( simpleType.getQName() );
        }

        XmlSchemaTypeInfo parentTypeInfo = null;

        if ( XmlSchemaBaseSimpleType.isBaseSimpleType(simpleType.getQName()) ) {
          // If this is a base simple type, use it!
          typeInfo =
              new XmlSchemaTypeInfo(
                  XmlSchemaBaseSimpleType.getBaseSimpleTypeFor(
                      simpleType.getQName()),
                  mergeFacets(null, facets));

        } else {
          XmlSchemaSimpleType baseType = restr.getBaseType();
          if (baseType == null) {
              XmlSchema schema =
                  schemasByNamespace.get(
                      restr.getBaseTypeName().getNamespaceURI());
              baseType =
                  (XmlSchemaSimpleType) schema.getTypeByName(
                      restr.getBaseTypeName());
          }

          if (baseType != null) {
            final XmlSchemaScope parentScope = getScope(baseType);

            /* We need to track the original type as well as the set of facets
             * imposed on that type.  Once the recursion ends, and we make it
             * all the way back to the first scope, the user of this type info
             * will know the derived type and all of its imposed facets.
             *
             * Unions can restrict unions, lists can restrict lists, and atomic
             * types restrict other atomic types.  We need to follow all of
             * these too.
             */
            parentTypeInfo = parentScope.getTypeInfo();

            HashMap<XmlSchemaRestriction.Type, List<XmlSchemaRestriction>>
              mergedFacets = mergeFacets(parentTypeInfo.getFacets(), facets);

            typeInfo = restrictTypeInfo(parentTypeInfo, mergedFacets);

          } else {
              throw new IllegalArgumentException(
                  "Unrecognized base type for "
                  + getName(simpleType, "{Anonymous Simple Type}"));
          }
        }

        typeInfo.setUserRecognizedType(
            getUserRecognizedType(simpleType.getQName(), parentTypeInfo));

    } else {
        throw new IllegalArgumentException(
            "XmlSchemaSimpleType "
            + getName(simpleType, "{Anonymous Simple Type}")
            + "contains unrecognized XmlSchemaSimpleTypeContent "
            + content.getClass().getName());
    }
  }

  private void walk(XmlSchemaComplexType complexType) {
    XmlSchemaContent complexContent =
        (complexType.getContentModel() != null)
        ? complexType.getContentModel().getContent()
        : null;

    /* Process the complex type extensions and restrictions.
     * If there aren't any, the content is be defined by the particle.
     */
    if (complexContent != null) {
      boolean isMixed = false;
      if (complexType.isMixed()) {
        isMixed = complexType.isMixed();
      } else if (complexType.getContentModel()
                   instanceof XmlSchemaComplexContent) {
        isMixed =
            ((XmlSchemaComplexContent) complexType.getContentModel())
            .isMixed();
      }

      walk(isMixed, complexContent);

    } else {
      child = complexType.getParticle();
      attributes = createAttributeMap( complexType.getAttributes() );
      anyAttr = complexType.getAnyAttribute();
      typeInfo = new XmlSchemaTypeInfo( complexType.isMixed() );
    }
  }

  private void walk(boolean isMixed, XmlSchemaContent content) {
    if (content instanceof XmlSchemaComplexContentExtension) {
      XmlSchemaComplexContentExtension ext =
          (XmlSchemaComplexContentExtension) content;

      XmlSchema schema =
          schemasByNamespace.get( ext.getBaseTypeName().getNamespaceURI() );
      XmlSchemaType baseType = schema.getTypeByName( ext.getBaseTypeName() );

      XmlSchemaParticle baseParticle = null;
      XmlSchemaAnyAttribute baseAnyAttr = null;
      XmlSchemaScope parentScope = null;

      if (baseType != null) {
        /* Complex content extensions add attributes and elements
         * in addition to what was retrieved from the parent. Since
         * there will be no collisions, it is safe to perform a
         * straight add.
         */
        parentScope = getScope(baseType);
        final Collection<XmlSchemaAttribute> parentAttrs =
            parentScope.getAttributesInScope();

        attributes = createAttributeMap( ext.getAttributes() );

        if (attributes == null) {
          attributes = createAttributeMap(parentAttrs);
        } else if (parentAttrs != null) {
          for (XmlSchemaAttribute parentAttr : parentAttrs) {
            attributes.put(parentAttr.getQName(), parentAttr);
          }
        }

        baseParticle = parentScope.getParticle();
        baseAnyAttr = parentScope.anyAttr;
      }

      /* An extension of a complex type is equivalent to creating a sequence of
       * two particles: the parent particle followed by the child particle.
       */
      if (ext.getParticle() == null) {
        child = baseParticle;
      } else if (baseParticle == null) {
        child = ext.getParticle();
      } else {
        XmlSchemaSequence seq = new XmlSchemaSequence();
        seq.getItems().add((XmlSchemaSequenceMember) baseParticle);
        seq.getItems().add((XmlSchemaSequenceMember) ext.getParticle());
        child = seq;
      }

      /* An extension of an anyAttribute means the child defines the
       * processContents field, while a union of all namespaces between
       * the parent and child is taken.
       */
      if (baseAnyAttr == null) {
        anyAttr = ext.getAnyAttribute();
      } else if (ext.getAnyAttribute() == null) {
        anyAttr = baseAnyAttr;
      } else {
        String[] baseNamespaces = baseAnyAttr.getNamespace().split(" ");
        String[] childNamespaces =
            ext.getAnyAttribute().getNamespace().split(" ");

        HashSet<String> namespaces = new HashSet<String>();
        for (String baseNs : baseNamespaces) {
          if (!baseNs.isEmpty()) {
            namespaces.add(baseNs);
          }
        }
        for (String childNs : childNamespaces) {
          if (!childNs.isEmpty()) {
            namespaces.add(childNs);
          }
        }

        StringBuilder nsAsString = new StringBuilder();
        for (String namespace : namespaces) {
          nsAsString.append(namespace).append(" ");
        }

        anyAttr = new XmlSchemaAnyAttribute();
        anyAttr.setNamespace( nsAsString.toString() );
        anyAttr.setProcessContent( ext.getAnyAttribute().getProcessContent() );
        anyAttr.setAnnotation( ext.getAnyAttribute().getAnnotation() );
        anyAttr.setId( ext.getAnyAttribute().getId() );
        anyAttr.setLineNumber( ext.getAnyAttribute().getLineNumber() );
        anyAttr.setLinePosition( ext.getAnyAttribute().getLinePosition() );
        anyAttr.setMetaInfoMap( ext.getAnyAttribute().getMetaInfoMap() );
        anyAttr.setSourceURI( ext.getAnyAttribute().getSourceURI() );
        anyAttr.setUnhandledAttributes( ext.getUnhandledAttributes() );
      }

      final XmlSchemaTypeInfo parentTypeInfo =
          (parentScope == null)
          ? null
          : parentScope.getTypeInfo();

      if ((parentTypeInfo != null)
          && !parentTypeInfo
                .getType()
                .equals(XmlSchemaTypeInfo.Type.COMPLEX)) {
        typeInfo = parentScope.getTypeInfo();
      } else {
        typeInfo = new XmlSchemaTypeInfo(isMixed);
      }

    } else if (content instanceof XmlSchemaComplexContentRestriction) {
      final XmlSchemaComplexContentRestriction rstr =
          (XmlSchemaComplexContentRestriction) content;

      final XmlSchema schema =
          schemasByNamespace.get( rstr.getBaseTypeName().getNamespaceURI() );

      final XmlSchemaType baseType =
          schema.getTypeByName( rstr.getBaseTypeName() );

      XmlSchemaScope parentScope = null;
      if (baseType != null) {
        parentScope = getScope(baseType);

        attributes =
            mergeAttributes(
                parentScope.attributes,
                createAttributeMap( rstr.getAttributes() ));

        child = parentScope.getParticle();
      }

      /* There is no inheritance when restricting particles.  If the schema
       * writer wishes to include elements in the parent type, (s)he must
       * redefine them in the child.
       */
      if (rstr.getParticle() != null) {
        child = rstr.getParticle();
      }

      /* There is no inheritance when restricting attribute wildcards.
       * The only requirement is that the namespaces of the restricted
       * type is a subset of the namespaces of the base type.  This
       * will not be checked here (all schemas are assumed correct).
       */
      anyAttr = rstr.getAnyAttribute();

      final XmlSchemaTypeInfo parentTypeInfo =
          (parentScope == null)
          ? null
          : parentScope.getTypeInfo();

      if ((parentTypeInfo != null)
          && !parentTypeInfo
               .getType()
               .equals(XmlSchemaTypeInfo.Type.COMPLEX)) {
        typeInfo = parentTypeInfo;
      } else {
        typeInfo = new XmlSchemaTypeInfo(isMixed);
      }

    } else if (content instanceof XmlSchemaSimpleContentExtension) {
      XmlSchemaSimpleContentExtension ext =
          (XmlSchemaSimpleContentExtension) content;
      attributes = createAttributeMap( ext.getAttributes() );

      XmlSchema schema =
          schemasByNamespace.get( ext.getBaseTypeName().getNamespaceURI() );
      XmlSchemaType baseType = schema.getTypeByName( ext.getBaseTypeName() );

      if (baseType != null) {
        final XmlSchemaScope parentScope = getScope(baseType);
        typeInfo = parentScope.getTypeInfo();

        final Collection<XmlSchemaAttribute> parentAttrs =
            parentScope.getAttributesInScope();

        if (attributes == null) {
          attributes = createAttributeMap(parentAttrs);
        } else if (parentAttrs != null) {
          for (XmlSchemaAttribute parentAttr : parentAttrs) {
            attributes.put(parentAttr.getQName(), parentAttr);
          }
        }
      }

      anyAttr = ext.getAnyAttribute();

    } else if (content instanceof XmlSchemaSimpleContentRestriction) {
      XmlSchemaSimpleContentRestriction rstr =
          (XmlSchemaSimpleContentRestriction) content;
      attributes = createAttributeMap( rstr.getAttributes() );

      XmlSchemaType baseType = null;
      if (rstr.getBaseType() != null) {
        baseType = rstr.getBaseType();
      } else {
        XmlSchema schema =
            schemasByNamespace.get( rstr.getBaseTypeName().getNamespaceURI() );
        baseType = schema.getTypeByName( rstr.getBaseTypeName() );
      }

      if (baseType != null) {
        XmlSchemaScope parentScope = getScope(baseType);
        typeInfo =
            restrictTypeInfo(
                parentScope.getTypeInfo(),
                mergeFacets(
                    parentScope.getTypeInfo().getFacets(),
                    rstr.getFacets()));

        attributes = mergeAttributes(parentScope.attributes, attributes);
      }

      anyAttr = rstr.getAnyAttribute();
    }
  }

  private ArrayList<XmlSchemaAttribute> getAttributesOf(
      XmlSchemaAttributeGroupRef groupRef) {

    XmlSchemaAttributeGroup attrGroup = groupRef.getRef().getTarget();
    if (attrGroup == null) {
      XmlSchema schema =
          schemasByNamespace.get(groupRef.getTargetQName().getNamespaceURI());
      attrGroup = schema.getAttributeGroupByName(groupRef.getTargetQName());
    }
    return getAttributesOf(attrGroup);
  }

  private ArrayList<XmlSchemaAttribute> getAttributesOf(
      XmlSchemaAttributeGroup attrGroup) {

    ArrayList<XmlSchemaAttribute> attrs =
        new ArrayList<XmlSchemaAttribute>( attrGroup.getAttributes().size() );

    for (XmlSchemaAttributeGroupMember member : attrGroup.getAttributes()) {
      if (member instanceof XmlSchemaAttribute) {
        attrs.add( getAttribute((XmlSchemaAttribute) member, false) );

      } else if (member instanceof XmlSchemaAttributeGroup) {
        attrs.addAll( getAttributesOf((XmlSchemaAttributeGroup) member) );

      } else if (member instanceof XmlSchemaAttributeGroupRef) {
        attrs.addAll( getAttributesOf((XmlSchemaAttributeGroupRef) member) );

      } else {
        throw new IllegalArgumentException(
            "Attribute Group "
            + getName(attrGroup, "{Anonymous Attribute Group}")
            + " contains unrecognized attribute group memeber type "
            + member.getClass().getName());
      }
    }

    return attrs;
  }

  private XmlSchemaAttribute getAttribute(
      XmlSchemaAttribute attribute,
      boolean forceCopy) {

    if (!attribute.isRef()
        && (attribute.getSchemaType() != null)
        && !forceCopy) {

      if ( attribute.getUse().equals(XmlSchemaUse.NONE) ) {
        attribute.setUse(XmlSchemaUse.OPTIONAL);
      }

      return attribute;
    }

    XmlSchemaAttribute globalAttr = null;
    QName attrQName = null;
    if ( attribute.isRef() ) {
      attrQName = attribute.getRefBase().getTargetQName();
    } else {
      attrQName = attribute.getQName();
    }
    final XmlSchema schema =
        schemasByNamespace.get( attrQName.getNamespaceURI() );

    if (!attribute.isRef()
        && (forceCopy || (attribute.getSchemaType() == null))) {
      // If we are forcing a copy, there is no reference to follow.
      globalAttr = attribute;
    } else {
      if (attribute.getRef().getTarget() != null) {
        globalAttr = attribute.getRef().getTarget();
      } else {
        globalAttr = schema.getAttributeByName(attrQName);
      }
    }
  
    XmlSchemaSimpleType schemaType = globalAttr.getSchemaType();
    if (schemaType == null) {
      final QName typeQName = globalAttr.getSchemaTypeName();
      XmlSchema typeSchema =
          schemasByNamespace.get( typeQName.getNamespaceURI() );
      schemaType = (XmlSchemaSimpleType) typeSchema.getTypeByName(typeQName);
    }

    /* The attribute reference defines the attribute use and overrides the ID,
     * default, and fixed fields.  Everything else is defined by the global
     * attribute.
     */
    String fixedValue = attribute.getFixedValue();
    if ((fixedValue != null) && (attribute != globalAttr)) {
      fixedValue = globalAttr.getFixedValue();
    }

    String defaultValue = attribute.getDefaultValue();
    if ((defaultValue == null)
        && (fixedValue == null)
        && (attribute != globalAttr)) {
      defaultValue = globalAttr.getDefaultValue();
    }

    String id = attribute.getId();
    if ((id == null) && (attribute != globalAttr)) {
      id = globalAttr.getId();
    }

    XmlSchemaUse attrUsage = attribute.getUse();
    if ( attrUsage.equals(XmlSchemaUse.NONE) ) {
      attrUsage = XmlSchemaUse.OPTIONAL;
    }

    final XmlSchemaAttribute copy = new XmlSchemaAttribute(schema, false);
    copy.setName( globalAttr.getName() );

    copy.setAnnotation( globalAttr.getAnnotation() );
    copy.setDefaultValue(defaultValue);
    copy.setFixedValue(fixedValue);
    copy.setForm( globalAttr.getForm() );
    copy.setId(id);
    copy.setLineNumber( attribute.getLineNumber() );
    copy.setLinePosition( attribute.getLinePosition() );
    copy.setMetaInfoMap( globalAttr.getMetaInfoMap() );
    copy.setSchemaType(schemaType);
    copy.setSchemaTypeName( globalAttr.getSchemaTypeName() );
    copy.setSourceURI( globalAttr.getSourceURI() );
    copy.setUnhandledAttributes( globalAttr.getUnhandledAttributes() );
    copy.setUse(attrUsage);

    return copy;
  }

  private HashMap<QName, XmlSchemaAttribute> createAttributeMap(
      Collection<? extends XmlSchemaAttributeOrGroupRef> attrs) {

    if ((attrs == null) || attrs.isEmpty()) {
      return null;
    }

    HashMap<QName, XmlSchemaAttribute> attributes =
        new HashMap<QName, XmlSchemaAttribute>();

    for (XmlSchemaAttributeOrGroupRef attr : attrs) {

      if (attr instanceof XmlSchemaAttribute) {
        XmlSchemaAttribute attribute =
            getAttribute((XmlSchemaAttribute) attr, false);

        attributes.put(attribute.getQName(), attribute);

      } else if (attr instanceof XmlSchemaAttributeGroupRef) {
        final List<XmlSchemaAttribute> attrList =
            getAttributesOf((XmlSchemaAttributeGroupRef) attr);

        for (XmlSchemaAttribute attribute : attrList) {
          attributes.put(attribute.getQName(), attribute);
        }
      }
    }

    return attributes;
  }

  private HashMap<QName, XmlSchemaAttribute> mergeAttributes(
      HashMap<QName, XmlSchemaAttribute> parentAttrs,
      HashMap<QName, XmlSchemaAttribute> childAttrs) {

    if ((parentAttrs == null) || parentAttrs.isEmpty()) {
      return childAttrs;
    } else if ((childAttrs == null) || childAttrs.isEmpty()) {
      return parentAttrs;
    }

    HashMap<QName, XmlSchemaAttribute> newAttrs =
        (HashMap<QName, XmlSchemaAttribute>) parentAttrs.clone();

    /* Child attributes inherit all parent attributes, but may
     * change the type, usage, default value, or fixed value.
     */
    for (Map.Entry<QName, XmlSchemaAttribute> parentAttrEntry :
           parentAttrs.entrySet()) {

      XmlSchemaAttribute parentAttr = parentAttrEntry.getValue();
      XmlSchemaAttribute childAttr = childAttrs.get(parentAttrEntry.getKey());
      if (childAttr != null) {
        XmlSchemaAttribute newAttr = getAttribute(parentAttr, true);

        if (childAttr.getSchemaType() != null) {
          newAttr.setSchemaType( childAttr.getSchemaType() );
        }

        if (childAttr.getUse() != XmlSchemaUse.NONE) {
          newAttr.setUse( childAttr.getUse() );
        }

        // Attribute values may be defaulted or fixed, but not both.
        if (childAttr.getDefaultValue() != null) {
          newAttr.setDefaultValue( childAttr.getDefaultValue() );
          newAttr.setFixedValue(null);

        } else if (childAttr.getFixedValue() != null) {
          newAttr.setFixedValue( childAttr.getFixedValue() );
          newAttr.setDefaultValue(null);
        }

        newAttrs.put(newAttr.getQName(), newAttr);
      }
    }

    return newAttrs;
  }

  private XmlSchemaScope getScope(XmlSchemaType type) {
    if ((type.getQName() != null) && scopeCache.containsKey(type.getQName())) {
      return scopeCache.get(type.getQName());
    } else {
      XmlSchemaScope scope = new XmlSchemaScope(this, type);
      if (type.getQName() != null) {
        scopeCache.put(type.getQName(), scope);
      }
      return scope;
    }
  }

  private QName getUserRecognizedType(
      QName simpleType,
      XmlSchemaTypeInfo parent) {

    if (userRecognizedTypes == null) {
      return null;
    } else if (simpleType == null) {
      return (parent == null) ? null : parent.getUserRecognizedType();

    } else if ( userRecognizedTypes.contains(simpleType) ) {
      return simpleType;
    }

    if ( XmlSchemaBaseSimpleType.isBaseSimpleType(simpleType) ) {

      boolean checkAnyType = true;
      boolean checkAnySimpleType = true;
      switch ( XmlSchemaBaseSimpleType.getBaseSimpleTypeFor(simpleType) ) {
      case ANYTYPE:
        checkAnyType = false;
      case ANYSIMPLETYPE:
        checkAnySimpleType = false;
      default:
      }

      if (checkAnySimpleType) {
        final QName anySimpleType =
            XmlSchemaBaseSimpleType.ANYSIMPLETYPE.getQName();
        if ( userRecognizedTypes.contains(anySimpleType) ) {
          return anySimpleType;
        }
      }

      if (checkAnyType) {
        final QName anyType = XmlSchemaBaseSimpleType.ANYTYPE.getQName();
        if (userRecognizedTypes.contains(anyType)) {
          return anyType;
        }
      }
    }

    return (parent == null) ? null : parent.getUserRecognizedType();
  }

  private static String getName(XmlSchemaNamed name, String defaultName) {
    if (name.isAnonymous()) {
      return defaultName;
    } else {
      return name.getName();
    }
  }

  private static HashMap<XmlSchemaRestriction.Type, List<XmlSchemaRestriction>>
    mergeFacets(
        HashMap<XmlSchemaRestriction.Type,
        List<XmlSchemaRestriction>> parentFacets, List<XmlSchemaFacet> child) {

    if ((child == null) || child.isEmpty()) {
      return parentFacets;
    }

    HashMap<XmlSchemaRestriction.Type, List<XmlSchemaRestriction>> childFacets
      = new  HashMap<XmlSchemaRestriction.Type, List<XmlSchemaRestriction>>(
          child.size() );

    for (XmlSchemaFacet facet : child) {
      XmlSchemaRestriction rstr = new XmlSchemaRestriction(facet);
      List<XmlSchemaRestriction> rstrList = childFacets.get( rstr.getType() );
      if (rstrList == null) {
        // Only enumerations may have more than one value.
        if (rstr.getType() == XmlSchemaRestriction.Type.ENUMERATION) {
          rstrList = new ArrayList<XmlSchemaRestriction>(5);
        } else {
          rstrList = new ArrayList<XmlSchemaRestriction>(1);
        }
        childFacets.put(rstr.getType(), rstrList);
      }
      rstrList.add(rstr);
    }

    if (parentFacets == null) {
      return childFacets;
    }

    HashMap<XmlSchemaRestriction.Type, List<XmlSchemaRestriction>> mergedFacets
        = (HashMap<XmlSchemaRestriction.Type, List<XmlSchemaRestriction>>)
          parentFacets.clone();

    // Child facets override parent facets
    for (Map.Entry<XmlSchemaRestriction.Type, List<XmlSchemaRestriction>>
           rstrEntry : childFacets.entrySet()) {

      mergedFacets.put(rstrEntry.getKey(), rstrEntry.getValue());
    }

    return mergedFacets;
  }

  private static XmlSchemaTypeInfo restrictTypeInfo(
      XmlSchemaTypeInfo parentTypeInfo,
      HashMap<XmlSchemaRestriction.Type, List<XmlSchemaRestriction>> facets) {

    XmlSchemaTypeInfo typeInfo = null;

    switch(parentTypeInfo.getType()) {
    case LIST:
      typeInfo =
        new XmlSchemaTypeInfo(
            parentTypeInfo.getChildTypes().get(0),
            facets);
      break;
    case UNION:
      typeInfo =
        new XmlSchemaTypeInfo(
            parentTypeInfo.getChildTypes(),
            facets);
      break;
    case ATOMIC:
      typeInfo =
        new XmlSchemaTypeInfo(
          parentTypeInfo.getBaseType(),
          facets);
      break;
    default:
      throw new IllegalStateException(
          "Cannot restrict on a " + parentTypeInfo.getType() + " type.");
    }

    if (parentTypeInfo.getUserRecognizedType() != null) {
      typeInfo.setUserRecognizedType( parentTypeInfo.getUserRecognizedType() );
    }

    return typeInfo;
  }
}
