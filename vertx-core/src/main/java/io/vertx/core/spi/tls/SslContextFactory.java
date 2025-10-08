/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.spi.tls;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.util.List;
import java.util.Set;

/**
 * A factory for a Netty {@link SslContext}, the factory is configured with the fluent setters until {@link #create()}
 * to obtain a properly configured {@link SslContext}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface SslContextFactory {

  /**
   * Set whether to use ALPN.
   *
   * @param useAlpn {@code true} to use ALPN
   * @return a reference to this, so the API can be used fluently
   */
  default SslContextFactory useAlpn(boolean useAlpn) {
    return this;
  }

  /**
   * Configure the factory to build for server
   * @param clientAuth the client auth to use
   * @return a reference to this, so the API can be used fluently
   */
  default SslContextFactory forServer(ClientAuth clientAuth) {
    return this;
  }

  /**
   * Configure the factory to build for client
   * @param serverName the optional server name
   * @return a reference to this, so the API can be used fluently
   */
  default SslContextFactory forClient(String serverName, String endpointIdentificationAlgorithm) {
    return this;
  }

  default SslContextFactory enabledProtocols(Set<String> enabledProtocols) {
    return this;
  }

  /**
   * Set the key manager factory to use.
   * @param kmf the key manager factory instance
   * @return a reference to this, so the API can be used fluently
   */
  default SslContextFactory keyMananagerFactory(KeyManagerFactory kmf) {
    return this;
  }

  /**
   * Set the trust manager factory to use.
   * @param tmf the trust manager factory instance
   * @return a reference to this, so the API can be used fluently
   */
  default SslContextFactory trustManagerFactory(TrustManagerFactory tmf) {
    return this;
  }

  /**
   * Set the enabled cipher suites.
   * @param enabledCipherSuites the set of cipher suites
   * @return a reference to this, so the API can be used fluently
   */
  default SslContextFactory enabledCipherSuites(Set<String> enabledCipherSuites) {
    return this;
  }

  /**
   * Set the application protocols to use when using ALPN.
   * @param applicationProtocols this list of application protocols
   * @return a reference to this, so the API can be used fluently
   */
  default SslContextFactory applicationProtocols(List<String> applicationProtocols) {
    return this;
  }

  /**
   * @return a configured {@link SslContext}
   */
  SslContext create() throws SSLException;

}
