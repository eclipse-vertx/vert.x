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
package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.vertx.core.Handler;

/**
 * A decoder for content.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface ContentDecoder extends Handler<ByteBuf> {

  /**
   * Init the decoder, passing the {@code flowController}.
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
   * Signals the end of the stream
   */
  void handleEnd();

  /**
   * Destroy the decoder.
   */
  void destroy();

}
