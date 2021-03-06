/*
 * Copyright (c) 2015, the Dart project authors.
 *
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.dartlang.vm.service.element;

// This is a generated file.

import com.google.gson.JsonObject;

/**
 * A {@link Func} represents a Dart language function.
 */
public class Func extends Obj {

  public Func(JsonObject json) {
    super(json);
  }

  /**
   * The compiled code associated with this function.
   */
  public CodeRef getCode() {
    return new CodeRef((JsonObject) json.get("code"));
  }

  /**
   * The location of this function in the source code.
   */
  public SourceLocation getLocation() {
    return new SourceLocation((JsonObject) json.get("location"));
  }

  /**
   * The name of this function.
   */
  public String getName() {
    return json.get("name").getAsString();
  }
}
