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

import org.nodex.java.core.ConnectionPool;
import org.nodex.java.core.Handler;
import org.nodex.java.core.net.NetClient;
import org.nodex.java.core.net.NetSocket;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RedisPool {

  private final NetClient client = new NetClient();
  private final ConnectionPool<InternalConnection> pool = new ConnectionPool<InternalConnection>() {
    protected void connect(Handler<InternalConnection> connectHandler, long contextID) {
      internalConnect(connectHandler, contextID);
    }
  };
  private String host = "localhost";
  private int port = 6379;

  /**
   * Set the port that the client will attempt to connect to on the server to {@code port}. The default value is {@code 80}<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public RedisPool setPort(int port) {
    this.port = port;
    return this;
  }

  /**
   * Set the host that the client will attempt to connect to, to {@code host}. The default value is {@code localhost}<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public RedisPool setHost(String host) {
    this.host = host;
    return this;
  }

  /**
   * Set the maximum pool size to the value specified by {@code maxConnections}<p>
   * The client will maintain up to {@code maxConnections} HTTP connections in an internal pool<p>
   * @return A reference to this, so multiple invocations can be chained together.
   */
  public RedisPool setMaxPoolSize(int maxConnections) {
    pool.setMaxPoolSize(maxConnections);
    return this;
  }

  /**
   * Returns the maximum number of connections in the pool
   */
  public int getMaxPoolSize() {
    return pool.getMaxPoolSize();
  }

  public RedisConnection connection() {
    return new RedisConnection(pool);
  }

  public void close() {
    pool.close();
    client.close();
  }

  private void internalConnect(final Handler<InternalConnection> connectHandler, long contextID) {
    client.connect(port, host, new Handler<NetSocket>() {
      public void handle(NetSocket socket) {
        connectHandler.handle(new InternalConnection(pool, socket));
      }
    });
  }
}
