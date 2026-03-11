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
  public void testTLSTrailingDotHost() throws Exception {
    super.testTLSTrailingDotHost();
  }

  @Ignore("Handle this")
  @Test
  @Override
  public void testSNIServerIgnoresExtension2() throws Exception {
    super.testSNIServerIgnoresExtension2();
  }

  @Ignore("Handle this")
  @Test
  @Override
  public void testTLSServerSSLEnginePeerHost() throws Exception {
    super.testTLSServerSSLEnginePeerHost();
  }

  @Ignore("Handle this")
  @Test
  @Override
  public void testSniWithTrailingDotHost() throws Exception {
    super.testSniWithTrailingDotHost();
  }

  @Ignore("https://github.com/netty/netty/pull/16178")
  @Test
  @Override
  public void testTLSRevokedClientCertServer() throws Exception {
    super.testTLSRevokedClientCertServer();
  }

  @Ignore("https://github.com/netty/netty/pull/16178")
  @Test
  @Override
  public void testTLSClientCertClientNotTrusted() throws Exception {
    super.testTLSClientCertClientNotTrusted();
  }

  @Ignore("https://github.com/netty/netty/pull/16178")
  @Test
  @Override
  public void testTLSClientCertRequiredNoClientCert() throws Exception {
    super.testTLSClientCertRequiredNoClientCert();
  }

  @Ignore("https://github.com/netty/netty/pull/16426")
  @Test
  @Override
  public void testSNISubjectAltenativeNameCNMatch1() throws Exception {
    super.testSNISubjectAltenativeNameCNMatch1();
  }

  @Ignore("https://github.com/netty/netty/pull/16426")
  @Test
  @Override
  public void testSNISubjectAltenativeNameCNMatch1PKCS12() throws Exception {
    super.testSNISubjectAltenativeNameCNMatch1PKCS12();
  }

  @Ignore("https://github.com/netty/netty/pull/16426")
  @Test
  @Override
  public void testSNISubjectAltenativeNameCNMatch1PEM() throws Exception {
    super.testSNISubjectAltenativeNameCNMatch1PEM();
  }

  @Ignore("https://github.com/netty/netty/pull/16426")
  @Test
  @Override
  public void testTLSVerifyNonMatchingHost() throws Exception {
    super.testTLSVerifyNonMatchingHost();
  }

  @Ignore("Currently cipher suites are hardcoded")
  @Test
  @Override
  public void testTLSNonMatchingCipherSuites() throws Exception {
    super.testTLSNonMatchingCipherSuites();
  }
}
