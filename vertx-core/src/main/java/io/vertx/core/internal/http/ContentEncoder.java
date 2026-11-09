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
import io.vertx.core.internal.streams.WriteResult;

/**
 * An encoder for the outbound content of a stream.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface ContentEncoder {

  /**
   * Write a {@code chunk} of content, the encoder owns the {@code chunk} once this method is called and releases it
   * whatever happens: written, rejected, failed or cancelled. A failure is reported by the returned result, never by
   * throwing.
   *
   * @param chunk the chunk to write
   * @return the result of the write
   */
  WriteResult write(ByteBuf chunk);

  /**
   * Like {@link #write(ByteBuf)}, the {@code chunk} is the last chunk of content.
   *
   * @param chunk the last chunk to write
   * @return the result of the write
   */
  WriteResult end(ByteBuf chunk);

  /**
   * End the content.
   *
   * @return the result of the end
   */
  WriteResult end();

}
