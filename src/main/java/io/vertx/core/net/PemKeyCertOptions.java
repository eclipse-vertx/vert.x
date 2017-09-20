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
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Key store options configuring a list of private key and its certificate based on
 * <i>Privacy-enhanced Electronic Email</i> (PEM) files.
 * <p>
 *
 * A key file must contain a <b>non encrypted</b> private key in <b>PKCS8</b> format wrapped in a PEM
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
 * Or contain a <b>non encrypted</b> private key in <b>PKCS1</b> format wrapped in a PEM
 * block, for example:
 * <p>
 *
 * <pre>
 * -----BEGIN RSA PRIVATE KEY-----
 * MIIEowIBAAKCAQEAlO4gbHeFb/fmbUF/tOJfNPJumJUEqgzAzx8MBXv9Acyw9IRa
 * ...
 * zJ14Yd+t2fsLYVs2H0gxaA4DW6neCzgY3eKpSU0EBHUCFSXp/1+/
 * -----END RSA PRIVATE KEY-----
 * </pre><p>
 *
 * A certificate file must contain an X.509 certificate wrapped in a PEM block, for example:
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
 * Keys and certificates can either be loaded by Vert.x from the filesystem:
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
 * Several key/certificate pairs can be used:
 * <p>
 * <pre>
 * HttpServerOptions options = new HttpServerOptions();
 * options.setPemKeyCertOptions(new PemKeyCertOptions()
 *    .addKeyPath("/mykey1.pem").addCertPath("/mycert1.pem")
 *    .addKeyPath("/mykey2.pem").addCertPath("/mycert2.pem"));
 * </pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true)
public class PemKeyCertOptions implements KeyCertOptions, Cloneable {

  private List<String> keyPaths;
  private List<Buffer> keyValues;
  private List<String> certPaths;
  private List<Buffer> certValues;

  /**
   * Default constructor
   */
  public PemKeyCertOptions() {
    super();
    init();
  }

