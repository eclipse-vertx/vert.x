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

import java.util.concurrent.TimeUnit;

/**
 * The SSL refresh options which will be used in the {@link io.vertx.core.net.impl.SSLHelper}
 * and {@link io.vertx.core.http.HttpServerOptions} to determine how often to reload the ssl configuration.
 *
 * @author <a href="mailto:hakangoudberg@hotmail.com">Hakan Altindag</a>
 */
public final class SslRefreshOptions {

  private final TimeUnit sslRefreshTimeUnit;
  private final long sslRefreshTimeout;

  public SslRefreshOptions(TimeUnit sslRefreshTimeUnit, long sslRefreshTimeout) {
    this.sslRefreshTimeUnit = sslRefreshTimeUnit;
    this.sslRefreshTimeout = sslRefreshTimeout;
  }

  public TimeUnit getSslRefreshTimeUnit() {
    return sslRefreshTimeUnit;
  }

  public long getSslRefreshTimeout() {
    return sslRefreshTimeout;
  }

}
