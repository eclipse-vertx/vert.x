/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.core.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrame;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameDecoder;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameEncoder;
import org.nodex.java.core.Handler;
import org.nodex.java.core.SimpleHandler;
import org.nodex.java.core.buffer.Buffer;

import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

class ClientConnection extends AbstractConnection {

  ClientConnection(HttpClient client, Channel channel, String hostHeader, boolean ssl,
                   boolean keepAlive,
                   long contextID, Thread th) {
    super(channel, contextID, th);
    this.client = client;
    this.hostHeader = hostHeader;
    this.ssl = ssl;
    this.keepAlive = keepAlive;
  }

  final HttpClient client;
  final String hostHeader;
  final boolean keepAlive;
  private final boolean ssl;

  private volatile HttpClientRequest currentRequest;
  // Requests can be pipelined so we need a queue to keep track of requests
  private final Queue<HttpClientRequest> requests = new ConcurrentLinkedQueue();
  private volatile HttpClientResponse currentResponse;
  private Websocket ws;

  void toWebSocket(final String uri,
                   final Handler<Websocket> wsConnect) {
    if (ws != null) {
      throw new IllegalStateException("Already websocket");
    }

    final String key1 = WebsocketHandshakeHelper.genWSkey();
    final String key2 = WebsocketHandshakeHelper.genWSkey();
    long c = new Random().nextLong();

    final Buffer out = new Buffer(WebsocketHandshakeHelper.calcResponse(key1, key2, c));
    final ChannelBuffer buff = ChannelBuffers.buffer(8);
    buff.writeLong(c);

    HttpClientRequest req = new HttpClientRequest(client, "GET", uri, new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        if (resp.statusCode != 101 || !resp.statusMessage.equals("Web Socket Protocol Handshake")) {
          handleException(new IllegalStateException("Invalid protocol handshake - invalid status: " + resp.statusCode
              + "msg:" + resp.statusMessage));
        } else if (!resp.getHeader(HttpHeaders.Names.CONNECTION).equals(HttpHeaders.Values.UPGRADE)) {
          //TODO - these exceptions need to be piped to the *Request* exception handler
          handleException(new IllegalStateException("Invalid protocol handshake - no Connection header"));
        } else {
          final Buffer buff = Buffer.create(0);
          resp.dataHandler(new Handler<Buffer>() {
            public void handle(Buffer data) {
              buff.appendBuffer(data);
            }
          });
          resp.endHandler(new SimpleHandler() {
            public void handle() {
              boolean matched = true;
              if (buff.length() == out.length()) {
                for (int i = 0; i < buff.length(); i++) {
                  if (out.getByte(i) != buff.getByte(i)) {
                    matched = false;
                    break;
                  }
                }
                if (matched) {
                  //We upgraded ok
                  ChannelPipeline p = channel.getPipeline();
                  p.replace("decoder", "wsdecoder", new WebSocketFrameDecoder());
                  p.replace("encoder", "wsencoder", new WebSocketFrameEncoder());
                  ws = new Websocket(uri, ClientConnection.this);
                  wsConnect.handle(ws);
                  return;
                }
              }
              handleException(new IllegalStateException("Invalid protocol handshake - wrong response"));
            }
          });
        }
      }
    }, contextID, Thread.currentThread());

    setCurrentRequest(req);
    req.setChunked(false);
    req.putHeader(HttpHeaders.Names.UPGRADE, HttpHeaders.Values.WEBSOCKET).
        putHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.UPGRADE).
        putHeader(HttpHeaders.Names.ORIGIN, (ssl ? "http://" : "https://") + hostHeader).
        putHeader(HttpHeaders.Names.SEC_WEBSOCKET_KEY1, key1).
        putHeader(HttpHeaders.Names.SEC_WEBSOCKET_KEY2, key2);
    req.sendDirect(this, new Buffer(buff));
  }

  @Override
  public void close() {
//    if (ws != null) {
//      //Need to send 9 zeros to represent a close
//      byte[] bytes = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0};  // Just to be explicit
//      ChannelFuture future = channel.write(ChannelBuffers.copiedBuffer(bytes));
//      future.addListener(ChannelFutureListener.CLOSE);  // Close after it's written
//    }
    client.returnConnection(this);
  }

  void internalClose() {
    //channel.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    channel.close();
  }

  //TODO - combine these with same in ServerConnection and NetSocket

  void handleInterestedOpsChanged() {
    try {
      if (currentRequest != null) {
        if ((channel.getInterestOps() & Channel.OP_WRITE) == Channel.OP_WRITE) {
          setContextID();
          currentRequest.handleInterestedOpsChanged();
        }
      }
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }


  void handleResponse(HttpResponse resp) {
    HttpClientRequest req;
    if (resp.getStatus().getCode() == 100) {
      //If we get a 100 continue it will be followed by the real response later, so we don't remove it yet
      req = requests.peek();
    } else {
      req = requests.poll();
    }

    if (req == null) {
      throw new IllegalStateException("No response handler");
    }
    setContextID();
    HttpClientResponse nResp = new HttpClientResponse(this, resp, req.th);
    currentResponse = nResp;
    req.handleResponse(nResp);
  }

  void handleResponseChunk(Buffer buff) {
    setContextID();
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
      ws.handleFrame(frame);
    }
  }

  protected void handleClosed() {
    super.handleClosed();
  }

  protected long getContextID() {
    return super.getContextID();
  }

  protected void handleException(Exception e) {
    super.handleException(e);

    if (currentRequest != null) {
      currentRequest.handleException(e);
    }
    if (currentResponse != null) {
      currentResponse.handleException(e);
    }
  }

  protected void addFuture(Handler<Void> doneHandler, ChannelFuture future) {
    super.addFuture(doneHandler, future);
  }

  ChannelFuture write(Object obj) {
    return channel.write(obj);
  }

  void setCurrentRequest(HttpClientRequest req) {
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
