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

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.transform.stream.StreamSource;

import org.junit.Assert;

import org.apache.avro.Schema;
import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaAll;
import org.apache.ws.commons.schema.XmlSchemaAny;
import org.apache.ws.commons.schema.XmlSchemaAnyAttribute;
import org.apache.ws.commons.schema.XmlSchemaAttribute;
import org.apache.ws.commons.schema.XmlSchemaChoice;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.XmlSchemaFractionDigitsFacet;
import org.apache.ws.commons.schema.XmlSchemaMaxInclusiveFacet;
import org.apache.ws.commons.schema.XmlSchemaMinInclusiveFacet;
import org.apache.ws.commons.schema.XmlSchemaParticle;
import org.apache.ws.commons.schema.XmlSchemaPatternFacet;
import org.apache.ws.commons.schema.XmlSchemaSequence;
import org.apache.ws.commons.schema.XmlSchemaUse;
import org.apache.ws.commons.schema.XmlSchemaWhiteSpaceFacet;
import org.junit.Test;

/**
 * 
 *
 * @author  Mike Pigott
 */
public class TestSchemaWalker {

  private static enum Type {
    ELEMENT,
    SEQUENCE,
    CHOICE,
    ALL,
    SUBSTITUTION_GROUP
  }

  private static class StackEntry {
    StackEntry(Type type) {
      this.type = type;
      this.name = null;
      this.typeName = null;
      this.facets = null;
      this.schema = null;
      this.minOccurs = 1;
      this.maxOccurs = 1;
    }

    StackEntry(Type type, long minOccurs, long maxOccurs) {
      this(type);
      this.minOccurs = minOccurs;
      this.maxOccurs = maxOccurs;
    }

    StackEntry(Type type, String name) {
      this(type);
      this.name = name;
      this.typeName = null;
    }

    StackEntry(Type type, String name, String typeName) {
      this(type, name);
      this.typeName = typeName;
    }

    StackEntry(Type type, String name, String typeName, Schema schema) {
      this(type, name, typeName);
      this.schema = schema;
    }

    StackEntry(Type type, String name, String typeName, long minOccurs, long maxOccurs) {
      this(type, name, typeName);
      this.minOccurs = minOccurs;
      this.maxOccurs = maxOccurs;
    }

    StackEntry(Type type, String name, String typeName, Schema schema, long minOccurs, long maxOccurs) {
      this(type, name, typeName, schema);
      this.minOccurs = minOccurs;
      this.maxOccurs = maxOccurs;
    }

    StackEntry(Type type, String name, String typeName, Schema schema, Set<XmlSchemaRestriction> facets) {
      this(type, name, typeName, schema);
      this.facets = facets;
    }

    StackEntry(
        Type type,
        String name,
        String typeName,
        Schema schema,
        long minOccurs,
        long maxOccurs,
        Set<XmlSchemaRestriction> facets) {

      this(type, name, typeName, schema, minOccurs, maxOccurs);
      this.facets = facets;
      
    }

    Type type;
    String name;
    String typeName;
    Set<XmlSchemaRestriction> facets;
    long minOccurs;
    long maxOccurs;
    Schema schema;
  }

  private static class Attribute {
    public Attribute(String name, String typeName, Schema schema) {
      this.name = name;
      this.typeName = typeName;
      this.isOptional = false;
      this.schema = schema;
    }

    public Attribute(String name, String typeName, Schema schema, boolean isOptional) {
      this(name, typeName, schema);
      this.isOptional = isOptional;
    }

    public Attribute(String name, String typeName, Schema schema, Set<XmlSchemaRestriction> facets) {
      this(name, typeName, schema);
      this.facets = facets;
    }

    public Attribute(String name, String typeName, Schema schema, boolean isOptional, Set<XmlSchemaRestriction> facets) {
      this(name, typeName, schema, isOptional);
      this.facets = facets;
    }

    String name;
    String typeName;
    boolean isOptional;
    Set<XmlSchemaRestriction> facets;
    Schema schema;
  }

  private static class Visitor implements XmlSchemaVisitor {

    Visitor(List<StackEntry> stack, HashMap<String, List<Attribute>> attributes) {
      this.stack = stack;
      this.attributes = attributes;
    }

