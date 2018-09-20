/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.eventbus;

import io.vertx.core.VertxOptions;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.Http1xTest;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Ignore;
import org.junit.Test;
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


  public ClusteredEventBusWithSSLTest(Cert<?> cert, Trust<?> trust,
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
      options.addCrlPath("tls/root-ca/crl.pem");
    }

    options.setTrustOptions(trust.get());
    options.setKeyCertOptions(cert.get());

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
        {Cert.SERVER_JKS, Trust.NONE, false, true, false, Collections.emptyList()}, // trusts all server certs
        {Cert.SERVER_JKS, Trust.SERVER_JKS, false, false, true, Collections.emptyList()},
        {Cert.SERVER_PKCS12, Trust.SERVER_JKS, false, false, false, Collections.emptyList()},
        {Cert.SERVER_PEM, Trust.SERVER_JKS, false, false, false, Collections.emptyList()},
        {Cert.SERVER_PKCS12_ROOT_CA, Trust.SERVER_JKS_ROOT_CA, false, false, false, Collections.emptyList()},
        {Cert.SERVER_PEM_ROOT_CA, Trust.SERVER_PKCS12_ROOT_CA, false, false, false, Collections.emptyList()},
        {Cert.SERVER_JKS, Trust.SERVER_PEM_ROOT_CA, false, true, false, Arrays.asList(Http1xTest.ENABLED_CIPHER_SUITES)},
    });
  }

  @Override
  protected void startNodes(int numNodes) {
    super.startNodes(numNodes, new VertxOptions().setEventBusOptions(options));
  }

  @Test
  @Ignore
  @Override
  public void testSendWhileUnsubscribing() throws Exception {
    // This test can fail if CPU is busy, so avoid it with EventBus SSL
  }

}
