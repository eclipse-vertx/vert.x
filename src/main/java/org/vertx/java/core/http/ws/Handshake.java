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
 */

package org.vertx.java.core.http.ws;

import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.logging.Logger;

import java.security.NoSuchAlgorithmException;

/**
 * Handler for ietf-08.
 *
 * @author Michael Dobozy
 * @author Bob McWhirter
 */
public class Handshake {

  private static Logger log = Logger.getLogger(Handshake.class);

  private WebSocketChallenge challenge;

  protected String getWebSocketLocation(HttpRequest request) {
    return "ws://" + request.getHeader(HttpHeaders.Names.HOST) + request.getUri();
  }

  public Handshake() throws NoSuchAlgorithmException {
    this.challenge = new WebSocketChallenge();
  }

  public boolean matches(HttpRequest request) {
    return request.containsHeader("Sec-WebSocket-Key");
  }

  public void generateRequest(HttpClientRequest req, String hostHeader) throws Exception {
    req.putHeader("Sec-WebSocket-Version", "7");
    req.putHeader(HttpHeaders.Names.CONNECTION, "Upgrade");
    req.putHeader(HttpHeaders.Names.UPGRADE, "WebSocket");
    req.putHeader(HttpHeaders.Names.HOST, hostHeader);
    req.putHeader("Sec-WebSocket-Key", this.challenge.getNonceBase64());
  }

  public HttpResponse generateResponse(HttpRequest request) throws Exception {
    HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(101, "Web Socket Protocol Handshake - IETF-07"));
    response.addHeader(HttpHeaders.Names.UPGRADE, "WebSocket");
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
    String solution = WebSocketChallenge.solve(key);
    response.addHeader("Sec-WebSocket-Accept", solution);
    response.setChunked(false);
    return response;
  }

  public boolean isComplete(HttpClientResponse response) throws Exception {
    String challengeResponse = response.getHeader("Sec-WebSocket-Accept");
    return this.challenge.verify(challengeResponse);
  }
}