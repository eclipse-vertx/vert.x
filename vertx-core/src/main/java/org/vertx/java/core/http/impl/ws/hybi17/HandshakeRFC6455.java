/*
 * Copyright 2008-2011 Red Hat, Inc, and individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 *
 * Modified from original form by Tim Fox
 */

package org.vertx.java.core.http.impl.ws.hybi17;

import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
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

  public HttpResponse generateResponse(HttpRequest request) throws Exception {
    HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(101,
        "Switching Protocols"));
    response.addHeader(Names.UPGRADE, "WebSocket");
    response.addHeader(HttpHeaders.Names.CONNECTION, "Upgrade");
    String origin = request.getHeader(Names.ORIGIN);
    if (origin != null) {
      response.addHeader(Names.SEC_WEBSOCKET_ORIGIN, origin);
    }
    response.addHeader(Names.SEC_WEBSOCKET_LOCATION, getWebSocketLocation(request));
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