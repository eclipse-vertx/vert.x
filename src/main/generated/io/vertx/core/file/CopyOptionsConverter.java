/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.core.file;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter for {@link io.vertx.core.file.CopyOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.file.CopyOptions} original class using Vert.x codegen.
 */
public class CopyOptionsConverter {

  public static void fromJson(JsonObject json, CopyOptions obj) {
    if (json.getValue("atomicMove") instanceof Boolean) {
      obj.setAtomicMove((Boolean)json.getValue("atomicMove"));
    }
    if (json.getValue("copyAttributes") instanceof Boolean) {
      obj.setCopyAttributes((Boolean)json.getValue("copyAttributes"));
    }
    if (json.getValue("nofollowLinks") instanceof Boolean) {
      obj.setNofollowLinks((Boolean)json.getValue("nofollowLinks"));
    }
    if (json.getValue("replaceExisting") instanceof Boolean) {
      obj.setReplaceExisting((Boolean)json.getValue("replaceExisting"));
    }
  }

  public static void toJson(CopyOptions obj, JsonObject json) {
    json.put("atomicMove", obj.isAtomicMove());
    json.put("copyAttributes", obj.isCopyAttributes());
    json.put("nofollowLinks", obj.isNofollowLinks());
    json.put("replaceExisting", obj.isReplaceExisting());
  }
}