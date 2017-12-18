/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.codegen;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class AggregatedDataObject {

  private String value;

  public AggregatedDataObject() {
  }

  public AggregatedDataObject(AggregatedDataObject copy) {
  }

  public AggregatedDataObject(JsonObject json) {
    value = json.getString("value");
  }

  public String getValue() {
    return value;
  }

  public AggregatedDataObject setValue(String value) {
    this.value = value;
    return this;
  }

  public JsonObject toJson() {
    JsonObject ret = new JsonObject();
    if (value != null) {
      ret.put("value", value);
    }
    return ret;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof AggregatedDataObject) {
      AggregatedDataObject that = (AggregatedDataObject) obj;
      return value.equals(that.value);
    }
    return false;
  }
}
