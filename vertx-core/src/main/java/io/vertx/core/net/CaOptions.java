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
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Certificate Authority trust store options configuring certificates based on
 * <i>Privacy-enhanced Electronic Email</i> (PEM) files. The store is configured with a list of
 * validating certificates.<p>
 *
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
 * The certificates can either be loaded by Vert.x from the filesystem:<p>
 * <pre>
 * HttpServerOptions options = new HttpServerOptions();
 * options.setTrustStore(new CaOptions().addCertPath("/cert.pem"));
 * </pre>
 *
 * Or directly provided as a buffer:<p>
 *
 * <pre>
 * Buffer cert = vertx.fileSystem().readFileSync("/cert.pem");
 * HttpServerOptions options = new HttpServerOptions();
 * options.setTrustStore(new CaOptions().addCertValue(cert));
 * </pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class CaOptions implements TrustStoreOptions, Cloneable {

  private ArrayList<String> certPaths;
  private ArrayList<Buffer> certValues;

  public CaOptions() {
    super();
    this.certPaths = new ArrayList<>();
    this.certValues = new ArrayList<>();
  }

  public CaOptions(CaOptions other) {
    super();
    this.certPaths = new ArrayList<>(other.getCertPaths());
    this.certValues = new ArrayList<>(other.getCertValues());
  }

  public CaOptions(JsonObject json) {
    super();
    this.certPaths = new ArrayList<>();
    this.certValues = new ArrayList<>();
    for (Object certPath : json.getJsonArray("certPaths", new JsonArray())) {
      certPaths.add((String) certPath);
    }
    for (Object certValue : json.getJsonArray("certValues", new JsonArray())) {
      certValues.add(Buffer.buffer(Base64.getDecoder().decode((String) certValue)));
    }
  }

  public List<String> getCertPaths() {
    return certPaths;
  }

  public CaOptions addCertPath(String certPath) throws NullPointerException {
    Objects.requireNonNull(certPath, "No null certificate accepted");
    Arguments.require(!certPath.isEmpty(), "No empty certificate path accepted");
    certPaths.add(certPath);
    return this;
  }

  public List<Buffer> getCertValues() {
    return certValues;
  }

  public CaOptions addCertValue(Buffer certValue) throws NullPointerException {
    Objects.requireNonNull(certValue, "No null certificate accepted");
    certValues.add(certValue);
    return this;
  }

  @Override
  public CaOptions clone() {
    return new CaOptions(this);
  }

}
