/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * A {@literal GOAWAY} frame.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true, publicConverter = false)
public class GoAway {

  private long errorCode;
  private int lastStreamId;
  private Buffer debugData;

  public GoAway() {
  }

  public GoAway(JsonObject json) {
    GoAwayConverter.fromJson(json, this);
  }

  public GoAway(GoAway that) {
    errorCode = that.errorCode;
    lastStreamId = that.lastStreamId;
    debugData = that.debugData != null ? that.debugData.copy() : null;
  }

  /**
   * @return the {@literal GOAWAY} error code
   */
  public long getErrorCode() {
    return errorCode;
  }

  public GoAway setErrorCode(long errorCode) {
    this.errorCode = errorCode;
    return this;
  }

  /**
   * @return the highest numbered stream identifier for which the sender of the frame might have taken some
   *         action on or might yet take action
   */
  public int getLastStreamId() {
    return lastStreamId;
  }

  /**
   * Set the last stream id.
   *
   * @param lastStreamId the last stream id
   * @return a reference to this, so the API can be used fluently
   */
  public GoAway setLastStreamId(int lastStreamId) {
    this.lastStreamId = lastStreamId;
    return this;
  }

  /**
   * @return additional debug data
   */
  public Buffer getDebugData() {
    return debugData;
  }

  /**
   * Set the additional debug data
   *
   * @param debugData the data
   * @return a reference to this, so the API can be used fluently
   */
  public GoAway setDebugData(Buffer debugData) {
    this.debugData = debugData;
    return this;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    GoAwayConverter.toJson(this, json);
    return json;
  }
}
