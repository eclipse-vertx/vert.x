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
