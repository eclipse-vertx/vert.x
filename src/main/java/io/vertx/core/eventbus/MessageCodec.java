/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.eventbus;

import io.vertx.core.buffer.Buffer;

/**
 *
 * Instances of this class must be stateless as they will be used concurrently.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface MessageCodec<S, R> {

  // Called when object is encoded to wire
  void encodeToWire(Buffer buffer, S s);

  // Called when object is decoded from wire
  R decodeFromWire(int pos, Buffer buffer);

  // Used when sending locally and no wire involved
  // Must, at least, make a copy of the message if it is not immutable
  R transform(S s);

  String name();

  byte systemCodecID();
}
