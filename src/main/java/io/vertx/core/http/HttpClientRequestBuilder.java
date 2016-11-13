package io.vertx.core.http;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
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

  // Could be called end() instead ?
  // For RxJava we could generate with send(Observable<Buffer> stream
  // would it work well ? i.e could the Observable<Buffer> could be subscribed easily ?
  // in the case of FileSystem it *should* work (need to check):

  // Observable<Buffer> fileObs = fileSystem.open("file.txt", new OpenOptions());
  // Observable<HttpClientResponse> respObs = httpClient.createPost().send(fileObs);
  // respObs.subscribe(resp -> {});
  void send(ReadStream<Buffer> stream, Handler<AsyncResult<HttpClientResponse>> handler);

  void send(Handler<AsyncResult<HttpClientResponse>> handler);

  HttpClientResponseBuilder<Buffer> asBuffer();

  HttpClientResponseBuilder<String> asString();

  HttpClientResponseBuilder<JsonObject> asJsonObject();

  <T> HttpClientResponseBuilder<T> as(Class<T> clazz);

}
