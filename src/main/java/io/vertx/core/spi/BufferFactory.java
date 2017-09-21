/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.spi;

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface BufferFactory {

  Buffer buffer(int initialSizeHint);

  Buffer buffer();

  Buffer buffer(String str);

  Buffer buffer(String str, String enc);

  Buffer buffer(byte[] bytes);

  Buffer buffer(ByteBuf byteBuffer);
}
