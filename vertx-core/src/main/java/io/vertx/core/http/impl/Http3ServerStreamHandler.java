/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.StreamPriorityBase;

interface Http3ServerStreamHandler {

  Http3ServerResponse response();

  void dispatch(Handler<HttpServerRequest> handler);

  void handleReset(long errorCode);

  void handleException(Throwable cause);

  void handleClose();

  default void handleData(Buffer data) {
  }

  default void handleEnd(MultiMap trailers) {
  }

  default void handleCustomFrame(HttpFrame frame) {
  }

  default void handlePriorityChange(StreamPriorityBase streamPriority) {
  }

  default void onException(Throwable t) {
  }

  default void onClose() {
  }
}
