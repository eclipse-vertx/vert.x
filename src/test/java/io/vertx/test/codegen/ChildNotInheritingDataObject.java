/*
 * Copyright 2011-2017 Contributors to the Eclipse Foundation
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
@DataObject(inheritConverter = false, generateConverter = true)
public class ChildNotInheritingDataObject extends ParentDataObject {

  private String childProperty;

  public ChildNotInheritingDataObject() {
  }

  public ChildNotInheritingDataObject(ChildNotInheritingDataObject copy) {
  }

  public ChildNotInheritingDataObject(JsonObject json) {
  }

  public ChildNotInheritingDataObject setParentProperty(String parentProperty) {
    return (ChildNotInheritingDataObject) super.setParentProperty(parentProperty);
  }

  public String getChildProperty() {
    return childProperty;
  }

  public ChildNotInheritingDataObject setChildProperty(String childProperty) {
    this.childProperty = childProperty;
    return this;
  }
}
