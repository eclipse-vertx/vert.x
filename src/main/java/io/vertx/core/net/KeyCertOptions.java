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
   * Returns a function that maps SNI server names to {@link X509KeyManager} instance.
   *
   * The returned {@code X509KeyManager} must satisfies these rules:
   *
   * <ul>
   *   <li>{@link X509KeyManager#getPrivateKey(String)} returns the private key for the indicated server name,
   *   the {@code alias} parameter will be {@code null}.</li>
   *   <li>{@link X509KeyManager#getCertificateChain(String)} returns the certificate chain for the indicated server name,
   *   the {@code alias} parameter will be {@code null}.</li>
   * </ul>
   *
   * The mapper is only used when the server has SNI enabled and the client indicated a server name.
   * <p>
   * The returned function may return {@code null} in which case the default key manager provided by {@link #getKeyManagerFactory(Vertx)}
   * will be used.
   *
   * @deprecated instead use {@link #keyManagerFactoryMapper(Vertx)}
   */
  @Deprecated
  default Function<String, X509KeyManager> keyManagerMapper(Vertx vertx) throws Exception {
    return name -> null;
  }

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
   *
   * @implSpec the default implementation converts they {@link #keyManagerMapper(Vertx)} to a {@link KeyManagerFactory}
   * @implNote the default implementation does not any caching, creating a {@link KeyManagerFactory} can be expense, implementations
   *           are encouraged to reimplement this method and cache the result
   */
  default Function<String, KeyManagerFactory> keyManagerFactoryMapper(Vertx vertx) throws Exception {
    Function<String, X509KeyManager> mapper = keyManagerMapper(vertx);
    return name -> {
      X509KeyManager mgr = mapper.apply(name);
      if (mgr != null) {
        try {
          return KeyStoreHelper.toKeyManagerFactory(mgr);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      return null;
    };
  }

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
