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

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import java.security.KeyStore;
import java.security.Provider;
import java.util.Objects;

/**
 * {@link KeyManagerFactoryWrapper} serves as a {@link KeyManagerFactory} wrapper class for a {@link KeyManager} instance.
 * A {@link KeyManagerFactory} is usually used when transforming a {@link KeyStore} instance into a KeyManager[]. Vert.x core has a
 * base {@link KeyCertOptions} which relies on a {@link KeyManagerFactory} instance and therefore it can only be constructed from the a {@link KeyManagerFactory}.
 *
 * @author <a href="mailto:hakangoudberg@hotmail.com">Hakan Altindag</a>
 */
class KeyManagerFactoryWrapper extends KeyManagerFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(KeyManagerFactoryWrapper.class);
  private static final String KEY_MANAGER_FACTORY_ALGORITHM = "no-algorithm";
  private static final Provider PROVIDER = new Provider("", 1.0, "") {
  };

  KeyManagerFactoryWrapper(KeyManager keyManager) {
    super(new KeyManagerFactorySpiWrapper(keyManager), PROVIDER, KEY_MANAGER_FACTORY_ALGORITHM);
  }

  private static class KeyManagerFactorySpiWrapper extends KeyManagerFactorySpi {

    private final KeyManager[] keyManagers;

    private KeyManagerFactorySpiWrapper(KeyManager keyManager) {
      Objects.requireNonNull(keyManager);
      this.keyManagers = new KeyManager[]{keyManager};
    }

    @Override
    protected void engineInit(KeyStore keyStore, char[] keyStorePassword) {
      LOGGER.info("Ignoring provided KeyStore");
    }

    @Override
    protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {
      LOGGER.info("Ignoring provided ManagerFactoryParameters");
    }

    @Override
    protected KeyManager[] engineGetKeyManagers() {
      return keyManagers;
    }

  }

}
