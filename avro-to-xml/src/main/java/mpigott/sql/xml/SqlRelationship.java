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

/**
 * Represents a relationship in an SQL-based relational database.
 *
 * @author  Mike Pigott
 */
public enum SqlRelationship {
  ZERO_TO_ONE,
  ONE_TO_ONE,
  ONE_TO_MANY,
  ZERO_TO_MANY,
  MANY_TO_MANY;
}
