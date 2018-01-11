/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.buffer.impl;

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.spi.BufferFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class BufferFactoryImpl implements BufferFactory {

  @Override
  public Buffer buffer(int initialSizeHint) {
    return new BufferImpl(initialSizeHint);
  }

  @Override
  public Buffer buffer() {
    return new BufferImpl();
  }

  @Override
  public Buffer buffer(String str) {
    return new BufferImpl(str);
  }

  @Override
  public Buffer buffer(String str, String enc) {
    return new BufferImpl(str, enc);
  }

  @Override
  public Buffer buffer(byte[] bytes) {
    return new BufferImpl(bytes);
  }

  @Override
  public Buffer buffer(ByteBuf byteBuffer) {
    return new BufferImpl(byteBuffer);
  }
}
