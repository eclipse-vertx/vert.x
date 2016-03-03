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

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.vertx.core.Context;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.NetSocket;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
interface HttpClientStream {

  void writeHead(HttpMethod method, String uri, MultiMap headers, boolean chunked);
  void writeHeadWithContent(HttpMethod method, String uri, MultiMap headers, boolean chunked, ByteBuf buf, boolean end);
  void writeBuffer(ByteBuf buf, boolean end);
  String hostHeader();
  Context getContext();
  void doSetWriteQueueMaxSize(int size);
  boolean isNotWritable();
  void handleInterestedOpsChanged();

  // Perhaps it's possible to remove this with writeBuffer(buf, true) instead
  void endRequest();

  void doPause();
  void doResume();

  // Try to remove that ?
  void reportBytesWritten(long numberOfBytes);
  void reportBytesRead(long s);

  NetSocket createNetSocket();
}
