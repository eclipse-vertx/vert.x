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
import io.vertx.core.net.JksOptions;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@RunWith(value = Parameterized.class)
public class ClusteredEventBusWithSSLTest extends ClusteredEventBusTest {

  private final EventBusOptions options;


  public ClusteredEventBusWithSSLTest(boolean clientCert, boolean clientTrust,
                                      boolean serverCert, boolean serverTrust,
                                      boolean requireClientAuth, boolean clientTrustAll) {
    options = new EventBusOptions();
    options.setSsl(true);
    if (serverTrust) {
      options.setTrustStoreOptions(new JksOptions().setPath(findFileOnClasspath("tls/server-truststore.jks")).setPassword("wibble"));
    }
    if (serverCert) {
      options.setKeyStoreOptions(new JksOptions().setPath(findFileOnClasspath("tls/server-keystore.jks")).setPassword("wibble"));
    }
    if (requireClientAuth) {
      options.setClientAuth(ClientAuth.REQUIRED);
    }
    if (clientTrustAll) {
      options.setTrustAll(true);
    }
    if (clientTrust) {
      options.setTrustStoreOptions(new JksOptions().setPath(findFileOnClasspath("tls/server-truststore.jks")).setPassword("wibble"));
    }
    if (clientCert) {
      options.setKeyStoreOptions(new JksOptions().setPath(findFileOnClasspath("tls/client-keystore.jks")).setPassword("wibble"));
    }



  }

  @Parameterized.Parameters(name = "{index}: event bus SSL ({0} {1} {2} {3} {4} {5}")
  public static Iterable<Object[]> data() {
    // Parameters:
    //clientCert, clientTrust, serverCert, serverTrust, requireClientAuth, clientTrustAll

    return Arrays.asList(new Object[][] {
        { false, false, true, false, false, true }, // Start TLS with Trust All
        { false, false, true, false, false, true }, // Client trusts all server certs
        { false, true, true, false, false, false }, // Client trust only the server certs
        { false, false, true, false, false, false }, // server specifies certs
        { true, true, true, true, false, false }, // client specified certs
        { true, true, true, true, true, false } // client specifies cert
    });
  }

  @Override
  protected void startNodes(int numNodes) {
    super.startNodes(numNodes, new VertxOptions().setEventBusOptions(options));
  }
}
