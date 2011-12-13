/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.http;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.ConnectionBase;

public abstract class AbstractConnection extends ConnectionBase {

  private static final Logger log = Logger.getLogger(AbstractConnection.class);

  protected AbstractConnection(Channel channel, long contextID, Thread th) {
    super(channel, contextID, th);
  }

  ChannelFuture write(Object obj) {
    if (channel.isOpen()) {
      return channel.write(obj);
    } else {
      return null;
    }
  }

}
