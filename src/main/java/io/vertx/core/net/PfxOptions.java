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

package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * Key or trust store options configuring private key and/or certificates based on PKCS#12 files.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true, publicConverter = false)
public class PfxOptions extends KeyStoreOptionsBase {

  /**
   * Default constructor
   */
  public PfxOptions() {
    super();
    setType("PKCS12");
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public PfxOptions(PfxOptions other) {
    super(other);
  }

  /**
   * Create options from JSON
   *
   * @param json  the JSON
   */
  public PfxOptions(JsonObject json) {
    this();
    PfxOptionsConverter.fromJson(json, this);
  }

  public PfxOptions setPassword(String password) {
    return (PfxOptions) super.setPassword(password);
  }

  public PfxOptions setPath(String path) {
    return (PfxOptions) super.setPath(path);
  }

  /**
   * Set the key store as a buffer
   *
   * @param value  the key store as a buffer
   * @return a reference to this, so the API can be used fluently
   */
  public PfxOptions setValue(Buffer value) {
    return (PfxOptions) super.setValue(value);
  }

  @Override
  public String getAlias() {
    return super.getAlias();
  }

  @Override
  public KeyStoreOptions setAlias(String alias) {
    return (KeyStoreOptions) super.setAlias(alias);
  }

  @Override
  public PfxOptions copy() {
    return new PfxOptions(this);
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    PfxOptionsConverter.toJson(this, json);
    return json;
  }
}
