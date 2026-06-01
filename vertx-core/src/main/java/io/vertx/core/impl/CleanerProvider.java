/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl;

import java.lang.ref.Cleaner;

/**
 * Encapsulate a cleaner and provide a lazy instantiation policy.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CleanerProvider {

  public static CleanerProvider INSTANCE = new CleanerProvider();

  private CleanerProvider() {
  }

  /**
   * Default shared cleaner for Vert.x
   */
  private Cleaner cleaner;

  /**
   * @return the cleaner
   */
  synchronized Cleaner get() {
    Cleaner ret = cleaner;
    if (ret == null) {
      ret = Cleaner.create();
      cleaner = ret;
    }
    return ret;
  }
}
