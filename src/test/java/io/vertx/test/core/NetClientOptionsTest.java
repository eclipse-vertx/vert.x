/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;
import static org.junit.Assert.assertEquals;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClientOptions;

import org.junit.Test;

/**
 * test that the default object of NetClientOptions equals to when creating
 * a NetClientOptions object from an empty Json object. Previously the json constructor
 * used null for the enabledCipherSuite property which breaks the addEnabledCipherSuite
 * operation.
 */

/**
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 *
 */
public class NetClientOptionsTest {

  @Test
  public final void testEquals() {
    NetClientOptions options1 = new NetClientOptions();
    NetClientOptions options2 = new NetClientOptions(new JsonObject("{}"));
    assertEquals(options1, options2);

    options1.setHostnameVerificationAlgorithm("HTTPS");
    options2.setHostnameVerificationAlgorithm("HTTPS");
    assertEquals(options1, options2);
    options2.setHostnameVerificationAlgorithm(new String("HTTPS"));
    assertEquals(options1, options2);
  }

  @Test
  public final void testAdd() {
    NetClientOptions options = new NetClientOptions(new JsonObject("{}"));
    options.addEnabledCipherSuite("XXX");
  }

}
