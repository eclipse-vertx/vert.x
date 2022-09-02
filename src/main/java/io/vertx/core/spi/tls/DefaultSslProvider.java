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

/**
 * Returns a {@link SslProvider} implementation that configures a {@link io.netty.handler.ssl.SslContext} using
 * a {@link io.netty.handler.ssl.SslContextBuilder}.
 *
 * The context engine can use the JDK implementation or the OpenSSL implementation.
 */
public class DefaultSslProvider implements SslProvider {

  private final io.netty.handler.ssl.SslProvider provider;
  private final boolean sslSessionCacheEnabled;

  public DefaultSslProvider(io.netty.handler.ssl.SslProvider provider, boolean sslSessionCacheEnabled) {
    this.provider = provider;
    this.sslSessionCacheEnabled = sslSessionCacheEnabled;
  }

  @Override
  public DefaultSslContextFactory contextFactory() {
    return new DefaultSslContextFactory(provider, sslSessionCacheEnabled);
  }
}
