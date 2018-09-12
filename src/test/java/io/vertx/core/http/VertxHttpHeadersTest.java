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

package io.vertx.core.http;

import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxHttpHeadersTest extends CaseInsensitiveHeadersTest {

  @Override
  protected MultiMap newMultiMap() {
    return new VertxHttpHeaders();
  }

  @Override
  public void testHashMININT() {
    // Does not apply
  }
}
