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

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.impl.KeyStoreHelper;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509KeyManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
 * Buffer key = vertx.fileSystem().readFileBlocking("/mykey.pem");
 * Buffer cert = vertx.fileSystem().readFileBlocking("/mycert.pem");
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
@DataObject(generateConverter = true, publicConverter = false)
public class PemKeyCertOptions implements KeyCertOptions {

  private KeyStoreHelper helper;
  private List<String> keyPaths;
  private List<Buffer> keyValues;
  private List<String> certPaths;
  private List<Buffer> certValues;
  private Boolean reloadCerts;
  private Long certRefreshRateInSeconds;
  private Map<String, Long> keyFilesLastModifiedTimestamps;
  private Map<String, Long> certFilesLastModifiedTimestamps;

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
    keyFilesLastModifiedTimestamps = new HashMap<>();
    certFilesLastModifiedTimestamps = new HashMap<>();
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
    this.reloadCerts = other.reloadCerts;
    this.certRefreshRateInSeconds = other.certRefreshRateInSeconds;
    this.keyFilesLastModifiedTimestamps = other.keyFilesLastModifiedTimestamps != null
                                          ? new HashMap<>(other.keyFilesLastModifiedTimestamps)
                                          : new HashMap<>();
    this.certFilesLastModifiedTimestamps = other.certFilesLastModifiedTimestamps != null
                                           ? new HashMap<>(other.certFilesLastModifiedTimestamps)
                                           : new HashMap<>();
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
    keyFilesLastModifiedTimestamps.clear();
    if (keyPath != null) {
      keyPaths.add(keyPath);
      long recentLastModifiedTimestamp = getLastModifiedTimestamp(keyPath);
      keyFilesLastModifiedTimestamps.put(keyPath, recentLastModifiedTimestamp);
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
    this.keyFilesLastModifiedTimestamps.clear();
    this.keyPaths.addAll(keyPaths);
    keyPaths.forEach(keyPath -> {
      long recentLastModifiedTimestamp = getLastModifiedTimestamp(keyPath);
      this.keyFilesLastModifiedTimestamps.put(keyPath, recentLastModifiedTimestamp);
    });
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
    long recentLastModifiedTimestamp = getLastModifiedTimestamp(keyPath);
    keyFilesLastModifiedTimestamps.put(keyPath, recentLastModifiedTimestamp);
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
    certFilesLastModifiedTimestamps.clear();
    if (certPath != null) {
      certPaths.add(certPath);
      long recentLastModifiedTimestamp = getLastModifiedTimestamp(certPath);
      certFilesLastModifiedTimestamps.put(certPath, recentLastModifiedTimestamp);
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
    this.certFilesLastModifiedTimestamps.clear();
    this.certPaths.addAll(certPaths);
    certPaths.forEach(certPath -> {
      long recentLastModifiedTimestamp = getLastModifiedTimestamp(certPath);
      this.certFilesLastModifiedTimestamps.put(certPath, recentLastModifiedTimestamp);
    });
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
    long recentLastModifiedTimestamp = getLastModifiedTimestamp(certPath);
    certFilesLastModifiedTimestamps.put(certPath, recentLastModifiedTimestamp);
    return this;
  }

  private long getLastModifiedTimestamp(String path) {
    if (path == null) {
      return 0;
    }
    try {
      return Files.getLastModifiedTime(Paths.get(path)).toMillis();
    } catch (IOException e) {
      return 0;
    }
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

  /**
   * Set whether certificates should be reloaded from their path.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public PemKeyCertOptions setReloadCerts(Boolean reloadCerts) {
    this.reloadCerts = reloadCerts;
    return this;
  }

  /**
   * Set certificate refresh rate in seconds.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public PemKeyCertOptions setCertRefreshRateInSeconds(Long certRefreshRateInSeconds) {
    this.certRefreshRateInSeconds = certRefreshRateInSeconds;
    return this;
  }

  /**
   * @return certificate refresh rate.
   */
  public Long getCertRefreshRateInSeconds() {
    return this.certRefreshRateInSeconds;
  }

  /**
   * This method is used to check if context reloading is enabled.
   */
  public Boolean getReloadCerts() {
    return this.reloadCerts;
  }

  /**
   * @return {@code boolean} indicating whether certificates/keys should be reloaded or not.
   */
  @Override
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  public boolean isReloadNeeded() {
    boolean reloadNeeded = false;
    for (String path : keyPaths) {
      long recentLastModifiedTimestamp = getLastModifiedTimestamp(path);
      if (keyFilesLastModifiedTimestamps.get(path) != recentLastModifiedTimestamp) {
        reloadNeeded = true;
        keyFilesLastModifiedTimestamps.put(path, recentLastModifiedTimestamp);
      }
    }
    for (String path : certPaths) {
      long recentLastModifiedTimestamp = getLastModifiedTimestamp(path);
      if (certFilesLastModifiedTimestamps.get(path) != recentLastModifiedTimestamp) {
        reloadNeeded = true;
        certFilesLastModifiedTimestamps.put(path, recentLastModifiedTimestamp);
      }
    }
    return reloadNeeded;
  }

  @Override
  public PemKeyCertOptions copy() {
    return new PemKeyCertOptions(this);
  }

  KeyStoreHelper getHelper(Vertx vertx) throws Exception {
    if (helper == null) {
      List<Buffer> keys = new ArrayList<>();
      for (String keyPath : keyPaths) {
        keys.add(vertx.fileSystem().readFileBlocking(((VertxInternal)vertx).resolveFile(keyPath).getAbsolutePath()));
      }
      keys.addAll(keyValues);
      List<Buffer> certs = new ArrayList<>();
      for (String certPath : certPaths) {
        certs.add(vertx.fileSystem().readFileBlocking(((VertxInternal)vertx).resolveFile(certPath).getAbsolutePath()));
      }
      certs.addAll(certValues);
      helper = new KeyStoreHelper(KeyStoreHelper.loadKeyCert(keys, certs), KeyStoreHelper.DUMMY_PASSWORD, null);
    }
    return helper;
  }

  @Override
  public void reload() {
    this.helper = null;
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
}
