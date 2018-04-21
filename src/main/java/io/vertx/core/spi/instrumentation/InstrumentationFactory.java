/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.spi.instrumentation;

import io.vertx.core.ServiceHelper;

/**
 *
 */
public class InstrumentationFactory {

  private static volatile Instrumentation instrumentation;

  static {
    instrumentation = ServiceHelper.loadFactoryOrNull(Instrumentation.class);
  }

  public static Instrumentation getInstrumentation() {
    return instrumentation;
  }

  public static void setInstrumentation(Instrumentation instrumentation) {
    InstrumentationFactory.instrumentation = instrumentation;
  }
}
