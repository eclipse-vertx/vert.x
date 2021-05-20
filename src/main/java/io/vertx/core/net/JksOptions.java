/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * Key or trust store options configuring private key and/or certificates based on Java Keystore files.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true)
public class JksOptions extends KeyStoreOptionsBase {

  /**
   * Default constructor
   */
  public JksOptions() {
    super();
    setType("JKS");
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public JksOptions(JksOptions other) {
    super(other);
  }

  /**
   * Create options from JSON
   *
   * @param json  the JSON
   */
  public JksOptions(JsonObject json) {
    this();
    JksOptionsConverter.fromJson(json, this);
  }

  @Override
  public JksOptions setPassword(String password) {
    return (JksOptions) super.setPassword(password);
  }

  @Override
  public JksOptions setPath(String path) {
    return (JksOptions) super.setPath(path);
  }

  /**
   * Set the key store as a buffer
   *
   * @param value  the key store as a buffer
   * @return a reference to this, so the API can be used fluently
   */
  @Override
  public JksOptions setValue(Buffer value) {
    return (JksOptions) super.setValue(value);
  }

  @Override
  public String getAlias() {
    return super.getAlias();
  }

  @Override
  public JksOptions setAlias(String alias) {
    return (JksOptions) super.setAlias(alias);
  }

  @Override
  public JksOptions copy() {
    return new JksOptions(this);
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    JksOptionsConverter.toJson(this, json);
    return json;
  }
}
