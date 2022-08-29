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

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;

/**
 * Helper wrapper class which allows to speed-up {@link VertxHttpResponseEncoder#isContentAlwaysEmpty(HttpResponse)}
 * checks by using {@link #head()} info.<br>
 * The motivation why it's not extending {@link VertxHttpResponse} is because it favours reusing existing Netty types.
 */
final class VertxFullHttpResponse extends DefaultFullHttpResponse {

  private final boolean head;

  public VertxFullHttpResponse(boolean head, HttpVersion version, HttpResponseStatus status, HttpHeaders headers, ByteBuf buf, HttpHeaders trailingHeaders) {
    super(version, status, buf, headers, trailingHeaders);
    this.head = head;
  }

  boolean head() {
    return head;
  }
}
