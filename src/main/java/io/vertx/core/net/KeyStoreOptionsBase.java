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

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.KeyStoreHelper;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import java.security.KeyStore;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base class of {@code KeyStore} based options.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class KeyStoreOptionsBase implements KeyCertOptions, TrustOptions {

  private KeyStoreHelper helper;
  private String provider;
  private String type;
  private String password;
  private String path;
  private Buffer value;
  private String alias;

  /**
   * Default constructor
   */
  protected KeyStoreOptionsBase() {
    super();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  protected KeyStoreOptionsBase(KeyStoreOptionsBase other) {
    super();
    this.type = other.type;
    this.password = other.password;
    this.path = other.path;
    this.value = other.value;
    this.alias = other.alias;
  }

  protected String getType() {
    return type;
  }

  protected KeyStoreOptionsBase setType(String type) {
    this.type = type;
    return this;
  }

  protected String getProvider() {
    return provider;
  }

  protected KeyStoreOptionsBase setProvider(String provider) {
    this.provider = provider;
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
  public KeyStoreOptionsBase setPassword(String password) {
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
  public KeyStoreOptionsBase setPath(String path) {
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
  public KeyStoreOptionsBase setValue(Buffer value) {
    this.value = value;
    return this;
  }

  /**
   * @return the alias for a server certificate when the keystore has more than one, or {@code null}
   */
  public String getAlias() {
    return alias;
  }

  /**
   * Set the alias for a server certificate when the keystore has more than one.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public KeyStoreOptionsBase setAlias(String alias) {
    this.alias = alias;
    return this;
  }

  KeyStoreHelper getHelper(Vertx vertx) throws Exception {
    if (helper == null) {
      Supplier<Buffer> value;
      if (this.path != null) {
        value = () -> vertx.fileSystem().readFileBlocking(((VertxInternal) vertx).resolveFile(path).getAbsolutePath());
      } else if (this.value != null) {
        value = this::getValue;
      } else {
        return null;
      }
      helper = new KeyStoreHelper(KeyStoreHelper.loadKeyStore(type, provider, password, value, getAlias()), password);
    }
    return helper;
  }

  /**
   * Load and return a Java keystore.
   *
   * @param vertx the vertx instance
   * @return the {@code KeyStore}
   */
  public KeyStore loadKeyStore(Vertx vertx) throws Exception {
    KeyStoreHelper helper = getHelper(vertx);
    return helper != null ? helper.store() : null;
  }

  @Override
  public KeyManagerFactory getKeyManagerFactory(Vertx vertx) throws Exception {
    KeyStoreHelper helper = getHelper(vertx);
    return helper != null ? helper.getKeyMgrFactory() : null;
  }

  @Override
  public Function<String, X509KeyManager> keyManagerMapper(Vertx vertx) throws Exception {
    KeyStoreHelper helper = getHelper(vertx);
    return helper != null ? helper::getKeyMgr : null;
  }

  @Override
  public TrustManagerFactory getTrustManagerFactory(Vertx vertx) throws Exception {
    KeyStoreHelper helper = getHelper(vertx);
    return helper != null ? helper.getTrustMgrFactory((VertxInternal) vertx) : null;
  }

  @Override
  public Function<String, TrustManager[]> trustManagerMapper(Vertx vertx) throws Exception {
    KeyStoreHelper helper = getHelper(vertx);
    return helper != null ? helper::getTrustMgr : null;
  }

  @Override
  public abstract KeyStoreOptionsBase copy();

}
