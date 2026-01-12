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
package io.vertx.core.net.impl.quic;

import io.netty.handler.codec.quic.QuicTransportParameters;
import io.vertx.core.net.QuicTransportParams;

import java.time.Duration;

/**
 * Quic transport parameters implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicTransportParamsImpl implements QuicTransportParams {

  private final QuicTransportParameters params;

  public QuicTransportParamsImpl(QuicTransportParameters params) {
    this.params = params;
  }

  @Override
  public long initialMaxData() {
    return params.initialMaxData();
  }

  @Override
  public long initialMaxStreamDataBidiLocal() {
    return params.initialMaxStreamDataBidiLocal();
  }

  @Override
  public long initialMaxStreamDataBidiRemote() {
    return params.initialMaxStreamDataBidiRemote();
  }

  @Override
  public long initialMaxStreamsBidi() {
    return params.initialMaxStreamsBidi();
  }

  @Override
  public long initialMaxStreamsUni() {
    return params.initialMaxStreamsUni();
  }

  @Override
  public long initialMaxStreamDataUni() {
    return params.initialMaxStreamDataUni();
  }

  @Override
  public boolean disableActiveMigration() {
    return params.disableActiveMigration();
  }

  @Override
  public Duration maxIdleTimeout() {
    return Duration.ofMillis(params.maxIdleTimeout());
  }

  @Override
  public Duration maxAckDelay() {
    return Duration.ofMillis(params.maxAckDelay());
  }

  @Override
  public long ackDelayExponent() {
    return params.ackDelayExponent();
  }
}
