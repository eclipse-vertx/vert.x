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
    public void handleClose(boolean abrupt) {
    }
  };

  /**
   * Init the decoder, passing the {@code flowController}, this method is called when the decoder is set on the stream.
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
   * @param abrupt whether the stream closed without receiving the end, e.g. a reset or a connection close, in which case
   *               nothing is delivered after this call
   */
  void handleClose(boolean abrupt);

}
