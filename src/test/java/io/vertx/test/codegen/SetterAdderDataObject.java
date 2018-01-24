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

import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true)
public class SetterAdderDataObject {

  int sets = 0;
  int adds = 0;
  private List<String> values;

  public SetterAdderDataObject() {
  }

  public SetterAdderDataObject(JsonObject json) {
  }

  public List<String> getValues() {
    return values;
  }

  public SetterAdderDataObject setValues(List<String> values) {
    this.values = values;
    sets++;
    return this;
  }

  public SetterAdderDataObject addValue(String value) {
    this.values.add(value);
    adds++;
    return this;
  }
}
