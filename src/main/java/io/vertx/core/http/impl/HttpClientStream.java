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

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
interface HttpClientStream {

  HttpClientConnection connection();
  Context getContext();

  void writeHead(HttpMethod method, String uri, MultiMap headers, String hostHeader, boolean chunked);
  void writeHeadWithContent(HttpMethod method, String uri, MultiMap headers, String hostHeader, boolean chunked, ByteBuf buf, boolean end);
  void writeBuffer(ByteBuf buf, boolean end);

  void doSetWriteQueueMaxSize(int size);
  boolean isNotWritable();
  void handleInterestedOpsChanged();
  void doPause();
  void doResume();

  void reset(long code);

  void endRequest();
}
