/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.HAProxyMessageCompletionHandler;
import io.vertx.test.http.HttpTestBase;
import io.vertx.test.proxy.HAProxy;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assume.assumeTrue;

public abstract class HAProxyTest extends HttpTestBase {

  @Test
  public void testHAProxyProtocolIdleTimeout() throws Exception {
    HAProxy proxy = new HAProxy(testAddress, Buffer.buffer());
    proxy.start(vertx);

    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().
      setProxyProtocolTimeout(2).
      setUseProxyProtocol(true));
    server.requestHandler(req -> fail("Should not be called"));
    startServer(testAddress);
    vertx.createNetClient().connect(proxy.getPort(), proxy.getHost()).onComplete(res -> {
      res.result().closeHandler(event -> testComplete());
    });
    try {
      await();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testHAProxyProtocolIdleTimeoutNotHappened() throws Exception {
    waitFor(2);
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion1TCP4ProtocolHeader(remote, local);

    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);

    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().
      setProxyProtocolTimeout(100).
      setProxyProtocolTimeoutUnit(TimeUnit.MILLISECONDS).
      setUseProxyProtocol(true));
    server.requestHandler(req -> {
      req.response().end();
      complete();
    });
    startServer(testAddress);
    client.request(HttpMethod.GET, proxy.getPort(), proxy.getHost(), DEFAULT_TEST_URI)
      .compose(HttpClientRequest::send)
      .onComplete(onSuccess(v -> complete()));
    try {
      await();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testHAProxyProtocolVersion1TCP4() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion1TCP4ProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion1TCP6() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "2001:db8:85a3:0:0:8a2e:370:7334");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "2001:db8:85a3:0:0:8a2e:370:7333");
    Buffer header = HAProxy.createVersion1TCP6ProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion1Unknown() throws Exception {
    assumeTrue(testAddress.isInetSocket());
    Buffer header = HAProxy.createVersion1UnknownProtocolHeader();
    testHAProxyProtocolAccepted(header, null, null);
  }

  @Test
  public void testHAProxyProtocolVersion2TCP4() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion2TCP4ProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion2TCP6() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "2001:db8:85a3:0:0:8a2e:370:7334");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "2001:db8:85a3:0:0:8a2e:370:7333");
    Buffer header = HAProxy.createVersion2TCP6ProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion2UnixSocket() throws Exception {
    SocketAddress remote = SocketAddress.domainSocketAddress("/tmp/remoteSocket");
    SocketAddress local = SocketAddress.domainSocketAddress("/tmp/localSocket");
    Buffer header = HAProxy.createVersion2UnixStreamProtocolHeader(remote, local);
    testHAProxyProtocolAccepted(header, remote, local);
  }

  @Test
  public void testHAProxyProtocolVersion2Unknown() throws Exception {
    assumeTrue(testAddress.isInetSocket());
    Buffer header = HAProxy.createVersion2UnknownProtocolHeader();
    testHAProxyProtocolAccepted(header, null, null);
  }


  private void testHAProxyProtocolAccepted(Buffer header, SocketAddress remote, SocketAddress local) throws Exception {
    /*
     * In case remote / local is null then we will use the connected remote / local address from the proxy. This is needed
     * in order to test unknown protocol since we will use the actual connected addresses and ports.
     * This is only valid when testAddress is an InetSocketAddress. If testAddress is a DomainSocketAddress then
     * remoteAddress and localAddress are null
     *
     * Have in mind that proxies connectionRemoteAddress is the server request local address and proxies connectionLocalAddress is the
     * server request remote address.
     * */
    waitFor(2);
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);

    server.close();
    server = vertx.createHttpServer(createBaseServerOptions()
        .setUseProxyProtocol(true))
      .requestHandler(req -> {
        assertAddresses(remote == null && testAddress.isInetSocket() ?
            proxy.getConnectionLocalAddress() :
            remote,
          req.remoteAddress());
        assertAddresses(local == null && testAddress.isInetSocket() ?
            proxy.getConnectionRemoteAddress() :
            local,
          req.localAddress());
        req.response().end();
        complete();
      });
    startServer(testAddress);

    client.request(new RequestOptions()
      .setHost(proxy.getHost())
      .setPort(proxy.getPort())
      .setURI(DEFAULT_TEST_URI)).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(event -> complete()));
    }));
    try {
      await();
    } finally {
      proxy.stop();
    }
  }


  @Test
  public void testHAProxyProtocolVersion2UDP4() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion2UDP4ProtocolHeader(remote, local);
    testHAProxyProtocolRejected(header);
  }

  @Test
  public void testHAProxyProtocolVersion2UDP6() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "2001:db8:85a3:0:0:8a2e:370:7334");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "2001:db8:85a3:0:0:8a2e:370:7333");
    Buffer header = HAProxy.createVersion2UDP6ProtocolHeader(remote, local);
    testHAProxyProtocolRejected(header);
  }

  @Test
  public void testHAProxyProtocolVersion2UnixDataGram() throws Exception {
    SocketAddress remote = SocketAddress.domainSocketAddress("/tmp/remoteSocket");
    SocketAddress local = SocketAddress.domainSocketAddress("/tmp/localSocket");
    Buffer header = HAProxy.createVersion2UnixDatagramProtocolHeader(remote, local);
    testHAProxyProtocolRejected(header);
  }

  private void testHAProxyProtocolRejected(Buffer header) throws Exception {
    waitFor(2);
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);
    server.close();

    server = vertx.createHttpServer(createBaseServerOptions()
        .setUseProxyProtocol(true))
      .exceptionHandler(ex -> {
        if (ex.equals(HAProxyMessageCompletionHandler.UNSUPPORTED_PROTOCOL_EXCEPTION))
          complete();
      })
      .requestHandler(req -> fail());

    startServer(testAddress);
    client.request(new RequestOptions()
        .setPort(proxy.getPort())
        .setHost(proxy.getHost())
        .setURI(DEFAULT_TEST_URI))
      .compose(HttpClientRequest::send)
      .onComplete(onFailure(req -> complete()));

    try {
      await();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testHAProxyProtocolEmptyHeader() throws Exception {
    testHAProxyProtocolIllegal(Buffer.buffer());
  }

  @Test
  public void testHAProxyProtocolIllegalHeader() throws Exception {
    //IPv4 remote IPv6 Local
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "2001:db8:85a3:0:0:8a2e:370:7333");
    Buffer header = HAProxy.createVersion1TCP4ProtocolHeader(remote, local);
    testHAProxyProtocolIllegal(header);
  }

  private void testHAProxyProtocolIllegal(Buffer header) throws Exception {
    waitFor(2);
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);
    server.close();

    server = vertx.createHttpServer(createBaseServerOptions()
        .setUseProxyProtocol(true))
      .exceptionHandler(ex -> {
        if (ex instanceof io.netty.handler.codec.haproxy.HAProxyProtocolException)
          complete();
      })
      .requestHandler(req -> fail());

    startServer(testAddress);
    client.request(new RequestOptions()
        .setPort(proxy.getPort())
        .setHost(proxy.getHost())
        .setURI(DEFAULT_TEST_URI))
      .compose(HttpClientRequest::send)
      .onComplete(onFailure(ex -> complete()));
    await();
    proxy.stop();
  }

  private void assertAddresses(SocketAddress address1, SocketAddress address2) {
    if (address1 == null || address2 == null)
      assertEquals(address1, address2);
    else {
      assertEquals(address1.hostAddress(), address2.hostAddress());
      assertEquals(address1.port(), address2.port());
    }
  }
}
