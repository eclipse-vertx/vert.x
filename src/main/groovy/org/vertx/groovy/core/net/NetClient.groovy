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
package org.vertx.groovy.core.net

import org.vertx.java.core.Handler

/**
 * NetClient is an asynchronous factory for TCP or SSL connections
 * <p>
 * Multiple connections to different servers can be made using the same instance.
 * <p>
 * This client supports a configurable number of connection attempts and a configurable
 * delay between attempts.
 * <p>
 * This class is a thread safe and can safely be used by different threads.
 * <p>
 * If an instance is instantiated from an event loop then the handlers
 * of the instance will always be called on that same event loop.
 * If an instance is instantiated from some other arbitrary Java thread then
 * and event loop will be assigned to the instance and used when any of its handlers
 * are called.
 * <p>
 * Instances cannot be used from worker verticles
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class NetClient extends org.vertx.java.core.net.NetClient {

  /**
   * Attempt to open a connection to a server at the specific {@code port} and host localhost
   * The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code hndlr} instance
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient connect(int port, hndlr) {
    super.connect(port, wrapHandler(hndlr))
    this
  }

  /**
   * Attempt to open a connection to a server at the specific {@code port} and {@code host}.
   * {@code host} can be a valid host name or IP addresss. The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code hndlr} instance
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient connect(int port, String host, Closure hndlr) {
    super.connect(port, host, wrapHandler(hndlr))
    this
  }

  private wrapHandler(Closure hndlr) {
    return {hndlr.call(new NetSocket(it))} as Handler
  }

}
