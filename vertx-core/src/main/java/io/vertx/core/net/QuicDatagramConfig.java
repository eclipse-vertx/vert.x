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

import io.vertx.codegen.annotations.DataObject;

/**
 * QUIC datagram configuration.
 */
@DataObject
public class QuicDatagramConfig {

  public static final boolean DEFAULT_ENABLED = false;
  public static final int DEFAULT_SEND_QUEUE_LENGTH = 128;
  public static final int DEFAULT_RECEIVE_QUEUE_LENGTH = 128;

  private boolean enabled;
  private int sendQueueLength;
  private int receiveQueueLength;

  public QuicDatagramConfig() {
    this.enabled = DEFAULT_ENABLED;
    this.sendQueueLength = DEFAULT_SEND_QUEUE_LENGTH;
    this.receiveQueueLength = DEFAULT_RECEIVE_QUEUE_LENGTH;
  }

  public QuicDatagramConfig(QuicDatagramConfig other) {
    this.enabled = other.enabled;
    this.sendQueueLength = other.sendQueueLength;
    this.receiveQueueLength = other.receiveQueueLength;
  }

  /**
   * @return whether to support datagrams frames
   * @see #setEnabled(boolean)
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * <p>Set whether to support datagrams frames.</p>
   *
   * <p>The default value is {@code false} (disabled).</p>
   *
   * @param enableDatagrams the value to set
   * @return this instance
   */
  public QuicDatagramConfig setEnabled(boolean enableDatagrams) {
    this.enabled = enableDatagrams;
    return this;
  }

  /**
   * @return the datagram send queue length
   * @see #setSendQueueLength(int)
   */
  public int getSendQueueLength() {
    return sendQueueLength;
  }

  /**
   * <p>Set the datagram receive queue length.</p>
   *
   * <p>The default value is {@code 128}.</p>
   *
   * @param datagramSendQueueLength the value to use
   * @return this instance
   */
  public QuicDatagramConfig setSendQueueLength(int datagramSendQueueLength) {
    if (datagramSendQueueLength <= 0) {
      throw new IllegalArgumentException("sendQueueLength must be > 0");
    }
    this.sendQueueLength = datagramSendQueueLength;
    return this;
  }

  /**
   * @return the datagram receive queue length
   * @see #setReceiveQueueLength(int)
   */
  public int getReceiveQueueLength() {
    return receiveQueueLength;
  }

  /**
   * <p>Set the datagram send queue length.</p>
   *
   * <p>The default value is {@code 128}.</p>
   *
   * @param datagramReceiveQueueLength the value to use
   * @return this instance
   */
  public QuicDatagramConfig setReceiveQueueLength(int datagramReceiveQueueLength) {
    if (datagramReceiveQueueLength <= 0) {
      throw new IllegalArgumentException("receiveQueueLength must be > 0");
    }
    this.receiveQueueLength = datagramReceiveQueueLength;
    return this;
  }
}
