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

package io.vertx.it.transport;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.internal.VertxInternal;
import io.vertx.test.core.AsyncTestBase;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ServiceLoadedTransportTest extends AsyncTestBase {

  private Vertx vertx;

  @Override
  protected void tearDown() throws Exception {
    close(vertx);
    super.tearDown();
  }


  @Test
  public void testFallbackOnJDK() throws Exception {
    vertx = Vertx.vertx(new VertxOptions().setPreferNativeTransport(true));
    assertTrue(vertx.isNativeTransportEnabled());
    assertNotSame(CustomTransport.class, ((VertxInternal)vertx).transport());
  }
}
