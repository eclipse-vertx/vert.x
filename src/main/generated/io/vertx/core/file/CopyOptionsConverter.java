/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 and the Apache License, Version 2.0
 * which accompanies this distribution. The Eclipse Public License 2.0 is
 * available at http://www.eclipse.org/legal/epl-2.0.html, and the Apache
 * License, Version 2.0 is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
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
