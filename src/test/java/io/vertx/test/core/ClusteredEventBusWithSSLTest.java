/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.http.ClientAuth;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;


/**
 * Tests the clustered event bus with various SSL / TLS configuration.
 *
 * @author Clement Escoffier
 */
@RunWith(value = Parameterized.class)
public class ClusteredEventBusWithSSLTest extends ClusteredEventBusTestBase {

  private final EventBusOptions options;


  public ClusteredEventBusWithSSLTest(KeyCert cert, Trust trust,
                                      boolean requireClientAuth,
                                      boolean clientTrustAll,
                                      boolean useCrl,
                                      String... enabledCipherSuites) {
    options = new EventBusOptions();
    options.setSsl(true);

    if (clientTrustAll) {
      options.setTrustAll(true);
    }
    if (useCrl) {
      options.addCrlPath(findFileOnClasspath("tls/ca/crl.pem"));
    }

    setOptions(options, getClientTrustOptions(trust));
    setOptions(options, getServerCertOptions(cert));

    if (enabledCipherSuites != null) {
      for (String suite : enabledCipherSuites) {
        options.addEnabledCipherSuite(suite);
      }
    }

    if (requireClientAuth) {
      options.setClientAuth(ClientAuth.REQUIRED);
    }
  }


  @Parameterized.Parameters(name = "{index}: event bus SSL ({0} {1} {2} {3} {4} {5}")
  public static Iterable<Object[]> data() {
    // Parameters:
    //KeyCert, Trust, requireClientAuth, clientTrustAll, useCrl, enabledCipherSuites

    return Arrays.asList(new Object[][]{
        {KeyCert.JKS, Trust.NONE, false, true, false, new String[0]}, // trusts all server certs
        {KeyCert.JKS, Trust.JKS, false, false, false, new String[0]},
        {KeyCert.JKS, Trust.JKS, false, false, true, new String[0]},
        {KeyCert.PKCS12, Trust.JKS, false, false, false, new String[0]},
        {KeyCert.PEM, Trust.JKS, false, false, false, new String[0]},
        {KeyCert.JKS_CA, Trust.JKS_CA, false, false, false, new String[0]},
        {KeyCert.PKCS12_CA, Trust.JKS_CA, false, false, false, new String[0]},
        {KeyCert.PEM_CA, Trust.PKCS12_CA, false, false, false, new String[0]},
        {KeyCert.JKS, Trust.PKCS12, false, false, false, new String[0]},
        {KeyCert.JKS, Trust.PEM, false, false, false, new String[0]},
        {KeyCert.JKS, Trust.PKCS12, true, false, false, new String[0]},
        {KeyCert.JKS, Trust.PEM, true, false, false, new String[0]},
        {KeyCert.PKCS12, Trust.JKS, true, false, false, new String[0]},
        {KeyCert.PEM_CA, Trust.PEM_CA, true, false, false, new String[0]},
        {KeyCert.JKS, Trust.PEM_CA, false, true, false, HttpTest.ENABLED_CIPHER_SUITES},
    });
  }

  @Override
  protected void startNodes(int numNodes) {
    super.startNodes(numNodes, new VertxOptions().setEventBusOptions(options));
  }
}
