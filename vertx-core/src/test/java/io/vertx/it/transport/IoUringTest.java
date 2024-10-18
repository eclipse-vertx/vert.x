/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.it.transport;

import io.vertx.core.impl.Utils;
import io.vertx.core.transport.Transport;
import io.vertx.test.core.AsyncTestBase;
import org.junit.Test;

public class IoUringTest extends AsyncTestBase {
  @Test
  public void testNativeTransportFallback() {
    if (Utils.isLinux()) {
      Transport nativeTransport = Transport.nativeTransport();
      assertEquals("io_uring", nativeTransport.name());
    }
  }
}
