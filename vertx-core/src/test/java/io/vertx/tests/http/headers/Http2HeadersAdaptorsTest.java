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

import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import org.junit.Before;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http2HeadersAdaptorsTest extends HttpHeadersAdaptorsTestBase {

  @Before
  public void setUp() {
    DefaultHttp2Headers headers = new DefaultHttp2Headers();
    super.headers = headers;
    super.map = new Http2HeadersAdaptor(headers);
  }

  @Override
  protected MultiMap newMultiMap() {
    return new Http2HeadersAdaptor(new DefaultHttp2Headers());
  }

}