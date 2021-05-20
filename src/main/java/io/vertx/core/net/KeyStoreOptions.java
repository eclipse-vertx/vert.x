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
 * Key or trust store options configuring private key and/or certificates based on {@code KeyStore}.
 *
 * <ul>
 *   <li>when used as a key store, it should point to a store containing a private key and its certificate.</li>
 *   <li>when used as a trust store, it should point to a store containing a list of trusted certificates.</li>
 * </ul>
 *
 * <p> The store can either be loaded by Vert.x from the filesystem:
 *
 * <pre>
 * HttpServerOptions options = HttpServerOptions.httpServerOptions();
 * options.setKeyCertOptions(new KeyStoreOptions().setType("JKS").setPath("/mykeystore.jks").setPassword("foo"));
 * </pre>
 *
 * Or directly provided as a buffer:
 *
 * <pre>
 * Buffer store = vertx.fileSystem().readFileBlocking("/mykeystore.jks");
 * options.setKeyCertOptions(new KeyStoreOptions().setType("JKS").setValue(store).setPassword("foo"));
 * </pre>
 *
 * <p> You can also use specific subclasses {@link JksOptions} or {@link PfxOptions} that will set
 * the {@link #setType} for you:
 *
 * <pre>
 * HttpServerOptions options = HttpServerOptions.httpServerOptions();
 * options.setKeyCertOptions(new JksOptions().setPath("/mykeystore.jks").setPassword("foo"));
 * </pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true)
public class KeyStoreOptions extends KeyStoreOptionsBase {

  /**
   * Default constructor
   */
  public KeyStoreOptions() {
    super();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public KeyStoreOptions(KeyStoreOptions other) {
    super(other);
  }

  /**
   * Create options from JSON
   *
   * @param json  the JSON
   */
  public KeyStoreOptions(JsonObject json) {
    this();
    KeyStoreOptionsConverter.fromJson(json, this);
  }

  /**
   * @return the store provider
   */
  public String getProvider() {
    return super.getProvider();
  }

  /**
   * Set the store provider.
   *
   * @param provider the type
   * @return a reference to this, so the API can be used fluently
   */
  public KeyStoreOptions setProvider(String provider) {
    return (KeyStoreOptions) super.setProvider(provider);
  }

  /**
   * @return the store type
   */
  public String getType() {
    return super.getType();
  }

  /**
   * Set the store type.
   *
   * @param type the type
   * @return a reference to this, so the API can be used fluently
   */
  public KeyStoreOptions setType(String type) {
    return (KeyStoreOptions) super.setType(type);
  }

  @Override
  public KeyStoreOptions setPassword(String password) {
    return (KeyStoreOptions) super.setPassword(password);
  }

  @Override
  public KeyStoreOptions setPath(String path) {
    return (KeyStoreOptions) super.setPath(path);
  }

  @Override
  public KeyStoreOptions setValue(Buffer value) {
    return (KeyStoreOptions) super.setValue(value);
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
  public KeyStoreOptions copy() {
    return new KeyStoreOptions(this);
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    KeyStoreOptionsConverter.toJson(this, json);
    return json;
  }
}
