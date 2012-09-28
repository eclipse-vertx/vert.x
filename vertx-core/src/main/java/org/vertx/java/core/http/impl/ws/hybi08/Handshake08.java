/*
 * Copyright 2008-2011 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * Modified from original form by Tim Fox
 */

package org.vertx.java.core.http.impl.ws.hybi08;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.impl.ws.Handshake;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.security.NoSuchAlgorithmException;

/**
 * Handler for ietf-08.
 *
 * @author Michael Dobozy
 * @author Bob McWhirter
 *
 * Adapted by Tim Fox
 */
public class Handshake08 implements Handshake {

  private static Logger log = LoggerFactory.getLogger(Handshake08.class);

  protected final WebSocketChallenge08 challenge;

  protected String getWebSocketLocation(HttpRequest request, String serverOrigin) {
    String scheme = serverOrigin.substring(0, 5).toLowerCase().equals("https") ? "wss://" : "ws://";
    return scheme + request.getHeader(HttpHeaders.Names.HOST) + request.getUri();
  }

  public Handshake08() throws NoSuchAlgorithmException {
    this.challenge = new WebSocketChallenge08();
  }

  public static boolean matches(HttpRequest request) {
    String sVers = request.getHeader("Sec-WebSocket-Version");
    if (sVers != null) {
      Integer ver = Integer.parseInt(sVers);
      return request.containsHeader("Sec-WebSocket-Key") && ver >= 7;
    } else {
      return false;
    }
  }

  public void fillInRequest(HttpClientRequest req, String hostHeader) throws Exception {
    req.headers().put("Sec-WebSocket-Version", "8");
    req.headers().put(HttpHeaders.Names.CONNECTION, "Upgrade");
    req.headers().put(HttpHeaders.Names.UPGRADE, "WebSocket");
    req.headers().put(HttpHeaders.Names.HOST, hostHeader);
    req.headers().put("Sec-WebSocket-Key", this.challenge.getNonceBase64());
  }

  public HttpResponse generateResponse(HttpRequest request, String serverOrigin) throws Exception {
    HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
        new HttpResponseStatus(101, "Switching Protocols"));
    response.addHeader(HttpHeaders.Names.UPGRADE, "WebSocket");

    response.addHeader(HttpHeaders.Names.CONNECTION, "Upgrade");
    String origin = request.getHeader(Names.ORIGIN);
    if (origin == null) {
      origin = serverOrigin;
    }
    response.addHeader(Names.SEC_WEBSOCKET_ORIGIN, origin);
    response.addHeader(Names.SEC_WEBSOCKET_LOCATION, getWebSocketLocation(request, serverOrigin));
    String protocol = request.getHeader(Names.SEC_WEBSOCKET_PROTOCOL);
    if (protocol != null) {
      response.addHeader(Names.SEC_WEBSOCKET_PROTOCOL, protocol);
    }
    String key = request.getHeader("Sec-WebSocket-Key");
    String solution = WebSocketChallenge08.solve(key);
    response.addHeader("Sec-WebSocket-Accept", solution);
    response.setChunked(false);
    return response;
  }

  public void onComplete(HttpClientResponse response, final AsyncResultHandler<Void> doneHandler) throws Exception {
    String challengeResponse = response.headers().get("sec-websocket-accept");
    AsyncResult<Void> res;
    if (challenge.verify(challengeResponse)) {
      res = new AsyncResult<>((Void)null);
    } else {
      res = new AsyncResult<>(new Exception("Invalid websocket handshake response"));
    }
    doneHandler.handle(res);
  }

  public ChannelHandler getEncoder(boolean server) {
    return new WebSocketFrameEncoder08(!server);
  }

  public ChannelHandler getDecoder() {
    return new WebSocketFrameDecoder08();
  }
}