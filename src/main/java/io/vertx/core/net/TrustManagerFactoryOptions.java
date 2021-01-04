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
 * @author Hakan Altindag
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
