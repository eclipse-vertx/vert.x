package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.VertxGen;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface SendContext<T> {

  Message<T> message();

  void next();
}
