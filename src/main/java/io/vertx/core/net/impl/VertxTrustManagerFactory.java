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
