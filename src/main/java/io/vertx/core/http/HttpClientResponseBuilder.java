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
public interface HttpClientResponseBuilder<T> {

  void send(Handler<AsyncResult<HttpResponse<T>>> handler);

  void send(ReadStream<Buffer> stream, Handler<AsyncResult<HttpResponse<T>>> handler);

}
