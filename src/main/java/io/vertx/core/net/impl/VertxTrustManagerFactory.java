/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.net.impl;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class VertxTrustManagerFactory extends TrustManagerFactory {

  private static final Provider PROVIDER = new Provider("", 0.0, "") {
  };

  VertxTrustManagerFactory(TrustManager... tm) {
    super(new TrustManagerFactorySpi() {
      @Override
      protected void engineInit(KeyStore keyStore) throws KeyStoreException {
      }

      @Override
      protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {
      }

      @Override
      protected TrustManager[] engineGetTrustManagers() {
        return tm.clone();
      }
    }, PROVIDER, "");
  }
}
