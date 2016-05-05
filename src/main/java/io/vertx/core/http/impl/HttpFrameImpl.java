/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;

/**
 *
 */
public class HttpFrameImpl implements HttpFrame {

  private final int type;
  private final int flags;
  private final Buffer payload;

  public HttpFrameImpl(int type, int flags, Buffer payload) {
    this.type = type;
    this.flags = flags;
    this.payload = payload;
  }

  @Override
  public int flags() {
    return flags;
  }

  @Override
  public int type() {
    return type;
  }

  @Override
  public Buffer payload() {
    return payload;
  }
}