    @Override
    public void onEnterElement(XmlSchemaElement element,
        XmlSchemaTypeInfo typeInfo, boolean previouslyVisited) {

      StackEntry next = pop();
      if (next.type != Type.ELEMENT) {
        throw new IllegalStateException("Expected a " + next.type + " named \"" + next.name + "\" but received an element named \"" + element.getName() + "\".");

      } else if (!next.name.equals( element.getName() )) {
        throw new IllegalStateException("Expected an element named \"" + next.name + "\" but received an element named " + element.getName() + "\"");

      } else if ((next.typeName == null) && !element.getSchemaType().isAnonymous()) {
        throw new IllegalStateException("Expected the element named \"" + next.name + "\" to carry an anonymous type, but the type was " + element.getSchemaType().getQName());

      } else if ((next.typeName != null) && element.getSchemaType().isAnonymous()) {
        throw new IllegalStateException("Expected the element named \"" + next.name + "\" to carry a type named \"" + next.typeName + "\"; but the type was anonymous instead.");

      }

      checkMinAndMaxOccurs(next, element);

      if (typeInfo != null) {
        final HashMap<XmlSchemaRestriction.Type, List<XmlSchemaRestriction>> facets = typeInfo.getFacets();
        if (facets.isEmpty() && !next.facets.isEmpty()) {
          throw new IllegalStateException("Expected " + next.facets.size() + " facets for element \"" + next.name + "\" but found none.");
        } else if (!facets.isEmpty() && next.facets.isEmpty()) {
          throw new IllegalStateException("Element " + next.name + " has facets, but none were expected.");
        }
  
        for (Map.Entry<XmlSchemaRestriction.Type, List<XmlSchemaRestriction>> facetsForType : facets.entrySet()) {
          for (XmlSchemaRestriction facet : facetsForType.getValue()) {
            if (!next.facets.remove(facet)) {
              throw new IllegalStateException("Element \"" + next.name + "\" has unexpected facet \"" + facet + "\".");
            }
          }
        }

        if ((next.schema == null) && (typeInfo.getAvroType() != null)) {
          throw new IllegalStateException("Element \"" + next.name + "\" was not expected to have an Avro schema, but has a schema of " + typeInfo.getAvroType());
        } else if ((next.schema != null) && (typeInfo.getAvroType() == null)) {
          throw new IllegalStateException("Element \"" + next.name + "\" was expected to have a schema of " + next.schema + " but instead has no schema.");
        } else if (!next.schema.equals(typeInfo.getAvroType())) {
          throw new IllegalStateException("Element \"" + next.name + "\" was expected to have a schema of " + next.schema + " but instead has a schema of " + typeInfo.getAvroType());
        }

      } else if (next.schema != null) {
        throw new IllegalStateException("Expected a schema of " + next.schema + " but received none.");
      }

      if ((next.facets != null) && !next.facets.isEmpty()) {
        StringBuilder errMsg = new StringBuilder("Element \"");
        errMsg.append(next.name).append("\" was expected to have the following facets, but did not:");
        for (XmlSchemaRestriction facet : next.facets) {
          errMsg.append(" \"").append(facet).append('\"');
        }
        throw new IllegalStateException( errMsg.toString() );
      }
    }

    @Override
    public void onExitElement(
        XmlSchemaElement element,
        XmlSchemaTypeInfo typeInfo,
        boolean previouslyVisited) {

      if ( !previouslyVisited && attributes.containsKey( element.getName() ) ) {
        List<Attribute> remainingAttrs = attributes.get( element.getName() );
        if ( !remainingAttrs.isEmpty() ) {
          StringBuilder errMsg = new StringBuilder("Element \"");
          errMsg.append( element.getName() );
          errMsg.append("\" did not have the expected attributes of the following names:");
          for (Attribute attr : remainingAttrs) {
            errMsg.append(" \"").append( attr.name ).append('\"');
          }
          throw new IllegalStateException( errMsg.toString() );
        }
      }
    }

