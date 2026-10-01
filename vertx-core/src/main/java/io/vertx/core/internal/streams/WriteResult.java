package io.vertx.core.internal.streams;

import io.vertx.core.Future;

/**
 * Result of a write operation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface WriteResult<T> extends Future<T> {

  /**
   * @return whether the stream was writable after the write operation was submitted
   */
  boolean isWritable();

}
