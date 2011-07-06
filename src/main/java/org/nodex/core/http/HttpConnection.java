package org.nodex.core.http;

import org.jboss.netty.channel.Channel;
import org.nodex.core.buffer.Buffer;

/**
 * User: tfox
 * Date: 04/07/11
 * Time: 16:30
 */
public class HttpConnection {

  private final Channel channel;

  HttpConnection(Channel channel) {
    this.channel = channel;
  }

  private HttpCallback httpCallback;

  private volatile HttpRequest currentRequest;

  public void request(HttpCallback httpCallback) {
    this.httpCallback = httpCallback;
  }

  void handleRequest(HttpRequest req) {
    try {
      this.currentRequest = req;
      if (httpCallback != null) {
        httpCallback.onRequest(req, new HttpResponse(channel, false));
      }
    } catch (Throwable t) {
      handleThrowable(t);
    }
  }

  void handleChunk(Buffer chunk) {
    try {
      if (currentRequest != null) {
        currentRequest.dataReceived(chunk);
      }
    } catch (Throwable t) {
      handleThrowable(t);
    }
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
