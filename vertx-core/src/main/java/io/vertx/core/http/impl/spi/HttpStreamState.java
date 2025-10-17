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
package io.vertx.core.http.impl.spi;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.internal.ContextInternal;

/**
 * The stream from the provider perspective.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpStreamState {

  int id();

  ContextInternal context();

  boolean isHeadersReceived();

  void init(int id, boolean writable);

  void priority(StreamPriority streamPriority);

  void onPriorityChange(StreamPriority streamPriority);
  void onHeaders(Http2HeadersMultiMap headers);
  void onData(Buffer buffer);
  void onCustomFrame(int type, int flags, Buffer payload);
  void onTrailers();
  void onTrailers(Http2HeadersMultiMap trailers);
  void onReset(long code);
  void onWritabilityChanged();
  void onException(Throwable err);
  void onClose();

}