    @Override
    public void onVisitAttribute(XmlSchemaElement element,
        XmlSchemaAttribute attribute, XmlSchemaTypeInfo attributeType) {

      if ( !attributes.containsKey( element.getName() ) ) {
        throw new IllegalStateException("No attributes were expected for \"" + element.getName() + "\", but \"" + attribute.getQName() + "\" was found.");
      }

      List<Attribute> attrs = attributes.get( element.getName() );
      boolean found = false;
      int index = 0;

      for (; index < attrs.size(); ++index) {
        Attribute attr = attrs.get(index);
        if ( attr.name.equals( attribute.getName() ) ) {
          if ((attr.typeName == null) && !attribute.getSchemaType().isAnonymous()) {
            throw new IllegalStateException("Element \"" + element.getName() + "\" has an attribute named \"" + attr.name + "\" whose type was expected to be anonymous, but actually is named \"" + attribute.getSchemaType().getName() + "\"");

          } else if ((attr.typeName != null) && attribute.getSchemaType().isAnonymous()) {
            throw new IllegalStateException("Element \"" + element.getName() + "\" has an attribute named \"" + attr.name + "\" whose type was expected to be \"" + attr.typeName + "\"; but is anonymous instead.");

          } else if ((attr.typeName != null) && !attr.typeName.equals( attribute.getSchemaType().getName() )) {
            throw new IllegalStateException("Element \"" + element.getName() + "\" has an attribute named \"" + attr.name + "\"; its type was expected to be \"" + attr.typeName + "\" but instead was \"" + attribute.getSchemaType().getName() + "\"");

          } else if (attr.isOptional && !attribute.getUse().equals(XmlSchemaUse.OPTIONAL)) {
            throw new IllegalStateException("Element \"" + element.getName() + "\" has an attribute named \"" + attr.name + "\" whose usage was expected to be optional, but instead is " + attribute.getUse());

          } else if (!attr.isOptional && attribute.getUse().equals(XmlSchemaUse.OPTIONAL)) {
            throw new IllegalStateException("Element \"" + element.getName() + "\" has an attribute named \"" + attr.name + "\" whose usage was expected to be required, but is actually optional.");

          } else if (!attr.schema.equals(attributeType.getAvroType())) {
            throw new IllegalStateException("Element \"" + element.getName() + "\" has an attribute named \"" + attr.name + "\" whose schema was expected to be " + attr.schema + " but actually was " + attributeType.getAvroType());

          } else {
            found = true;
            break;
          }
        }
      }

      if (found) {
        attrs.remove(index);
      } else {
        throw new IllegalStateException("Element \"" + element.getName() + "\" has unexpected attribute \"" + attribute.getName() + "\"");
      }
    }

    @Override
    public void onEnterSubstitutionGroup(XmlSchemaElement base) {
      StackEntry next = pop();

      if (next.type != Type.SUBSTITUTION_GROUP) {
        throw new IllegalStateException("Expected a " + next.type + " but instead found a substition group of \"" + base.getName() + "\"");

      } else if (!next.name.equals( base.getName() )) {
        throw new IllegalStateException("Expected a substitution group for element \"" + next.name + "\", but instead received one for \"" + base.getName() + "\"");
      }
    }

    @Override
    public void onExitSubstitutionGroup(XmlSchemaElement base) { }

    @Override
    public void onEnterAllGroup(XmlSchemaAll all) {
      StackEntry next = pop();
      if (next.type != Type.ALL) {
        throw new IllegalStateException("Expected a " + next.type + " but received an All group.");
      }
      checkMinAndMaxOccurs(next, all);
    }

    @Override
    public void onExitAllGroup(XmlSchemaAll all) { }

    @Override
    public void onEnterChoiceGroup(XmlSchemaChoice choice) {
      StackEntry next = pop();
      if (next.type != Type.CHOICE) {
        throw new IllegalStateException("Expected a " + next.type + " but received a Choice group.");
      }
      checkMinAndMaxOccurs(next, choice);
    }

    @Override
    public void onExitChoiceGroup(XmlSchemaChoice choice) { }

    @Override
    public void onEnterSequenceGroup(XmlSchemaSequence seq) {
      StackEntry next = pop();
      if (next.type != Type.SEQUENCE) {
        throw new IllegalStateException("Expected a " + next.type + " but received a Sequence group.");
      }
      checkMinAndMaxOccurs(next, seq);
    }

    @Override
    public void onExitSequenceGroup(XmlSchemaSequence seq) { }

    @Override
    public void onVisitAny(XmlSchemaAny any) {
      throw new IllegalStateException("No Any types were expected in the schema.");
    }

    @Override
    public void onVisitAnyAttribute(XmlSchemaElement element,
        XmlSchemaAnyAttribute anyAttr) {

      throw new IllegalStateException("No anyAttribute types were expected in the schema.");
    }

    private void checkMinAndMaxOccurs(StackEntry next, XmlSchemaParticle particle) {
      if (next.minOccurs != particle.getMinOccurs()) {
        throw new IllegalStateException("Expected a minOccurs of " + next.minOccurs + " for " + next.type + " \"" + next.name + "\", but found a minOccurs of " + particle.getMinOccurs());

      } else if (next.maxOccurs != particle.getMaxOccurs()) {
        throw new IllegalStateException("Expected a maxOccurs of " + next.maxOccurs + " for " + next.type + " \"" + next.name + "\", but found a maxOccurs of " + particle.getMaxOccurs());

      }
    }

    private StackEntry pop() {
      if ( stack.isEmpty() ) {
        throw new IllegalStateException("Ran out of stack!");
      }

      StackEntry entry = stack.get(0);
      stack.remove(stack.get(0));
      return entry;
    }

    private List<StackEntry> stack;
    private HashMap<String, List<Attribute>> attributes;
  }

