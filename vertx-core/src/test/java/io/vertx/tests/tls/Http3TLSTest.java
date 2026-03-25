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

package io.vertx.tests.tls;

import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import io.vertx.tests.http.http3.Http3Config;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http3TLSTest extends HttpTLSTest {

  public Http3TLSTest() {
    super(new Http3Config(DEFAULT_HTTPS_PORT, "localhost"));
  }

  @Ignore("Handle this")
  @Test
  @Override
  public void testSNIServerSSLEnginePeerHost() throws Exception {
    super.testSNIServerSSLEnginePeerHost();
  }

  @Ignore("Handle this")
  @Test
  @Override
  public void testTLSServerSSLEnginePeerHost() throws Exception {
    super.testTLSServerSSLEnginePeerHost();
  }

  @Ignore("Currently cipher suites are hardcoded")
  @Test
  @Override
  public void testTLSNonMatchingCipherSuites() throws Exception {
    super.testTLSNonMatchingCipherSuites();
  }

  @Ignore("Handle this")
  @Test
  @Override
  public void testServerSharingUpdateSSLOptions() throws Exception {
    super.testServerSharingUpdateSSLOptions();
  }

  @Ignore("Handle this")
  @Test
  @Override
  public void testTLSRevokedClientCertServer() throws Exception {
    super.testTLSRevokedClientCertServer();
  }
}
