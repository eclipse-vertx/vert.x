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
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.impl.KeyStoreHelper;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Certificate Authority options configuring certificates based on
 * <i>Privacy-enhanced Electronic Email</i> (PEM) files. The options is configured with a list of
 * validating certificates.
 * <p>
 * Validating certificates must contain X.509 certificates wrapped in a PEM block:<p>
 *
 * <pre>
 * -----BEGIN CERTIFICATE-----
 * MIIDezCCAmOgAwIBAgIEVmLkwTANBgkqhkiG9w0BAQsFADBuMRAwDgYDVQQGEwdV
 * ...
 * z5+DuODBJUQst141Jmgq8bS543IU/5apcKQeGNxEyQ==
 * -----END CERTIFICATE-----
 * </pre>
 *
 * The certificates can either be loaded by Vert.x from the filesystem:
 * <p>
 * <pre>
 * HttpServerOptions options = new HttpServerOptions();
 * options.setPemTrustOptions(new PemTrustOptions().addCertPath("/cert.pem"));
 * </pre>
 *
 * Or directly provided as a buffer:
 * <p>
 *
 * <pre>
 * Buffer cert = vertx.fileSystem().readFileBlocking("/cert.pem");
 * HttpServerOptions options = new HttpServerOptions();
 * options.setPemTrustOptions(new PemTrustOptions().addCertValue(cert));
 * </pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class PemTrustOptions implements TrustOptions, Cloneable {

  private KeyStoreHelper helper;
  private ArrayList<String> certPaths;
  private ArrayList<Buffer> certValues;

  /**
   * Default constructor
   */
  public PemTrustOptions() {
    super();
    this.certPaths = new ArrayList<>();
    this.certValues = new ArrayList<>();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public PemTrustOptions(PemTrustOptions other) {
    super();
    this.certPaths = new ArrayList<>(other.getCertPaths());
    this.certValues = new ArrayList<>(other.getCertValues());
  }

  /**
   * Create options from JSON
   *
   * @param json  the JSON
   */
  public PemTrustOptions(JsonObject json) {
    this();
    PemTrustOptionsConverter.fromJson(json, this);
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    PemTrustOptionsConverter.toJson(this, json);
    return json;
  }

  /**
   * @return  the certificate paths used to locate certificates
   */
  public List<String> getCertPaths() {
    return certPaths;
  }

  /**
   * Add a certificate path
   *
   * @param certPath  the path to add
   * @return a reference to this, so the API can be used fluently
   * @throws NullPointerException
   */
  public PemTrustOptions addCertPath(String certPath) throws NullPointerException {
    Objects.requireNonNull(certPath, "No null certificate accepted");
    Arguments.require(!certPath.isEmpty(), "No empty certificate path accepted");
    certPaths.add(certPath);
    return this;
  }

  /**
   *
   * @return the certificate values
   */
  public List<Buffer> getCertValues() {
    return certValues;
  }

  /**
   * Add a certificate value
   *
   * @param certValue  the value to add
   * @return a reference to this, so the API can be used fluently
   * @throws NullPointerException
   */
  public PemTrustOptions addCertValue(Buffer certValue) throws NullPointerException {
    Objects.requireNonNull(certValue, "No null certificate accepted");
    certValues.add(certValue);
    return this;
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
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == getClass()) {
      PemTrustOptions that = (PemTrustOptions) obj;
      return Objects.equals(certPaths, that.certPaths) && Objects.equals(certValues, that.certValues);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hashCode = Objects.hashCode(certPaths);
    hashCode = 31 * hashCode + Objects.hashCode(certValues);
    return hashCode;
  }

  @Override
  public PemTrustOptions copy() {
    return new PemTrustOptions(this);
  }

  public KeyStoreHelper getHelper(Vertx vertx) throws Exception {
    if (helper == null) {
      Stream<Buffer> certValues = certPaths.
        stream().
        map(path -> ((VertxInternal)vertx).fileResolver().resolve(path).getAbsolutePath()).
        map(vertx.fileSystem()::readFileBlocking);
      certValues = Stream.concat(certValues, this.certValues.stream());
      helper = new KeyStoreHelper(KeyStoreHelper.loadCA(certValues), null, null);
    }
    return helper;
  }

}
