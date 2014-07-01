/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.vertx.test.core;

import org.junit.Test;
import io.vertx.core.net.JKSOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.PKCS12Options;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class KeyStoreTest extends VertxTestBase {

  @Test
  public void testJKSOptions() throws Exception {
    JKSOptions options = new JKSOptions();

    assertNull(options.getPath());
    String randString = TestUtils.randomAlphaString(100);
    assertEquals(options, options.setPath(randString));
    assertEquals(randString, options.getPath());

    assertNull(options.getPassword());
    randString = TestUtils.randomAlphaString(100);
    assertEquals(options, options.setPassword(randString));
    assertEquals(randString, options.getPassword());
  }

  @Test
  public void testPKCS12Options() throws Exception {
    PKCS12Options options = new PKCS12Options();

    assertNull(options.getPath());
    String randString = TestUtils.randomAlphaString(100);
    assertEquals(options, options.setPath(randString));
    assertEquals(randString, options.getPath());

    assertNull(options.getPassword());
    randString = TestUtils.randomAlphaString(100);
    assertEquals(options, options.setPassword(randString));
    assertEquals(randString, options.getPassword());
  }

  @Test
  public void testKeyCertOptions() throws Exception {
    KeyCertOptions options = new KeyCertOptions();

    assertNull(options.getKeyPath());
    String randString = TestUtils.randomAlphaString(100);
    assertEquals(options, options.setKeyPath(randString));
    assertEquals(randString, options.getKeyPath());

    assertNull(options.getCertPath());
    randString = TestUtils.randomAlphaString(100);
    assertEquals(options, options.setCertPath(randString));
    assertEquals(randString, options.getCertPath());
  }
}
