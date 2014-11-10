/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.core.net;

import io.vertx.codegen.annotations.Options;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * Key or trust store options configuring private key and/or certificates based on PKCS#12 files.<p>
 * When used as a key store, it should point to a store containing a private key and its certificate.
 * When used as a trust store, it should point to a store containing a list of accepted certificates.<p>
 *
 * The store can either be loaded by Vert.x from the filesystem:<p>
 * <pre>
 * HttpServerOptions options = new HttpServerOptions();
 * options.setKeyStore(new PKCS12Options().setPath("/mykeystore.p12").setPassword("foo"));
 * </pre>
 *
 * Or directly provided as a buffer:<p>
 *
 * <pre>
 * Buffer store = vertx.fileSystem().readFileSync("/mykeystore.p12");
 * options.setKeyStore(new PKCS12Options().setValue(store).setPassword("foo"));
 * </pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class PKCS12Options implements KeyStoreOptions, TrustStoreOptions, Cloneable {

  private String password;
  private String path;
  private Buffer value;

  public PKCS12Options() {
    super();
  }

  public PKCS12Options(PKCS12Options other) {
    super();
    this.password = other.getPassword();
    this.path = other.getPath();
    this.value = other.getValue();
  }

  public PKCS12Options(JsonObject json) {
    super();
    this.password = json.getString("password");
    this.path = json.getString("path");
    byte[] value = json.getBinary("value");
    this.value = value != null ? Buffer.buffer(value) : null;
  }

  public String getPassword() {
    return password;
  }

  public PKCS12Options setPassword(String password) {
    this.password = password;
    return this;
  }

  public String getPath() {
    return path;
  }

  public PKCS12Options setPath(String path) {
    this.path = path;
    return this;
  }

  public Buffer getValue() {
    return value;
  }

  public PKCS12Options setValue(Buffer value) {
    this.value = value;
    return this;
  }

  @Override
  public PKCS12Options clone() {
    return new PKCS12Options(this);
  }
}
