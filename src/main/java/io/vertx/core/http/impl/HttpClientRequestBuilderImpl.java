package io.vertx.core.http.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientRequestBuilder;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpClientRequestBuilderImpl implements HttpClientRequestBuilder {

  final HttpClient client;
  HttpMethod method;
  Integer port;
  String host;
  String requestURI;
  MultiMap headers;

  public HttpClientRequestBuilderImpl(HttpClient client, HttpMethod method) {
    this.client = client;
    this.method = method;
  }

  private HttpClientRequestBuilderImpl(HttpClientRequestBuilderImpl other) {
    this.client = other.client;
    this.method = other.method;
    this.port = other.port;
    this.host = other.host;
    this.requestURI = other.requestURI;
    this.headers = other.headers != null ? new CaseInsensitiveHeaders().addAll(other.headers) : null;
  }

  @Override
  public HttpClientRequestBuilder method(HttpMethod method) {
    HttpClientRequestBuilderImpl other = new HttpClientRequestBuilderImpl(this);
    other.method = method;
    return other;
  }

  @Override
  public HttpClientRequestBuilder port(int port) {
    HttpClientRequestBuilderImpl other = new HttpClientRequestBuilderImpl(this);
    other.port = port;
    return other;
  }

  @Override
  public HttpClientRequestBuilder host(String host) {
    HttpClientRequestBuilderImpl other = new HttpClientRequestBuilderImpl(this);
    other.host = host;
    return other;
  }

  @Override
  public HttpClientRequestBuilder requestURI(String requestURI) {
    HttpClientRequestBuilderImpl other = new HttpClientRequestBuilderImpl(this);
    other.requestURI = requestURI;
    return other;
  }

  @Override
  public HttpClientRequestBuilder putHeader(String name, String value) {
    HttpClientRequestBuilderImpl other = new HttpClientRequestBuilderImpl(this);
    if (other.headers == null) {
      other.headers = new CaseInsensitiveHeaders();
    }
    other.headers.add(name, value);
    return other;
  }

  @Override
  public void request(ReadStream<Buffer> stream, Handler<AsyncResult<HttpClientResponse>> handler) {
    perform(stream, handler);
  }

  @Override
  public void request(Handler<AsyncResult<HttpClientResponse>> handler) {
    perform(null, handler);
  }

  private void perform(ReadStream<Buffer> stream, Handler<AsyncResult<HttpClientResponse>> handler) {
    Future<HttpClientResponse> fut = Future.future();
    HttpClientRequest req = client.request(method, port, host, requestURI);
    if (headers != null) {
      req.headers().addAll(headers);
    }
    req.exceptionHandler(err -> {
      if (!fut.isComplete()) {
        fut.fail(err);
      }
    });
    req.handler(resp -> {
      if (!fut.isComplete()) {
        fut.complete(resp);
      }
    });
    if (stream != null) {
      if (headers == null || !headers.contains("Content-Length")) {
        req.setChunked(true);
      }
      Pump pump = Pump.pump(stream, req);
      stream.exceptionHandler(err -> {
        req.reset();
        if (!fut.isComplete()) {
          fut.fail(err);
        }
      });
      stream.endHandler(v -> {
        pump.stop();
        req.end();
      });
      pump.start();
    } else {
      req.end();
    }
    fut.setHandler(handler);
  }
}
