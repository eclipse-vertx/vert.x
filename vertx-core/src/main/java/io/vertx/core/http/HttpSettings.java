/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

/**
 * HTTP settings, is a general settings class for http2Settings and http3Settings.<p>
 * <p>
 *
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public abstract class HttpSettings {

  public HttpSettings() {
  }

  /**
   * Create a settings from JSON
   *
   * @param json the JSON
   */
  public HttpSettings(JsonObject json) {
    this();
    HttpSettingsConverter.fromJson(json, this);
  }

  @Override
  public String toString() {
    return toJson().encode();
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    HttpSettingsConverter.toJson(this, json);
    return json;
  }

}