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
 */
public interface SslContextFactory {

  default SslContextFactory useAlpn(boolean useAlpn) {
    return this;
  }

  default SslContextFactory clientAuth(ClientAuth clientAuth) {
    return this;
  }

  default SslContextFactory forClient(boolean forClient) {
    return this;
  }

  default SslContextFactory keyMananagerFactory(KeyManagerFactory kmf) {
    return this;
  }

  default SslContextFactory trustManagerFactory(TrustManagerFactory tmf) {
    return this;
  }

  default SslContextFactory enabledCipherSuites(Set<String> enabledCipherSuites) {
    return this;
  }

  default SslContextFactory applicationProtocols(List<String> applicationProtocols) {
    return this;
  }

  /**
   * @return a configured {@link SslContext}
   */
  SslContext create() throws SSLException;

}
