package io.vertx5.core.http;

import io.netty5.handler.codec.http.HttpMessage;
import io.netty5.handler.codec.http.HttpObject;
import io.netty5.handler.codec.http.HttpResponse;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.ContextInternal;
import io.vertx5.core.buffer.Buffer;
import io.vertx5.core.net.NetSocket;

import java.util.ArrayDeque;

public class HttpClientConnection extends HttpConnection {

  private ArrayDeque<Promise<HttpClientStream>> pending = new ArrayDeque<>();
  private HttpClientStream current;

  HttpClientConnection(NetSocket so) {
    super(so);
  }

  public synchronized Future<HttpClientStream> createStream() {
    ContextInternal ctx = (ContextInternal) so.context();
    if (current == null) {
      HttpClientStream stream = new HttpClientStream(this);
      current = stream;
      return ctx.succeededFuture(stream);
    } else {
      Promise<HttpClientStream> waiter = ctx.promise();
      pending.add(waiter);
      return waiter.future();
    }
  }

  void handleMessage(HttpMessage headers) {
    HttpClientStream stream = current;
    if (stream != null) {
      stream.handleHeaders((HttpResponse) headers);
    }
  }

  void handleContent(Buffer chunk) {
    HttpClientStream stream = current;
    if (stream != null) {
      stream.handleContent(chunk);
    }
  }

  void handleLast() {
    HttpClientStream stream = current;
    if (stream != null) {
      stream.handleLast();
    }
    Promise<HttpClientStream> next;
    synchronized (HttpClientConnection.this) {
      next = pending.poll();
      if (next != null) {
        stream = new HttpClientStream(this);
        current = stream;
      } else {
        current = null;
        return;
      }
    }
    next.complete(stream);
  }

  Future<Void> writeObject(HttpObject obj) {
    return so.writeMessage(obj);
  }
}
