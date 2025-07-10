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
package io.vertx.core.http.impl.http2;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.StreamPriorityBase;

public interface Http2StreamHandler {

  void handleHeaders(Http2HeadersMultiMap head);
  void handleReset(long errorCode);
  void handleException(Throwable cause);
  void handleClose();
  void handleData(Buffer data);
  void handleTrailers(MultiMap trailers);
  void handleCustomFrame(HttpFrame frame);
  void handlePriorityChange(StreamPriorityBase streamPriority);
  void handleDrained();

}
