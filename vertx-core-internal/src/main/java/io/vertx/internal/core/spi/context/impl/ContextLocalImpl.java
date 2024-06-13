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
package io.vertx.internal.core.spi.context.impl;

import io.vertx.internal.core.spi.context.ContextLocal;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ContextLocalImpl<T> implements ContextLocal<T> {

  public final int index;

  public ContextLocalImpl(int index) {
    this.index = index;
  }

  public ContextLocalImpl() {
    this.index = LocalSeq.next();
  }
}
