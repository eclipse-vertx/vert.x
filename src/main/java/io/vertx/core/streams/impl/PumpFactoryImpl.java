/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
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
