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

package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.impl.KeyStoreHelper;

import javax.net.ssl.KeyManagerFactory;

/**
 * Key or trust store options configuring private key and/or certificates based on Java Keystore files.
 * <p>
 * When used as a key store, it should point to a store containing a private key and its certificate.
 * When used as a trust store, it should point to a store containing a list of trusted certificates.
 * <p>
 * The store can either be loaded by Vert.x from the filesystem:
 * <p>
 * <pre>
 * HttpServerOptions options = HttpServerOptions.httpServerOptions();
 * options.setKeyStore(new JKSOptions().setPath("/mykeystore.jks").setPassword("foo"));
 * </pre>
 *
 * Or directly provided as a buffer:
 * <p>
 *
 * <pre>
 * Buffer store = vertx.fileSystem().readFileSync("/mykeystore.jks");
 * options.setKeyStore(new JKSOptions().setValue(store).setPassword("foo"));
 * </pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true)
public class JksOptions implements KeyCertOptions, TrustOptions, Cloneable {

  private String password;
  private String path;
  private Buffer value;

  /**
   * Default constructor
   */
  public JksOptions() {
    super();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public JksOptions(JksOptions other) {
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
  public JksOptions(JsonObject json) {
    super();
    JksOptionsConverter.fromJson(json, this);
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
  public JksOptions setPassword(String password) {
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
  public JksOptions setPath(String path) {
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
  public JksOptions setValue(Buffer value) {
    this.value = value;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof JksOptions)) {
      return false;
    }

    JksOptions that = (JksOptions) o;

    if (password != null ? !password.equals(that.password) : that.password != null) {
      return false;
    }
    if (path != null ? !path.equals(that.path) : that.path != null) {
      return false;
    }
    if (value != null ? !value.equals(that.value) : that.value != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = 1;
    result += 31 * result + (password != null ? password.hashCode() : 0);
    result += 31 * result + (path != null ? path.hashCode() : 0);
    result += 31 * result + (value != null ? value.hashCode() : 0);

    return result;
  }

  @Override
  public JksOptions clone() {
    return new JksOptions(this);
  }
}
