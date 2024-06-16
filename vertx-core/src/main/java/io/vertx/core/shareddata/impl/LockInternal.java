/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.shareddata.impl;

import io.vertx.core.shareddata.Lock;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface LockInternal extends Lock {

  /**
   * @return an estimate of the number of waiters
   */
  int waiters();

}
