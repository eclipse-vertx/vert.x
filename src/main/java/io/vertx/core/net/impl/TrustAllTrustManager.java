/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * A TrustManager which trusts everything
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TrustAllTrustManager implements X509TrustManager {

  public static TrustAllTrustManager INSTANCE = new TrustAllTrustManager();

  private TrustAllTrustManager() {
  }

  @Override
  public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
  }

  @Override
  public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return new X509Certificate[0];
  }
}
