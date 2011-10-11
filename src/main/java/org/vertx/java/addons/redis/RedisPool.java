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

package org.vertx.java.addons.redis;

import org.vertx.java.core.ConnectionPool;
import org.vertx.java.core.Handler;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;

/**
 * <p>Instances of this class maintain a pool of connections to a Redis Server and act as a factory for
 * {@link RedisConnection} instances via the {@link #connection} method. Once a RedisConnection has been done
 * with, the {@link RedisConnection#close} method should be called to return it's underlying TCP connection to
 * the pool.</p>
 * <p>If Redis authentication is enabled on the server, a password should be set using the {@link #setPassword}
 * method.</p>
 * <p>This pool supports reconnect attempts. If the Redis Server is unavailable it can be configured to
 * automatically reconnect until it is available.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RedisPool {

  private static final Logger log = Logger.getLogger(RedisPool.class);

  private final NetClient client = new NetClient();
  private final ConnectionPool<InternalConnection> pool = new ConnectionPool<InternalConnection>() {
    protected void connect(Handler<InternalConnection> connectHandler, long contextID) {
      internalConnect(connectHandler, contextID);
    }
  };
  private String host = "localhost";
  private int port = 6379;
  private String password;

  /**
   * Create a new RedisPool
   */
  public RedisPool() {
    client.exceptionHandler(new Handler<Exception>() {
      public void handle(Exception e) {
        log.error("Failed to connect", e);
      }
    });
  }

  /**
   * Set the port that the client will attempt to connect to on the server to {@code port}. The default value is {@code 6379}<p>
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
   * Set the maximum pool size <p>
   * The pool will maintain up to this number of Redis connections in an internal pool<p>
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

  /**
   * Set the password to be used for authentication
   */
  public RedisPool setPassword(String password) {
    this.password = password;
    return this;
  }

  /**
   * Set the number of reconnection attempts. In the event a connection attempt fails, the client will attempt
   * to connect a further number of times, before it fails. Default value is zero.
   */
  public void setReconnectAttempts(int attempts) {
    client.setReconnectAttempts(attempts);
  }

  /**
   * Get the number of reconnect attempts
   */
  public int getReconnectAttempts() {
    return client.getReconnectAttempts();
  }

  /**
   * Set the reconnect interval, in milliseconds
   */
  public void setReconnectInterval(long interval) {
    client.setReconnectInterval(interval);
  }

  /**
   * Get the reconnect interval, in milliseconds.
   */
  public long getReconnectInterval() {
    return client.getReconnectInterval();
  }

  /**
   * Get a RedisConnection.
   */
  public RedisConnection connection() {
    return new RedisConnection(pool, password);
  }

  /**
   * Close the pool. Any pooled connections will be closed.
   */
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
