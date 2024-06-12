/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl;

/**
 * Result of the SslContext update operation.
 */
public class SslContextUpdate {

  private final SslChannelProvider sslChannelProvider;
  private final boolean updated;
  private final Throwable error;

  SslContextUpdate(SslChannelProvider sslChannelProvider, boolean updated, Throwable error) {
    this.sslChannelProvider = sslChannelProvider;
    this.updated = updated;
    this.error = error;
  }

  /**
   * @return the latest and freshest {@link SslChannelProvider}
   */
  public SslChannelProvider sslChannelProvider() {
    return sslChannelProvider;
  }

  /**
   * @return whether the update occurred
   */
  public boolean isUpdated() {
    return updated;
  }

  /**
   * @return the optional error of the update operation
   */
  public Throwable error() {
    return error;
  }
}
