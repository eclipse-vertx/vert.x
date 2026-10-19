/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.http;

import io.netty.buffer.ByteBuf;
import io.vertx.core.Handler;

/**
 * A decoder for content.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface ContentDecoder extends Handler<ByteBuf> {

  /**
   * A decoder discarding the content.
   */
  ContentDecoder NULL = new ContentDecoder() {
    @Override
    public void init(FlowController flowController) {
    }
    @Override
    public void handle(ByteBuf chunk) {
    }
    @Override
    public void handleEnd() {
    }
    @Override
    public void handleClose(Throwable cause) {
    }
  };

  /**
   * Init the decoder, passing the {@code flowController}, this method is called when the decoder is set on the stream.
   *
   * <p>This method is not expected to fail: checking that the decoder can decode the content of a stream belongs to
   * the creation of the decoder. A failure is a programming error, it is thrown to the caller setting the decoder
   * and the decoder is not set.
   *
   * @param flowController the flow controller
   */
  void init(FlowController flowController);

  /**
   * Signals a chunk of content, the {@code chunk} will be released after this method call, therefore the implementation
   * should either copy the chunk or increase its reference count for later use.
   *
   * @param chunk the buffer to handle
   */
  void handle(ByteBuf chunk);

  /**
   * Signals the end of the stream and no more chunk will be delivered.
   */
  void handleEnd();

  /**
   * Signals the underlying stream is closed, content chunks might still be delivered when the queue is paused.
   *
   * @param cause {@code null} when the stream closed after receiving the end, otherwise the cause of the abrupt close,
   *              e.g. a reset or a connection close, in which case nothing is delivered after this call
   */
  void handleClose(Throwable cause);

}
