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

package org.vertx.java.core.http.impl.ws.hybi17;

import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.impl.ws.hybi08.Handshake08;
import org.vertx.java.core.http.impl.ws.hybi08.WebSocketChallenge08;

import java.security.NoSuchAlgorithmException;

/**
 * Handler for RFC6455.
 *
 * @author Michael Dobozy
 * @author Bob McWhirter
 */
public class HandshakeRFC6455 extends Handshake08 {

  public static boolean matches(HttpRequest request) {
    String sVers = request.getHeader("Sec-WebSocket-Version");
    if (sVers != null) {
      Integer ver = Integer.parseInt(sVers);
      return request.containsHeader("Sec-WebSocket-Key") && ver == 13;
    } else {
      return false;
    }
  }

  public HandshakeRFC6455() throws NoSuchAlgorithmException {
  }

  public void fillInRequest(HttpClientRequest req, String hostHeader) throws Exception {
    req.headers().put("Sec-WebSocket-Version", "13");
    req.headers().put(HttpHeaders.Names.CONNECTION, "Upgrade");
    req.headers().put(HttpHeaders.Names.UPGRADE, "WebSocket");
    req.headers().put(HttpHeaders.Names.HOST, hostHeader);
    req.headers().put("Sec-WebSocket-Key", challenge.getNonceBase64());
  }

  public HttpResponse generateResponse(HttpRequest request, String serverOrigin) throws Exception {
    HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(101,
        "Switching Protocols"));
    response.addHeader(Names.UPGRADE, "WebSocket");
    response.addHeader(HttpHeaders.Names.CONNECTION, "Upgrade");
    String origin = request.getHeader(Names.ORIGIN);
    if (origin != null) {
      response.addHeader(Names.SEC_WEBSOCKET_ORIGIN, origin);
    }
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

}