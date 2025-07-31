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
  protected Http2HeadersMultiMap createHttpHeader() {
    return new Http2HeadersMultiMap(new DefaultHttp3Headers());
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
}
