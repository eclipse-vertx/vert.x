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

import java.util.ArrayList;
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
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CaOptions implements TrustStoreOptions {

  private ArrayList<String> certPaths;

  public CaOptions() {
    super();
    this.certPaths = new ArrayList<>();
  }

  public CaOptions(CaOptions other) {
    super();
    this.certPaths = new ArrayList<>(other.certPaths);
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

  @Override
  public CaOptions clone() {
    return new CaOptions(this);
  }
}
