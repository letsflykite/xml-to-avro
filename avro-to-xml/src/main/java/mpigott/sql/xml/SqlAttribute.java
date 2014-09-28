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

package mpigott.sql.xml;

import org.apache.ws.commons.schema.walker.XmlSchemaTypeInfo;

/**
 * Represents an attribute in a relational database's table.
 *
 * @author  Mike Pigott
 */
public final class SqlAttribute {
  SqlType type;
  String name;
  boolean isNullable;
  // Other nice things.

  SqlAttribute(String name, XmlSchemaTypeInfo type) {
    
  }
}
