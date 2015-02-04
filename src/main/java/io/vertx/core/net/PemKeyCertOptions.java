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

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * Key store options configuring a private key and its certificate based on
 * <i>Privacy-enhanced Electronic Email</i> (PEM) files.
 * <p>
 *
 * The key file must contain a <b>non encrypted</b> private key in <b>PKCS8</b> format wrapped in a PEM
 * block, for example:
 * <p>
 *
 * <pre>
 * -----BEGIN PRIVATE KEY-----
 * MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDV6zPk5WqLwS0a
 * ...
 * K5xBhtm1AhdnZjx5KfW3BecE
 * -----END PRIVATE KEY-----
 * </pre><p>
 *
 * The certificate file must contain an X.509 certificate wrapped in a PEM block, for example:
 * <p>
 *
 * <pre>
 * -----BEGIN CERTIFICATE-----
 * MIIDezCCAmOgAwIBAgIEZOI/3TANBgkqhkiG9w0BAQsFADBuMRAwDgYDVQQGEwdV
 * ...
 * +tmLSvYS39O2nqIzzAUfztkYnUlZmB0l/mKkVqbGJA==
 * -----END CERTIFICATE-----
 * </pre>
 *
 * The key and certificate can either be loaded by Vert.x from the filesystem:
 * <p>
 * <pre>
 * HttpServerOptions options = new HttpServerOptions();
 * options.setPemKeyCertOptions(new PemKeyCertOptions().setKeyPath("/mykey.pem").setCertPath("/mycert.pem"));
 * </pre>
 *
 * Or directly provided as a buffer:<p>
 *
 * <pre>
 * Buffer key = vertx.fileSystem().readFileSync("/mykey.pem");
 * Buffer cert = vertx.fileSystem().readFileSync("/mycert.pem");
 * options.setPemKeyCertOptions(new PemKeyCertOptions().setKeyValue(key).setCertValue(cert));
 * </pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
public class PemKeyCertOptions implements KeyCertOptions, Cloneable {

  private String keyPath;
  private Buffer keyValue;
  private String certPath;
  private Buffer certValue;

  /**
   * Default constructor
   */
  public PemKeyCertOptions() {
    super();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public PemKeyCertOptions(PemKeyCertOptions other) {
    super();
    this.keyPath = other.getKeyPath();
    this.keyValue = other.getKeyValue();
    this.certPath = other.getCertPath();
    this.certValue = other.getCertValue();
  }

  /**
   * Create options from JSON
   *
   * @param json  the JSON
   */
  public PemKeyCertOptions(JsonObject json) {
    super();
    keyPath = json.getString("keyPath");
    byte[] keyValue = json.getBinary("keyValue");
    this.keyValue = keyValue != null ? Buffer.buffer(keyValue) : null;
    certPath = json.getString("certPath");
    byte[] certValue = json.getBinary("certValue");
    this.certValue = certValue != null ? Buffer.buffer(certValue) : null;
  }

  /**
   * Get the path to the key file
   *
   * @return the path to the key file
   */
  public String getKeyPath() {
    return keyPath;
  }

  /**
   * Set the path to the key file
   *
   * @param keyPath  the path to the key file
   * @return a reference to this, so the API can be used fluently
   */
  public PemKeyCertOptions setKeyPath(String keyPath) {
    this.keyPath = keyPath;
    return this;
  }

  /**
   * Get the path to the certificate file
   *
   * @return  the path to the certificate file
   */
  public String getCertPath() {
    return certPath;
  }

  /**
   * Get the key as a buffer
   *
   * @return  key as a buffer
   */
  public Buffer getKeyValue() {
    return keyValue;
  }

  /**
   * Set the key a a buffer
   *
   * @param keyValue  key as a buffer
   * @return a reference to this, so the API can be used fluently
   */
  public PemKeyCertOptions setKeyValue(Buffer keyValue) {
    this.keyValue = keyValue;
    return this;
  }

  /**
   * Set the path to the certificate
   *
   * @param certPath  the path to the certificate
   * @return a reference to this, so the API can be used fluently
   */
  public PemKeyCertOptions setCertPath(String certPath) {
    this.certPath = certPath;
    return this;
  }

  /**
   * Get the certificate as a buffer
   *
   * @return  the certificate as a buffer
   */
  public Buffer getCertValue() {
    return certValue;
  }

  /**
   * Set the certificate as a buffer
   *
   * @param certValue  the certificate as a buffer
   * @return a reference to this, so the API can be used fluently
   */
  public PemKeyCertOptions setCertValue(Buffer certValue) {
    this.certValue = certValue;
    return this;
  }

  @Override
  public PemKeyCertOptions clone() {
    return new PemKeyCertOptions(this);
  }
}
