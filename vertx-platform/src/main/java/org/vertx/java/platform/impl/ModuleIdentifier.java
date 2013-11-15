/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.platform.impl;

public class ModuleIdentifier {

  public static final String SEPARATOR = "~";
  private static final String SPLIT_REGEX = "\\" + SEPARATOR;

  private final String owner;
  private final String name;
  private final String version;
  private final String stringForm;

  private static final String LEGAL = "[A-Za-z0-9!£$()-_+=@~;,]+";

  public ModuleIdentifier(String stringForm) {
    if (stringForm == null) {
      throw new NullPointerException("Module identifier cannot be null");
    }
    this.stringForm = stringForm;
    String[] parts = stringForm.split(SPLIT_REGEX);
    if (parts.length != 3) {
      throw createException("Should be of form owner" + SEPARATOR + "name" + SEPARATOR + "version");
    }
    if (parts[0].isEmpty()) {
      throw createException("owner should not be empty");
    }
    if (parts[1].isEmpty()) {
      throw createException("name should not be empty");
    }
    if (parts[2].isEmpty()) {
      throw createException("version should not be empty");
    }
    owner = parts[0];
    checkIllegalChars(owner);
    name = parts[1];
    checkIllegalChars(name);
    version = parts[2];
    checkIllegalChars(version);
  }

  private static void checkIllegalChars(String str) {
    if (!str.matches(LEGAL)) {
      throw new IllegalArgumentException("Module identifier: " + str + " contains illegal characters");
    }
  }

  private IllegalArgumentException createException(String msg) {
    return new IllegalArgumentException("Invalid module identifier: " + stringForm + ". " + msg);
  }

  public String toString() {
    return stringForm;
  }

  public String getOwner() {
    return owner;
  }

  public String getName() {
    return name;
  }

  public String getVersion() {
    return version;
  }

  public static ModuleIdentifier createInternalModIDForVerticle(String depName) {
    return new ModuleIdentifier("__vertx" + SEPARATOR + depName + SEPARATOR + "__vertx");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ModuleIdentifier that = (ModuleIdentifier) o;

    if (!name.equals(that.name)) return false;
    if (!owner.equals(that.owner)) return false;
    if (!version.equals(that.version)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = owner.hashCode();
    result = 31 * result + name.hashCode();
    result = 31 * result + version.hashCode();
    return result;
  }
}
