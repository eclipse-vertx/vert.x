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
package io.vertx.tests.net;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.*;
import io.vertx.test.proxy.HAProxy;
import io.vertx.test.proxy.HttpProxy;
import io.vertx.test.proxy.Socks4Proxy;
import io.vertx.test.proxy.SocksProxy;
import io.vertx.tests.http.HttpOptionsFactory;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static io.vertx.test.http.HttpTestBase.*;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http2NetTest extends NetTest {

  protected NetServerOptions createNetServerOptions() {
    return new NetServerOptions();
  }

  protected NetClientOptions createNetClientOptions() {
    return new NetClientOptions();
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return HttpOptionsFactory.createHttp2ClientOptions();
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return HttpOptionsFactory.createHttp2ServerOptions(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);
  }

  @Override
  protected HttpServerOptions createHttpServerOptionsForNetTest() {
    return new HttpServerOptions();
  }

  @Override
  protected HttpClientOptions createHttpClientOptionsForNetTest() {
    return new HttpClientOptions();
  }

  protected Socks4Proxy createSocks4Proxy() {
    return new Socks4Proxy();
  }

  protected SocksProxy createSocksProxy() {
    return new SocksProxy();
  }

  protected HttpProxy createHttpProxy() {
    return new HttpProxy();
  }

  @Override
  protected HAProxy createHAProxy(SocketAddress remoteAddress, Buffer header) {
    return new HAProxy(remoteAddress, header);
  }

}
