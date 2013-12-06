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

package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

/**
 *
 * This is an implementation of the server side part of <a href="https://github.com/sockjs">SockJS</a><p>
 *
 * <p>SockJS enables browsers to communicate with the server using a simple WebSocket-like api for sending
 * and receiving messages. Under the bonnet SockJS chooses to use one of several protocols depending on browser
 * capabilities and what appears to be working across the network.<p>
 *
 * Available protocols include:<p>
 *
 * <ul>
 *   <li>WebSockets</li>
 *   <li>xhr-polling</li>
 *   <li>xhr-streaming</li>
 *   <li>json-polling</li>
 *   <li>event-source</li>
 *   <li>html-file</li>
 * </ul><p>
 *
 * This means, it should <i>just work</i> irrespective of what browser is being used, and whether there are nasty
 * things like proxies and load balancers between the client and the server.<p>
 *
 * For more detailed information on SockJS, see their website.<p>
 *
 * On the server side, you interact using instances of {@link SockJSSocket} - this allows you to send data to the
 * client or receive data via the {@link SockJSSocket#dataHandler}.<p>
 *
 * You can register multiple applications with the same SockJSServer, each using different path prefixes, each
 * application will have its own handler, and configuration.<p>
 *
 * Instances of this class are not thread-safe.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface SockJSServer {

  /**
   * Install an application
   * @param config The application configuration
   * @param sockHandler A handler that will be called when new SockJS sockets are created
   */
  SockJSServer installApp(JsonObject config, Handler<SockJSSocket> sockHandler);

  /**
   * Install an app which bridges the SockJS server to the event bus
   * @param sjsConfig The config for the app
   * @param inboundPermitted A list of JSON objects which define permitted matches for inbound (client-&gt;server) traffic
   * @param outboundPermitted A list of JSON objects which define permitted matches for outbound (server-&gt;client)
   * traffic
   */
  SockJSServer bridge(JsonObject sjsConfig, JsonArray inboundPermitted, JsonArray outboundPermitted);

  /**
   * Install an app which bridges the SockJS server to the event bus
   * @param sjsConfig The config for the app
   * @param inboundPermitted A list of JSON objects which define permitted matches for inbound (client-&gt;server) traffic
   * @param outboundPermitted A list of JSON objects which define permitted matches for outbound (server-&gt;client)
   * traffic
   * @param authTimeout Default time an authorisation will be cached for in the bridge (defaults to 5 minutes)
   */
  SockJSServer bridge(JsonObject sjsConfig, JsonArray inboundPermitted, JsonArray outboundPermitted,
              long authTimeout);

  /**
   * Install an app which bridges the SockJS server to the event bus
   * @param sjsConfig The config for the app
   * @param inboundPermitted A list of JSON objects which define permitted matches for inbound (client-&gt;server) traffic
   * @param outboundPermitted A list of JSON objects which define permitted matches for outbound (server-&gt;client)
   * traffic
   * @param authTimeout Default time an authorisation will be cached for in the bridge (defaults to 5 minutes)
   * @param authAddress Address of auth manager. Defaults to 'vertx.basicauthmanager.authorise'
   */
  SockJSServer bridge(JsonObject sjsConfig, JsonArray inboundPermitted, JsonArray outboundPermitted,
              long authTimeout, String authAddress);

  /**
   * Install an app which bridges the SockJS server to the event bus
   * @param sjsConfig The config for the app
   * @param inboundPermitted A list of JSON objects which define permitted matches for inbound (client-&gt;server) traffic
   * @param outboundPermitted A list of JSON objects which define permitted matches for outbound (server-&gt;client)
   * traffic
   * @param bridgeConfig JSON object holding config for the EventBusBridge
   */
  SockJSServer bridge(JsonObject sjsConfig, JsonArray inboundPermitted, JsonArray outboundPermitted,
                      JsonObject bridgeConfig);

  /**
   * Set a EventBusBridgeHook on the SockJS server
   * @param hook The hook
   */
  SockJSServer setHook(EventBusBridgeHook hook);

  /**
   * Close the server
   */
  void close();
}

