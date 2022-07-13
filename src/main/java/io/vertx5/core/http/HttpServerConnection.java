package io.vertx5.core.http;

import io.netty5.handler.codec.http.HttpMessage;
import io.netty5.handler.codec.http.HttpObject;
import io.netty5.handler.codec.http.HttpRequest;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx5.core.buffer.Buffer;
import io.vertx5.core.net.NetSocket;

public class HttpServerConnection extends HttpConnection {

  private final Handler<HttpServerStream> handler;
  private HttpServerStream stream;

  HttpServerConnection(NetSocket so, Handler<HttpServerStream> handler) {
    super(so);
    this.handler = handler;
  }

  Future<Void> writeObject(HttpObject obj) {
    return so.writeMessage(obj);
  }

  @Override
  void handleMessage(HttpMessage headers) {
    HttpRequest request = (HttpRequest) headers;
    request.method();
    ContextInternal ctx = (ContextInternal) so.context();
    stream = new HttpServerStream(ctx, this, request);
    ctx.emit(stream, handler);
  }

  @Override
  void handleContent(Buffer chunk) {
    stream.handleContent(chunk);
  }

  @Override
  void handleLast() {
    stream.handleLast();
    stream = null;
  }
}
