/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.addons.redis;

import org.nodex.java.core.Handler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.internal.NodexInternal;
import org.nodex.java.core.net.NetSocket;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * <p>Represents a connection to a Redis server.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class RedisConnection {

  private final NetSocket socket;
  private Queue<RedisDeferred<?>> requests = new ConcurrentLinkedQueue<>();

  RedisConnection(NetSocket socket) {
    this.socket = socket;
    socket.dataHandler(new ReplyParser(new Handler<RedisReply>() {
      public void handle(RedisReply reply) {
        doHandle(reply);
      }
    }));
  }

  void writeRequest(RedisDeferred<?> deferred, Buffer request) {
    requests.add(deferred);
    socket.write(request);
  }

  private void doHandle(RedisReply reply) {
    RedisDeferred<?> deferred = requests.poll();
    if (deferred == null) {
      throw new IllegalStateException("Unsolicited response");
    }
    deferred.setReply(reply);
  }
}


