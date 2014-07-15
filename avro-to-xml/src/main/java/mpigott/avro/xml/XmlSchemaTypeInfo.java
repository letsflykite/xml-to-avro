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

import org.apache.avro.Schema;
import org.codehaus.jackson.JsonNode;

/**
 * Represents the an Avro {@link Schema.Type} and its corresponding
 * {@link org.apache.ws.commons.schema.XmlSchemaType}, encoded in JSON.
 *
 * <p>
 * In cases where we do not have access to the original XML Schema,
 * the XML Schema Type encoded in JSON will disambiguate the lower
 * fidelity of the Avro schema type.
 * </p>
 *
 * @author  Mike Pigott
 */
final class XmlSchemaTypeInfo {
  private Schema.Type avroSchemaType;
  private JsonNode xmlSchemaType;
}