  /**
   * Test for src/main/resources/test_schema.xsd
   */
  @Test
  public void test() throws Exception {
    // Build the expectations.
    ArrayList<Attribute> attrGroupAttrs = new ArrayList<Attribute>(43);
    
    attrGroupAttrs.add( new Attribute("anySimpleType", "anySimpleType", Schema.create(Schema.Type.STRING), true) );

    HashSet<XmlSchemaRestriction> whiteSpaceCollapseFixedRestrictions = new HashSet<XmlSchemaRestriction>();
    whiteSpaceCollapseFixedRestrictions.add( new XmlSchemaRestriction(new XmlSchemaWhiteSpaceFacet("collapse", true)) );

    attrGroupAttrs.add( new Attribute("duration",     "duration",     Schema.create(Schema.Type.STRING),  true, (Set<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone()) );
    attrGroupAttrs.add( new Attribute("dateTime",     "dateTime",     Schema.create(Schema.Type.STRING),  true, (Set<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone()) );
    attrGroupAttrs.add( new Attribute("date",         "date",         Schema.create(Schema.Type.STRING),  true, (Set<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone()) );
    attrGroupAttrs.add( new Attribute("time",         "time",         Schema.create(Schema.Type.STRING),  true, (Set<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone()) );
    attrGroupAttrs.add( new Attribute("gYearMonth",   "gYearMonth",   Schema.create(Schema.Type.STRING),  true, (Set<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone()) );
    attrGroupAttrs.add( new Attribute("gYear",        "gYear",        Schema.create(Schema.Type.STRING),  true, (Set<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone()) );
    attrGroupAttrs.add( new Attribute("gDay",         "gDay",         Schema.create(Schema.Type.STRING),  true, (Set<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone()) );
    attrGroupAttrs.add( new Attribute("gMonth",       "gMonth",       Schema.create(Schema.Type.STRING),  true, (Set<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone()) );
    attrGroupAttrs.add( new Attribute("gMonthDay",    "gMonthDay",    Schema.create(Schema.Type.STRING),  true, (Set<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone()) );
    attrGroupAttrs.add( new Attribute("boolean",      "boolean",      Schema.create(Schema.Type.BOOLEAN), true, (Set<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone()) );
    attrGroupAttrs.add( new Attribute("base64Binary", "base64Binary", Schema.create(Schema.Type.BYTES),   true, (Set<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone()) );
    attrGroupAttrs.add( new Attribute("hexBinary",    "hexBinary",    Schema.create(Schema.Type.BYTES),   true, (Set<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone()) );
    attrGroupAttrs.add( new Attribute("float",        "float",        Schema.create(Schema.Type.FLOAT),   true, (Set<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone()) );
    attrGroupAttrs.add( new Attribute("decimal",      "decimal",      Schema.create(Schema.Type.DOUBLE),  true, (Set<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone()) );
    attrGroupAttrs.add( new Attribute("double",       "double",       Schema.create(Schema.Type.DOUBLE),  true, (Set<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone()) );
    attrGroupAttrs.add( new Attribute("anyURI",       "anyURI",       Schema.create(Schema.Type.STRING),  true, (Set<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone()) );
    attrGroupAttrs.add( new Attribute("qname",        "QName",        Schema.create(Schema.Type.STRING),  true, (Set<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone()) );

    HashSet<XmlSchemaRestriction> integerFacets = (HashSet<XmlSchemaRestriction>) whiteSpaceCollapseFixedRestrictions.clone();
    integerFacets.add( new XmlSchemaRestriction(new XmlSchemaFractionDigitsFacet(new Integer(0), true)));
    integerFacets.add( new XmlSchemaRestriction(new XmlSchemaPatternFacet("[\\-+]?[0-9]+", false)));
    attrGroupAttrs.add( new Attribute("integer", "integer", Schema.create(Schema.Type.DOUBLE), true, integerFacets) );

    HashSet<XmlSchemaRestriction> nonPositiveIntegerFacets = (HashSet<XmlSchemaRestriction>) integerFacets.clone();
    nonPositiveIntegerFacets.add( new XmlSchemaRestriction(new XmlSchemaMaxInclusiveFacet(new Integer(0), false)) );
    attrGroupAttrs.add( new Attribute("nonPositiveInteger", "nonPositiveInteger", Schema.create(Schema.Type.DOUBLE), true, nonPositiveIntegerFacets) );

    HashSet<XmlSchemaRestriction> negativeIntegerFacets = (HashSet<XmlSchemaRestriction>) integerFacets.clone();
    negativeIntegerFacets.add( new XmlSchemaRestriction(new XmlSchemaMaxInclusiveFacet(new Integer(-1), false)) );
    attrGroupAttrs.add( new Attribute("negativeInteger", "negativeInteger", Schema.create(Schema.Type.DOUBLE), true, negativeIntegerFacets) );

    HashSet<XmlSchemaRestriction> longFacets = (HashSet<XmlSchemaRestriction>) integerFacets.clone();
    longFacets.add( new XmlSchemaRestriction(new XmlSchemaMinInclusiveFacet(new Long(-9223372036854775808L), false)) );
    longFacets.add( new XmlSchemaRestriction(new XmlSchemaMaxInclusiveFacet(new Long( 9223372036854775807L), false)) );
    attrGroupAttrs.add( new Attribute("long", "long", Schema.create(Schema.Type.LONG), true, longFacets) );

    HashSet<XmlSchemaRestriction> intFacets = (HashSet<XmlSchemaRestriction>) integerFacets.clone();
    intFacets.add( new XmlSchemaRestriction( new XmlSchemaMinInclusiveFacet(new Integer(-2147483648), false) ) );
    intFacets.add( new XmlSchemaRestriction( new XmlSchemaMaxInclusiveFacet(2147483647, false) ) );
    attrGroupAttrs.add( new Attribute("int", "int", Schema.create(Schema.Type.INT), true, intFacets) );

    HashSet<XmlSchemaRestriction> shortFacets = (HashSet<XmlSchemaRestriction>) integerFacets.clone();
    shortFacets.add( new XmlSchemaRestriction( new XmlSchemaMinInclusiveFacet(new Short((short) -32768), false) ) );
    shortFacets.add( new XmlSchemaRestriction( new XmlSchemaMaxInclusiveFacet(new Short((short)  32767), false) ) );
    attrGroupAttrs.add( new Attribute("short", "short", Schema.create(Schema.Type.INT), true, shortFacets) );

    HashSet<XmlSchemaRestriction> byteFacets = (HashSet<XmlSchemaRestriction>) integerFacets.clone();
    byteFacets.add( new XmlSchemaRestriction( new XmlSchemaMinInclusiveFacet(new Byte((byte) -128), false) ) );
    byteFacets.add( new XmlSchemaRestriction( new XmlSchemaMaxInclusiveFacet(new Byte((byte)  127), false) ) );
    attrGroupAttrs.add( new Attribute("byte", "byte", Schema.create(Schema.Type.INT), true, byteFacets) );

    HashSet<XmlSchemaRestriction> nonNegativeIntegerFacets = (HashSet<XmlSchemaRestriction>) integerFacets.clone();
    nonNegativeIntegerFacets.add( new XmlSchemaRestriction( new XmlSchemaMinInclusiveFacet(new Integer(0), false) ) );
    attrGroupAttrs.add( new Attribute("nonNegativeInteger", "nonNegativeInteger", Schema.create(Schema.Type.DOUBLE), true, nonNegativeIntegerFacets) );

    HashSet<XmlSchemaRestriction> positiveIntegerFacets = (HashSet<XmlSchemaRestriction>) integerFacets.clone();
    positiveIntegerFacets.add( new XmlSchemaRestriction(new XmlSchemaMinInclusiveFacet(new Integer(1), false)) );
    attrGroupAttrs.add( new Attribute("positiveInteger", "positiveInteger", Schema.create(Schema.Type.DOUBLE), true, positiveIntegerFacets) );

    HashSet<XmlSchemaRestriction> unsignedLongFacets = (HashSet<XmlSchemaRestriction>) integerFacets.clone();
    unsignedLongFacets.add( new XmlSchemaRestriction( new XmlSchemaMaxInclusiveFacet(new BigInteger("18446744073709551615"), false) ) );
    attrGroupAttrs.add( new Attribute("unsignedLong", "unsignedLong", Schema.create(Schema.Type.DOUBLE), true, unsignedLongFacets) );

    HashSet<XmlSchemaRestriction> unsignedIntFacets = (HashSet<XmlSchemaRestriction>) integerFacets.clone();
    unsignedIntFacets.add( new XmlSchemaRestriction( new XmlSchemaMaxInclusiveFacet(new Long(4294967295L), false) ) );
    attrGroupAttrs.add( new Attribute("unsignedInt", "unsignedInt", Schema.create(Schema.Type.LONG), true, unsignedIntFacets) );

    HashSet<XmlSchemaRestriction> unsignedShortFacets = (HashSet<XmlSchemaRestriction>) integerFacets.clone();
    unsignedShortFacets.add( new XmlSchemaRestriction( new XmlSchemaMaxInclusiveFacet(new Integer(65535), false) ) );
    attrGroupAttrs.add( new Attribute("unsignedShort", "unsignedShort", Schema.create(Schema.Type.INT), true, unsignedShortFacets) );

    HashSet<XmlSchemaRestriction> unsignedByteFacets = (HashSet<XmlSchemaRestriction>) integerFacets.clone();
    unsignedByteFacets.add( new XmlSchemaRestriction( new XmlSchemaMaxInclusiveFacet(new Short((short) 255), false) ) );
    attrGroupAttrs.add( new Attribute("unsignedByte", "unsignedByte", Schema.create(Schema.Type.INT), true, unsignedByteFacets) );

    HashSet<XmlSchemaRestriction> stringFacets = new HashSet<XmlSchemaRestriction>();
    stringFacets.add( new XmlSchemaRestriction( new XmlSchemaWhiteSpaceFacet("preserve", false) ) );
    attrGroupAttrs.add( new Attribute("string", "string", Schema.create(Schema.Type.STRING), true, stringFacets) );

    HashSet<XmlSchemaRestriction> normalizedStringFacets = new HashSet<XmlSchemaRestriction>();
    normalizedStringFacets.add( new XmlSchemaRestriction( new XmlSchemaWhiteSpaceFacet("replace", false) ) );
    attrGroupAttrs.add( new Attribute("normalizedString", "normalizedString", Schema.create(Schema.Type.STRING), true, normalizedStringFacets) );

    HashSet<XmlSchemaRestriction> tokenFacets = new HashSet<XmlSchemaRestriction>();
    tokenFacets.add( new XmlSchemaRestriction( new XmlSchemaWhiteSpaceFacet("collapse", false) ) );
    attrGroupAttrs.add( new Attribute("token", "token", Schema.create(Schema.Type.STRING), true, tokenFacets) );

    HashSet<XmlSchemaRestriction> languageFacets = (HashSet<XmlSchemaRestriction>) tokenFacets.clone();
    languageFacets.add( new XmlSchemaRestriction( new XmlSchemaPatternFacet("[a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8})*", false) ) );
    attrGroupAttrs.add( new Attribute("language", "language", Schema.create(Schema.Type.STRING), true, languageFacets) );

    HashSet<XmlSchemaRestriction> nmTokenFacets = (HashSet<XmlSchemaRestriction>) tokenFacets.clone();
    nmTokenFacets.add( new XmlSchemaRestriction( new XmlSchemaPatternFacet("\\c+", false) ) );
    attrGroupAttrs.add( new Attribute("nmtoken", "NMTOKEN", Schema.create(Schema.Type.STRING), true, nmTokenFacets) );

    HashSet<XmlSchemaRestriction> nameFacets = (HashSet<XmlSchemaRestriction>) tokenFacets.clone();
    nameFacets.add( new XmlSchemaRestriction( new XmlSchemaPatternFacet("\\i\\c*", false) ) );
    attrGroupAttrs.add( new Attribute("name", "Name", Schema.create(Schema.Type.STRING), true, nameFacets) );

    HashSet<XmlSchemaRestriction> ncNameFacets = (HashSet<XmlSchemaRestriction>) tokenFacets.clone();
    ncNameFacets.add( new XmlSchemaRestriction( new XmlSchemaPatternFacet("[\\i-[:]][\\c-[:]]*", false) ) );
    attrGroupAttrs.add( new Attribute("ncName", "NCName", Schema.create(Schema.Type.STRING), true, ncNameFacets) );

    attrGroupAttrs.add( new Attribute("id",       "ID",       Schema.create(Schema.Type.STRING),                       true, (Set<XmlSchemaRestriction>) ncNameFacets.clone()) );
    attrGroupAttrs.add( new Attribute("idref",    "IDREF",    Schema.create(Schema.Type.STRING),                       true, (Set<XmlSchemaRestriction>) ncNameFacets.clone()) );
    attrGroupAttrs.add( new Attribute("idrefs",   "IDREFS",   Schema.createArray( Schema.create(Schema.Type.STRING) ), true, (Set<XmlSchemaRestriction>) ncNameFacets.clone()) );
    attrGroupAttrs.add( new Attribute("entity",   "ENTITY",   Schema.create(Schema.Type.STRING),                       true, (Set<XmlSchemaRestriction>) ncNameFacets.clone()) );
    attrGroupAttrs.add( new Attribute("entities", "ENTITIES", Schema.createArray( Schema.create(Schema.Type.STRING) ), true, (Set<XmlSchemaRestriction>) ncNameFacets.clone()) );
    attrGroupAttrs.add( new Attribute("nmtokens", "NMTOKENS", Schema.createArray( Schema.create(Schema.Type.STRING) ), true, (Set<XmlSchemaRestriction>) ncNameFacets.clone()) );

    HashSet<XmlSchemaRestriction> nonNullPrimitiveTypeFacets =
        new HashSet<XmlSchemaRestriction>(14);
    nonNullPrimitiveTypeFacets.add( new XmlSchemaRestriction(XmlSchemaRestriction.Type.ENUMERATION, "boolean",  false) );
    nonNullPrimitiveTypeFacets.add( new XmlSchemaRestriction(XmlSchemaRestriction.Type.ENUMERATION, "int",      false) );
    nonNullPrimitiveTypeFacets.add( new XmlSchemaRestriction(XmlSchemaRestriction.Type.ENUMERATION, "long",     false) );
    nonNullPrimitiveTypeFacets.add( new XmlSchemaRestriction(XmlSchemaRestriction.Type.ENUMERATION, "float",    false) );
    nonNullPrimitiveTypeFacets.add( new XmlSchemaRestriction(XmlSchemaRestriction.Type.ENUMERATION, "double",   false) );
    nonNullPrimitiveTypeFacets.add( new XmlSchemaRestriction(XmlSchemaRestriction.Type.ENUMERATION, "decimal",  false) );
    nonNullPrimitiveTypeFacets.add( new XmlSchemaRestriction(XmlSchemaRestriction.Type.ENUMERATION, "bytes",    false) );
    nonNullPrimitiveTypeFacets.add( new XmlSchemaRestriction(XmlSchemaRestriction.Type.ENUMERATION, "string",   false) );
    nonNullPrimitiveTypeFacets.add( new XmlSchemaRestriction(XmlSchemaRestriction.Type.PATTERN,     "\\c+",     false) );
    nonNullPrimitiveTypeFacets.add( new XmlSchemaRestriction(XmlSchemaRestriction.Type.WHITESPACE,  "collapse", false) );

    Schema nonNullPrimitiveTypeSchema = Schema.create(Schema.Type.STRING);
    Schema primitiveTypeSchema = Schema.create(Schema.Type.STRING);

    HashSet<XmlSchemaRestriction> primitiveTypeFacets =
        new HashSet<XmlSchemaRestriction>(15);
    primitiveTypeFacets.add( new XmlSchemaRestriction(XmlSchemaRestriction.Type.ENUMERATION, "null",     false) );
    primitiveTypeFacets.addAll(nonNullPrimitiveTypeFacets);

    LinkedList<StackEntry> stack = new LinkedList<StackEntry>();

    // Indentation follows tree.
    stack.add( new StackEntry(Type.ELEMENT, "root") );
      stack.add( new StackEntry(Type.SEQUENCE) );
        stack.add( new StackEntry(Type.CHOICE, 0, Long.MAX_VALUE) );
          stack.add( new StackEntry(Type.ELEMENT, "primitive", "primitiveType", primitiveTypeSchema, (Set<XmlSchemaRestriction>)primitiveTypeFacets.clone()) );
          stack.add( new StackEntry(Type.ELEMENT, "nonNullPrimitive", "nonNullPrimitiveType", nonNullPrimitiveTypeSchema, (Set<XmlSchemaRestriction>) nonNullPrimitiveTypeFacets.clone()) );
          stack.add( new StackEntry(Type.SUBSTITUTION_GROUP, "record") );
            stack.add( new StackEntry(Type.ELEMENT, "record", "recordType") );
              stack.add( new StackEntry(Type.SEQUENCE) );
                stack.add( new StackEntry(Type.CHOICE, 0, Long.MAX_VALUE) );
/* 10 */          stack.add( new StackEntry(Type.ELEMENT, "primitive", "primitiveType", primitiveTypeSchema, (Set<XmlSchemaRestriction>)primitiveTypeFacets.clone()) );
                  stack.add( new StackEntry(Type.ELEMENT, "nonNullPrimitive", "nonNullPrimitiveType", nonNullPrimitiveTypeSchema, (Set<XmlSchemaRestriction>) nonNullPrimitiveTypeFacets.clone()) );
                  stack.add( new StackEntry(Type.SUBSTITUTION_GROUP, "record") );
                    stack.add( new StackEntry(Type.ELEMENT, "record", "recordType") );
                    stack.add( new StackEntry(Type.ELEMENT, "map") );
                      stack.add( new StackEntry(Type.SEQUENCE) );
                      stack.add( new StackEntry(Type.CHOICE, 0, Long.MAX_VALUE) );
                        stack.add( new StackEntry(Type.ELEMENT, "primitive", "primitiveType", primitiveTypeSchema, (Set<XmlSchemaRestriction>)primitiveTypeFacets.clone()) );
                        stack.add( new StackEntry(Type.ELEMENT, "nonNullPrimitive", "nonNullPrimitiveType", nonNullPrimitiveTypeSchema, (Set<XmlSchemaRestriction>) nonNullPrimitiveTypeFacets.clone()) );
                        stack.add( new StackEntry(Type.SUBSTITUTION_GROUP, "record") );
/* 20 */                  stack.add( new StackEntry(Type.ELEMENT, "record", "recordType") );
                          stack.add( new StackEntry(Type.ELEMENT, "map") );
                        stack.add( new StackEntry(Type.ELEMENT, "list") );
                          stack.add( new StackEntry(Type.CHOICE) );
                            stack.add( new StackEntry(Type.ELEMENT, "primitive", "primitiveType", primitiveTypeSchema, 1, 100, (Set<XmlSchemaRestriction>)primitiveTypeFacets.clone()) );
                            stack.add( new StackEntry(Type.SUBSTITUTION_GROUP, "record") );
                              stack.add( new StackEntry(Type.ELEMENT, "record", "recordType", 1, 100) );
                              stack.add( new StackEntry(Type.ELEMENT, "map") );
                        stack.add( new StackEntry(Type.ELEMENT, "tuple") );
                          stack.add( new StackEntry(Type.ALL) );
/* 30 */                    stack.add( new StackEntry(Type.ELEMENT, "primitive", "primitiveType", primitiveTypeSchema, (Set<XmlSchemaRestriction>)primitiveTypeFacets.clone()) );
                            stack.add( new StackEntry(Type.ELEMENT, "nonNullPrimitive", "nonNullPrimitiveType", nonNullPrimitiveTypeSchema, (Set<XmlSchemaRestriction>)nonNullPrimitiveTypeFacets.clone()) );
                            stack.add( new StackEntry(Type.SUBSTITUTION_GROUP, "record") );
                              stack.add( new StackEntry(Type.ELEMENT, "record", "recordType") );
                              stack.add( new StackEntry(Type.ELEMENT, "map") );
                            stack.add( new StackEntry(Type.ELEMENT, "list") );
                  stack.add( new StackEntry(Type.ELEMENT, "list") );
                  stack.add( new StackEntry(Type.ELEMENT, "tuple") );
            stack.add( new StackEntry(Type.ELEMENT, "map") );
          stack.add( new StackEntry(Type.ELEMENT, "list") );
          stack.add( new StackEntry(Type.ELEMENT, "tuple") );


    HashMap<String, List<Attribute>> attributes = new HashMap<String, List<Attribute>>();
    attributes.put("root", attrGroupAttrs);

    HashSet<XmlSchemaRestriction> listAttrFacets = (HashSet<XmlSchemaRestriction>) nonNegativeIntegerFacets.clone();
    listAttrFacets.add( new XmlSchemaRestriction(XmlSchemaRestriction.Type.EXCLUSIVE_MAX, 100, false) );
    ArrayList<Attribute> listAttributes = new ArrayList<Attribute>(1);
    listAttributes.add( new Attribute("size", null, Schema.create(Schema.Type.DOUBLE), listAttrFacets) );
    attributes.put("list", listAttributes);

    HashSet<XmlSchemaRestriction> mapAttrFacets = (HashSet<XmlSchemaRestriction>) ncNameFacets.clone();
    mapAttrFacets.add( new XmlSchemaRestriction(XmlSchemaRestriction.Type.LENGTH_MIN, 1, false) );
    ArrayList<Attribute> mapAttributes = new ArrayList<Attribute>(1);
    mapAttributes.add( new Attribute("id", null, Schema.create(Schema.Type.STRING), mapAttrFacets) );
    attributes.put("map", mapAttributes);

    // Compare against the actual.
    final Visitor visitor = new Visitor(stack, attributes);
    final int numEntries = stack.size();

    XmlSchemaCollection collection = null;
    FileReader fileReader = null;
    try {
      File file = new File("src\\test\\resources\\test_schema.xsd");
      fileReader = new FileReader(file);

      collection = new XmlSchemaCollection();
      collection.setSchemaResolver(new XmlSchemaMultiBaseUriResolver());
      collection.read(new StreamSource(fileReader, file.getAbsolutePath()));

    } finally {
      if (fileReader != null) {
        try {
          fileReader.close();
        } catch (IOException ioe) {
          ioe.printStackTrace();
        }
      }
    }

    XmlSchemaElement elem = getElementOf(collection, "root");
    XmlSchemaWalker walker = new XmlSchemaWalker(collection, visitor);
    try {
      walker.walk(elem);
    } catch (Exception e) {
      throw new IllegalStateException("Failed on stack entry " + (numEntries - stack.size()), e);
    }

    Assert.assertTrue( stack.isEmpty() );
  }

  private static XmlSchemaElement getElementOf(XmlSchemaCollection collection, String name) {
    XmlSchemaElement elem = null;
    for (XmlSchema schema : collection.getXmlSchemas()) {
      elem = schema.getElementByName(name);
      if (elem != null) {
        break;
      }
    }
    return elem;
  }

}
