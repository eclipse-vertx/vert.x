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

package org.vertx.java.core.http.ws.hybi00;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.vertx.java.core.CompletionHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleFuture;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.ws.Handshake;
import org.vertx.java.core.http.ws.hybi08.Handshake08;
import org.vertx.java.core.logging.Logger;

import java.security.NoSuchAlgorithmException;

/**
 * Handler for ietf-08.
 *
 * @author Michael Dobozy
 * @author Bob McWhirter
 */
public class Handshake00 implements Handshake {

  private static Logger log = Logger.getLogger(Handshake08.class);

  private WebSocketChallenge00 challenge;

  protected String getWebSocketLocation(HttpRequest request) {
    return "ws://" + request.getHeader(HttpHeaders.Names.HOST) + request.getUri();
  }

  public Handshake00() throws NoSuchAlgorithmException {
    this.challenge = new WebSocketChallenge00();
  }

  public static boolean matches(HttpRequest request) {
    return (request.containsHeader("Sec-WebSocket-Key1") && request.containsHeader("Sec-WebSocket-Key2"));
  }

  public void fillInRequest(HttpClientRequest req, String hostHeader) throws Exception {

    req.putHeader(HttpHeaders.Names.CONNECTION, "Upgrade");
    req.putHeader(HttpHeaders.Names.UPGRADE, "WebSocket");
    req.putHeader(HttpHeaders.Names.HOST, hostHeader);

    req.putHeader(HttpHeaders.Names.SEC_WEBSOCKET_KEY1, this.challenge.getKey1String());
    req.putHeader(HttpHeaders.Names.SEC_WEBSOCKET_KEY2, this.challenge.getKey2String());

    Buffer buff = Buffer.create(6);
    buff.appendBytes(challenge.getKey3());
    buff.appendByte((byte) '\r');
    buff.appendByte((byte) '\n');
    req.write(buff);
  }

  public HttpResponse generateResponse(HttpRequest request) throws Exception {

    HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(101, "Web Socket Protocol Handshake - IETF-00"));
    response.addHeader(HttpHeaders.Names.CONNECTION, "Upgrade");
    response.addHeader(HttpHeaders.Names.UPGRADE, "WebSocket");
    String origin = request.getHeader(Names.ORIGIN);
    if (origin != null) {
      response.addHeader(Names.SEC_WEBSOCKET_ORIGIN, request.getHeader(Names.ORIGIN));
    }
    response.addHeader(Names.SEC_WEBSOCKET_LOCATION, getWebSocketLocation(request));

    String protocol = request.getHeader(Names.SEC_WEBSOCKET_PROTOCOL);

    if (protocol != null) {
      response.addHeader(Names.SEC_WEBSOCKET_PROTOCOL, protocol);
    }

    // Calculate the answer of the challenge.
    String key1 = request.getHeader(Names.SEC_WEBSOCKET_KEY1);
    String key2 = request.getHeader(Names.SEC_WEBSOCKET_KEY2);
    byte[] key3 = new byte[8];

    request.getContent().readBytes(key3);

    byte[] solution = WebSocketChallenge00.solve(key1, key2, key3);

    ChannelBuffer buffer = ChannelBuffers.dynamicBuffer(solution.length + 2);
    buffer.writeBytes(solution);

    response.addHeader("Content-Length", buffer.readableBytes());

    response.setContent(buffer);
    response.setChunked(false);

    return response;
  }

  public void onComplete(HttpClientResponse response, final CompletionHandler<Void> doneHandler) {

    final Buffer buff = Buffer.create(16);
    response.dataHandler(new Handler<Buffer>() {
      public void handle(Buffer data) {
        buff.appendBuffer(data);
      }
    });
    response.endHandler(new SimpleHandler() {
      public void handle() {
        byte[] bytes = buff.getBytes();
        SimpleFuture<Void> fut = new SimpleFuture<>();
        try {
          if (challenge.verify(bytes)) {
            fut.setResult(null);
          } else {
            fut.setException(new Exception("Invalid websocket handshake response"));
          }
        } catch (Exception e) {
          fut.setException(e);
        }
        doneHandler.handle(fut);
      }
    });
  }

  public ChannelHandler getEncoder(boolean server) {
    return new WebSocketFrameEncoder00();
  }

  public ChannelHandler getDecoder() {
    return new WebSocketFrameDecoder00();
  }

}