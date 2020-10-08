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

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.buffer.Buffer;

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
 * options.setKeyStore(new KeyStoreOptions().setType("JKS").setPath("/mykeystore.jks").setPassword("foo"));
 * </pre>
 *
 * Or directly provided as a buffer:
 *
 * <pre>
 * Buffer store = vertx.fileSystem().readFileBlocking("/mykeystore.jks");
 * options.setKeyStore(new JKSOptions().setType("JKS").setValue(store).setPassword("foo"));
 * </pre>
 *
 * <p> You can also use specific subclasses {@link JksOptions} or {@link PfxOptions} that will set
 * the {@link #setType} for you:
 *
 * <pre>
 * HttpServerOptions options = HttpServerOptions.httpServerOptions();
 * options.setKeyStore(new JksOptions().setPath("/mykeystore.jks").setPassword("foo"));
 * </pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class KeyStoreOptions implements KeyCertOptions, TrustOptions {

  private String type;
  private String password;
  private String path;
  private Buffer value;

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
    super();
    this.type = other.getType();
    this.password = other.getPassword();
    this.path = other.getPath();
    this.value = other.getValue();
  }

  @GenIgnore
  public String getType() {
    return type;
  }

  @GenIgnore
  public KeyStoreOptions setType(String type) {
    this.type = type;
    return this;
  }

  /**
   * @return the password for the key store
   */
  public String getPassword() {
    return password;
  }

  /**
   * Set the password for the key store
   *
   * @param password  the password
   * @return a reference to this, so the API can be used fluently
   */
  public KeyStoreOptions setPassword(String password) {
    this.password = password;
    return this;
  }

  /**
   * Get the path to the ksy store
   *
   * @return the path
   */
  public String getPath() {
    return path;
  }

  /**
   * Set the path to the key store
   *
   * @param path  the path
   * @return a reference to this, so the API can be used fluently
   */
  public KeyStoreOptions setPath(String path) {
    this.path = path;
    return this;
  }

  /**
   * Get the key store as a buffer
   *
   * @return  the key store as a buffer
   */
  public Buffer getValue() {
    return value;
  }

  /**
   * Set the key store as a buffer
   *
   * @param value  the key store as a buffer
   * @return a reference to this, so the API can be used fluently
   */
  public KeyStoreOptions setValue(Buffer value) {
    this.value = value;
    return this;
  }

  @Override
  public KeyStoreOptions copy() {
    return new KeyStoreOptions(this);
  }
}
