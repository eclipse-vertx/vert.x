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

package io.vertx.tests.http.headers;

import io.netty.handler.codec.Headers;
import io.netty.handler.codec.http3.DefaultHttp3Headers;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3HeadersAdaptorsTest extends Http2HeadersAdaptorsTest {

  @Override
  protected Headers<CharSequence, CharSequence, ?> createHeaders() {
    return new DefaultHttp3Headers();
  }
}
