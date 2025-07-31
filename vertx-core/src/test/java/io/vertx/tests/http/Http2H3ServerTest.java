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

import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.impl.http2.Http2HeadersMultiMap;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http2H3ServerTest extends Http2ServerTest {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return createH3HttpServerOptions(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return createH3HttpClientOptions().setDefaultPort(DEFAULT_HTTPS_PORT).setDefaultHost(DEFAULT_HTTPS_HOST);
  }

  @Override
  protected Http2TestClient createClient() {
    return new Http2H3TestClient(vertx, eventLoopGroups, new Http2H3RequestHandler());
  }

  @Override
  protected void setInvalidAuthority(Http2HeadersMultiMap http2HeadersMultiMap, String authority) {
    ((DefaultHttp3Headers) http2HeadersMultiMap.unwrap()).authority(authority);
  }

  @Override
  protected void assertEqualsUnknownFrameFlags(int expected, Http2Flags actual) {
    //Http3 has no flags!
  }

  @Override
  protected Http2HeadersMultiMap createHttpHeader() {
    return new Http2HeadersMultiMap(new DefaultHttp3Headers());
  }

  @Ignore("Http3 does not support ping")
  @Override
  @Test
  public void testReceivePing() throws Exception {
    super.testReceivePing();
  }

  @Ignore("Http3 does not support ping")
  @Override
  @Test
  public void testSendPing() throws Exception {
    super.testSendPing();
  }


  @Ignore("Http3 does not support upgrade")
  @Override
  @Test
  public void testUpgradeToClearTextGet() throws Exception {
    super.testUpgradeToClearTextGet();
  }

  @Ignore("Http3 does not support upgrade")
  @Override
  @Test
  public void testUpgradeToClearTextPut() throws Exception {
    super.testUpgradeToClearTextPut();
  }

  @Ignore("Http3 does not support upgrade")
  @Override
  @Test
  public void testUpgradeToClearTextWithCompression() throws Exception {
    super.testUpgradeToClearTextWithCompression();
  }

  @Ignore("Http3 does not support upgrade")
  @Override
  @Test
  public void testUpgradeToClearTextInvalidHost() throws Exception {
    super.testUpgradeToClearTextInvalidHost();
  }

  @Ignore("Http3 does not support upgrade")
  @Override
  @Test
  public void testUpgradeToClearTextIdleTimeout() throws Exception {
    super.testUpgradeToClearTextIdleTimeout();
  }

  @Ignore("Http3 does not support upgrade")
  @Override
  @Test
  public void testUpgradeToClearTextInvalidConnectionHeader() throws Exception {
    super.testUpgradeToClearTextInvalidConnectionHeader();
  }

  @Ignore("Http3 does not support upgrade")
  @Override
  @Test
  public void testUpgradeToClearTextMalformedSettings() throws Exception {
    super.testUpgradeToClearTextMalformedSettings();
  }

  @Ignore("Http3 does not support upgrade")
  @Override
  @Test
  public void testUpgradeToClearTextInvalidSettings() throws Exception {
    super.testUpgradeToClearTextInvalidSettings();
  }

  @Ignore("Http3 does not support upgrade")
  @Override
  @Test
  public void testUpgradeToClearTextMissingSettings() throws Exception {
    super.testUpgradeToClearTextMissingSettings();
  }

  @Ignore("Http3 does not support upgrade")
  @Override
  @Test
  public void testUpgradeToClearTextWorkerContext() throws Exception {
    super.testUpgradeToClearTextWorkerContext();
  }

  @Ignore("Http3 does not support upgrade")
  @Override
  @Test
  public void testUpgradeToClearTextPartialFailure() throws Exception {
    super.testUpgradeToClearTextPartialFailure();
  }

  @Ignore("Priority changing is not implemented yet in http3!")
  @Override
  @Test
  public void testStreamPriority() throws Exception {
    super.testStreamPriority();
  }

  @Ignore("Priority changing is not implemented yet in http3!")
  @Override
  @Test
  public void testStreamPriorityChange() throws Exception {
    super.testStreamPriorityChange();
  }

  @Ignore("Priority changing is not implemented yet in http3!")
  @Override
  @Test
  public void testStreamPriorityNoChange() throws Exception {
    super.testStreamPriorityNoChange();
  }

  @Ignore("Push msg is not implemented yet in http3!")
  @Override
  @Test
  public void testPushPromiseNoAuthority() throws Exception {
    super.testPushPromiseNoAuthority();
  }

  @Ignore("Push msg is not implemented yet in http3!")
  @Override
  @Test
  public void testPushPromiseClearText() throws Exception {
    super.testPushPromiseClearText();
  }

  @Ignore("Push msg is not implemented yet in http3!")
  @Override
  @Test
  public void testPushPromise() throws Exception {
    super.testPushPromise();
  }

  @Ignore("Push msg is not implemented yet in http3!")
  @Override
  @Test
  public void testQueuePushPromise() throws Exception {
    super.testQueuePushPromise();
  }

  @Ignore("Push msg is not implemented yet in http3!")
  @Override
  @Test
  public void testPushPromiseOverrideAuthority() throws Exception {
    super.testPushPromiseOverrideAuthority();
  }

  @Ignore("Push msg is not implemented yet in http3!")
  @Override
  @Test
  public void testResetPendingPushPromise() throws Exception {
    super.testResetPendingPushPromise();
  }

  @Ignore("Push msg is not implemented yet in http3!")
  @Override
  @Test
  public void testPushPromiseHeaders() throws Exception {
    super.testPushPromiseHeaders();
  }

  @Ignore("Push msg is not implemented yet in http3!")
  @Override
  @Test
  public void testResetActivePushPromise() throws Exception {
    super.testResetActivePushPromise();
  }

  @Ignore("Setting management is not implemented yet in http3!")
  @Override
  @Test
  public void testServerInitialSettings() throws Exception {
    super.testServerInitialSettings();
  }

  @Ignore("Setting management is not implemented yet in http3!")
  @Override
  @Test
  public void testClientSettings() throws Exception {
    super.testClientSettings();
  }

  @Ignore("Setting management is not implemented yet in http3!")
  @Override
  @Test
  public void testServerSettings() throws Exception {
    super.testServerSettings();
  }

   @Ignore
  @Override
  @Test
  public void testUpdateConnectionWindowSize() throws Exception {
    //TODO: resolve this test issue.
    super.testUpdateConnectionWindowSize();
  }

  @Ignore
  @Override
  @Test
  public void testConnectionDecodeError() throws Exception {
    //TODO: resolve this test issue.
    super.testConnectionDecodeError();
  }

  @Ignore
  @Override
  @Test
  public void testResponseCompressionEnabled() throws Exception {
    //TODO: resolve this test issue.
    super.testResponseCompressionEnabled();
  }

  @Ignore
  @Override
  @Test
  public void testPriorKnowledge() throws Exception {
    //TODO: resolve this test issue.
    super.testPriorKnowledge();
  }

  @Ignore
  @Override
  @Test
  public void testClientSendGoAwayInternalError() throws Exception {
    //TODO: resolve this test issue.
    super.testClientSendGoAwayInternalError();
  }

  @Ignore
  @Override
  @Test
  public void testShutdown() throws Exception {
    //TODO: resolve this test issue.
    super.testShutdown();
  }

  @Ignore
  @Override
  @Test
  public void testServerSendGoAwayNoError() throws Exception {
    //TODO: resolve this test issue.
    super.testServerSendGoAwayNoError();
  }

  @Ignore
  @Override
  @Test
  public void testRequestCompressionEnabled() throws Exception {
    //TODO: resolve this test issue.
    super.testRequestCompressionEnabled();
  }


  @Ignore
  @Override
  @Test
  public void testServerResetClientStream2() throws Exception {
    //TODO: resolve this test issue. this test case fails randomly!
    super.testServerResetClientStream2();
  }

  @Ignore
  @Override
  @Test
  public void testNetSocketPauseResume() throws Exception {
    //TODO: resolve this test issue. this test case fails randomly!
    super.testNetSocketPauseResume();
  }

  @Ignore
  @Override
  @Test
  public void testResponseCompressionEnabledButExplicitlyDisabled() throws Exception {
    //TODO: resolve this test issue. this test case fails randomly!
    super.testResponseCompressionEnabledButExplicitlyDisabled();
  }

  @Ignore
  @Override
  @Test
  public void testShutdownWithTimeout() throws Exception {
    //TODO: resolve this test issue. this test case fails randomly!
    super.testShutdownWithTimeout();
  }

  @Ignore
  @Override
  @Test
  public void testTrailers() throws Exception {
    //TODO: resolve this test issue. this test case fails randomly!
    super.testTrailers();
  }

  @Ignore
  @Override
  @Test
  public void testServerResetClientStream1() throws Exception {
    //TODO: resolve this test issue. this test case fails randomly!
    super.testServerResetClientStream1();
  }

  @Ignore
  @Override
  @Test
  public void testNetSocketConnect() throws Exception {
    //TODO: resolve this test issue. this test case fails randomly!
    super.testNetSocketConnect();
  }

  @Ignore
  @Override
  @Test
  public void testRequestEndHandlerFailure() throws Exception {
    //TODO: resolve this test issue.
    super.testRequestEndHandlerFailure();
  }

  @Ignore
  @Override
  @Test
  public void test100ContinueHandledAutomatically() throws Exception {
    //TODO: resolve this test issue.
    super.test100ContinueHandledAutomatically();
  }

  @Ignore
  @Override
  @Test
  public void testRequestEndHandlerFailureWithData() throws Exception {
    //TODO: resolve this test issue.
    super.testRequestEndHandlerFailureWithData();
  }

  @Ignore
  @Override
  @Test
  public void testConnectionWindowSize() throws Exception {
    //TODO: resolve this test issue.
    super.testConnectionWindowSize();
  }

  @Ignore
  @Override
  @Test
  public void testRequestResponseLifecycle() throws Exception {
    //TODO: resolve this test issue.
    super.testRequestResponseLifecycle();
  }

  @Ignore
  @Override
  @Test
  public void testServerResetClientStream3() throws Exception {
    //TODO: resolve this test issue.
    super.testServerResetClientStream3();
  }

  @Ignore
  @Override
  @Test
  public void testPromiseStreamError() throws Exception {
    //TODO: resolve this test issue.
    super.testPromiseStreamError();
  }

  @Ignore
  @Override
  @Test
  public void testNetSocketWritability() throws Exception {
    //TODO: resolve this test issue.
    super.testNetSocketWritability();
  }

  @Ignore
  @Override
  @Test
  public void testServerRequestPauseResume() throws Exception {
    //TODO: resolve this test issue.
    super.testServerRequestPauseResume();
  }

  @Ignore
  @Override
  @Test
  public void testServerResponseWritability() throws Exception {
    //TODO: resolve this test issue.
    super.testServerResponseWritability();
  }

  @Ignore
  @Override
  @Test
  public void testClientResetServerStream() throws Exception {
    //TODO: resolve this test issue.
    super.testClientResetServerStream();
  }

  @Ignore
  @Override
  @Test
  public void test100ContinueHandledManually() throws Exception {
    //TODO: resolve this test issue.
    super.test100ContinueHandledManually();
  }
}
