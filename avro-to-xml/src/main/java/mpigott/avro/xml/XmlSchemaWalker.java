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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaAll;
import org.apache.ws.commons.schema.XmlSchemaAny;
import org.apache.ws.commons.schema.XmlSchemaAttribute;
import org.apache.ws.commons.schema.XmlSchemaChoice;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.XmlSchemaGroup;
import org.apache.ws.commons.schema.XmlSchemaGroupParticle;
import org.apache.ws.commons.schema.XmlSchemaGroupRef;
import org.apache.ws.commons.schema.XmlSchemaObject;
import org.apache.ws.commons.schema.XmlSchemaParticle;
import org.apache.ws.commons.schema.XmlSchemaSequence;
import org.apache.ws.commons.schema.XmlSchemaSequenceMember;
import org.apache.ws.commons.schema.XmlSchemaType;

/**
 * Walks an {@link XmlSchema} from a starting {@link XmlSchemaElement},
 * notifying attached visitors as it decends.  Can be configured for
 * either a depth-first or a breadth-first walk.
 *
 * @author  Mike Pigott
 */
final class XmlSchemaWalker {

  /**
   * Initializes the {@link XmlSchemaWalker} with the {@link XmlScheamCollection}
   * to reference when following an {@link XmlSchemaElement}.
   */
  XmlSchemaWalker(XmlSchemaCollection xmlSchemas) {
    if (xmlSchemas == null) {
      throw new IllegalArgumentException("Input XmlSchemaCollection cannot be null.");
    }

    schemas = xmlSchemas;
    visitors = new ArrayList<XmlSchemaVisitor>(1);

    schemasByNamespace = new HashMap<String, XmlSchema>();
    elemsBySubstGroup = new HashMap<QName, List<XmlSchemaElement>>();

    for (XmlSchema schema : schemas.getXmlSchemas()) {
      schemasByNamespace.put(schema.getTargetNamespace(), schema);

      for (XmlSchemaElement elem : schema.getElements().values()) {
        if (elem.getSubstitutionGroup() != null) {
          List<XmlSchemaElement> elems = elemsBySubstGroup.get( elem.getSubstitutionGroup() );
          if (elems == null) {
            elems = new ArrayList<XmlSchemaElement>();
            elemsBySubstGroup.put(elem.getSubstitutionGroup(), elems);
          }
          elems.add(elem);
        }
      }
    }
  }

  XmlSchemaWalker(XmlSchemaCollection xmlSchemas, XmlSchemaVisitor visitor) {
    this(xmlSchemas);
    if (visitor != null) {
      visitors.add(visitor);
    }
  }

  XmlSchemaWalker addVisitor(XmlSchemaVisitor visitor) {
    visitors.add(visitor);
    return this;
  }

  XmlSchemaWalker removeVisitor(XmlSchemaVisitor visitor) {
    if (visitor != null) {
      visitors.remove(visitor);
    }
    return this;
  }

  // Depth-first search.  Visitors will build a stack of XmlSchemaParticle.
  void walk(XmlSchemaElement element) {
    element = getElement(element);

    /* If this element is the root of a
     * substitution group, notify the visitors.
     */
    List<XmlSchemaElement> substitutes = null;
    if ( elemsBySubstGroup.containsKey(element.getQName()) ) {
      substitutes = elemsBySubstGroup.get( element.getQName() );

      for (XmlSchemaVisitor visitor : visitors) {
        visitor.onEnterSubstitutionGroup(element);
      }
    }

    XmlSchemaType schemaType = element.getSchemaType();
    if (schemaType == null) {
      final QName typeQName = element.getSchemaTypeName();
      XmlSchema schema = schemasByNamespace.get( typeQName.getNamespaceURI() );
      schemaType = schema.getTypeByName(typeQName);
    }

    XmlSchemaScope scope = new XmlSchemaScope(schemaType, schemasByNamespace);

    // 1. Fetch all attributes as a List<XmlSchemaAttribute>.
    final Collection<XmlSchemaAttribute> attrs = scope.getAttributesInScope();
    final XmlSchemaTypeInfo typeInfo = scope.getTypeInfo();

    // 2. for each visitor, call visitor.startElement(element, type);
    for (XmlSchemaVisitor visitor : visitors) {
      visitor.onEnterElement(element, typeInfo);
    }

    // 3. Walk the attributes in the element, retrieving type information.
    if (attrs != null) {
      for (XmlSchemaAttribute attr : attrs) {
        final XmlSchemaScope attrScope =
            new XmlSchemaScope(attr.getSchemaType(), schemasByNamespace);
        final XmlSchemaTypeInfo attrTypeInfo = attrScope.getTypeInfo();
  
        for (XmlSchemaVisitor visitor : visitors) {
          visitor.onVisitAttribute(element, attr, attrTypeInfo);
        }
      }
    }

    // 4. Walk the child groups and elements (if any), depth-first.
    final XmlSchemaParticle child = scope.getParticle();
    if (child != null) {
      walk(child);
    }

    // 5. On the way back up, call visitor.endElement(element, type, attributes);
    for (XmlSchemaVisitor visitor : visitors) {
      visitor.onExitElement(element, typeInfo);
    }

    // Now handle substitute elements, if any.
    if (substitutes != null) {
      for (XmlSchemaElement substitute : substitutes) {
        walk(substitute);
      }

      for (XmlSchemaVisitor visitor : visitors) {
        visitor.onExitSubstitutionGroup(element);
      }
    }
  }

