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

package org.vertx.java.core.http.impl.ws.hybi00;

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
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.impl.ws.Handshake;
import org.vertx.java.core.http.impl.ws.hybi08.Handshake08;
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
public class Handshake00 implements Handshake {

  private static Logger log = LoggerFactory.getLogger(Handshake08.class);

  private final WebSocketChallenge00 challenge;

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

    req.headers().put(HttpHeaders.Names.CONNECTION, "Upgrade");
    req.headers().put(HttpHeaders.Names.UPGRADE, "WebSocket");
    req.headers().put(HttpHeaders.Names.HOST, hostHeader);

    req.headers().put(HttpHeaders.Names.SEC_WEBSOCKET_KEY1, this.challenge.getKey1String());
    req.headers().put(HttpHeaders.Names.SEC_WEBSOCKET_KEY2, this.challenge.getKey2String());

    Buffer buff = new Buffer(6);
    buff.appendBytes(challenge.getKey3());
    buff.appendByte((byte) '\r');
    buff.appendByte((byte) '\n');
    req.write(buff);
  }

  public HttpResponse generateResponse(HttpRequest request, String serverOrigin) throws Exception {

    HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(101,
        "WebSocket Protocol Handshake"));
    response.addHeader(HttpHeaders.Names.CONNECTION, "Upgrade");
    response.addHeader(HttpHeaders.Names.UPGRADE, "WebSocket");
    String origin = request.getHeader(Names.ORIGIN);
    if (origin == null) {
      origin = serverOrigin;
    }
    response.addHeader(Names.SEC_WEBSOCKET_ORIGIN, origin);
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

  public void onComplete(final HttpClientResponse response, final AsyncResultHandler<Void> doneHandler) {
    final Buffer buff = new Buffer(16);
    response.dataHandler(new Handler<Buffer>() {
      public void handle(Buffer data) {
        buff.appendBuffer(data);
      }
    });
    response.endHandler(new SimpleHandler() {
      public void handle() {
        byte[] bytes = buff.getBytes();
        AsyncResult<Void> res;
        try {
          if (challenge.verify(bytes)) {
            res = new AsyncResult<>((Void)null);
          } else {
            res = new AsyncResult<>(new Exception("Invalid websocket handshake response"));
          }
        } catch (Exception e) {
          res = new AsyncResult<>(e);
        }
        doneHandler.handle(res);
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