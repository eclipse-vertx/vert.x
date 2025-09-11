/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net;

import io.netty.util.internal.ObjectUtil;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

import java.time.Duration;
import java.util.Objects;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class QuicOptions {

  /**
   * Default use initialMaxStreamsBidirectional = 100
   */
  public static final long DEFAULT_HTTP3_INITIAL_MAX_STREAMS_BIDIRECTIONAL = 100;

  /**
   * Default use http3InitialMaxStreamsUnidirectional = 100
   */
  public static final long DEFAULT_HTTP3_INITIAL_MAX_STREAMS_UNIDIRECTIONAL = 100;

  /**
   * Default use http3InitialMaxData = 2,097,152 ~ 2MB
   */
  public static final long DEFAULT_HTTP3_INITIAL_MAX_DATA = 2_097_152;

  /**
   * Default use http3InitialMaxStreamDataBidirectionalLocal = 262,144 ~ 256 KB
   */
  public static final long DEFAULT_HTTP3_INITIAL_MAX_STREAM_DATA_BIDIRECTIONAL_LOCAL = 262_144;

  /**
   * Default use http3InitialMaxStreamDataBidirectionalRemote = 262,144 ~ 256 KB
   */
  public static final long DEFAULT_HTTP3_INITIAL_MAX_STREAM_DATA_BIDIRECTIONAL_REMOTE = 262_144;

  /**
   * Default use http3InitialMaxStreamDataUnidirectional = 131,072 ~ 128KB
   */
  public static final long DEFAULT_HTTP3_INITIAL_MAX_STREAM_DATA_UNIDIRECTIONAL = 131_072;

  public static final Duration MAX_SSL_HANDSHAKE_TIMEOUT = Duration.ofDays(1);

  private long http3InitialMaxStreamsBidirectional;
  private long http3InitialMaxData;
  private long http3InitialMaxStreamDataBidirectionalLocal;
  private long http3InitialMaxStreamDataBidirectionalRemote;
  private long http3InitialMaxStreamDataUnidirectional;
  private long http3InitialMaxStreamsUnidirectional;

  /**
   * Default constructor
   */
  public QuicOptions() {
    init();
  }

  /**
   * Copy constructor
   *
   * @param other the options to copy
   */
  public QuicOptions(QuicOptions other) {
    this.http3InitialMaxStreamsBidirectional = other.http3InitialMaxStreamsBidirectional;
    this.http3InitialMaxData = other.http3InitialMaxData;
    this.http3InitialMaxStreamDataBidirectionalLocal = other.http3InitialMaxStreamDataBidirectionalLocal;
    this.http3InitialMaxStreamDataBidirectionalRemote = other.http3InitialMaxStreamDataBidirectionalRemote;
    this.http3InitialMaxStreamDataUnidirectional = other.http3InitialMaxStreamDataUnidirectional;
    this.http3InitialMaxStreamsUnidirectional = other.http3InitialMaxStreamsUnidirectional;
  }

  /**
   * Create options from JSON
   *
   * @param json the JSON
   */
  public QuicOptions(JsonObject json) {
    this();
    QuicOptionsConverter.fromJson(json, this);
  }

  protected void init() {
    http3InitialMaxStreamsBidirectional = DEFAULT_HTTP3_INITIAL_MAX_STREAMS_BIDIRECTIONAL;
    http3InitialMaxData = DEFAULT_HTTP3_INITIAL_MAX_DATA;
    http3InitialMaxStreamDataBidirectionalLocal = DEFAULT_HTTP3_INITIAL_MAX_STREAM_DATA_BIDIRECTIONAL_LOCAL;
    http3InitialMaxStreamDataBidirectionalRemote = DEFAULT_HTTP3_INITIAL_MAX_STREAM_DATA_BIDIRECTIONAL_REMOTE;
    http3InitialMaxStreamDataUnidirectional = DEFAULT_HTTP3_INITIAL_MAX_STREAM_DATA_UNIDIRECTIONAL;
    http3InitialMaxStreamsUnidirectional = DEFAULT_HTTP3_INITIAL_MAX_STREAMS_UNIDIRECTIONAL;
  }

  public QuicOptions copy() {
    return new QuicOptions(this);
  }

  /**
   * @return get HTTP/3 initial max streams bidirectional count
   */
  public long getHttp3InitialMaxStreamsBidirectional() {
    return http3InitialMaxStreamsBidirectional;
  }

  /**
   * Set the HTTP/3 initial max streams bidirectional count.
   *
   * @param http3InitialMaxStreamsBidirectional the HTTP/3 initial max streams bidirectional count
   */
  public QuicOptions setHttp3InitialMaxStreamsBidirectional(long http3InitialMaxStreamsBidirectional) {
    ObjectUtil.checkPositive(http3InitialMaxStreamsBidirectional, "http3InitialMaxStreamsBidirectional");
    this.http3InitialMaxStreamsBidirectional = http3InitialMaxStreamsBidirectional;
    return this;
  }

  /**
   * @return get HTTP/3 initial max data
   */
  public long getHttp3InitialMaxData() {
    return http3InitialMaxData;
  }

  /**
   * Set the HTTP/3 Initial Max Data .
   *
   * @param http3InitialMaxData HTTP/3 initial max data
   */
  public QuicOptions setHttp3InitialMaxData(long http3InitialMaxData) {
    this.http3InitialMaxData = http3InitialMaxData;
    return this;
  }

  /**
   * @return get HTTP/3 initial max stream data bidirectional local
   */
  public long getHttp3InitialMaxStreamDataBidirectionalLocal() {
    return http3InitialMaxStreamDataBidirectionalLocal;
  }

  /**
   * Set the HTTP/3 initial max stream data bidirectional local.
   *
   * @param http3InitialMaxStreamDataBidirectionalLocal HTTP/3 initial max stream data bidirectional local
   */
  public QuicOptions setHttp3InitialMaxStreamDataBidirectionalLocal(long http3InitialMaxStreamDataBidirectionalLocal) {
    ObjectUtil.checkPositive(http3InitialMaxStreamDataBidirectionalLocal, "http3InitialMaxStreamDataBidirectionalLocal");
    this.http3InitialMaxStreamDataBidirectionalLocal = http3InitialMaxStreamDataBidirectionalLocal;
    return this;
  }

  /**
   * @return get HTTP/3 initial max stream data bidirectional remote
   */
  public long getHttp3InitialMaxStreamDataBidirectionalRemote() {
    return http3InitialMaxStreamDataBidirectionalRemote;
  }

  /**
   * Set the HTTP/3 initial max stream data bidirectional remote.
   *
   * @param http3InitialMaxStreamDataBidirectionalRemote http/3 initial max stream data bidirectional remote
   */
  public QuicOptions setHttp3InitialMaxStreamDataBidirectionalRemote(long http3InitialMaxStreamDataBidirectionalRemote) {
    this.http3InitialMaxStreamDataBidirectionalRemote = http3InitialMaxStreamDataBidirectionalRemote;
    return this;
  }

  /**
   * @return get HTTP/3 initial max stream data unidirectional
   */
  public long getHttp3InitialMaxStreamDataUnidirectional() {
    return http3InitialMaxStreamDataUnidirectional;
  }

  /**
   * Set the HTTP/3 initial max stream data unidirectional.
   *
   * @param http3InitialMaxStreamDataUnidirectional HTTP/3 initial max stream data unidirectional
   */
  public QuicOptions setHttp3InitialMaxStreamDataUnidirectional(long http3InitialMaxStreamDataUnidirectional) {
    this.http3InitialMaxStreamDataUnidirectional = http3InitialMaxStreamDataUnidirectional;
    return this;
  }

  /**
   * @return get HTTP/3 initial max streams unidirectional
   */
  public long getHttp3InitialMaxStreamsUnidirectional() {
    return http3InitialMaxStreamsUnidirectional;
  }

  /**
   * Set the HTTP/3 initial max streams unidirectional.
   *
   * @param http3InitialMaxStreamsUnidirectional http/3 initial max streams unidirectional
   */
  public QuicOptions setHttp3InitialMaxStreamsUnidirectional(long http3InitialMaxStreamsUnidirectional) {
    this.http3InitialMaxStreamsUnidirectional = http3InitialMaxStreamsUnidirectional;
    return this;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof QuicOptions) {
      QuicOptions that = (QuicOptions) obj;
      return Objects.equals(http3InitialMaxStreamsBidirectional, that.http3InitialMaxStreamsBidirectional) &&
        Objects.equals(http3InitialMaxData, that.http3InitialMaxData) &&
        Objects.equals(http3InitialMaxStreamDataBidirectionalLocal, that.http3InitialMaxStreamDataBidirectionalLocal) &&
        Objects.equals(http3InitialMaxStreamDataBidirectionalRemote, that.http3InitialMaxStreamDataBidirectionalRemote) &&
        Objects.equals(http3InitialMaxStreamDataUnidirectional, that.http3InitialMaxStreamDataUnidirectional) &&
        Objects.equals(http3InitialMaxStreamsUnidirectional, that.http3InitialMaxStreamsUnidirectional);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(http3InitialMaxStreamsBidirectional, http3InitialMaxData, http3InitialMaxStreamDataBidirectionalLocal, http3InitialMaxStreamDataBidirectionalRemote, http3InitialMaxStreamDataUnidirectional, http3InitialMaxStreamsUnidirectional);
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    QuicOptionsConverter.toJson(this, json);
    return json;
  }
}
