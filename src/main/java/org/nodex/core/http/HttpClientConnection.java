package org.nodex.core.http;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioSocketChannelConfig;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.nodex.core.ConnectionBase;
import org.nodex.core.DoneHandler;
import org.nodex.core.ExceptionHandler;
import org.nodex.core.NodexInternal;
import org.nodex.core.buffer.Buffer;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * User: timfox
 * Date: 22/07/2011
 * Time: 11:49
 *
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

  // Public API ------------------------------------------------------------------------------------------------

  public HttpClientRequest request(String method, String uri, HttpResponseHandler responseHandler) {
    return new HttpClientRequest(this, method, uri, responseHandler);
  }

  // Quick get method when there's no body and it doesn't require an end
  public void getNow(String uri, HttpResponseHandler responseHandler) {
    HttpClientRequest req = get(uri, responseHandler);
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
      //TODO better exception handling
      t.printStackTrace(System.err);
    }
  }

  void handleResponse(HttpClientResponse resp) {
    setContextID();
    HttpResponseHandler handler = respHandlers.poll();
    if (handler == null) {
      throw new IllegalStateException("No response handler");
    }
    currentResponse = resp;
    handler.onResponse(resp);
  }

  void handleChunk(Buffer buff) {
    setContextID();
    currentResponse.handleChunk(buff);
  }

  // Called from request / response

  void handleEnd(Map<String, String> trailers) {
    currentResponse.handleEnd(trailers);
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
