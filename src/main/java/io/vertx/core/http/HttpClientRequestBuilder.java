package io.vertx.core.http;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface HttpClientRequestBuilder {

  HttpClientRequestBuilder method(HttpMethod method);

  HttpClientRequestBuilder port(int port);

  HttpClientRequestBuilder host(String host);

  HttpClientRequestBuilder requestURI(String requestURI);

  HttpClientRequestBuilder putHeader(String name, String value);

  void request(ReadStream<Buffer> stream, Handler<AsyncResult<HttpClientResponse>> handler);

  void request(Handler<AsyncResult<HttpClientResponse>> handler);

}
