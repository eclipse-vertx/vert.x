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
 * /**
 * Key store options configuring a private key and its certificate based on
 * <i>Privacy-enhanced Electronic Email</i> (PEM) files.<p>
 *
 * The key file must contain a <b>non encrypted</b> private key in <b>PKCS8</b> format wrapped in a PEM
 * block, for example:<p>
 *
 * <pre>
 * -----BEGIN PRIVATE KEY-----
 * MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDV6zPk5WqLwS0a
 * ...
 * K5xBhtm1AhdnZjx5KfW3BecE
 * -----END PRIVATE KEY-----
 * </pre><p>
 *
 * The certificate file must contain an X.509 certificate wrapped in a PEM block, for example:<p>
 *
 * <pre>
 * -----BEGIN CERTIFICATE-----
 * MIIDezCCAmOgAwIBAgIEZOI/3TANBgkqhkiG9w0BAQsFADBuMRAwDgYDVQQGEwdV
 * ...
 * +tmLSvYS39O2nqIzzAUfztkYnUlZmB0l/mKkVqbGJA==
 * -----END CERTIFICATE-----
 * </pre>
 *
 * The key and certificate can either be loaded by Vert.x from the filesystem:<p>
 * <pre>
 * HttpServerOptions options = new HttpServerOptions();
 * options.setKeyStore(new KeyCertOptions().setKeyPath("/mykey.pem").setCertPath("/mycert.pem"));
 * </pre>
 *
 * Or directly provided as a buffer:<p>
 *
 * <pre>
 * Buffer key = vertx.fileSystem().readFileSync("/mykey.pem");
 * Buffer cert = vertx.fileSystem().readFileSync("/mycert.pem");
 * options.setKeyStore(new KeyCertOptions().setKeyValue(key).setCertValue(cert));
 * </pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class KeyCertOptions implements KeyStoreOptions, Cloneable {

  private String keyPath;
  private Buffer keyValue;
  private String certPath;
  private Buffer certValue;

  public KeyCertOptions() {
    super();
  }

  public KeyCertOptions(KeyCertOptions other) {
    super();
    this.keyPath = other.getKeyPath();
    this.keyValue = other.getKeyValue();
    this.certPath = other.getCertPath();
    this.certValue = other.getCertValue();
  }

  public KeyCertOptions(JsonObject json) {
    super();
    keyPath = json.getString("keyPath");
    byte[] keyValue = json.getBinary("keyValue");
    this.keyValue = keyValue != null ? Buffer.buffer(keyValue) : null;
    certPath = json.getString("certPath");
    byte[] certValue = json.getBinary("certValue");
    this.certValue = certValue != null ? Buffer.buffer(certValue) : null;
  }

  public String getKeyPath() {
    return keyPath;
  }

  public KeyCertOptions setKeyPath(String keyPath) {
    this.keyPath = keyPath;
    return this;
  }

  public String getCertPath() {
    return certPath;
  }

  public Buffer getKeyValue() {
    return keyValue;
  }

  public KeyCertOptions setKeyValue(Buffer keyValue) {
    this.keyValue = keyValue;
    return this;
  }

  public KeyCertOptions setCertPath(String certPath) {
    this.certPath = certPath;
    return this;
  }

  public Buffer getCertValue() {
    return certValue;
  }

  public KeyCertOptions setCertValue(Buffer certValue) {
    this.certValue = certValue;
    return this;
  }

  @Override
  public KeyCertOptions clone() {
    return new KeyCertOptions(this);
  }
}
