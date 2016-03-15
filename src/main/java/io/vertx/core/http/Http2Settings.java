/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http;

import io.netty.handler.codec.http2.Http2CodecUtil;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP2 settings.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true)
public class Http2Settings {

  public static final long DEFAULT_HEADER_TABLE_SIZE = 4096;
  public static final boolean DEFAULT_ENABLE_PUSH = true;
  public static final long DEFAULT_MAX_CONCURRENT_STREAMS = 0xFFFFFFFFL;
  public static final int DEFAULT_INITIAL_WINDOW_SIZE = 65535;
  public static final int DEFAULT_MAX_FRAME_SIZE = 16384;
  public static final int DEFAULT_MAX_HEADER_LIST_SIZE = Integer.MAX_VALUE;
  public static final Map<Integer, Long> DEFAULT_EXTRA_SETTINGS = null;

  private long headerTableSize;
  private boolean enablePush;
  private long maxConcurrentStreams;
  private int initialWindowSize;
  private int maxFrameSize;
  private int maxHeaderListSize;
  private Map<Integer, Long> extraSettings;

  public Http2Settings() {
    headerTableSize = DEFAULT_HEADER_TABLE_SIZE;
    enablePush = DEFAULT_ENABLE_PUSH;
    maxConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;
    initialWindowSize = DEFAULT_INITIAL_WINDOW_SIZE;
    maxFrameSize = DEFAULT_MAX_FRAME_SIZE;
    maxHeaderListSize = DEFAULT_MAX_HEADER_LIST_SIZE;
    extraSettings = DEFAULT_EXTRA_SETTINGS;
  }

  public Http2Settings(JsonObject json) {
    this();
    Http2SettingsConverter.fromJson(json, this);
  }

  public Http2Settings(Http2Settings that) {
    headerTableSize = that.headerTableSize;
    enablePush = that.enablePush;
    maxConcurrentStreams = that.maxConcurrentStreams;
    initialWindowSize = that.initialWindowSize;
    maxFrameSize = that.maxFrameSize;
    maxHeaderListSize = that.maxHeaderListSize;
    extraSettings = that.extraSettings != null ? new HashMap<>(that.extraSettings) : null;
  }

  public long getHeaderTableSize() {
    return headerTableSize;
  }

  public Http2Settings setHeaderTableSize(long headerTableSize) {
    Arguments.require(headerTableSize >= Http2CodecUtil.MIN_HEADER_TABLE_SIZE,
        "headerTableSize must be >= " + Http2CodecUtil.MIN_HEADER_TABLE_SIZE);
    Arguments.require(headerTableSize <= Http2CodecUtil.MAX_HEADER_TABLE_SIZE,
        "headerTableSize must be <= " + Http2CodecUtil.MAX_HEADER_TABLE_SIZE);
    this.headerTableSize = headerTableSize;
    return this;
  }

  public boolean getEnablePush() {
    return enablePush;
  }

  public Http2Settings setEnablePush(boolean enablePush) {
    this.enablePush = enablePush;
    return this;
  }

  public long getMaxConcurrentStreams() {
    return maxConcurrentStreams;
  }

  public Http2Settings setMaxConcurrentStreams(long maxConcurrentStreams) {
    Arguments.require(maxConcurrentStreams >= Http2CodecUtil.MIN_CONCURRENT_STREAMS,
        "maxConcurrentStreams must be >= " + Http2CodecUtil.MIN_CONCURRENT_STREAMS);
    Arguments.require(maxConcurrentStreams <= Http2CodecUtil.MAX_CONCURRENT_STREAMS,
        "maxConcurrentStreams must be < " + Http2CodecUtil.MAX_CONCURRENT_STREAMS);
    this.maxConcurrentStreams = maxConcurrentStreams;
    return this;
  }

  public int getInitialWindowSize() {
    return initialWindowSize;
  }

  public Http2Settings setInitialWindowSize(int initialWindowSize) {
    Arguments.require(initialWindowSize >= Http2CodecUtil.MIN_INITIAL_WINDOW_SIZE,
        "initialWindowSize must be >= " + Http2CodecUtil.MIN_INITIAL_WINDOW_SIZE);
//    Arguments.require(initialWindowSize <= Http2CodecUtil.MAX_INITIAL_WINDOW_SIZE,
//        "initialWindowSize must be < " + Http2CodecUtil.MAX_INITIAL_WINDOW_SIZE);
    this.initialWindowSize = initialWindowSize;
    return this;
  }

  public int getMaxFrameSize() {
    return maxFrameSize;
  }

  public Http2Settings setMaxFrameSize(int maxFrameSize) {
    Arguments.require(maxFrameSize >= Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND,
        "maxFrameSize must be >= " + Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND);
    Arguments.require(maxFrameSize <= Http2CodecUtil.MAX_FRAME_SIZE_UPPER_BOUND,
        "maxFrameSize must be <= " + Http2CodecUtil.MAX_FRAME_SIZE_UPPER_BOUND);
    this.maxFrameSize = maxFrameSize;
    return this;
  }

  public int getMaxHeaderListSize() {
    return maxHeaderListSize;
  }

  public Http2Settings setMaxHeaderListSize(int maxHeaderListSize) {
    Arguments.require(maxHeaderListSize >= Http2CodecUtil.MIN_HEADER_LIST_SIZE,
        "maxHeaderListSize must be >= " + Http2CodecUtil.MIN_HEADER_LIST_SIZE);
    this.maxHeaderListSize = maxHeaderListSize;
    return this;
  }

  @GenIgnore
  public Map<Integer, Long> getExtraSettings() {
    return extraSettings;
  }

  @GenIgnore
  public Http2Settings setExtraSettings(Map<Integer, Long> extraSettings) {
    this.extraSettings = extraSettings;
    return this;
  }

  public Long get(int code) {
    switch (code) {
      case 1:
        return (long)headerTableSize;
      case 2:
        return enablePush ? 1L : 0L;
      case 3:
        return maxConcurrentStreams;
      case 4:
        return (long)initialWindowSize;
      case 5:
        return (long)maxFrameSize;
      case 6:
        return (long)maxHeaderListSize;
      default:
        return extraSettings != null ? extraSettings.get(code) : null;
    }
  }

  public Http2Settings put(int code, long setting) {
    Arguments.require(code >= 0 || code <= 0xFFFF, "Setting code must me an unsigned 16-bit value");
    Arguments.require(setting >= 0 || setting <= 0xFFFFFFFF, "Setting value must me an unsigned 32-bit value");
    switch (code) {
      case 1:
        setHeaderTableSize(setting);
        break;
      case 2:
        Arguments.require(setting == 0 || setting == 1, "enablePush must be 0 or 1");
        setEnablePush(setting == 1);
        break;
      case 3:
        setMaxConcurrentStreams(setting);
        break;
      case 4:
        setInitialWindowSize((int) setting);
        break;
      case 5:
        setMaxFrameSize((int) setting);
        break;
      case 6:
        Arguments.require(setting <= Integer.MAX_VALUE, "maxHeaderListSize must be <= " + Integer.MAX_VALUE);
        setMaxHeaderListSize((int) setting);
        break;
      default:
        if (extraSettings == null) {
          extraSettings = new HashMap<>();
        }
        extraSettings.put(code, setting);
    }
    return this;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    Http2SettingsConverter.toJson(this, json);
    return json;
  }
}
