package io.vertx.tests.net;

import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.http.HttpTestBase;
import io.vertx.test.proxy.TestProxyBase;

import java.io.File;

public abstract class ProxyProviderTest extends VertxTestBase {

  protected SocketAddress testAddress;
  protected NetServer server;
  protected NetClient client;
  protected TestProxyBase proxy;
  private File tmp;

  protected NetServerOptions createNetServerOptions() {
    return NetTest.createH3NetServerOptions();
  }

  protected NetClientOptions createNetClientOptions() {
    return NetTest.createH3NetClientOptions();
  }

  protected HttpServerOptions createBaseServerOptions() {
    return HttpTestBase.createH3HttpServerOptions(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST);
  }

  protected HttpClientOptions createBaseClientOptions() {
    return HttpTestBase.createH3HttpClientOptions();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    if (USE_DOMAIN_SOCKETS) {
      assertTrue("Native transport not enabled", TRANSPORT.implementation().supportsDomainSockets());
      tmp = TestUtils.tmpFile(".sock");
      testAddress = SocketAddress.domainSocketAddress(tmp.getAbsolutePath());
    } else {
      testAddress = SocketAddress.inetSocketAddress(1234, "localhost");
    }
    client = vertx.createNetClient(createNetClientOptions().setConnectTimeout(1000));
    server = vertx.createNetServer(createNetServerOptions());
  }

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.getAddressResolverOptions().setHostsValue(Buffer.buffer("" +
      "127.0.0.1 localhost\n" +
      "127.0.0.1 host1\n" +
      "127.0.0.1 host2.com\n" +
      "127.0.0.1 example.com"));
    return options;
  }

  @Override
  protected void tearDown() throws Exception {
    if (tmp != null) {
      tmp.delete();
    }
    if (proxy != null) {
      proxy.stop();
    }
    super.tearDown();
  }

}
