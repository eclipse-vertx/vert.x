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
import java.util.Collections;
import java.util.List;


/**
 * Tests the clustered event bus with various SSL / TLS configuration.
 *
 * @author Clement Escoffier
 */
@RunWith(value = Parameterized.class)
public class ClusteredEventBusWithSSLTest extends ClusteredEventBusTestBase {

  private final EventBusOptions options;


  public ClusteredEventBusWithSSLTest(TLSCert cert, TLSCert trust,
                                      boolean requireClientAuth,
                                      boolean clientTrustAll,
                                      boolean useCrl,
                                      List<String> enabledCipherSuites) {
    options = new EventBusOptions();
    options.setSsl(true);

    if (clientTrustAll) {
      options.setTrustAll(true);
    }
    if (useCrl) {
      options.addCrlPath("tls/ca/crl.pem");
    }

    setOptions(options, trust.getClientTrustOptions());
    setOptions(options, cert.getServerKeyCertOptions());

    if (enabledCipherSuites != null) {
      enabledCipherSuites.forEach(options::addEnabledCipherSuite);
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
        {TLSCert.JKS, TLSCert.NONE, false, true, false, Collections.emptyList()}, // trusts all server certs
        {TLSCert.JKS, TLSCert.JKS, false, false, true, Collections.emptyList()},
        {TLSCert.PKCS12, TLSCert.JKS, false, false, false, Collections.emptyList()},
        {TLSCert.PEM, TLSCert.JKS, false, false, false, Collections.emptyList()},
        {TLSCert.PKCS12_CA, TLSCert.JKS_CA, false, false, false, Collections.emptyList()},
        {TLSCert.PEM_CA, TLSCert.PKCS12_CA, false, false, false, Collections.emptyList()},
        {TLSCert.JKS, TLSCert.PEM_CA, false, true, false, Arrays.asList(Http1xTest.ENABLED_CIPHER_SUITES)},
    });
  }

  @Override
  protected void startNodes(int numNodes) {
    super.startNodes(numNodes, new VertxOptions().setEventBusOptions(options));
  }
}
