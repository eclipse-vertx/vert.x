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
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.HAProxyMessageCompletionHandler;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.http.HttpTestBase2;
import io.vertx.test.proxy.HAProxy;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.assertj.core.api.Assertions;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public abstract class HAProxyTest extends HttpTestBase2 {

  @Test
  public void testHAProxyProtocolIdleTimeout(Checkpoint checkpoint) throws Exception {
    HAProxy proxy = new HAProxy(testAddress, Buffer.buffer());
    proxy.start(vertx);

    try {

      server = vertx.createHttpServer(createBaseServerOptions().
        setProxyProtocolTimeout(2).
        setUseProxyProtocol(true));
      server.requestHandler(req -> fail("Should not be called"));
      startServer(testAddress);
      NetClient client = vertx.createNetClient();
      client.connect(proxy.getPort(), proxy.getHost())
        .onComplete(res -> {
        res.result().closeHandler(event -> checkpoint.succeed());
      });

      checkpoint.awaitSuccess();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testHAProxyProtocolIdleTimeoutNotHappened() throws Exception {
    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion1TCP4ProtocolHeader(remote, local);

    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);

    try {
      server = vertx
        .createHttpServer(createBaseServerOptions().
          setProxyProtocolTimeout(100).
          setProxyProtocolTimeoutUnit(TimeUnit.MILLISECONDS).
          setUseProxyProtocol(true))
        .requestHandler(req -> req.response().end());
      startServer(testAddress);
      client.request(HttpMethod.GET, proxy.getPort(), proxy.getHost(), DEFAULT_TEST_URI)
        .compose(HttpClientRequest::send)
        .await();
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
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);

    try {
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
          assertUniqueIdTLV(header, req.connection().proxyProtocolV2HeaderTLVs());
          req.response().end();
        });

      startServer(testAddress);

      client.request(new RequestOptions()
          .setHost(proxy.getHost())
          .setPort(proxy.getPort())
          .setURI(DEFAULT_TEST_URI))
        .compose(request -> request
          .send()
          .compose(HttpClientResponse::end))
        .await();

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

    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);

    AtomicBoolean unsupportedProtocolExceptionCaught = new AtomicBoolean();

    try {
      server = vertx.createHttpServer(createBaseServerOptions()
          .setUseProxyProtocol(true))
        .exceptionHandler(ex -> {
          unsupportedProtocolExceptionCaught.set(ex.equals(HAProxyMessageCompletionHandler.UNSUPPORTED_PROTOCOL_EXCEPTION));
        })
        .requestHandler(req -> fail());

      startServer(testAddress);

      client.request(new RequestOptions()
          .setPort(proxy.getPort())
          .setHost(proxy.getHost())
          .setURI(DEFAULT_TEST_URI))
        .compose(HttpClientRequest::send)
        .await();
      fail();
    } catch(Exception expected) {
      assertTrue(unsupportedProtocolExceptionCaught.get());
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
    HAProxy proxy = new HAProxy(testAddress, header);
    proxy.start(vertx);
    server.close();

    AtomicBoolean haProtocolExceptionCaught = new AtomicBoolean();

    try {
      server = vertx.createHttpServer(createBaseServerOptions()
          .setUseProxyProtocol(true))
        .exceptionHandler(ex -> {
          haProtocolExceptionCaught.set(ex instanceof io.netty.handler.codec.haproxy.HAProxyProtocolException);
        })
        .requestHandler(req -> fail());

      startServer(testAddress);

      client.request(new RequestOptions()
          .setPort(proxy.getPort())
          .setHost(proxy.getHost())
          .setURI(DEFAULT_TEST_URI))
        .compose(HttpClientRequest::send)
        .await();
      fail();
    } catch (Exception expected) {
      Assert.assertTrue(haProtocolExceptionCaught.get());
    } finally {
      proxy.stop();
    }
  }

  private void assertAddresses(SocketAddress address1, SocketAddress address2) {
    if (address1 == null || address2 == null)
      assertEquals(address1, address2);
    else {
      assertEquals(address1.hostAddress(), address2.hostAddress());
      assertEquals(address1.port(), address2.port());
    }
  }

  private void assertUniqueIdTLV(Buffer header, List<Map.Entry<Buffer, Buffer>> tlvs) {
    // only supported for v2 header and protocol family != unknown
    if (header.length() < 12 || header.getByte(12) != 0x21 || header.getByte(13) == 0x00) {
      return;
    }

    Assertions.assertThat(tlvs).hasSize(1);

    Map.Entry<Buffer, Buffer> uniqueIdTLV = tlvs.stream().findFirst().orElse(null);
    Assertions.assertThat(uniqueIdTLV.getKey().getByte(0)).isEqualTo((byte)0x05);

    UUID uuid = new UUID(uniqueIdTLV.getValue().getLong(0), uniqueIdTLV.getValue().getLong(Long.BYTES));
    Assertions.assertThat(uuid).isEqualTo(UUID.fromString("1f29a3b5-7cc4-4592-a8f1-879ff1f47124"));
  }
}
