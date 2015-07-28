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
