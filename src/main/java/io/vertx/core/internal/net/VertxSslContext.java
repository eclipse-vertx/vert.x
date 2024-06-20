/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.net;

import io.netty.handler.ssl.DelegatingSslContext;
import io.netty.handler.ssl.SslContext;

/**
 * Convenience to obtain the wrapped context that is useful in some cases.
 */
public abstract class VertxSslContext extends DelegatingSslContext {

  private final SslContext wrapped;

  public VertxSslContext(SslContext ctx) {
    super(ctx);

    this.wrapped = ctx;
  }

  /**
   * @return the wrapped context
   */
  public final SslContext unwrap() {
    return wrapped;
  }
}
