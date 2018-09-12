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

package io.vertx.core.net;

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
