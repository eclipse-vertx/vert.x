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

import io.vertx.core.Vertx;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.util.function.Function;

/**
 * Trust options configuring trusted certificates based on {@link TrustManagerFactory} or {@link TrustManager}.
 *
 * <pre>
 * InputStream trustStoreStream = null;
 * KeyStore trustStore = KeyStore.getInstance("JKS");
 * trustStore.load(trustStoreStream, "truststore-password".toCharArray());
 *
 * TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
 * trustManagerFactory.init(trustStore);
 *
 * HttpServerOptions options = HttpServerOptions.httpServerOptions();
 * options.setTrustOptions(new TrustManagerFactoryOptions(keyManagerFactory));
 *
 * // or with a TrustManager
 *
 * TrustManager trustManager = trustManagerFactory.getTrustManagers()[0];
 * options.setTrustOptions(new TrustManagerFactoryOptions(trustManager));
 * </pre>
 *
 * As this class is not available because it is part of the internal api the proper usage would be:
 * <pre>
 * options.setTrustOptions(TrustOptions.wrap(trustManager));
 * </pre>
 *
 * @author <a href="mailto:hakangoudberg@hotmail.com">Hakan Altindag</a>
 */
class TrustManagerFactoryOptions implements TrustOptions {

  private final TrustManagerFactory trustManagerFactory;

  TrustManagerFactoryOptions(TrustManagerFactory trustManagerFactory) {
    if (trustManagerFactory == null
      || trustManagerFactory.getTrustManagers() == null
      || trustManagerFactory.getTrustManagers().length == 0) {
      throw new IllegalArgumentException("TrustManagerFactory is not present or is not initialized yet");
    }
    this.trustManagerFactory = trustManagerFactory;
  }

  TrustManagerFactoryOptions(TrustManager trustManager) {
    this(new TrustManagerFactoryWrapper(trustManager));
  }

  private TrustManagerFactoryOptions(TrustManagerFactoryOptions other) {
    trustManagerFactory = other.trustManagerFactory;
  }

  @Override
  public TrustOptions copy() {
    return new TrustManagerFactoryOptions(this);
  }

  @Override
  public TrustManagerFactory getTrustManagerFactory(Vertx vertx) {
    return trustManagerFactory;
  }

  @Override
  public Function<String, TrustManager[]> trustManagerMapper(Vertx vertx) {
    return serverName -> trustManagerFactory.getTrustManagers();
  }

}
