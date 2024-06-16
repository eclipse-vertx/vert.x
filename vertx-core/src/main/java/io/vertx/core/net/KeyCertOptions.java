/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net;

import io.vertx.core.Vertx;
import io.vertx.core.net.impl.KeyStoreHelper;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509KeyManager;
import java.util.function.Function;

/**
 * Key/cert configuration options.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface KeyCertOptions {

  /**
   * @return a copy of these options
   */
  KeyCertOptions copy();

  /**
   * Create and return the key manager factory for these options.
   * <p>
   * The returned key manager factory should be already initialized and ready to use.
   *
   * @param vertx the vertx instance
   * @return the key manager factory
   */
  KeyManagerFactory getKeyManagerFactory(Vertx vertx) throws Exception;

  /**
   * Returns a function that maps SNI server names to {@link KeyManagerFactory} instance.
   *
   * The returned {@code KeyManagerFactory} must satisfies these rules:
   *
   * <ul>
   *   <li>The store private key must match the indicated server name for a null alias.</li>
   *   <li>The store certificate chain must match the indicated server name for a null alias.</li>
   * </ul>
   *
   * The mapper is only used when the server has SNI enabled and the client indicated a server name.
   * <p>
   * The returned function may return {@code null} in which case the default key manager provided by {@link #getKeyManagerFactory(Vertx)}
   * will be used.
   */
  Function<String, KeyManagerFactory> keyManagerFactoryMapper(Vertx vertx) throws Exception;

  /**
   * Returns a {@link KeyCertOptions} from the provided {@link X509KeyManager}
   *
   * @param keyManager the keyManager instance
   * @return the {@link KeyCertOptions}
   */
  static KeyCertOptions wrap(X509KeyManager keyManager) {
    return new KeyManagerFactoryOptions(keyManager);
  }

  /**
   * Returns a {@link KeyCertOptions} from the provided {@link KeyManagerFactory}
   *
   * @param keyManagerFactory the keyManagerFactory instance
   * @return the {@link KeyCertOptions}
   */
  static KeyCertOptions wrap(KeyManagerFactory keyManagerFactory) {
    return new KeyManagerFactoryOptions(keyManagerFactory);
  }
}
