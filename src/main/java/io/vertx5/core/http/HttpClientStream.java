package io.vertx5.core.http;

import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.DefaultHttpContent;
import io.netty5.handler.codec.http.DefaultHttpRequest;
import io.netty5.handler.codec.http.DefaultHttpResponse;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.HttpMessage;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpResponse;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http.HttpVersion;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.impl.ContextInternal;
import io.vertx5.core.buffer.Buffer;

public class HttpClientStream extends HttpStream {

  private final HttpClientConnection connection;
  private String httpMethod;
  private String uri;
  private Promise<Void> response;
  private int statusCode;

  public HttpClientStream(HttpClientConnection connection) {
    super(connection);
    this.connection = connection;
    this.response = ((ContextInternal)connection.so.context()).promise();
  }

  public HttpClientStream method(String method) {
    httpMethod = method;
    return this;
  }

  public HttpClientStream uri(String s) {
    uri = s;
    return this;
  }

  public Future<Void> response() {
    return response.future();
  }

  public int statusCode() {
    return statusCode;
  }

  @Override
  public HttpClientStream handler(Handler<Buffer> handler) {
    return (HttpClientStream) super.handler(handler);
  }

  @Override
  public HttpClientStream endHandler(Handler<Void> handler) {
    return (HttpClientStream) super.endHandler(handler);
  }

  @Override
  HttpMessage createPartialMessage(io.netty5.buffer.api.Buffer payload) {
    DefaultHttpRequest msg = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(httpMethod), uri);
    copyHeaders(requestHeaders(), msg);
    DefaultHttpContent content = new DefaultHttpContent(payload);
    return new PartialHttpRequest<>(msg, content);
  }

  @Override
  FullHttpRequest createFullMessage(io.netty5.buffer.api.Buffer payload) {
    if (httpMethod == null) {
      throw new IllegalStateException("Missing HTTP method");
    }
    if (uri == null) {
      throw new IllegalStateException("Missing request URI");
    }
    DefaultFullHttpRequest msg = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(httpMethod), uri, payload);
    copyHeaders(requestHeaders(), msg);
    return msg;
  }

  void handleHeaders(HttpResponse response) {
    statusCode = response.status().code();
    copyHeaders(response, responseHeaders());
    this.response.complete();
  }
}
