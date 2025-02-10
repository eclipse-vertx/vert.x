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

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.tests.http.HttpOptionsFactory;
import org.junit.Ignore;
import org.junit.Test;

import static io.vertx.test.http.HttpTestBase.*;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3NetTest extends NetTest {

  protected NetServerOptions createNetServerOptions() {
    return HttpOptionsFactory.createH3NetServerOptions();
  }

  protected NetClientOptions createNetClientOptions() {
    return HttpOptionsFactory.createH3NetClientOptions();
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return HttpOptionsFactory.createH3HttpServerOptions(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return HttpOptionsFactory.createH3HttpClientOptions();
  }

  @Ignore
  @Override
  @Test
  public void testTLSClientCertRequiredNoClientCert1_3() throws Exception {
    super.testTLSClientCertRequiredNoClientCert1_3();
  }

  @Ignore
  @Override
  @Test
  public void testClientIdleTimeout2() {
    super.testClientIdleTimeout2();
  }


  @Ignore
  @Override
  @Test
  public void testServerIdleTimeout4() {
    super.testServerIdleTimeout4();
  }

  @Ignore
  @Override
  @Test
  public void testClientIdleTimeout6() {
    super.testClientIdleTimeout6();
  }

  @Ignore
  @Override
  @Test
  public void testServerIdleTimeout5() {
    super.testServerIdleTimeout5();
  }


  @Ignore
  @Override
  @Test
  public void testClientDrainHandler() {
    super.testClientDrainHandler();
  }

  @Ignore
  @Override
  @Test
  public void testServerDrainHandler() {
    super.testServerDrainHandler();
  }


  @Ignore
  @Override
  @Test
  public void testNetClientInternalTLS() throws Exception {
    super.testNetClientInternalTLS();
  }

  @Ignore
  @Override
  @Test
  public void testNetSocketInternalEvent() throws Exception {
    super.testNetSocketInternalEvent();
  }

  @Ignore
  @Override
  @Test
  public void testSslHandshakeTimeoutHappenedOnServer() throws Exception {
    super.testSslHandshakeTimeoutHappenedOnServer();
  }

  @Ignore
  @Override
  @Test
  public void testSniImplicitServerNameDisabledForShortname1() throws Exception {
    super.testSniImplicitServerNameDisabledForShortname1();
  }

  @Ignore
  @Override
  @Test
  public void testClientSniMultipleServerName() throws Exception {
    super.testClientSniMultipleServerName();
  }

  @Ignore
  @Override
  @Test
  public void testConnectTimeoutOverride() {
    super.testConnectTimeoutOverride();
  }

  @Ignore
  @Override
  @Test
  public void testServerCloseHandlersCloseFromServer() {
    super.testServerCloseHandlersCloseFromServer();
  }

  @Ignore
  @Override
  @Test
  public void testWriteHandlerSuccess() throws Exception {
    super.testWriteHandlerSuccess();
  }

  @Ignore
  @Override
  @Test
  public void testSniWithServerNameStartTLS() throws Exception {
    super.testSniWithServerNameStartTLS();
  }

  @Ignore
  @Override
  @Test
  public void testClientLogging() throws Exception {
    super.testClientLogging();
  }

  @Ignore
  @Override
  @Test
  public void testSniOverrideServerName() throws Exception {
    super.testSniOverrideServerName();
  }

  @Ignore
  @Override
  @Test
  public void testConnectTimeout() {
    super.testConnectTimeout();
  }

  @Ignore
  @Override
  @Test
  public void testStartTLSServerSSLEnginePeerHost() throws Exception {
    super.testStartTLSServerSSLEnginePeerHost();
  }


  @Ignore
  @Override
  @Test
  public void testSslHandshakeTimeoutHappenedOnSniServer() throws Exception {
    super.testSslHandshakeTimeoutHappenedOnSniServer();
  }

  @Ignore
  @Override
  @Test
  public void testServerShutdown() throws Exception {
    super.testServerShutdown();
  }

  @Ignore
  @Override
  @Test
  public void testUpgradeToSSLIncorrectClientOptions1() {
    super.testUpgradeToSSLIncorrectClientOptions1();
  }

  @Ignore
  @Override
  @Test
  public void testUpgradeToSSLIncorrectClientOptions2() {
    super.testUpgradeToSSLIncorrectClientOptions2();
  }

  @Ignore
  @Override
  @Test
  public void testTLSClientCertClientNotTrusted() throws Exception {
    super.testTLSClientCertClientNotTrusted();
  }

  @Ignore
  @Override
  @Test
  public void testServerShutdownOverride() throws Exception {
    super.testServerShutdownOverride();
  }

  @Ignore
  @Override
  @Test
  public void testSniWithServerNameTrustFallbackFail() {
    super.testSniWithServerNameTrustFallbackFail();
  }

  @Ignore
  @Override
  @Test
  public void testServerLogging() throws Exception {
    super.testServerLogging();
  }

  @Ignore
  @Override
  @Test
  public void testNetClientInternal() throws Exception {

    super.testNetClientInternal_(HttpOptionsFactory.createH3HttpServerOptions(1234, "localhost"), false);
  }

  @Ignore
  @Override
  @Test
  public void testServerNetSocketShouldBeClosedWhenTheClosedHandlerIsCalled() throws Exception {
    super.testServerNetSocketShouldBeClosedWhenTheClosedHandlerIsCalled();
  }

  @Ignore
  @Override
  @Test
  public void testNetServerInternal() throws Exception {
    super.testNetServerInternal();
  }

  @Ignore
  @Override
  @Test
  public void testServerWithIdleTimeoutSendChunkedFile() throws Exception {
    super.testServerWithIdleTimeoutSendChunkedFile();
  }

  @Ignore
  @Override
  @Test
  public void testWriteHandlerFailure() throws Exception {
    super.testWriteHandlerFailure();
  }


  @Ignore
  @Override
  @Test
  public void testDefaultServerOptionsJson() {
    super.testDefaultServerOptionsJson();
  }

  @Ignore
  @Override
  @Test
  public void testWithSocks4LocalResolver() throws Exception {
    super.testWithSocks4LocalResolver();
  }

  @Ignore
  @Override
  @Test
  public void testOverrideClientSSLOptions() {
    super.testOverrideClientSSLOptions();
  }


  @Ignore
  @Override
  @Test
  public void testSharedServersRoundRobinButFirstStartAndStopServer() throws Exception {
    super.testSharedServersRoundRobinButFirstStartAndStopServer();
  }

  @Ignore
  @Override
  @Test
  public void testSharedServersRoundRobin() throws Exception {
    super.testSharedServersRoundRobin();
  }

  @Ignore
  @Override
  @Test
  public void testReconnectAttemptsInfinite() {
    super.testReconnectAttemptsInfinite();
  }

  @Ignore
  @Override
  @Test
  public void testNetClientInternalTLSWithSuppliedSSLContext() throws Exception {
    super.testNetClientInternalTLSWithSuppliedSSLContext();
  }

  @Ignore
  @Override
  @Test
  public void testListenOnPortNoHandler() {
    super.testListenOnPortNoHandler();
  }

  @Ignore
  @Override
  @Test
  public void sendFileServerToClient() throws Exception {
    super.sendFileServerToClient();
  }

  @Ignore
  @Override
  @Test
  public void testStartTLSClientCertClientNotTrusted() throws Exception {
    super.testStartTLSClientCertClientNotTrusted();
  }

  @Ignore
  @Override
  @Test
  public void testReconnectAttemptsMany() {
    super.testReconnectAttemptsMany();
  }

  @Ignore
  @Override
  @Test
  public void testSniForceShortname() throws Exception {
    super.testSniForceShortname();
  }

  @Ignore
  @Override
  @Test
  public void testHalfCloseCallsEndHandlerAfterBuffersAreDelivered() throws Exception {
    super.testHalfCloseCallsEndHandlerAfterBuffersAreDelivered();
  }

  @Ignore
  @Override
  @Test
  public void testNetServerInternalTLS() throws Exception {
    super.testNetServerInternalTLS();
  }

  @Ignore
  @Override
  @Test
  public void testTLSClientCertRequiredNoClientCert() throws Exception {
    super.testTLSClientCertRequiredNoClientCert();
  }

  @Ignore
  @Override
  @Test
  public void sendFileClientToServer() throws Exception {
    super.sendFileClientToServer();
  }

  @Ignore
  @Override
  @Test
  public void testSniWithServerNameTrustFail() {
    super.testSniWithServerNameTrustFail();
  }

  @Ignore
  @Override
  @Test
  public void testWorkerClient() throws Exception {
    super.testWorkerClient();
  }

  @Ignore
  @Override
  @Test
  public void testInvalidTlsProtocolVersion() throws Exception {
    super.testInvalidTlsProtocolVersion();
  }

  @Ignore
  @Override
  @Test
  public void testSniWithoutServerNameUsesTheFirstKeyStoreEntry2() throws Exception {
    super.testSniWithoutServerNameUsesTheFirstKeyStoreEntry2();
  }

  @Ignore
  @Override
  @Test
  public void testTLSClientUntrustedServer() throws Exception {
    super.testTLSClientUntrustedServer();
  }

  @Ignore
  @Override
  @Test
  public void testSniWithUnknownServer2() throws Exception {
    super.testSniWithUnknownServer2();
  }

  @Ignore
  @Override
  @Test
  public void testClientLocalAddress() {
    super.testClientLocalAddress();
  }

  @Ignore
  @Override
  @Test
  public void testTLSHostnameCertCheckCorrect() {
    super.testTLSHostnameCertCheckCorrect();
  }

  @Ignore
  @Override
  @Test
  public void testClientIdleTimeout1() {
    super.testClientIdleTimeout1();
  }

  @Ignore
  @Override
  @Test
  public void testClientIdleTimeout4() {
    super.testClientIdleTimeout4();
  }

  @Ignore
  @Override
  @Test
  public void testClientIdleTimeout5() {
    super.testClientIdleTimeout5();
  }

  @Ignore
  @Override
  @Test
  public void testClientWorkerMissBufferWhenBufferArriveBeforeConnectCallback() throws Exception {
    super.testClientWorkerMissBufferWhenBufferArriveBeforeConnectCallback();
  }

  @Ignore
  @Override
  @Test
  public void testClientMissingHostnameVerificationAlgorithm1() {
    super.testClientMissingHostnameVerificationAlgorithm1();
  }

  @Ignore
  @Override
  @Test
  public void testClientMissingHostnameVerificationAlgorithm2() {
    super.testClientMissingHostnameVerificationAlgorithm2();
  }

  @Ignore
  @Override
  @Test
  public void testClientMissingHostnameVerificationAlgorithm3() {
    super.testClientMissingHostnameVerificationAlgorithm3();
  }


  @Ignore
  @Override
  @Test
  public void testDefaultClientOptionsJson() {
    super.testDefaultClientOptionsJson();
  }

  @Ignore
  @Override
  @Test
  public void testSharedServersRoundRobinWithOtherServerRunningOnDifferentPort() throws Exception {
    super.testSharedServersRoundRobinWithOtherServerRunningOnDifferentPort();
  }


  @Ignore
  @Override
  @Test
  public void testSocketAddress() {
    super.testSocketAddress();
  }

  @Ignore
  @Override
  @Test
  public void testFanout() throws Exception {
    super.testFanout();
  }

  @Ignore
  @Override
  @Test
  public void testListen() {
    super.testListen();
  }


  @Ignore
  @Override
  @Test
  public void testServerIdleTimeout6() {
    super.testServerIdleTimeout6();
  }

  @Ignore
  @Override
  @Test
  public void testStartTLSClientTrustAll() throws Exception {
    super.testStartTLSClientTrustAll();
  }

  @Ignore
  @Override
  @Test
  public void testNoLogging() throws Exception {
    super.testNoLogging();
  }

  @Ignore
  @Override
  @Test
  public void testServerOptions() {
    super.testServerOptions();
  }

  @Ignore
  @Override
  @Test
  public void testHostVerificationHttpsNotMatching() {
    super.testHostVerificationHttpsNotMatching();
  }

  @Ignore
  @Override
  @Test
  public void testClientCloseHandlersCloseFromServer() {
    super.testClientCloseHandlersCloseFromServer();
  }

  @Ignore
  @Override
  @Test
  public void testMissingClientSSLOptions() throws Exception {
    super.testMissingClientSSLOptions();
  }

  @Ignore
  @Override
  @Test
  public void testSslHandshakeTimeoutHappenedWhenUpgradeSsl() {
    super.testSslHandshakeTimeoutHappenedWhenUpgradeSsl();
  }


  @Ignore
  @Override
  @Test
  public void testReceiveMessageAfterExplicitClose() throws Exception {
    super.testReceiveMessageAfterExplicitClose();
  }

  //TODO: resolve group1

  @Ignore
  @Override
  @Test
  public void testClientMultiThreaded() throws Exception {
    super.testClientMultiThreaded();
  }

  @Ignore
  @Override
  @Test
  public void testWorkerServer() {
    super.testWorkerServer();
  }

  @Ignore
  @Override
  @Test
  public void testClientOptions() {
    super.testClientOptions();
  }

  @Ignore
  @Override
  @Test
  public void testInVerticle() throws Exception {
    super.testInVerticle();
  }

  //TODO: resolve group2


  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion1TCP4() throws Exception {
    super.testHAProxyProtocolVersion1TCP4();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion1TCP6() throws Exception {
    super.testHAProxyProtocolVersion1TCP6();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion2TCP4() throws Exception {
    super.testHAProxyProtocolVersion2TCP4();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion2TCP6() throws Exception {
    super.testHAProxyProtocolVersion2TCP6();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion2UDP4() throws Exception {
    super.testHAProxyProtocolVersion2UDP4();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion2UDP6() throws Exception {
    super.testHAProxyProtocolVersion2UDP6();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolIllegalHeader1() throws Exception {
    super.testHAProxyProtocolIllegalHeader1();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolIllegalHeader2() throws Exception {
    super.testHAProxyProtocolIllegalHeader2();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolIdleTimeout() throws Exception {
    super.testHAProxyProtocolIdleTimeout();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion2UnixDataGram() throws Exception {
    super.testHAProxyProtocolVersion2UnixDataGram();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion2UnixSocket() throws Exception {
    super.testHAProxyProtocolVersion2UnixSocket();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolConnectSSL() throws Exception {
    super.testHAProxyProtocolConnectSSL();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolIdleTimeoutNotHappened() throws Exception {
    super.testHAProxyProtocolIdleTimeoutNotHappened();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion1Unknown() throws Exception {
    super.testHAProxyProtocolVersion1Unknown();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion2Unknown() throws Exception {
    super.testHAProxyProtocolVersion2Unknown();
  }

  //TODO: resolve group3


  @Ignore
  @Override
  @Test
  public void testWithSocks5Proxy() throws Exception {
    super.testWithSocks5Proxy();
  }

  @Ignore
  @Override
  @Test
  public void testWithHttpConnectProxy() throws Exception {
    super.testWithHttpConnectProxy();
  }

  @Ignore
  @Override
  @Test
  public void testConnectSSLWithSocks5Proxy() throws Exception {
    super.testConnectSSLWithSocks5Proxy();
  }

  @Ignore
  @Override
  @Test
  public void testUpgradeSSLWithSocks5Proxy() throws Exception {
    super.testUpgradeSSLWithSocks5Proxy();
  }

  @Ignore
  @Override
  @Test
  public void testWithSocks4aProxyAuth() throws Exception {
    super.testWithSocks4aProxyAuth();
  }

  @Ignore
  @Override
  @Test
  public void testWithSocks4aProxy() throws Exception {
    super.testWithSocks4aProxy();
  }

  @Ignore
  @Override
  @Test
  public void testWithSocks5ProxyAuth() throws Exception {
    super.testWithSocks5ProxyAuth();
  }


  //TODO: resolve group4


  @Ignore
  @Override
  @Test
  public void testTLSServerSSLEnginePeerHost() throws Exception {
    super.testTLSServerSSLEnginePeerHost();
  }

  @Ignore
  @Override
  @Test
  public void testTLSClientTrustAll() throws Exception {
    super.testTLSClientTrustAll();
  }

  @Ignore
  @Override
  @Test
  public void testTLSClientTrustServerCert() throws Exception {
    super.testTLSClientTrustServerCert();
  }

  @Ignore
  @Override
  @Test
  public void testTLSClientCertNotRequired() throws Exception {
    super.testTLSClientCertNotRequired();
  }

  @Ignore
  @Override
  @Test
  public void testTLSClientCertRequired() throws Exception {
    super.testTLSClientCertRequired();
  }

  @Ignore
  @Override
  @Test
  public void testTLSCipherSuites() throws Exception {
    super.testTLSCipherSuites();
  }

  @Ignore
  @Override
  @Test
  public void testSpecificTlsProtocolVersion() throws Exception {
    super.testSpecificTlsProtocolVersion();
  }

  @Ignore
  @Override
  @Test
  public void testSniWithoutServerNameUsesTheFirstKeyStoreEntry1() throws Exception {
    super.testSniWithoutServerNameUsesTheFirstKeyStoreEntry1();
  }


  @Ignore
  @Override
  @Test
  public void testSniWithTrailingDotHost() throws Exception {
    super.testSniWithTrailingDotHost();
  }

  @Ignore
  @Override
  @Test
  public void testClientShutdown() throws Exception {
    super.testClientShutdown();
  }


  //TODO: resolve group5


  @Ignore
  @Override
  @Test
  public void testClientShutdownOverride() throws Exception {
    super.testClientShutdownOverride();
  }

  @Ignore
  @Override
  @Test
  public void testServerIdleTimeout1() {
    super.testServerIdleTimeout1();
  }

  @Ignore
  @Override
  @Test
  public void testServerIdleTimeout2() {
    super.testServerIdleTimeout2();
  }

  @Ignore
  @Override
  @Test
  public void testServerIdleTimeout3() {
    super.testServerIdleTimeout3();
  }


  @Ignore
  @Override
  @Test
  public void testWriteSameBufferMoreThanOnce() throws Exception {
    super.testWriteSameBufferMoreThanOnce();
  }


  @Ignore
  @Override
  @Test
  public void testSniImplicitServerNameDisabledForShortname2() throws Exception {
    super.testSniImplicitServerNameDisabledForShortname2();
  }

  @Ignore
  @Override
  @Test
  public void testSNIServerSSLEnginePeerHost() throws Exception {
    super.testSNIServerSSLEnginePeerHost();
  }


  @Ignore
  @Override
  @Test
  public void testServerCertificateMultiple() throws Exception {
    super.testServerCertificateMultiple();
  }

  @Ignore
  @Override
  @Test
  public void testSniWithServerNameTrustFallback() {
    super.testSniWithServerNameTrustFallback();
  }


  @Ignore
  @Override
  @Test
  public void testSniWithServerNameTrust() {
    super.testSniWithServerNameTrust();
  }


  @Ignore
  @Override
  @Test
  public void testServerCertificateMultipleWithKeyPassword() throws Exception {
    super.testServerCertificateMultipleWithKeyPassword();
  }


  @Ignore
  @Override
  @Test
  public void testSniImplicitServerName() throws Exception {
    super.testSniImplicitServerName();
  }


  @Ignore
  @Override
  @Test
  public void testSniWithUnknownServer1() throws Exception {
    super.testSniWithUnknownServer1();
  }


  @Ignore
  @Override
  @Test
  public void testTLSHostnameCertCheckIncorrect() {
    super.testTLSHostnameCertCheckIncorrect();
  }


}
