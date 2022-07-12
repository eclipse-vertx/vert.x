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

package io.vertx5.core.impl;

import io.netty5.util.concurrent.FastThreadLocalThread;
import io.vertx.core.impl.ContextInternal;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class VertxThread extends FastThreadLocalThread {

  private long execStart;
  ContextInternal context;
  ClassLoader topLevelTCCL;

  public VertxThread(Runnable target, String name) {
    super(target, name);
  }

  public ContextInternal context() {
    return context;
  }

  void executeStart() {
//    if (context == null) {
//      execStart = System.nanoTime();
//    }
  }

//  void executeEnd() {
//    if (context == null) {
//      execStart = 0;
//    }
//  }

  public long startTime() {
    return execStart;
  }

}
