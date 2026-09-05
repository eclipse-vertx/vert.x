/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import java.time.Duration;

/**
 * Configuration of a client resolver, the resolver translates a logical address into a socket address and a port.
 */
public class ClientResolverConfig {

  private Duration keepAliveTimeout = Duration.ofSeconds(10);
  private Duration maxKeepAlive = Duration.ofMinutes(5);

  public ClientResolverConfig() {
  }

  public ClientResolverConfig(ClientResolverConfig other) {
    keepAliveTimeout = other.keepAliveTimeout;
    maxKeepAlive = other.maxKeepAlive;
  }

  /**
   *
   * @return the amoutn of time after which an unused entry is evicted
   */
  public Duration getKeepAliveTimeout() {
    return keepAliveTimeout;
  }

  /**
   * Set the {@code amount} of time after which an unused entry is evicted.
   * @param amount the amount of time
   * @return a reference to this, so the API can be used fluently
   */
  public ClientResolverConfig setKeepAliveTimeout(Duration amount) {
    if (amount.isNegative() || amount.isZero()) {
      throw new IllegalArgumentException("Invalid resolver idle timeout");
    }
    this.keepAliveTimeout = amount;
    return this;
  }

  /**
   * @return the maximum amount of time after which an entry is evicted
   */
  public Duration getMaxKeepAlive() {
    return maxKeepAlive;
  }

  /**
   * Set the maximum {@code amount} of time after which an entry is evicted.
   *
   * @param amount the amount of time
   * @return a reference to this, so the API can be used fluently
   */
  public ClientResolverConfig setMaxKeepAlive(Duration amount) {
    if (amount.isNegative() || amount.isZero()) {
      throw new IllegalArgumentException("Invalid resolver max ttl");
    }
    this.maxKeepAlive = amount;
    return this;
  }
}
