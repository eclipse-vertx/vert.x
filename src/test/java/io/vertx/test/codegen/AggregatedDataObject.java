/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
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
