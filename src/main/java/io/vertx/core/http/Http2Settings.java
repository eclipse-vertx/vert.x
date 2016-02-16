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
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;

/**
 * HTTP2 settings.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true)
public class Http2Settings {

  private Integer headerTableSize;
  private Boolean enablePush;
  private Long maxConcurrentStreams;
  private Integer initialWindowSize;
  private Integer maxFrameSize;
  private Integer maxHeaderListSize;

  public Http2Settings() {
  }

  public Http2Settings(JsonObject json) {
    Http2SettingsConverter.fromJson(json, this);
  }

  public Http2Settings(Http2Settings that) {
    headerTableSize = that.headerTableSize;
    enablePush = that.enablePush;
    maxConcurrentStreams = that.maxConcurrentStreams;
    initialWindowSize = that.initialWindowSize;
    maxFrameSize = that.maxFrameSize;
    maxHeaderListSize = that.maxHeaderListSize;
  }

  public Integer getHeaderTableSize() {
    return headerTableSize;
  }

  public Http2Settings setHeaderTableSize(Integer headerTableSize) {
    Arguments.require(headerTableSize == null || headerTableSize >= Http2CodecUtil.MIN_HEADER_TABLE_SIZE,
        "headerTableSize must be >= " + Http2CodecUtil.MIN_HEADER_TABLE_SIZE);
    Arguments.require(headerTableSize == null || headerTableSize < Http2CodecUtil.MAX_HEADER_TABLE_SIZE,
        "headerTableSize must be <= " + Http2CodecUtil.MAX_HEADER_TABLE_SIZE);
    this.headerTableSize = headerTableSize;
    return this;
  }

  public Boolean getEnablePush() {
    return enablePush;
  }

  public Http2Settings setEnablePush(Boolean enablePush) {
    this.enablePush = enablePush;
    return this;
  }

  public Long getMaxConcurrentStreams() {
    return maxConcurrentStreams;
  }

  public Http2Settings setMaxConcurrentStreams(Long maxConcurrentStreams) {
    Arguments.require(maxConcurrentStreams == null || maxConcurrentStreams >= Http2CodecUtil.MIN_CONCURRENT_STREAMS,
        "maxConcurrentStreams must be >= " + Http2CodecUtil.MIN_CONCURRENT_STREAMS);
    Arguments.require(maxConcurrentStreams == null || maxConcurrentStreams <= Http2CodecUtil.MAX_CONCURRENT_STREAMS,
        "maxConcurrentStreams must be < " + Http2CodecUtil.MAX_CONCURRENT_STREAMS);
    this.maxConcurrentStreams = maxConcurrentStreams;
    return this;
  }

  public Integer getInitialWindowSize() {
    return initialWindowSize;
  }

  public Http2Settings setInitialWindowSize(Integer initialWindowSize) {
    Arguments.require(initialWindowSize == null || initialWindowSize >= Http2CodecUtil.MIN_INITIAL_WINDOW_SIZE,
        "initialWindowSize must be >= " + Http2CodecUtil.MIN_INITIAL_WINDOW_SIZE);
    Arguments.require(initialWindowSize == null || initialWindowSize < Http2CodecUtil.MAX_INITIAL_WINDOW_SIZE,
        "initialWindowSize must be < " + Http2CodecUtil.MAX_INITIAL_WINDOW_SIZE);
    this.initialWindowSize = initialWindowSize;
    return this;
  }

  public Integer getMaxFrameSize() {
    return maxFrameSize;
  }

  public Http2Settings setMaxFrameSize(Integer maxFrameSize) {
    Arguments.require(maxFrameSize == null || maxFrameSize >= Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND,
        "maxFrameSize must be >= " + Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND);
    Arguments.require(maxFrameSize == null || maxFrameSize <= Http2CodecUtil.MAX_FRAME_SIZE_UPPER_BOUND,
        "maxFrameSize must be <= " + Http2CodecUtil.MAX_FRAME_SIZE_UPPER_BOUND);
    this.maxFrameSize = maxFrameSize;
    return this;
  }

  public Integer getMaxHeaderListSize() {
    return maxHeaderListSize;
  }

  public Http2Settings setMaxHeaderListSize(Integer maxHeaderListSize) {
    Arguments.require(maxHeaderListSize == null || maxHeaderListSize >= Http2CodecUtil.MIN_HEADER_LIST_SIZE,
        "maxHeaderListSize must be >= " + Http2CodecUtil.MIN_HEADER_LIST_SIZE);
    Arguments.require(maxHeaderListSize == null || maxHeaderListSize <= Http2CodecUtil.MAX_HEADER_LIST_SIZE,
        "maxHeaderListSize must be <= " + Http2CodecUtil.MAX_HEADER_LIST_SIZE);
    this.maxHeaderListSize = maxHeaderListSize;
    return this;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    Http2SettingsConverter.toJson(this, json);
    return json;
  }
}
