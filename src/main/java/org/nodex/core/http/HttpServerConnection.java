package org.nodex.core.http;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioSocketChannelConfig;
import org.jboss.netty.handler.ssl.SslHandler;
import org.nodex.core.ConnectionBase;
import org.nodex.core.DoneHandler;
import org.nodex.core.ExceptionHandler;
import org.nodex.core.NodexInternal;
import org.nodex.core.buffer.Buffer;

/**
 * User: tfox
 * Date: 04/07/11
 * Time: 16:30
 */
public class HttpServerConnection extends ConnectionBase {

  private HttpRequestHandler mainHandler;
  private HttpRequestHandler getHandler;
  private HttpRequestHandler optionsHandler;
  private HttpRequestHandler headHandler;
  private HttpRequestHandler postHandler;
  private HttpRequestHandler putHandler;
  private HttpRequestHandler deleteHandler;
  private HttpRequestHandler traceHandler;
  private HttpRequestHandler connectHandler;
  private HttpRequestHandler patchHandler;

  private HttpServerRequest currentRequest;
  private HttpServerResponse currentResponse;

  HttpServerConnection(Channel channel, String contextID, Thread th) {
    super(channel, contextID, th);
  }

  // Public API ----------------------------------------------------------------------------------------------------

  public void request(HttpRequestHandler handler) {
    this.mainHandler = handler;
  }

  public void options(HttpRequestHandler handler) {
    this.optionsHandler = handler;
  }

  public void get(HttpRequestHandler handler) {
    this.getHandler = handler;
  }

  public void head(HttpRequestHandler handler) {
    this.headHandler = handler;
  }

  public void post(HttpRequestHandler handler) {
    this.postHandler = handler;
  }

  public void put(HttpRequestHandler handler) {
    this.putHandler = handler;
  }

  public void delete(HttpRequestHandler handler) {
    this.deleteHandler = handler;
  }

  public void trace(HttpRequestHandler handler) {
    this.traceHandler = handler;
  }

  public void connect(HttpRequestHandler handler) {
    this.connectHandler = handler;
  }

  public void patch(HttpRequestHandler handler) {
    this.patchHandler = handler;
  }

  // Internal API --------------------------------------------------------------------------------------------------

  // Called by Netty

  //FIXME - what if one request comes in, then another one comes in, but then we write the response back for
  //the second one before the first one?
  void handleRequest(HttpServerRequest req, HttpServerResponse resp) {
    setContextID();
    try {
      this.currentRequest = req;
      this.currentResponse = resp;
      if (mainHandler != null) {
        mainHandler.onRequest(req, resp);
      } else {
        if (getHandler != null && "GET".equals(req.method)) {
          getHandler.onRequest(req, resp);
        } else if (postHandler != null && "POST".equals(req.method)) {
          postHandler.onRequest(req, resp);
        } else if (putHandler != null && "PUT".equals(req.method)) {
          putHandler.onRequest(req, resp);
        } else if (headHandler != null && "HEAD".equals(req.method)) {
          headHandler.onRequest(req, resp);
        } else if (deleteHandler != null && "DELETE".equals(req.method)) {
          deleteHandler.onRequest(req, resp);
        } else if (traceHandler != null && "TRACE".equals(req.method)) {
          traceHandler.onRequest(req, resp);
        } else if (connectHandler != null && "CONNECT".equals(req.method)) {
          connectHandler.onRequest(req, resp);
        } else if (optionsHandler != null && "OPTIONS".equals(req.method)) {
          optionsHandler.onRequest(req, resp);
        } else if (patchHandler != null && "PATCH".equals(req.method)) {
          patchHandler.onRequest(req, resp);
        }
      }
    } catch (Throwable t) {
      handleThrowable(t);
    }
  }

  //TODO tidy up exception handling on these
  void handleChunk(Buffer chunk) {
    try {
      if (currentRequest != null) {
        setContextID();
        currentRequest.handleData(chunk);
      }
    } catch (Throwable t) {
      handleThrowable(t);
    }
  }

  void handleEnd() {
    try {
      setContextID();
      currentRequest.handleEnd();
      if (currentResponse != null) {
        pause();
      }
      currentRequest = null;
    } catch (Throwable t) {
      handleThrowable(t);
    }
  }

  void handleInterestedOpsChanged() {
    try {
      if (currentResponse != null) {
        if ((channel.getInterestOps() & Channel.OP_WRITE) == Channel.OP_WRITE) {
          currentResponse.handleInterestedOpsChanged();
        }
      }
    } catch (Throwable t) {
      handleThrowable(t);
    }
  }

  // Called by request / response

  // We need to ensure that pipelined requests have their responses written in the same order, or we can get in a mess
  // We do this by pausing the delivery of the next request until the current response has been completed
  void responseComplete() {
    currentResponse = null;
    if (currentRequest == null) {
      resume();
    }
  }

  ChannelFuture write(Object obj) {
    return channel.write(obj);
  }

  boolean isSSL() {
    return channel.getPipeline().get(SslHandler.class) != null;
  }

  private void handleThrowable(Throwable t) {
    //We log errors otherwise they will get swallowed
    //TODO logging can be improved
    t.printStackTrace();
    if (t instanceof RuntimeException) {
      throw (RuntimeException) t;
    } else if (t instanceof Error) {
      throw (Error) t;
    }
  }
}
