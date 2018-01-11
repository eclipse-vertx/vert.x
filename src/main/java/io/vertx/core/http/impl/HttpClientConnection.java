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

package io.vertx.core.http.impl;

import io.netty.channel.Channel;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.impl.ContextImpl;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
interface HttpClientConnection extends HttpConnection {

  Channel channel();

  void reportBytesWritten(long numberOfBytes);

  void reportBytesRead(long s);

  void close();

  void createStream(HttpClientRequestImpl req, Handler<AsyncResult<HttpClientStream>> handler);

  ContextImpl getContext();

}
