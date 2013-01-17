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

package org.vertx.java.core.http.impl;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.http.WebSocketVersion;
import org.vertx.java.core.http.impl.ws.Handshake;
import org.vertx.java.core.http.impl.ws.WebSocketFrame;
import org.vertx.java.core.http.impl.ws.hybi00.Handshake00;
import org.vertx.java.core.http.impl.ws.hybi08.Handshake08;
import org.vertx.java.core.http.impl.ws.hybi17.HandshakeRFC6455;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ClientConnection extends AbstractConnection {

  private static final Logger log = LoggerFactory.getLogger(ClientConnection.class);

  ClientConnection(VertxInternal vertx, DefaultHttpClient client, Channel channel, String hostHeader, boolean ssl,
                   boolean keepAlive,
                   Context context) {
    super(vertx, channel, context);
    this.client = client;
    this.hostHeader = hostHeader;
    this.ssl = ssl;
    this.keepAlive = keepAlive;
  }

  final DefaultHttpClient client;
  final String hostHeader;
  boolean keepAlive;
  private final boolean ssl;
  private boolean wsHandshakeConnection;

  private volatile DefaultHttpClientRequest currentRequest;
  // Requests can be pipelined so we need a queue to keep track of requests
  private final Queue<DefaultHttpClientRequest> requests = new ConcurrentLinkedQueue();
  private volatile DefaultHttpClientResponse currentResponse;
  private DefaultWebSocket ws;

  void toWebSocket(final String uri,
                   final Handler<WebSocket> wsConnect,
                   final WebSocketVersion wsVersion) {
    if (ws != null) {
      throw new IllegalStateException("Already websocket");
    }

    try {

      final Handshake shake;
      if (wsVersion == WebSocketVersion.HYBI_00) {
        shake = new Handshake00();
      } else if (wsVersion == WebSocketVersion.HYBI_08) {
        shake = new Handshake08();
      } else if (wsVersion == WebSocketVersion.RFC6455) {
        shake = new HandshakeRFC6455();
      } else {
        throw new IllegalArgumentException("Invalid version");
      }

      final ChannelPipeline p = channel.getPipeline();
      SwitchingHttpClientCodec codec = (SwitchingHttpClientCodec)p.get("codec");

      codec.setSwitch("wsdecoder", shake.getDecoder());

      wsHandshakeConnection = true;

      // Create a raw request
      DefaultHttpClientRequest req = new DefaultHttpClientRequest(client, "GET", uri, new Handler<HttpClientResponse>() {
        public void handle(HttpClientResponse resp) {
          if (resp.statusCode == 101) {
            try {
              shake.onComplete(resp, new AsyncResultHandler<Void>() {
                public void handle(AsyncResult<Void> fut) {
                  if (fut.succeeded()) {
                    //We upgraded ok
                    p.addLast("wsencoder", shake.getEncoder(false));
                    ws = new DefaultWebSocket(vertx, null, ClientConnection.this, null);
                    wsConnect.handle(ws);
                  } else {
                    client.handleException(fut.exception);
                  }
                }
              });
            } catch (Exception e) {
              client.handleException(e);
            }
          } else {
            client.handleException(new IOException("Websocket connection attempt returned HTTP status code " + resp.statusCode));
          }
        }
      }, context, this);
      shake.fillInRequest(req, (ssl ? "http://" : "https://") + hostHeader);
      req.end();
    } catch (Exception e) {
      handleException(e);
    }
  }

  @Override
  public void close() {
//    if (ws != null) {
//      //Need to send 9 zeros to represent a close
//      byte[] bytes = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0};  // Just to be explicit
//      ChannelFuture future = channel.write(ChannelBuffers.copiedBuffer(bytes));
//      future.addListener(ChannelFutureListener.CLOSE);  // Close after it's written
//    }
    if (wsHandshakeConnection) {
      // Do nothing - this will be ugraded
    } else if (!keepAlive) {
      //Close it
      internalClose();
    } else {
      client.returnConnection(this);
    }
  }

  void internalClose() {
    channel.close();
  }

  boolean isClosed() {
    return !channel.isOpen();
  }

  //TODO - combine these with same in ServerConnection and NetSocket

  void handleInterestedOpsChanged() {
    try {
      if (channel.isWritable()) {
        if (currentRequest != null) {
          setContext();
          currentRequest.handleDrained();
        } else if (ws != null) {
          ws.writable();
        }
      }
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }


  void handleResponse(HttpResponse resp) {
    DefaultHttpClientRequest req;
    if (resp.getStatus().getCode() == 100) {
      //If we get a 100 continue it will be followed by the real response later, so we don't remove it yet
      req = requests.peek();
    } else {
      req = requests.poll();
    }
    if (req == null) {
      throw new IllegalStateException("No response handler");
    }
    setContext();
    DefaultHttpClientResponse nResp = new DefaultHttpClientResponse(this, resp);
    currentResponse = nResp;
    req.handleResponse(nResp);
  }

  void handleResponseChunk(Buffer buff) {
    setContext();
    try {
      currentResponse.handleChunk(buff);
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  void handleResponseEnd() {
    handleResponseEnd(null);
  }

  void handleResponseEnd(HttpChunkTrailer trailer) {
    setContext();
    try {
      currentResponse.handleEnd(trailer);
    } catch (Throwable t) {
      handleHandlerException(t);
    }
    if (!keepAlive) {
      close();
    }
  }

  void handleWsFrame(WebSocketFrame frame) {
    if (ws != null) {
      setContext();
      ws.handleFrame(frame);
    }
  }

  protected void handleClosed() {
    super.handleClosed();
    if (ws != null) {
      ws.handleClosed();
    }
  }

  protected Context getContext() {
    return super.getContext();
  }

  protected void handleException(Exception e) {
    super.handleException(e);

    if (currentRequest != null) {
      currentRequest.handleException(e);
    } else if (currentResponse != null) {
      currentResponse.handleException(e);
    }
  }

  protected void addFuture(Handler<Void> doneHandler, ChannelFuture future) {
    super.addFuture(doneHandler, future);
  }

  ChannelFuture write(Object obj) {
    if (!channel.isOpen()) {
      throw new IllegalStateException("Connection is closed");
    }
    return channel.write(obj);
  }

  void setCurrentRequest(DefaultHttpClientRequest req) {
    if (currentRequest != null) {
      throw new IllegalStateException("Connection is already writing a request");
    }
    this.currentRequest = req;
    this.requests.add(req);
  }

  void endRequest() {
    if (currentRequest == null) {
      throw new IllegalStateException("No write in progress");
    }
    currentRequest = null;

    if (keepAlive) {
      //Close just returns connection to the pool
      close();
    } else {
      //The connection gets closed after the response is received
    }
  }
}
