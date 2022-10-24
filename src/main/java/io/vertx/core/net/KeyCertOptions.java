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

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.Vertx;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509KeyManager;
import java.util.concurrent.TimeUnit;
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
   * This method is used to check if context reloading is enabled.
   */
  default Boolean getReloadCerts() {
    return Boolean.FALSE;
  }

  /**
   * This method is used by {@link io.vertx.core.net.impl.SSLHelper} to check if context reloading is needed.
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  default boolean isReloadNeeded() {
    return false;
  }

  /**
   * This method returns default certificate refresh rate. By default, set to 5 hrs.
   */
  default Long getCertRefreshRateInSeconds() {
    return TimeUnit.HOURS.toSeconds(5);
  }

  /**
   * This method is used by {@link io.vertx.core.net.impl.SSLHelper} for reloading certs present in cert/keystore paths.
   */
  default void reload() {
    throw new UnsupportedOperationException("Default reload no-op method called");
  }

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
   * The returned function may return null in which case the default key manager provided by {@link #getKeyManagerFactory(Vertx)}
   * will be used.
   *
   */
  Function<String, X509KeyManager> keyManagerMapper(Vertx vertx) throws Exception;

  /**
   * Returns a {@link KeyCertOptions} from the provided {@link X509KeyManager}
   *
   * @param keyManager the keyManager instance
   * @return the {@link KeyCertOptions}
   */
  static KeyCertOptions wrap(X509KeyManager keyManager) {
    return new KeyManagerFactoryOptions(keyManager);
  }

}
