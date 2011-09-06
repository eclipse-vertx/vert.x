/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.java.addons.redis;

import org.nodex.java.core.NodexInternal;
import redis.clients.jedis.Jedis;

public class RedisClient {
  public static RedisClient createClient() {
    return new RedisClient();
  }

  private RedisClient() {
  }

  public void connect(int port, String host, final RedisConnectHandler connectHandler) {
    final Jedis jedis = new Jedis(host, port);
    NodexInternal.instance.executeInBackground(new Runnable() {
      public void run() {
        jedis.connect();
        connectHandler.onConnect(new RedisConnection(jedis));
      }
    });
  }
}
