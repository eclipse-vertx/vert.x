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

import java.time.Duration;

/**
 * Quic transport options.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicOptions extends TransportOptions {

  public static final long DEFAULT_MAX_INITIAL_DATA = 0L;
  public static final long DEFAULT_MAX_STREAM_DATA_BIDI_LOCAL = 0L;
  public static final long DEFAULT_MAX_STREAM_DATA_BIDI_REMOTE = 0L;
  public static final long DEFAULT_MAX_STREAMS_DATA_UNI = 0L;
  public static final long DEFAULT_MAX_STREAMS_DATA_BIDI = 0L;
  public static final long DEFAULT_MAX_STREAM_DATA_UNI = 0L;
  public static final boolean DEFAULT_ACTIVE_MIGRATION = false;
  public static final Duration DEFAULT_MAX_IDLE_TIMEOUT = null;
  public static final boolean DEFAULT_ENABLE_DATAGRAMS = false;
  public static final int DEFAULT_DATAGRAM_SEND_QUEUE_LENGTH = 128;
  public static final int DEFAULT_DATAGRAM_RECEIVE_QUEUE_LENGTH = 128;
  public static final Duration DEFAULT_MAX_ACK_DELAY = Duration.ofMillis(25);
  public static final int DEFAULT_ACK_DELAY_EXPONENT = 3;
  public static final QuicCongestionControlAlgorithm DEFAULT_CONGESTION_CONTROL_ALGORITHM = QuicCongestionControlAlgorithm.CUBIC;
  public static final boolean DEFAULT_GREASE = true;
  public static final boolean DEFAULT_HYSTART = true;
  public static final int DEFAULT_INITIAL_CONGESTION_WINDOW_PACKETS = 10;

  private long initialMaxData = DEFAULT_MAX_INITIAL_DATA;
  private long initialMaxStreamDataBidirectionalLocal = DEFAULT_MAX_STREAM_DATA_BIDI_LOCAL;
  private long initialMaxStreamDataBidirectionalRemote = DEFAULT_MAX_STREAM_DATA_BIDI_REMOTE;
  private long initialMaxStreamDataUnidirectional = DEFAULT_MAX_STREAMS_DATA_UNI;
  private long initialMaxStreamsBidirectional = DEFAULT_MAX_STREAMS_DATA_BIDI;
  private long initialMaxStreamsUnidirectional = DEFAULT_MAX_STREAM_DATA_UNI;
  private boolean activeMigration = DEFAULT_ACTIVE_MIGRATION;
  private Duration maxIdleTimeout = DEFAULT_MAX_IDLE_TIMEOUT;
  private boolean enableDatagrams = DEFAULT_ENABLE_DATAGRAMS;
  private int datagramSendQueueLength = DEFAULT_DATAGRAM_SEND_QUEUE_LENGTH;
  private int datagramReceiveQueueLength = DEFAULT_DATAGRAM_RECEIVE_QUEUE_LENGTH;
  private Duration maxAckDelay = DEFAULT_MAX_ACK_DELAY;
  private int ackDelayExponent = DEFAULT_ACK_DELAY_EXPONENT;
  private QuicCongestionControlAlgorithm congestionControlAlgorithm = DEFAULT_CONGESTION_CONTROL_ALGORITHM;
  private boolean grease = DEFAULT_GREASE;
  private boolean hystart = DEFAULT_HYSTART;
  private int initialCongestionWindowPackets = DEFAULT_INITIAL_CONGESTION_WINDOW_PACKETS;

  public QuicOptions() {
  }

  public QuicOptions(QuicOptions other) {
    this.initialMaxData = other.initialMaxData;
    this.initialMaxStreamDataBidirectionalLocal = other.initialMaxStreamDataBidirectionalLocal;
    this.initialMaxStreamDataBidirectionalRemote = other.initialMaxStreamDataBidirectionalRemote;
    this.initialMaxStreamsBidirectional = other.initialMaxStreamsBidirectional;
    this.initialMaxStreamsUnidirectional = other.initialMaxStreamsUnidirectional;
    this.initialMaxStreamDataUnidirectional = other.initialMaxStreamDataUnidirectional;
    this.activeMigration = other.activeMigration;
    this.maxIdleTimeout = other.maxIdleTimeout;
    this.enableDatagrams = other.enableDatagrams;
    this.datagramSendQueueLength = other.datagramSendQueueLength;
    this.datagramReceiveQueueLength = other.datagramReceiveQueueLength;
    this.maxAckDelay = other.maxAckDelay;
    this.ackDelayExponent = other.ackDelayExponent;
    this.congestionControlAlgorithm = other.congestionControlAlgorithm;
    this.grease = other.grease;
    this.hystart = other.hystart;
    this.initialCongestionWindowPackets = other.initialCongestionWindowPackets;
  }

  @Override
  protected QuicOptions copy() {
    return new QuicOptions(this);
  }

  /**
   * @return the {@code initialMaxData} parameter value
   * @see #setInitialMaxData(long)
   */
  public long getInitialMaxData() {
    return initialMaxData;
  }

  /**
   * <p>Set the {@code initialMaxData} transport parameter.</p>
   *
   * <p>When set to a non-zero value, it will only allow at most {@code initialMaxData} bytes of incoming stream data to be buffered
   * for the whole connection (that is, data that is not yet read by the application) and will allow more data to be
   * received as the buffer is consumed by the application.</p>
   *
   * <p>The default value is {@code 0}</p>
   *
   * @param initialMaxData the value to set
   * @return this instance
   */
  public QuicOptions setInitialMaxData(long initialMaxData) {
    if (initialMaxData < 0) {
      throw new IllegalArgumentException("initialMaxData must be >= 0");
    }
    this.initialMaxData = initialMaxData;
    return this;
  }

  /**
   * @return the {@code initialMaxStreamDataBidirectionalLocal } parameter value
   * @see #setInitialMaxStreamDataBidirectionalLocal(long)
   */
  public long getInitialMaxStreamDataBidirectionalLocal() {
    return initialMaxStreamDataBidirectionalLocal;
  }

  /**
   * <p>Set the {@code initialMaxStreamDataBidirectionalLocal} transport parameter.</p>
   *
   * <p>When set to a non-zero value it will only allow at most {@code initialMaxStreamDataBidirectionalLocal} bytes of incoming stream data
   * to be buffered for each locally-initiated bidirectional stream (that is, data that is not yet read by the application) and will
   * allow more data to be received as the buffer is consumed by the application.</p>
   *
   * <p>The default value is {@code 0}.</p>
   *
   * @param initialMaxStreamDataBidirectionalLocal the value to set
   * @return this instance
   */
  public QuicOptions setInitialMaxStreamDataBidirectionalLocal(long initialMaxStreamDataBidirectionalLocal) {
    if (initialMaxStreamDataBidirectionalLocal < 0) {
      throw new IllegalArgumentException("initialMaxStreamDataBidirectionalLocal must be >= 0");
    }
    this.initialMaxStreamDataBidirectionalLocal = initialMaxStreamDataBidirectionalLocal;
    return this;
  }

  /**
   * @return the {@code initialMaxStreamDataBidirectionalRemote } parameter value
   * @see #setInitialMaxStreamDataBidirectionalRemote(long)
   */
  public long getInitialMaxStreamDataBidirectionalRemote() {
    return initialMaxStreamDataBidirectionalRemote;
  }

  /**
   * <p>Set the {@code initialMaxStreamDataBidirectionalRemote} transport parameter.</p>
   *
   * <p>When set to a non-zero value it will only allow at most {@code initialMaxStreamDataBidirectionalRemote} bytes of incoming
   * stream data to be buffered for each remotely-initiated bidirectional stream (that is, data that is not yet read by the application)
   * and will allow more data to be received as the buffer is consumed by the application.</p>
   *
   * <p>The default value is {@code 0}.</p>
   *
   * @param initialMaxStreamDataBidirectionalRemote the value to set
   * @return this instance
   */
  public QuicOptions setInitialMaxStreamDataBidirectionalRemote(long initialMaxStreamDataBidirectionalRemote) {
    if (initialMaxStreamDataBidirectionalRemote < 0) {
      throw new IllegalArgumentException("initialMaxStreamDataBidirectionalRemote must be >= 0");
    }
    this.initialMaxStreamDataBidirectionalRemote = initialMaxStreamDataBidirectionalRemote;
    return this;
  }

  /**
   * @return the {@code initialMaxStreamsBidirectional } parameter value
   * @see #setInitialMaxStreamsBidirectional(long)
   */
  public long getInitialMaxStreamsBidirectional() {
    return initialMaxStreamsBidirectional;
  }

  /**
   * <p>Set the {@code setInitialMaxStreamsBidirectional} transport parameter.</p>
   *
   * <p>When set to a non-zero value it will only allow {@code initialMaxStreamsBidirectional} number of concurrent
   * remotely-initiated bidirectional streams to be open at any given time and will increase the limit
   * automatically as streams are completed.</p>
   *
   * <p>A bidirectional stream is considered completed when all incoming data has been read by the application (up to the fin offset)
   * or the stream's read direction has been shutdown, and all outgoing data has been acked by the peer (up to the fin offset)
   * or the stream's write direction has been shutdown.</p>
   *
   * <p>The default value is {@code 0}.</p>
   *
   * @param initialMaxStreamsBidirectional the value to set
   * @return this instance
   */
  public QuicOptions setInitialMaxStreamsBidirectional(long initialMaxStreamsBidirectional) {
    if (initialMaxStreamsBidirectional < 0) {
      throw new IllegalArgumentException("initialMaxStreamsBidirectional must be >= 0");
    }
    this.initialMaxStreamsBidirectional = initialMaxStreamsBidirectional;
    return this;
  }

  /**
   * @return the {@code initialMaxStreamsUnidirectional } parameter value
   * @see #setInitialMaxStreamsUnidirectional(long)
   */
  public long getInitialMaxStreamsUnidirectional() {
    return initialMaxStreamsUnidirectional;
  }

  /**
   * <p>Sets the {@code initialMaxStreamsUnidirectional} transport parameter.</p>
   *
   * <p>When set to a non-zero value it will only allow {@code initialMaxStreamsUnidirectional} number of concurrent
   * remotely-initiated unidirectional streams to be open at any given time and will increase the limit automatically
   * as streams are completed.</p>
   *
   * <p>A unidirectional stream is considered completed when all incoming data has been read by the application (up to the fin offset)
   * or the stream's read direction has been shutdown.</p>
   *
   * <p>The default value is {@code 0}.</p>
   *
   * @param initialMaxStreamsUnidirectional the value to set
   * @return this instance
   */
  public QuicOptions setInitialMaxStreamsUnidirectional(long initialMaxStreamsUnidirectional) {
    if (initialMaxStreamsUnidirectional < 0) {
      throw new IllegalArgumentException("initialMaxStreamsUnidirectional must be >= 0");
    }
    this.initialMaxStreamsUnidirectional = initialMaxStreamsUnidirectional;
    return this;
  }

  /**
   * @return the {@code initialMaxStreamDataUnidirectional } parameter value
   * @see #setInitialMaxStreamDataUnidirectional(long)
   */
  public long getInitialMaxStreamDataUnidirectional() {
    return initialMaxStreamDataUnidirectional;
  }

  /**
   * <p>Sets the {@code initialMaxStreamDataUnidirectional} transport parameter.</p>
   *
   * <p>When set to a non-zero value it will only allow at most {@code initialMaxStreamDataUnidirectional} bytes of incoming
   * stream data to be buffered for each unidirectional stream (that is, data that is not yet read by the application) and
   * will allow more data to be received as the buffer is consumed by the application.</p>
   *
   * <p>The default value is {@code 0}.</p>
   *
   * @param initialMaxStreamDataUnidirectional the value to set
   * @return this instance
   */
  public QuicOptions setInitialMaxStreamDataUnidirectional(long initialMaxStreamDataUnidirectional) {
    if (initialMaxStreamDataUnidirectional < 0) {
      throw new IllegalArgumentException("initialMaxStreamDataUnidirectional must be >= 0");
    }
    this.initialMaxStreamDataUnidirectional = initialMaxStreamDataUnidirectional;
    return this;
  }

  /**
   * @return the {@code activeMigration } parameter value
   * @see #setActiveMigration(boolean)
   */
  public boolean getActiveMigration() {
    return activeMigration;
  }

  /**
   * <p>Set whether to allow active migration.</p>
   *
   * <p>The default value is {@code false}.</p>
   *
   * @param activeMigration the value to set
   * @return this instance
   */
  public QuicOptions setActiveMigration(boolean activeMigration) {
    this.activeMigration = activeMigration;
    return this;
  }

  /**
   * @return the {@code maxIdleTimeout } parameter value
   * @see #setMaxIdleTimeout(Duration)
   */
  public Duration getMaxIdleTimeout() {
    return maxIdleTimeout;
  }

  /**
   * <p>Sets the {@code maxIdleTimeout} transport parameter.</p>
   *
   * <p>The default value {@code null} means infinite, that is, no timeout is used.</p>
   *
   * @param maxIdleTimeout the value to set
   * @return this instance
   */
  public QuicOptions setMaxIdleTimeout(Duration maxIdleTimeout) {
    if (maxIdleTimeout != null && (maxIdleTimeout.isZero() || maxIdleTimeout.isNegative())) {
      throw new IllegalArgumentException("maxIdleTimeout must be > 0 or null (no timeout)");
    }
    this.maxIdleTimeout = maxIdleTimeout;
    return this;
  }

  /**
   * @return whether to support datagrams frames
   * @see #setEnableDatagrams(boolean)
   */
  public boolean isEnableDatagrams() {
    return enableDatagrams;
  }

  /**
   * <p>Set whether to support datagrams frames.</p>
   *
   * <p>The default value is {@code false} (disabled).</p>
   *
   * @param enableDatagrams the value to set
   * @return this instance
   */
  public QuicOptions setEnableDatagrams(boolean enableDatagrams) {
    this.enableDatagrams = enableDatagrams;
    return this;
  }

  /**
   * @return the datagram send queue length
   * @see #setDatagramSendQueueLength(int)
   */
  public int getDatagramSendQueueLength() {
    return datagramSendQueueLength;
  }

  /**
   * <p>Set the datagram receive queue length.</p>
   *
   * <p>The default value is {@code 128}.</p>
   *
   * @param datagramSendQueueLength the value to use
   * @return this instance
   */
  public QuicOptions setDatagramSendQueueLength(int datagramSendQueueLength) {
    if (datagramSendQueueLength <= 0) {
      throw new IllegalArgumentException("datagramSendQueueLength must be > 0");
    }
    this.datagramSendQueueLength = datagramSendQueueLength;
    return this;
  }

  /**
   * @return the datagram receive queue length
   * @see #setDatagramReceiveQueueLength(int)
   */
  public int getDatagramReceiveQueueLength() {
    return datagramReceiveQueueLength;
  }

  /**
   * <p>Set the datagram send queue length.</p>
   *
   * <p>The default value is {@code 128}.</p>
   *
   * @param datagramReceiveQueueLength the value to use
   * @return this instance
   */
  public QuicOptions setDatagramReceiveQueueLength(int datagramReceiveQueueLength) {
    if (datagramReceiveQueueLength <= 0) {
      throw new IllegalArgumentException("datagramReceiveQueueLength must be > 0");
    }
    this.datagramReceiveQueueLength = datagramReceiveQueueLength;
    return this;
  }

  /**
   * @return the {@code maxAckDelay } parameter value
   * @see #setMaxAckDelay(Duration)
   */
  public Duration getMaxAckDelay() {
    return maxAckDelay;
  }

  /**
   * <p>Sets the {@code maxAckDelay} transport parameter.</p>
   *
   * <p>The default value is {@code 25} (milliseconds).</p>
   *
   * @param maxAckDelay the value to set
   * @return this instance
   */
  public QuicOptions setMaxAckDelay(Duration maxAckDelay) {
    if (maxAckDelay == null || maxAckDelay.isZero() || maxAckDelay.isNegative()) {
      throw new IllegalArgumentException("maxAckDelay must be > 0");
    }
    this.maxAckDelay = maxAckDelay;
    return this;
  }

  /**
   * @return the {@code ackDelayExponent } parameter value
   * @see #setAckDelayExponent(int)
   */
  public int getAckDelayExponent() {
    return ackDelayExponent;
  }

  /**
   * <p>Sets the {@code ackDelayExponent} transport parameter.</p>
   *
   * <p>The default value is {@code 3}.</p>
   *
   * @param ackDelayExponent the value to set
   * @return this instance
   */
  public QuicOptions setAckDelayExponent(int ackDelayExponent) {
    if (ackDelayExponent <= 0) {
      throw new IllegalArgumentException("ackDelayExponent must be > 0");
    }
    this.ackDelayExponent = ackDelayExponent;
    return this;
  }

  /**
   * @return the {@code congestionControlAlgorithm } parameter value
   * @see #setCongestionControlAlgorithm(QuicCongestionControlAlgorithm)
   */
  public QuicCongestionControlAlgorithm getCongestionControlAlgorithm() {
    return congestionControlAlgorithm;
  }

  /**
   * <p>Sets the congestion control algorithm used.</p>
   *
   * <p>The default value is {@link QuicCongestionControlAlgorithm#CUBIC}.</p>
   *
   * @param congestionControlAlgorithm the congestion control algorithm to use
   * @return this instance
   */
  public QuicOptions setCongestionControlAlgorithm(QuicCongestionControlAlgorithm congestionControlAlgorithm) {
    if (congestionControlAlgorithm == null) {
      throw new IllegalArgumentException("congestionControlAlgorithm must be not null");
    }
    this.congestionControlAlgorithm = congestionControlAlgorithm;
    return this;
  }

  /**
   * @return whether to send GREASE values
   * @see #setGrease(boolean)
   */
  public boolean getGrease() {
    return grease;
  }

  /**
   * <p>Configures whether to send GREASE values.</p>
   *
   * <p>The default value is {@code true}</p>
   *
   * @param grease whether to send GREASE values.
   * @return this instance
   */
  public QuicOptions setGrease(boolean grease) {
    this.grease = grease;
    return this;
  }

  /**
   * @return whether to enable HyStart++
   * @see #setHystart(boolean)
   */
  public boolean getHystart() {
    return hystart;
  }

  /**
   * <p>Configures whether to enable HyStart++.</p>
   *
   * <p>The default value is {@code true}.</p>
   *
   * @param hystart whether to enable HyStart++.
   * @return this instance
   */
  public QuicOptions setHystart(boolean hystart) {
    this.hystart = hystart;
    return this;
  }

  /**
   * @return the initial congestion window size in terms of packet count
   * @see #setInitialCongestionWindowPackets(int)
   */
  public int getInitialCongestionWindowPackets() {
    return initialCongestionWindowPackets;
  }

  /**
   * <p>Sets initial congestion window size in terms of packet count.</p>
   *
   * <p>The default value is {@code 10}.</p>
   *
   * @param initialCongestionWindowPackets the value to set
   * @return this instance
   */
  public QuicOptions setInitialCongestionWindowPackets(int initialCongestionWindowPackets) {
    if (initialCongestionWindowPackets <= 0) {
      throw new IllegalArgumentException("initialCongestionWindowPackets must be > 0");
    }
    this.initialCongestionWindowPackets = initialCongestionWindowPackets;
    return this;
  }
}
