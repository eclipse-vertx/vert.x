/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.streams.impl;

import java.util.Objects;

import io.vertx.core.spi.PumpFactory;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class PumpFactoryImpl implements PumpFactory {
  @Override
  public <T> Pump pump(ReadStream<T> rs, WriteStream<T> ws) {
    Objects.requireNonNull(rs);
    Objects.requireNonNull(ws);
    return new PumpImpl<>(rs, ws);
  }

  @Override
  public <T> Pump pump(ReadStream<T> rs, WriteStream<T> ws, int writeQueueMaxSize) {
    Objects.requireNonNull(rs);
    Objects.requireNonNull(ws);
    return new PumpImpl<>(rs, ws, writeQueueMaxSize);
  }
}
