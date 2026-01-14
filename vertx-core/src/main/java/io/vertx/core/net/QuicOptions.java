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
  public static final boolean DEFAULT_DISABLE_ACTIVE_MIGRATION = true;
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
  private long initialMaxStreamDataBidiLocal = DEFAULT_MAX_STREAM_DATA_BIDI_LOCAL;
  private long initialMaxStreamDataBidiRemote = DEFAULT_MAX_STREAM_DATA_BIDI_REMOTE;
  private long initialMaxStreamDataUni = DEFAULT_MAX_STREAMS_DATA_UNI;
  private long initialMaxStreamsBidi = DEFAULT_MAX_STREAMS_DATA_BIDI;
  private long initialMaxStreamsUni = DEFAULT_MAX_STREAM_DATA_UNI;
  private boolean disableActiveMigration = DEFAULT_DISABLE_ACTIVE_MIGRATION;
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
    this.initialMaxStreamDataBidiLocal = other.initialMaxStreamDataBidiLocal;
    this.initialMaxStreamDataBidiRemote = other.initialMaxStreamDataBidiRemote;
    this.initialMaxStreamsBidi = other.initialMaxStreamsBidi;
    this.initialMaxStreamsUni = other.initialMaxStreamsUni;
    this.initialMaxStreamDataUni = other.initialMaxStreamDataUni;
    this.disableActiveMigration = other.disableActiveMigration;
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
   * @return the {@code initialMaxStreamDataBidiLocal } parameter value
   * @see #setInitialMaxStreamDataBidiLocal(long)
   */
  public long getInitialMaxStreamDataBidiLocal() {
    return initialMaxStreamDataBidiLocal;
  }

  /**
   * <p>Set the {@code initialMaxStreamDataBidiLocal} transport parameter.</p>
   *
   * <p>When set to a non-zero value it will only allow at most {@code initialMaxStreamDataBidiLocal} bytes of incoming stream data
   * to be buffered for each locally-initiated bidirectional stream (that is, data that is not yet read by the application) and will
   * allow more data to be received as the buffer is consumed by the application.</p>
   *
   * <p>The default value is {@code 0}.</p>
   *
   * @param initialMaxStreamDataBidiLocal the value to set
   * @return this instance
   */
  public QuicOptions setInitialMaxStreamDataBidiLocal(long initialMaxStreamDataBidiLocal) {
    if (initialMaxStreamDataBidiLocal < 0) {
      throw new IllegalArgumentException("initialMaxStreamDataBidilLocal must be >= 0");
    }
    this.initialMaxStreamDataBidiLocal = initialMaxStreamDataBidiLocal;
    return this;
  }

  /**
   * @return the {@code initialMaxStreamDataBidiRemote } parameter value
   * @see #setInitialMaxStreamDataBidiRemote(long)
   */
  public long getInitialMaxStreamDataBidiRemote() {
    return initialMaxStreamDataBidiRemote;
  }

  /**
   * <p>Set the {@code initialMaxStreamDataBidiRemote} transport parameter.</p>
   *
   * <p>When set to a non-zero value it will only allow at most {@code initialMaxStreamDataBidiRemote} bytes of incoming
   * stream data to be buffered for each remotely-initiated bidirectional stream (that is, data that is not yet read by the application)
   * and will allow more data to be received as the buffer is consumed by the application.</p>
   *
   * <p>The default value is {@code 0}.</p>
   *
   * @param initialMaxStreamDataBidiRemote the value to set
   * @return this instance
   */
  public QuicOptions setInitialMaxStreamDataBidiRemote(long initialMaxStreamDataBidiRemote) {
    if (initialMaxStreamDataBidiRemote < 0) {
      throw new IllegalArgumentException("initialMaxStreamDataBidiRemote must be >= 0");
    }
    this.initialMaxStreamDataBidiRemote = initialMaxStreamDataBidiRemote;
    return this;
  }

  /**
   * @return the {@code initialMaxStreamsBidi } parameter value
   * @see #setInitialMaxStreamsBidi(long)
   */
  public long getInitialMaxStreamsBidi() {
    return initialMaxStreamsBidi;
  }

  /**
   * <p>Set the {@code setInitialMaxStreamsBidi} transport parameter.</p>
   *
   * <p>When set to a non-zero value it will only allow {@code initialMaxStreamsBidi} number of concurrent
   * remotely-initiated bidirectional streams to be open at any given time and will increase the limit
   * automatically as streams are completed.</p>
   *
   * <p>A bidirectional stream is considered completed when all incoming data has been read by the application (up to the fin offset)
   * or the stream's read direction has been shutdown, and all outgoing data has been acked by the peer (up to the fin offset)
   * or the stream's write direction has been shutdown.</p>
   *
   * <p>The default value is {@code 0}.</p>
   *
   * @param initialMaxStreamsBidi the value to set
   * @return this instance
   */
  public QuicOptions setInitialMaxStreamsBidi(long initialMaxStreamsBidi) {
    if (initialMaxStreamsBidi < 0) {
      throw new IllegalArgumentException("initialMaxStreamsBidi must be >= 0");
    }
    this.initialMaxStreamsBidi = initialMaxStreamsBidi;
    return this;
  }

  /**
   * @return the {@code initialMaxStreamsUni } parameter value
   * @see #setInitialMaxStreamsUni(long)
   */
  public long getInitialMaxStreamsUni() {
    return initialMaxStreamsUni;
  }

  /**
   * <p>Sets the {@code initialMaxStreamsUni} transport parameter.</p>
   *
   * <p>When set to a non-zero value it will only allow {@code initialMaxStreamsUni} number of concurrent
   * remotely-initiated unidirectional streams to be open at any given time and will increase the limit automatically
   * as streams are completed.</p>
   *
   * <p>A unidirectional stream is considered completed when all incoming data has been read by the application (up to the fin offset)
   * or the stream's read direction has been shutdown.</p>
   *
   * <p>The default value is {@code 0}.</p>
   *
   * @param initialMaxStreamsUni the value to set
   * @return this instance
   */
  public QuicOptions setInitialMaxStreamsUni(long initialMaxStreamsUni) {
    if (initialMaxStreamsUni < 0) {
      throw new IllegalArgumentException("initialMaxStreamsUni must be >= 0");
    }
    this.initialMaxStreamsUni = initialMaxStreamsUni;
    return this;
  }

  /**
   * @return the {@code initialMaxStreamDataUni } parameter value
   * @see #setInitialMaxStreamDataUni(long)
   */
  public long getInitialMaxStreamDataUni() {
    return initialMaxStreamDataUni;
  }

  /**
   * <p>Sets the {@code initialMaxStreamDataUni} transport parameter.</p>
   *
   * <p>When set to a non-zero value it will only allow at most {@code initialMaxStreamDataUni} bytes of incoming
   * stream data to be buffered for each unidirectional stream (that is, data that is not yet read by the application) and
   * will allow more data to be received as the buffer is consumed by the application.</p>
   *
   * <p>The default value is {@code 0}.</p>
   *
   * @param initialMaxStreamDataUni the value to set
   * @return this instance
   */
  public QuicOptions setInitialMaxStreamDataUni(long initialMaxStreamDataUni) {
    if (initialMaxStreamDataUni < 0) {
      throw new IllegalArgumentException("initialMaxStreamDataUni must be >= 0");
    }
    this.initialMaxStreamDataUni = initialMaxStreamDataUni;
    return this;
  }

  /**
   * @return the {@code activeMigration } parameter value
   * @see #setDisableActiveMigration(boolean)
   */
  public boolean getDisableActiveMigration() {
    return disableActiveMigration;
  }

  /**
   * <p>Set whether to allow active migration.</p>
   *
   * <p>The default value is {@code false}.</p>
   *
   * @param disableActiveMigration the value to set
   * @return this instance
   */
  public QuicOptions setDisableActiveMigration(boolean disableActiveMigration) {
    this.disableActiveMigration = disableActiveMigration;
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
