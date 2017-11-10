/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.http.impl.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextImpl;

/**
 * Provides how the connection manager interacts its connections.
 */
public interface ConnectionProvider<C> {

  void connect(
    ConnectionListener<C> listener,
    Object endpointMetric,
    Bootstrap bootstrap,
    ContextImpl context,
    String peerHost,
    boolean ssl,
    String host,
    int port,
    Handler<AsyncResult<C>> handler);

  Channel channel(C conn);

  void close(C conn);

}