  private void walk(XmlSchemaParticle particle) {
    if (particle instanceof XmlSchemaGroupRef) {
      XmlSchemaGroupRef groupRef = (XmlSchemaGroupRef) particle;
      XmlSchemaGroupParticle group = groupRef.getParticle();
      if (group == null) {
        XmlSchema schema = schemasByNamespace.get( groupRef.getRefName().getNamespaceURI() );
        group = schema.getGroupByName( groupRef.getRefName() ).getParticle();
      }
      walk(group, groupRef.getMinOccurs(), groupRef.getMaxOccurs());

    } else if (particle instanceof XmlSchemaGroupParticle) {
      walk((XmlSchemaGroupParticle) particle,
           particle.getMinOccurs(),
           particle.getMaxOccurs());

    } else if (particle instanceof XmlSchemaElement) {
      walk((XmlSchemaElement) particle);

    } else if (particle instanceof XmlSchemaAny) {
      // Ignored.

    } else {
      throw new IllegalArgumentException("Unknown particle type " + particle.getClass().getName());
    }

  }

  private void walk(XmlSchemaGroupParticle group, long minOccurs, long maxOccurs) {
    // Only make a copy of the particle if the minOccurs or maxOccurs was set.
    final boolean forceCopy =
        ((minOccurs != group.getMinOccurs())
            || (maxOccurs != group.getMaxOccurs()));

    // 1. Determine the group particle type.
    // 2. Make a copy if necessary.
    // 3. Notify the visitors.
    // 4. Walk the children.
    // 5. Notify the visitors of the ending
  }

  /**
   * If the provided {@link XmlSchemaElement} is a reference, track down the
   * original and add the minimum and maximum occurrence fields.  Otherwise,
   * just return the provided <code>element</code>.
   *
   * @param element The element to get the definition of.
   * @return The real {@link XmlSchemaElement}.
   */
  private XmlSchemaElement getElement(XmlSchemaElement element) {
    if ( !element.isRef() ) {
      return element;
    }

    final QName elemQName = element.getRefBase().getTargetQName();
    final XmlSchema schema = schemasByNamespace.get( elemQName.getNamespaceURI() );

    XmlSchemaElement globalElem = null;
    if (element.getRef().getTarget() != null) {
      globalElem = element.getRef().getTarget();
    } else {
      globalElem = schema.getElementByName(elemQName);
    }

    /* An XML Schema element reference defines the id, minOccurs, and maxOccurs
     * attributes, while the global element definition defines id and all other
     * attributes.  This combines the two together.
     */
    String id = element.getId();
    if (id == null) {
      id = globalElem.getId();
    }

    final XmlSchemaElement copy = new XmlSchemaElement(schema, false);
    copy.setAbstract( globalElem.isAbstract() );
    copy.setAnnotation( globalElem.getAnnotation() );
    copy.setBlock( globalElem.getBlock() );
    copy.setDefaultValue( globalElem.getDefaultValue() );
    copy.setFinal( globalElem.getFinal() );
    copy.setFixedValue( globalElem.getFixedValue() );
    copy.setForm( globalElem.getForm() );
    copy.setId(id);
    copy.setLineNumber( element.getLineNumber() );
    copy.setLinePosition( element.getLinePosition() );
    copy.setMaxOccurs( element.getMaxOccurs() );
    copy.setMinOccurs( element.getMinOccurs() );
    copy.setMetaInfoMap( globalElem.getMetaInfoMap() );
    copy.setName( globalElem.getName() );
    copy.setNillable( globalElem.isNillable() );
    copy.setType( globalElem.getSchemaType() );
    copy.setSchemaTypeName( globalElem.getSchemaTypeName() );
    copy.setSourceURI( globalElem.getSourceURI() );
    copy.setSubstitutionGroup( globalElem.getSubstitutionGroup() );
    copy.setUnhandledAttributes( globalElem.getUnhandledAttributes() );

    return copy;
  }

  private XmlSchemaCollection schemas;
  private ArrayList<XmlSchemaVisitor> visitors;
  private Map<QName, List<XmlSchemaElement>> elemsBySubstGroup;
  private Map<String, XmlSchema> schemasByNamespace;
}
