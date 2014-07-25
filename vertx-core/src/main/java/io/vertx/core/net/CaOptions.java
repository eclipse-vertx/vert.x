/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.vertx.core.net;

import io.vertx.core.buffer.Buffer;
import io.vertx.codegen.annotations.Options;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

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
 */
@Options
public class CaOptions implements TrustStoreOptions {

  private ArrayList<String> certPaths;
  private ArrayList<Buffer> certValues;

  public CaOptions() {
    super();
    this.certPaths = new ArrayList<>();
    this.certValues = new ArrayList<>();
  }

  public CaOptions(CaOptions other) {
    super();
    this.certPaths = new ArrayList<>(other.certPaths);
    this.certValues = new ArrayList<>(other.certValues);
  }

  public CaOptions(JsonObject json) {
    super();
    this.certPaths = new ArrayList<>();
    this.certValues = new ArrayList<>();
    for (Object certPath : json.getArray("certPaths", new JsonArray())) {
      certPaths.add((String) certPath);
    }
    for (Object certValue : json.getArray("certValues", new JsonArray())) {
      certValues.add(Buffer.buffer(Base64.getDecoder().decode((String) certValue)));
    }
  }

  public List<String> getCertPaths() {
    return certPaths;
  }

  public CaOptions addCertPath(String certPath) throws NullPointerException {
    if (certPath == null) {
      throw new NullPointerException("No null certificate accepted");
    }
    certPaths.add(certPath);
    return this;
  }

  public List<Buffer> getCertValues() {
    return certValues;
  }

  public CaOptions addCertValue(Buffer certValue) throws NullPointerException {
    if (certValue == null) {
      throw new NullPointerException("No null certificate accepted");
    }
    certValues.add(certValue);
    return this;
  }

  @Override
  public CaOptions clone() {
    return new CaOptions(this);
  }
}
