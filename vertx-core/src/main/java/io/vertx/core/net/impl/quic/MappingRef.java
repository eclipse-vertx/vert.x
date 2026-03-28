/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl.quic;

import io.netty.util.Mapping;

/**
 * A cleanable proxy of {@link Mapping}
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class MappingRef<I, O> implements Mapping<I, O> {

  volatile Mapping<I, O> actual;

  @Override
  public O map(I input) {
    Mapping<I, O> val = actual;
    return val != null ? val.map(input) : null;
  }
}
