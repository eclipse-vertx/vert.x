package io.vertx.core.http;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.Future;

import java.nio.channels.Channel;

public interface FileSender<T extends Channel> {

  /**
   * Same as {@link #sendFile(T, String, long)} using length @code{Long.MAX_VALUE} which means until the end of the
   * file.
   *
   * @param channel the file channel to the file to serve
   * @param extension the file extension if known
   * @return a future completed with the body result
   */
  default Future<Void> sendFile(T channel, String extension) {
    return sendFile(channel, extension, 0);
  }

  /**
   * Same as {@link #sendFile(T, String, long, long)} using length @code{Long.MAX_VALUE} which means until the end of the
   * file.
   *
   * @param channel the file channel to the file to serve
   * @param extension the file extension if known
   * @param offset offset to start serving from
   * @return a future completed with the body result
   */
  default Future<Void> sendFile(T channel, String extension, long offset) {
    return sendFile(channel, extension, offset, Long.MAX_VALUE);
  }

  /**
   * Ask the OS to stream a file as specified by {@code channel} directly
   * from disk to the outgoing connection, bypassing userspace altogether
   * (where supported by the underlying operating system). Contrary to {@link HttpServerResponse#sendFile(String, long, long)},
   * the caller is responsible to close {@code channel} when no more needed.
   * This is a very efficient way to serve files.<p>
   * The actual serve is asynchronous and may not complete until some time after this method has returned.
   *
   * @param channel the file channel to the file to serve
   * @param offset offset to start serving from
   * @param length the number of bytes to send
   * @return a future completed with the body result
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  Future<Void> sendFile(T channel, String extension, long offset, long length);

}
