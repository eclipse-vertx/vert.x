/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.tls;

import io.vertx.core.Vertx;
import io.vertx.core.net.TrustOptions;

import javax.net.ssl.*;
import java.net.Socket;
import java.security.KeyStore;
import java.security.Provider;
import java.security.cert.X509Certificate;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class TrustAllOptions implements TrustOptions {

  private static final TrustManager TRUST_ALL_MANAGER_NO_VERIFY = new X509ExtendedTrustManager() {
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) { }
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) { }
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) { }
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) { }
    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) { }
    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) { }
    @Override
    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
  };

  private static final TrustManager TRUST_ALL_MANAGER_VERIFY = new X509TrustManager() {
    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) { }
    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) { }
    @Override
    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
  };

  private static final Provider PROVIDER = new Provider("", 0.0, "") {
  };

  private static TrustAllOptions INSTANCE_NO_VERIFY = new TrustAllOptions(TRUST_ALL_MANAGER_NO_VERIFY);
  private static TrustAllOptions INSTANCE_VERIFY = new TrustAllOptions(TRUST_ALL_MANAGER_VERIFY);

  static TrustOptions instance(boolean verifyHost) {
    return verifyHost ? INSTANCE_VERIFY : INSTANCE_NO_VERIFY;
  }

  private final TrustManager tm;

  private TrustAllOptions(TrustManager tm) {
    this.tm = tm;
  }

  @Override
  public TrustOptions copy() {
    return this;
  }

  @Override
  public TrustManagerFactory getTrustManagerFactory(Vertx vertx) {
    return new TrustManagerFactory(new TrustManagerFactorySpi() {
      @Override
      protected void engineInit(KeyStore keyStore) {
      }

      @Override
      protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {
      }

      @Override
      protected TrustManager[] engineGetTrustManagers() {
        return new TrustManager[] { tm };
      }
    }, PROVIDER, "") {

    };
  }

  @Override
  public Function<String, TrustManager[]> trustManagerMapper(Vertx vertx) {
    return name -> new TrustManager[] { tm };
  }
}
