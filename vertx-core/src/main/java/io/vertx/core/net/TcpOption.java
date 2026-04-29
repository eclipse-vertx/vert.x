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

/**
 * Generic TCP configuration option.
 */
public class TcpOption<T> {

  /**
   * The {@code TCP_NODELAY} option - only with Linux native transport.
   */
  public static final TcpOption<Boolean> NODELAY = new TcpOption<>(Boolean.class, TCPSSLOptions.DEFAULT_TCP_NO_DELAY);

  /**
   * The {@code TCP_QUICKACK} option - only with Linux native transport.
   */
  public static final TcpOption<Boolean> QUICKACK = new TcpOption<>(Boolean.class, TCPSSLOptions.DEFAULT_TCP_QUICKACK);

  /**
   * The {@code TCP_CORK} option - only with Linux native transport.
   */
  public static final TcpOption<Boolean> CORK = new TcpOption<>(Boolean.class, TCPSSLOptions.DEFAULT_TCP_CORK);

  /**
   * The {@code TCP_USER_TIMEOUT} option - only with Linux native transport.
   */
  public static final TcpOption<Integer> USER_TIMEOUT = new TcpOption<>(Integer.class, TCPSSLOptions.DEFAULT_TCP_USER_TIMEOUT) {
    @Override
    protected void validate(Integer value) {
      if (value < 0) {
        throw new IllegalArgumentException("USER_TIMEOUT must be >= 0");
      }
    }
  };

  /**
   * The {@code TCP_FASTOPEN_CONNECT} option - only with Linux native transport.
   */
  public static final TcpOption<Boolean> FASTOPEN_CONNECT = new TcpOption<>(Boolean.class, TCPSSLOptions.DEFAULT_TCP_FAST_OPEN);

  /**
   * The {@code TCP_FASTOPEN} option - only with Linux native transport.
   */
  public static final TcpOption<Integer> FASTOPEN = new TcpOption<>(Integer.class, 0);

  final Class<T> type;
  final T defaultValue;

  private TcpOption(Class<T> type, T defaultValue) {
    this.type = type;
    this.defaultValue = defaultValue;
  }

  protected void validate(T value) {
  }
}
