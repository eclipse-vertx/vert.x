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
 * <p>
 * When used as a key store, it should point to a store containing a private key and its certificate.
 * When used as a trust store, it should point to a store containing a list of accepted certificates.
 * <p>
 *
 * The store can either be loaded by Vert.x from the filesystem:
 * <p>
 * <pre>
 * HttpServerOptions options = new HttpServerOptions();
 * options.setPfxKeyCertOptions(new PfxOptions().setPath("/mykeystore.p12").setPassword("foo"));
 * </pre>
 *
 * Or directly provided as a buffer:<p>
 *
 * <pre>
 * Buffer store = vertx.fileSystem().readFileBlocking("/mykeystore.p12");
 * options.setPfxKeyCertOptions(new PfxOptions().setValue(store).setPassword("foo"));
 * </pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true, publicConverter = false)
public class PfxOptions implements KeyCertOptions, TrustOptions {

  private String password;
  private String path;
  private Buffer value;

  /**
   * Default constructor
   */
  public PfxOptions() {
    super();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public PfxOptions(PfxOptions other) {
    super();
    this.password = other.getPassword();
    this.path = other.getPath();
    this.value = other.getValue();
  }

  /**
   * Create options from JSON
   *
   * @param json  the JSON
   */
  public PfxOptions(JsonObject json) {
    super();
    PfxOptionsConverter.fromJson(json, this);
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

  /**
   * Get the password
   *
   * @return  the password
   */
  public String getPassword() {
    return password;
  }

  /**
   * Set the password
   *
   * @param password  the password
   * @return a reference to this, so the API can be used fluently
   */
  public PfxOptions setPassword(String password) {
    this.password = password;
    return this;
  }

  /**
   * Get the path
   *
   * @return the path
   */
  public String getPath() {
    return path;
  }

  /**
   * Set the path
   *
   * @param path  the path
   * @return a reference to this, so the API can be used fluently
   */
  public PfxOptions setPath(String path) {
    this.path = path;
    return this;
  }

  /**
   * Get the store as a buffer
   *
   * @return store as buffer
   */
  public Buffer getValue() {
    return value;
  }

  /**
   * Set the store as a buffer
   *
   * @param value  the store as a buffer
   * @return a reference to this, so the API can be used fluently
   */
  public PfxOptions setValue(Buffer value) {
    this.value = value;
    return this;
  }

  @Override
  public PfxOptions copy() {
    return new PfxOptions(this);
  }
}
