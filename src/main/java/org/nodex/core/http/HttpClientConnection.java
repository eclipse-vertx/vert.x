package org.nodex.core.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrame;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameDecoder;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameEncoder;
import org.nodex.core.ConnectionBase;
import org.nodex.core.DoneHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * User: timfox
 * Date: 22/07/2011
 * Time: 11:49
 */
public class HttpClientConnection extends ConnectionBase {

  HttpClientConnection(Channel channel, boolean keepAlive, String hostHeader,
                       String contextID, Thread th) {
    super(channel, contextID, th);
    this.keepAlive = keepAlive;
    this.hostHeader = hostHeader;
  }

  final boolean keepAlive;
  final String hostHeader;

  private HttpClientRequest currentRequest;
  // Requests can be pipelined so we need a queue to keep track of handlers
  private Queue<HttpResponseHandler> respHandlers = new ConcurrentLinkedQueue<HttpResponseHandler>();
  private HttpClientResponse currentResponse;
  private Websocket ws;

  // Public API ------------------------------------------------------------------------------------------------

  public void upgradeToWebSocket(final String uri, final WebsocketConnectHandler wsConnect) {
    upgradeToWebSocket(uri, null, wsConnect);
  }

  public void upgradeToWebSocket(final String uri, Map<String, ? extends Object> headers,
                           final WebsocketConnectHandler wsConnect) {
    if (headers == null) headers = new HashMap<String, String>();
    String key1 = WebsocketHandshakeHelper.genWSkey();
    String key2 = WebsocketHandshakeHelper.genWSkey();
    long c = new Random().nextLong();

    final Buffer out = Buffer.fromChannelBuffer(WebsocketHandshakeHelper.calcResponse(key1, key2, c));
    ChannelBuffer buff = ChannelBuffers.buffer(8);
    buff.writeLong(c);

    //This handshake is from the draft-ietf-hybi-thewebsocketprotocol-00 version of the spec
    //supported by Chrome etc
    HttpClientRequest req = get(uri, new HttpResponseHandler() {
      public void onResponse(HttpClientResponse resp) {
        if (resp.statusCode != 101 || !resp.statusMessage.equals("Web Socket Protocol Handshake")) {
          handleException(new IllegalStateException("Invalid protocol handshake - invalid status: " + resp.statusCode
          + "msg:" + resp.statusMessage));
        } else if (!resp.getHeader(HttpHeaders.Names.CONNECTION).equals(HttpHeaders.Values.UPGRADE)) {
          handleException(new IllegalStateException("Invalid protocol handshake - no Connection header"));
        } else {
          final Buffer buff = Buffer.newDynamic(0);
          resp.data(new DataHandler() {
            public void onData(Buffer data) {
              buff.append(data);
            }
          });
          resp.end(new DoneHandler() {
            public void onDone() {
              boolean matched = true;
              if (buff.length() == out.length()) {
                for (int i = 0; i < buff.length(); i++) {
                  if (out.byteAt(i) != buff.byteAt(i)) {
                    matched = false;
                    break;
                  }
                }
                if (matched) {
                  //We upgraded ok
                  ChannelPipeline p = channel.getPipeline();
                  p.replace("decoder", "wsdecoder", new WebSocketFrameDecoder());
                  p.replace("encoder", "wsencoder", new WebSocketFrameEncoder());
                  ws = new Websocket(uri, HttpClientConnection.this);
                  wsConnect.onConnect(ws);
                  return;
                }
              }
              handleException(new IllegalStateException("Invalid protocol handshake - wrong response"));
            }
          });
        }
      }
    });
    req.putHeader(HttpHeaders.Names.UPGRADE, HttpHeaders.Values.WEBSOCKET).
        putHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.UPGRADE).
        putHeader(HttpHeaders.Names.ORIGIN, "http://" + hostHeader). //TODO what about HTTPS?
        putHeader(HttpHeaders.Names.SEC_WEBSOCKET_KEY1, key1).
        putHeader(HttpHeaders.Names.SEC_WEBSOCKET_KEY2, key2).
        write(Buffer.fromChannelBuffer(buff)).
        end();
  }

  public HttpClientRequest request(String method, String uri, HttpResponseHandler responseHandler) {
    if (ws != null) {
      throw new IllegalStateException("Cannot make requests on connection if upgraded to websocket");
    }
    return new HttpClientRequest(this, method, uri, responseHandler);
  }

  // Quick get method when there's no body and it doesn't require an end
  public void getNow(String uri, HttpResponseHandler responseHandler) {
    HttpClientRequest req = get(uri, responseHandler);
    req.end();
  }

  public void getNow(String uri, Map<String, ? extends Object> headers, HttpResponseHandler responseHandler) {
    HttpClientRequest req = get(uri, responseHandler);
    req.putAllHeaders(headers);
    req.end();
  }

  public HttpClientRequest options(String uri, HttpResponseHandler responseHandler) {
    return request("OPTIONS", uri, responseHandler);
  }

  public HttpClientRequest get(String uri, HttpResponseHandler responseHandler) {
    return request("GET", uri, responseHandler);
  }

  public HttpClientRequest head(String uri, HttpResponseHandler responseHandler) {
    return request("HEAD", uri, responseHandler);
  }

  public HttpClientRequest post(String uri, HttpResponseHandler responseHandler) {
    return request("POST", uri, responseHandler);
  }

  public HttpClientRequest put(String uri, HttpResponseHandler responseHandler) {
    return request("PUT", uri, responseHandler);
  }

  public HttpClientRequest delete(String uri, HttpResponseHandler responseHandler) {
    return request("DELETE", uri, responseHandler);
  }

  public HttpClientRequest trace(String uri, HttpResponseHandler responseHandler) {
    return request("TRACE", uri, responseHandler);
  }

  public HttpClientRequest connect(String uri, HttpResponseHandler responseHandler) {
    return request("CONNECT", uri, responseHandler);
  }

  public HttpClientRequest patch(String uri, HttpResponseHandler responseHandler) {
    return request("PATCH", uri, responseHandler);
  }

  // Internal API ----------------------------------------------------------------------------------------

  //FIXME - combine these with same in HttpServerConnection and NetSocket

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

  void handleResponse(HttpClientResponse resp) {
    setContextID();
    HttpResponseHandler handler = respHandlers.poll();
    if (handler == null) {
      throw new IllegalStateException("No response handler");
    }
    currentResponse = resp;
    try {
      handler.onResponse(resp);
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  void handleChunk(Buffer buff) {
    setContextID();
    try {
      currentResponse.handleChunk(buff);
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  void handleEnd() {
    handleEnd(null);
  }

  void handleEnd(HttpChunkTrailer trailer) {
    try {
      currentResponse.handleEnd(trailer);
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  void handleWsFrame(WebSocketFrame frame) {
    if (ws != null) {
      ws.handleFrame(frame);
    }
  }


  // Internal ------------------------------------------------------------------------------------------------

  protected void handleClosed() {
    super.handleClosed();
  }

  protected String getContextID() {
    return super.getContextID();
  }

  protected void handleException(Exception e) {
    super.handleException(e);
  }

  protected void addFuture(DoneHandler done, ChannelFuture future) {
    super.addFuture(done, future);
  }

  ChannelFuture write(Object obj, HttpClientRequest req) {
    if (req != currentRequest) {
      throw new IllegalStateException("Do not interleave request writes");
    }
    return channel.write(obj);
  }

  void setCurrentRequest(HttpClientRequest req) {
    if (currentRequest != null) {
      throw new IllegalStateException("Connection is already writing a request");
    }
    this.currentRequest = req;
    this.respHandlers.add(req.getResponseHandler());
  }

  void endRequest(HttpClientRequest req) {
    if (currentRequest == null) {
      throw new IllegalStateException("No write in progress");
    }
    currentRequest = null;
  }
}
