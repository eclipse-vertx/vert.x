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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.headers.HttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface Http2Stream {

  long id();
  String scheme();
  HostAndPort authority();

  ContextInternal context();

  boolean isHeadersReceived();

  void init(int id, boolean writable);

  void priority(StreamPriority streamPriority);

  void onPriorityChange(StreamPriority streamPriority);
  void onHeaders(HttpHeaders headers);
  void onData(Buffer buffer);
  void onCustomFrame(int type, int flags, Buffer payload);
  void onTrailers();
  void onTrailers(HttpHeaders trailers);
  void onReset(long code);
  void onWritabilityChanged();
  void onException(Throwable err);
  void onClose(GoAway goAway);

}
