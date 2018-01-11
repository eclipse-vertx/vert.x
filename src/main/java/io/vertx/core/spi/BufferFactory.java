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
