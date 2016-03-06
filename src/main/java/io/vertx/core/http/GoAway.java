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

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class GoAway {

  private long errorCode;
  private int lastStreamId;
  private Buffer debugData;

  public GoAway() {
  }

  public GoAway(JsonObject json) {
  }

  public GoAway(GoAway that) {
    errorCode = that.errorCode;
    lastStreamId = that.lastStreamId;
    debugData = that.debugData != null ? that.debugData.copy() : null;
  }

  public long getErrorCode() {
    return errorCode;
  }

  public GoAway setErrorCode(long errorCode) {
    this.errorCode = errorCode;
    return this;
  }

  public int getLastStreamId() {
    return lastStreamId;
  }

  public GoAway setLastStreamId(int lastStreamId) {
    this.lastStreamId = lastStreamId;
    return this;
  }

  public Buffer getDebugData() {
    return debugData;
  }

  public GoAway setDebugData(Buffer debugData) {
    this.debugData = debugData;
    return this;
  }

  public JsonObject toJson() {
    throw new UnsupportedOperationException();
  }
}
