/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;

import java.util.Objects;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
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

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    HttpFrameImpl httpFrame = (HttpFrameImpl) o;
    return type == httpFrame.type && flags == httpFrame.flags && Objects.equals(payload, httpFrame.payload);
  }
}
