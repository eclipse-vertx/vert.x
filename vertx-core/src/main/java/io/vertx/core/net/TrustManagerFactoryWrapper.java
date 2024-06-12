/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import java.security.KeyStore;
import java.security.Provider;
import java.util.Objects;

/**
 * {@link TrustManagerFactoryWrapper} serves as a {@link TrustManagerFactory} wrapper class for a {@link TrustManager} instance.
 * A {@link TrustManagerFactory} is usually used when transforming a {@link KeyStore TrustStore} instance into a TrustManager[]. Vert.x core has a
 * base {@link TrustOptions} which relies on a {@link TrustManagerFactory} instance and therefore it can only be constructed from the a {@link TrustManagerFactory}.
 *
 * @author <a href="mailto:hakangoudberg@hotmail.com">Hakan Altindag</a>
 */
class TrustManagerFactoryWrapper extends TrustManagerFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(TrustManagerFactoryWrapper.class);
  private static final String KEY_MANAGER_FACTORY_ALGORITHM = "no-algorithm";
  private static final Provider PROVIDER = new Provider("", 1.0, "") {
  };

  TrustManagerFactoryWrapper(TrustManager trustManager) {
    super(new TrustManagerFactorySpiWrapper(trustManager), PROVIDER, KEY_MANAGER_FACTORY_ALGORITHM);
  }

  private static class TrustManagerFactorySpiWrapper extends TrustManagerFactorySpi {

    private final TrustManager[] trustManagers;

    private TrustManagerFactorySpiWrapper(TrustManager trustManager) {
      Objects.requireNonNull(trustManager);
      this.trustManagers = new TrustManager[]{trustManager};
    }

    @Override
    protected void engineInit(KeyStore keyStore) {
      LOGGER.info("Ignoring provided KeyStore");
    }

    @Override
    protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {
      LOGGER.info("Ignoring provided ManagerFactoryParameters");
    }

    @Override
    protected TrustManager[] engineGetTrustManagers() {
      return trustManagers;
    }

  }

}