  private void init() {
    keyPaths = new ArrayList<>();
    keyValues = new ArrayList<>();
    certPaths = new ArrayList<>();
    certValues = new ArrayList<>();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public PemKeyCertOptions(PemKeyCertOptions other) {
    super();
    this.keyPaths = other.keyPaths != null ? new ArrayList<>(other.keyPaths) : new ArrayList<>();
    this.keyValues = other.keyValues != null ? new ArrayList<>(other.keyValues) : new ArrayList<>();
    this.certPaths = other.certPaths != null ? new ArrayList<>(other.certPaths) : new ArrayList<>();
    this.certValues = other.certValues != null ? new ArrayList<>(other.certValues) : new ArrayList<>();
  }

  /**
   * Create options from JSON
   *
   * @param json  the JSON
   */
  public PemKeyCertOptions(JsonObject json) {
    super();
    init();
    PemKeyCertOptionsConverter.fromJson(json, this);
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    PemKeyCertOptionsConverter.toJson(this, json);
    return json;
  }

  /**
   * Get the path to the first key file
   *
   * @return the path to the key file
   */
  @GenIgnore
  public String getKeyPath() {
    return keyPaths.isEmpty() ? null : keyPaths.get(0);
  }

  /**
   * Set the path of the first key file, replacing the keys paths
   *
   * @param keyPath  the path to the first key file
   * @return a reference to this, so the API can be used fluently
   */
  public PemKeyCertOptions setKeyPath(String keyPath) {
    keyPaths.clear();
    if (keyPath != null) {
      keyPaths.add(keyPath);
    }
    return this;
  }

  /**
   * Get all the paths to the key files
   *
   * @return the paths to the keys files
   */
  public List<String> getKeyPaths() {
    return keyPaths;
  }

  /**
   * Set all the paths to the keys files
   *
   * @param keyPaths  the paths to the keys files
   * @return a reference to this, so the API can be used fluently
   */
  public PemKeyCertOptions setKeyPaths(List<String> keyPaths) {
    this.keyPaths.clear();
    this.keyPaths.addAll(keyPaths);
    return this;
  }

  /**
   * Add a path to a key file
   *
   * @param keyPath  the path to the key file
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public PemKeyCertOptions addKeyPath(String keyPath) {
    Arguments.require(keyPath != null, "Null keyPath");
    keyPaths.add(keyPath);
    return this;
  }

  /**
   * Get the first key as a buffer
   *
   * @return  the first key as a buffer
   */
  @GenIgnore
  public Buffer getKeyValue() {
    return keyValues.isEmpty() ? null : keyValues.get(0);
  }

  /**
   * Set the first key a a buffer, replacing the previous keys buffers
   *
   * @param keyValue  key as a buffer
   * @return a reference to this, so the API can be used fluently
   */
  public PemKeyCertOptions setKeyValue(Buffer keyValue) {
    keyValues.clear();
    if (keyValue != null) {
      keyValues.add(keyValue);
    }
    return this;
  }

  /**
   * Get all the keys as a list of buffer
   *
   * @return  keys as a list of buffers
   */
  public List<Buffer> getKeyValues() {
    return keyValues;
  }

  /**
   * Set all the keys as a list of buffer
   *
   * @param keyValues  the keys as a list of buffer
   * @return a reference to this, so the API can be used fluently
   */
  public PemKeyCertOptions setKeyValues(List<Buffer> keyValues) {
    this.keyValues.clear();
    this.keyValues.addAll(keyValues);
    return this;
  }

  /**
   * Add a key as a buffer
   *
   * @param keyValue the key to add
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public PemKeyCertOptions addKeyValue(Buffer keyValue) {
    Arguments.require(keyValue != null, "Null keyValue");
    keyValues.add(keyValue);
    return this;
  }

  /**
   * Get the path to the first certificate file
   *
   * @return  the path to the certificate file
   */
  @GenIgnore
  public String getCertPath() {
    return certPaths.isEmpty() ? null : certPaths.get(0);
  }

  /**
   * Set the path of the first certificate, replacing the previous certificates paths
   *
   * @param certPath  the path to the certificate
   * @return a reference to this, so the API can be used fluently
   */
  public PemKeyCertOptions setCertPath(String certPath) {
    certPaths.clear();
    if (certPath != null) {
      certPaths.add(certPath);
    }
    return this;
  }

  /**
   * Get all the paths to the certificates files
   *
   * @return the paths to the certificates files
   */
  public List<String> getCertPaths() {
    return certPaths;
  }

  /**
   * Set all the paths to the certificates files
   *
   * @param certPaths  the paths to the certificates files
   * @return a reference to this, so the API can be used fluently
   */
  public PemKeyCertOptions setCertPaths(List<String> certPaths) {
    this.certPaths.clear();
    this.certPaths.addAll(certPaths);
    return this;
  }

  /**
   * Add a path to a certificate file
   *
   * @param certPath  the path to the certificate file
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public PemKeyCertOptions addCertPath(String certPath) {
    Arguments.require(certPath != null, "Null certPath");
    certPaths.add(certPath);
    return this;
  }

  /**
   * Get the first certificate as a buffer
   *
   * @return  the first certificate as a buffer
   */
  @GenIgnore
  public Buffer getCertValue() {
    return certValues.isEmpty() ? null : certValues.get(0);
  }

  /**
   * Set the first certificate as a buffer, replacing the previous certificates buffers
   *
   * @param certValue  the first certificate as a buffer
   * @return a reference to this, so the API can be used fluently
   */
  public PemKeyCertOptions setCertValue(Buffer certValue) {
    certValues.clear();
    if (certValue != null) {
      certValues.add(certValue);
    }
    return this;
  }

  /**
   * Get all the certificates as a list of buffer
   *
   * @return  certificates as a list of buffers
   */
  public List<Buffer> getCertValues() {
    return certValues;
  }

  /**
   * Set all the certificates as a list of buffer
   *
   * @param certValues  the certificates as a list of buffer
   * @return a reference to this, so the API can be used fluently
   */
  public PemKeyCertOptions setCertValues(List<Buffer> certValues) {
    this.certValues.clear();
    this.certValues.addAll(certValues);
    return this;
  }

  /**
   * Add a certificate as a buffer
   *
   * @param certValue the certificate to add
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  public PemKeyCertOptions addCertValue(Buffer certValue) {
    Arguments.require(certValue != null, "Null certValue");
    certValues.add(certValue);
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PemKeyCertOptions)) {
      return false;
    }

    PemKeyCertOptions that = (PemKeyCertOptions) o;
    if (!keyPaths.equals(that.keyPaths)) {
      return false;
    }
    if (!keyValues.equals(that.keyValues)) {
      return false;
    }
    if (!certPaths.equals(that.certPaths)) {
      return false;
    }
    if (!certValues.equals(that.certValues)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = 1;
    result += 31 * result + keyPaths.hashCode();
    result += 31 * result + keyValues.hashCode();
    result += 31 * result + certPaths.hashCode();
    result += 31 * result + certValues.hashCode();

    return result;
  }

  @Override
  public PemKeyCertOptions clone() {
    return new PemKeyCertOptions(this);
  }
}
