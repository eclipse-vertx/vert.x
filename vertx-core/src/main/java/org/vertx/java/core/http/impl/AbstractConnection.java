/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.http.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.impl.ConnectionBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class AbstractConnection extends ConnectionBase {

  private static final Logger log = LoggerFactory.getLogger(AbstractConnection.class);

  protected AbstractConnection(VertxInternal vertx, Channel channel, Context context) {
    super(vertx, channel, context);
  }

  void queueForWrite(Object obj) {
    channel.outboundMessageBuffer().add(obj);
  }


  ChannelFuture write(Object obj) {
    if (channel.isOpen()) {
      return channel.write(obj);
    } else {
      return null;
    }
  }
}
