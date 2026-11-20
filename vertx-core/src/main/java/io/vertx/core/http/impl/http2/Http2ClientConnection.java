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

import io.vertx.core.http.impl.HttpClientConnection;

public interface Http2ClientConnection extends Http2Connection, HttpClientConnection {

  /**
   * Create the stream on this connection, this must be called from the connection event loop.
   *
   * <p>When the stream cannot be created, the stream is closed before this method returns.</p>
   *
   * @param stream the stream to create
   * @return {@code null} when the stream is created, otherwise the cause of the failure
   */
  Throwable createStream(Http2ClientStream stream);

}
