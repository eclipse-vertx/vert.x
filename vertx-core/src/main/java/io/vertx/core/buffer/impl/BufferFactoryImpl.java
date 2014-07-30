/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
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
