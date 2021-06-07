/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.eventbus.impl;

import java.util.Iterator;

@SuppressWarnings("rawtypes")
public interface Handlers {

  Handlers add(HandlerHolder holder);

  default Handlers add(Handlers other) {
    return add(other.next(true));
  }

  Handlers remove(HandlerHolder holder);

  boolean isEmpty();

  HandlerHolder next(boolean includeLocalOnly);

  int count(boolean includeLocalOnly);

  Iterator<HandlerHolder> iterator(boolean includeLocalOnly);
}
