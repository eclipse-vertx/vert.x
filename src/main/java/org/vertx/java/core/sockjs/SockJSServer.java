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

package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import org.vertx.java.core.sockjs.impl.SockJSServerImpl;

/**
 *
 * <p>This is an implementation of the server side part of <a href="https://github.com/sockjs">SockJS</a></p>
 *
 * <p>SockJS enables browsers to communicate with the server using a simple WebSocket-like api for sending
 * and receiving messages. Under the bonnet SockJS chooses to use one of several protocols depending on browser
 * capabilities and what apppears to be working across the network.</p>
 *
 * <p>Available protocols include:</p>
 *
 * <p><ul>
 *   <li>WebSockets</li>
 *   <li>xhr-polling</li>
 *   <li>xhr-streaming</li>
 *   <li>json-polling</li>
 *   <li>event-source</li>
 *   <li>html-file</li>
 * </ul></p>
 *
 * <p>This means, it should <i>just work</i> irrespective of what browser is being used, and whether there are nasty
 * things like proxies and load balancers between the client and the server.</p>
 *
 * <p>For more detailed information on SockJS, see their website.</p>
 *
 * <p>On the server side, you interact using instances of {@link SockJSSocket} - this allows you to send data to the
 * client or receive data via the {@link SockJSSocket#dataHandler}.</p>
 *
 * <p>You can register multiple applications with the same SockJSServer, each using different path prefixes, each
 * application will have its own handler, and configuration as described by {@link AppConfig}</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SockJSServer {

  private static final Logger log = LoggerFactory.getLogger(SockJSServer.class);

  private final SockJSServerImpl serverImpl;

  /**
   * Create a new SockJSServer.
   * @param httpServer - you must pass in an HttpServer instance
   */
  public SockJSServer(HttpServer httpServer) {
    serverImpl = new SockJSServerImpl(httpServer);
  }

  /**
   * Install an application
   * @param config The application configuration
   * @param sockHandler A handler that will be called when new SockJS sessions are created
   */
  public void installApp(AppConfig config,
                         final Handler<SockJSSocket> sockHandler) {
    serverImpl.installApp(config, sockHandler);
  }

}

