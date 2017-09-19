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
 * Converter for {@link io.vertx.core.file.OpenOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.file.OpenOptions} original class using Vert.x codegen.
 */
public class OpenOptionsConverter {

  public static void fromJson(JsonObject json, OpenOptions obj) {
    if (json.getValue("append") instanceof Boolean) {
      obj.setAppend((Boolean)json.getValue("append"));
    }
    if (json.getValue("create") instanceof Boolean) {
      obj.setCreate((Boolean)json.getValue("create"));
    }
    if (json.getValue("createNew") instanceof Boolean) {
      obj.setCreateNew((Boolean)json.getValue("createNew"));
    }
    if (json.getValue("deleteOnClose") instanceof Boolean) {
      obj.setDeleteOnClose((Boolean)json.getValue("deleteOnClose"));
    }
    if (json.getValue("dsync") instanceof Boolean) {
      obj.setDsync((Boolean)json.getValue("dsync"));
    }
    if (json.getValue("perms") instanceof String) {
      obj.setPerms((String)json.getValue("perms"));
    }
    if (json.getValue("read") instanceof Boolean) {
      obj.setRead((Boolean)json.getValue("read"));
    }
    if (json.getValue("sparse") instanceof Boolean) {
      obj.setSparse((Boolean)json.getValue("sparse"));
    }
    if (json.getValue("sync") instanceof Boolean) {
      obj.setSync((Boolean)json.getValue("sync"));
    }
    if (json.getValue("truncateExisting") instanceof Boolean) {
      obj.setTruncateExisting((Boolean)json.getValue("truncateExisting"));
    }
    if (json.getValue("write") instanceof Boolean) {
      obj.setWrite((Boolean)json.getValue("write"));
    }
  }

  public static void toJson(OpenOptions obj, JsonObject json) {
    json.put("append", obj.isAppend());
    json.put("create", obj.isCreate());
    json.put("createNew", obj.isCreateNew());
    json.put("deleteOnClose", obj.isDeleteOnClose());
    json.put("dsync", obj.isDsync());
    if (obj.getPerms() != null) {
      json.put("perms", obj.getPerms());
    }
    json.put("read", obj.isRead());
    json.put("sparse", obj.isSparse());
    json.put("sync", obj.isSync());
    json.put("truncateExisting", obj.isTruncateExisting());
    json.put("write", obj.isWrite());
  }
}
