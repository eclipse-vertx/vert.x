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
package io.vertx.core.net;

import io.netty.handler.logging.ByteBufFormat;
import io.vertx.codegen.annotations.DataObject;

import java.util.Objects;

/**
 * Generic purpose logging of network operations configuration.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class NetworkLogging {

  private boolean enabled;
  private ByteBufFormat dataFormat;

  public NetworkLogging() {
    enabled = NetworkOptions.DEFAULT_LOG_ENABLED;
    dataFormat = NetworkOptions.DEFAULT_LOG_ACTIVITY_FORMAT;
  }

  public NetworkLogging(NetworkLogging other) {
    enabled = other.enabled;
    dataFormat = other.dataFormat;
  }

  /**
   * @return {@code} when network logging is enabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Set to true to enable network logging: Netty's pipeline is configured for logging on Netty's logger.
   *
   * @param enabled true for logging the network activity
   * @return a reference to this, so the API can be used fluently
   */
  public NetworkLogging setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  /**
   * @return per stream Netty's logging handler's data format.
   */
  public ByteBufFormat getDataFormat() {
    return dataFormat;
  }

  /**
   * Set the value of Netty's logging handler's data format: Netty's pipeline is configured for logging on Netty's logger.
   *
   * @param dataFormat the format to use
   * @return a reference to this, so the API can be used fluently
   */
  public NetworkLogging setDataFormat(ByteBufFormat dataFormat) {
    this.dataFormat = Objects.requireNonNull(dataFormat);
    return this;
  }
}
