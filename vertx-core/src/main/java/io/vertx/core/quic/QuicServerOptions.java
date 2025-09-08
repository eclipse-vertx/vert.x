/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.quic;

import io.vertx.core.net.ServerSSLOptions;

/**
 * Config operations of a Quic server.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicServerOptions extends QuicEndpointOptions {

  public static final boolean DEFAULT_LOAD_BALANCED = false;

  private boolean loadBalanced;

  public QuicServerOptions() {
    loadBalanced = DEFAULT_LOAD_BALANCED;
  }

  public QuicServerOptions(QuicServerOptions other) {
    super(other);

    this.loadBalanced = other.loadBalanced;
  }

  @Override
  public ServerSSLOptions getSslOptions() {
    return (ServerSSLOptions) super.getSslOptions();
  }

  @Override
  protected ServerSSLOptions getOrCreateSSLOptions() {
    return new ServerSSLOptions();
  }

  /**
   * @return whether the server is load balanced
   */
  public boolean isLoadBalanced() {
    return loadBalanced;
  }

  /**
   * Set to {@code true} enables to bind multiples instances of a server on the same UDP port with the {@code SO_REUSE} options and let
   * set of bound server route UDP packets to the correct server instance.
   *
   * @param loadBalanced whether the server can be load balanced
   * @return this exact object instance
   */
  public QuicServerOptions setLoadBalanced(boolean loadBalanced) {
    this.loadBalanced = loadBalanced;
    return this;
  }
}
