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

import java.util.Collection;
import java.util.List;

import org.apache.ws.commons.schema.XmlSchemaAll;
import org.apache.ws.commons.schema.XmlSchemaAny;
import org.apache.ws.commons.schema.XmlSchemaAnyAttribute;
import org.apache.ws.commons.schema.XmlSchemaAttribute;
import org.apache.ws.commons.schema.XmlSchemaChoice;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.XmlSchemaSequence;

/**
 * Defines a visitor interface for notifications when walking
 * an {@link XmlSchema} using the {@link XmlSchemaWalker}.
 *
 * @author  Mike Pigott
 */
interface XmlSchemaVisitor {

  void onEnterElement(
      XmlSchemaElement element,
      XmlSchemaTypeInfo typeInfo);

  void onExitElement(
      XmlSchemaElement element,
      XmlSchemaTypeInfo typeInfo);

  void onVisitAttribute(
      XmlSchemaElement element,
      XmlSchemaAttribute attribute,
      XmlSchemaTypeInfo attributeType);

  void onEnterSubstitutionGroup(XmlSchemaElement base);
  void onExitSubstitutionGroup(XmlSchemaElement base);

  void onEnterAllGroup(XmlSchemaAll all);
  void onExitAllGroup(XmlSchemaAll all);

  void onEnterChoiceGroup(XmlSchemaChoice choice);
  void onExitChoiceGroup(XmlSchemaChoice choice);

  void onEnterSequenceGroup(XmlSchemaSequence seq);
  void onExitSequenceGroup(XmlSchemaSequence seq);

  void onVisitAny(XmlSchemaAny any);

  void onVisitAnyAttribute(
      XmlSchemaElement element,
      XmlSchemaAnyAttribute anyAttr);
}
