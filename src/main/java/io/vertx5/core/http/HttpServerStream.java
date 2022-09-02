package io.vertx5.core.http;

import io.netty5.buffer.api.Buffer;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.DefaultHttpContent;
import io.netty5.handler.codec.http.DefaultHttpResponse;
import io.netty5.handler.codec.http.DefaultLastHttpContent;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpMessage;
import io.netty5.handler.codec.http.HttpObject;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http.HttpVersion;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;

import java.util.Map;

public class HttpServerStream extends HttpStream {

  private final ContextInternal context;
  private final HttpServerConnection connection;
  private final String method;
  private final String uri;
  private int sc = 200;

  public HttpServerStream(ContextInternal context, HttpServerConnection connection, HttpRequest httpMsg) {
    super(connection);

    copyHeaders(httpMsg, requestHeaders());

    this.context = context;
    this.connection = connection;
    this.method = httpMsg.method().toString();
    this.uri = httpMsg.uri();
  }

  public String method() {
    return method;
  }

  public String uri() {
    return uri;
  }

  @Override
  public HttpServerStream handler(Handler<io.vertx5.core.buffer.Buffer> handler) {
    return (HttpServerStream) super.handler(handler);
  }

  public HttpServerStream endHandler(Handler<Void> handler) {
    return (HttpServerStream) super.endHandler(handler);
  }

  public HttpServerStream status(int sc) {
    this.sc = sc;
    return this;
  }

  @Override
  HttpMessage createPartialMessage(Buffer payload) {
    DefaultHttpResponse msg = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(sc));
    copyHeaders(responseHeaders(), msg);
    DefaultHttpContent content = new DefaultHttpContent(payload);
    return new PartialHttpResponse<>(msg, content);
  }

  @Override
  FullHttpResponse createFullMessage(Buffer payload) {
    DefaultFullHttpResponse msg = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(sc), payload);
    copyHeaders(responseHeaders(), msg);
    return msg;
  }
}
