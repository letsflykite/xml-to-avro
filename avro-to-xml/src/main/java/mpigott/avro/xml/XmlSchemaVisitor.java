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

import java.util.List;

import org.apache.ws.commons.schema.XmlSchemaAttribute;
import org.apache.ws.commons.schema.XmlSchemaElement;

/**
 * Defines a visitor interface for notifications when walking
 * an {@link XmlSchema} using the {@link XmlSchemaWalker}.
 *
 * @author  Mike Pigott
 */
interface XmlSchemaVisitor {

  void onEnterElement(
      XmlSchemaElement element);

  void onExitElement(
      XmlSchemaElement element,
      XmlSchemaTypeInfo typeInfo);

  void onVisitAttribute(
      XmlSchemaElement element,
      XmlSchemaAttribute attribute,
      XmlSchemaTypeInfo attributeType);


  void onEnterSubstitutionGroup(XmlSchemaElement base);
  void onExitSubstitutionGroup(XmlSchemaElement base);
}
