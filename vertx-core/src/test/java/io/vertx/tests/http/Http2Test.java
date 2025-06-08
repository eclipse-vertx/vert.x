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

package io.vertx.tests.http;

import io.netty.handler.codec.http2.Http2CodecUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.TestUtils;
import io.vertx.test.proxy.HAProxy;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2Test extends HttpCommonTest {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return HttpOptionsFactory.createHttp2ServerOptions(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);
  }

  @Override
  protected NetClientOptions createNetClientOptions() {
    return HttpOptionsFactory.createH2NetClientOptions();
  }

  @Override
  protected NetServerOptions createNetServerOptions() {
    return HttpOptionsFactory.createH2NetServerOptions();
  }

  @Override
  protected HAProxy createHAProxy(SocketAddress remoteAddress, Buffer header) {
    return new HAProxy(remoteAddress, header);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return HttpOptionsFactory.createHttp2ClientOptions();
  }

  @Override
  protected HttpVersion clientAlpnProtocolVersion() {
    return HttpVersion.HTTP_1_1;
  }

  @Override
  protected HttpVersion serverAlpnProtocolVersion() {
    return HttpVersion.HTTP_2;
  }

  @Override
  protected void addMoreOptions(HttpServerOptions opts) {
  }

  @Override
  protected HttpServerOptions setMaxConcurrentStreamsSettings(HttpServerOptions options, int maxConcurrentStreams) {
    return options.setInitialSettings(new Http2Settings().setMaxConcurrentStreams(maxConcurrentStreams));
  }

  @Override
  protected StreamPriorityBase generateStreamPriority() {
    return new Http2StreamPriority()
      .setDependency(TestUtils.randomPositiveInt())
      .setWeight((short) TestUtils.randomPositiveInt(255))
      .setExclusive(TestUtils.randomBoolean());
  }

  @Override
  protected StreamPriorityBase defaultStreamPriority() {
    return new Http2StreamPriority()
      .setDependency(0)
      .setWeight(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT)
      .setExclusive(false);
  }

  @Override
  protected void assertEqualsStreamPriority(StreamPriorityBase expectedStreamPriority,
                                            StreamPriorityBase actualStreamPriority) {
    assertEquals(expectedStreamPriority.getWeight(), actualStreamPriority.getWeight());
    assertEquals(expectedStreamPriority.getDependency(), actualStreamPriority.getDependency());
    assertEquals(expectedStreamPriority.isExclusive(), actualStreamPriority.isExclusive());
  }

  @Ignore
  @Test
  public void testAppendToHttpChunks() throws Exception {
    // This test does not work on http/2
  }

}
