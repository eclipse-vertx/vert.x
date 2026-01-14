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
package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;

import java.time.Duration;

/**
 * Quic transport parameters.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public interface QuicTransportParams {

  /**
   * <p>Return the {@code initialMaxData} transport parameter.</p>
   *
   * <p>When set to a non-zero value, it will only allow at most {@code initialMaxData} bytes of incoming stream data to be buffered
   * for the whole connection (that is, data that is not yet read by the application) and will allow more data to be
   * received as the buffer is consumed by the application.</p>
   *
   * @return the {@code initialMaxData} parameter value
   */
  long initialMaxData();

  /**
   * <p>Return the {@code initialMaxStreamDataBidiLocal} transport parameter.</p>
   *
   * <p>When set to a non-zero value it will only allow at most {@code initialMaxStreamDataBidiLocal} bytes of incoming stream data
   * to be buffered for each locally-initiated bidirectional stream (that is, data that is not yet read by the application) and will
   * allow more data to be received as the buffer is consumed by the application.</p>
   *
   * @return the {@code initialMaxStreamDataBidiLocal } parameter value
   */
  long initialMaxStreamDataBidiLocal();

  /**
   * <p>Return the {@code initialMaxStreamDataBidiRemote} transport parameter.</p>
   *
   * <p>When set to a non-zero value it will only allow at most {@code initialMaxStreamDataBidiRemote} bytes of incoming
   * stream data to be buffered for each remotely-initiated bidirectional stream (that is, data that is not yet read by the application)
   * and will allow more data to be received as the buffer is consumed by the application.</p>
   *
   * @return the {@code initialMaxStreamDataBidiRemote } parameter value
   */
  long initialMaxStreamDataBidiRemote();

  /**
   * <p>Return the {@code initialMaxStreamsBidi} transport parameter.</p>
   *
   * <p>When set to a non-zero value it will only allow {@code initialMaxStreamsBidi} number of concurrent
   * remotely-initiated bidirectional streams to be open at any given time and will increase the limit
   * automatically as streams are completed.</p>
   *
   * <p>A bidirectional stream is considered completed when all incoming data has been read by the application (up to the fin offset)
   * or the stream's read direction has been shutdown, and all outgoing data has been acked by the peer (up to the fin offset)
   * or the stream's write direction has been shutdown.</p>
   *
   * @return the {@code initialMaxStreamsBidi } parameter value
   */
  long initialMaxStreamsBidi();

  /**
   * <p>Return the {@code initialMaxStreamsUni} transport parameter.</p>
   *
   * <p>When set to a non-zero value it will only allow {@code initialMaxStreamsUni} number of concurrent
   * remotely-initiated unidirectional streams to be open at any given time and will increase the limit automatically
   * as streams are completed.</p>
   *
   * <p>A unidirectional stream is considered completed when all incoming data has been read by the application (up to the fin offset)
   * or the stream's read direction has been shutdown.</p>
   *
   * @return the {@code initialMaxStreamsUni } parameter value
   */
  long initialMaxStreamsUni();

  /**
   * <p>Return the {@code initialMaxStreamDataUni} transport parameter.</p>
   *
   * <p>When set to a non-zero value it will only allow at most {@code initialMaxStreamDataUni} bytes of incoming
   * stream data to be buffered for each unidirectional stream (that is, data that is not yet read by the application) and
   * will allow more data to be received as the buffer is consumed by the application.</p>
   *
   * @return the {@code initialMaxStreamDataUni } parameter value
   */
  long initialMaxStreamDataUni();

  /**
   * <p>Returns whether active migration is disabled.</p>
   *
   * @return the {@code disableActiveMigration } parameter value
   */
  boolean disableActiveMigration();

  /**
   * <p>Returns the {@code maxIdleTimeout} transport parameter.</p>
   *
   * <p>The default value {@code null} means infinite, that is, no timeout is used.</p>
   *
   * @return the {@code maxIdleTimeout } parameter value
   */
  Duration maxIdleTimeout();

  /**
   * <p>Returns the {@code maxAckDelay} transport parameter.</p>
   *
   * @return the {@code maxAckDelay } parameter value
   */
  Duration maxAckDelay();

  /**
   * <p>Returns the {@code ackDelayExponent} transport parameter.</p>
   *
   * @return the {@code ackDelayExponent } parameter value
   */
  long ackDelayExponent();

}
