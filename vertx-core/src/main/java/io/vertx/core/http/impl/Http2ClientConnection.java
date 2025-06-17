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
package io.vertx.core.http.impl;

import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;

public interface Http2ClientConnection extends Http2Connection {

  void createStream(VertxHttp2Stream vertxStream, HttpRequestHead head, Http2HeadersAdaptor headers) throws Exception;

}
