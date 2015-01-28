/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.streams;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.ServiceHelper;
import io.vertx.core.spi.PumpFactory;

/**
 * Pumps data from a {@link ReadStream} to a {@link WriteStream} and performs flow control where necessary to
 * prevent the write stream buffer from getting overfull.
 * <p>
 * Instances of this class read items from a {@link ReadStream} and write them to a {@link WriteStream}. If data
 * can be read faster than it can be written this could result in the write queue of the {@link WriteStream} growing
 * without bound, eventually causing it to exhaust all available RAM.
 * <p>
 * To prevent this, after each write, instances of this class check whether the write queue of the {@link
 * WriteStream} is full, and if so, the {@link ReadStream} is paused, and a {@code drainHandler} is set on the
 * {@link WriteStream}.
 * <p>
 * When the {@link WriteStream} has processed half of its backlog, the {@code drainHandler} will be
 * called, which results in the pump resuming the {@link ReadStream}.
 * <p>
 * This class can be used to pump from any {@link ReadStream} to any {@link WriteStream},
 * e.g. from an {@link io.vertx.core.http.HttpServerRequest} to an {@link io.vertx.core.file.AsyncFile},
 * or from {@link io.vertx.core.net.NetSocket} to a {@link io.vertx.core.http.WebSocket}.
 * <p>
 * Please see the documentation for more information.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface Pump {

  /**
   * Create a new {@code Pump} with the given {@code ReadStream} and {@code WriteStream}
   *
   * @param rs  the read stream
   * @param ws  the write stream
   * @return the pump
   */
  static <T> Pump pump(ReadStream<T> rs, WriteStream<T> ws) {
    return factory.pump(rs, ws);
  }

  /**
   * Create a new {@code Pump} with the given {@code ReadStream} and {@code WriteStream} and
   * {@code writeQueueMaxSize}
   *
   * @param rs  the read stream
   * @param ws  the write stream
   * @param writeQueueMaxSize  the max size of the write queue
   * @return the pump
   */
  static <T> Pump pump(ReadStream<T> rs, WriteStream<T> ws, int writeQueueMaxSize) {
    return factory.pump(rs, ws, writeQueueMaxSize);
  }

  /**
   * Set the write queue max size to {@code maxSize}
   *
   * @param maxSize  the max size
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  Pump setWriteQueueMaxSize(int maxSize);

  /**
   * Start the Pump. The Pump can be started and stopped multiple times.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  Pump start();

  /**
   * Stop the Pump. The Pump can be started and stopped multiple times.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  Pump stop();

  /**
   * Return the total number of items pumped by this pump.
   */
  int numberPumped();

  static final PumpFactory factory = ServiceHelper.loadFactory(PumpFactory.class);


}
