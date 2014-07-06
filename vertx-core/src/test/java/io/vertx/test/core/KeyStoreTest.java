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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.CaOptions;
import io.vertx.core.net.JKSOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.KeyStoreOptions;
import io.vertx.core.net.PKCS12Options;
import io.vertx.core.net.TrustStoreOptions;
import io.vertx.core.net.impl.KeyStoreHelper;
import org.junit.Test;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

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

  @Test
  public void testJKSPath() throws Exception {
    testKeyStore(getServerCertOptions(KS.JKS));
  }

  @Test
  public void testJKSValue() throws Exception {
    JKSOptions options = (JKSOptions) getServerCertOptions(KS.JKS);
    Buffer store = vertx.fileSystem().readFileSync(options.getPath());
    options.setPath(null).setValue(store);
    testKeyStore(options);
  }

  @Test
  public void testPKCS12Path() throws Exception {
    testKeyStore(getServerCertOptions(KS.PKCS12));
  }

  @Test
  public void testPKCS12Value() throws Exception {
    PKCS12Options options = (PKCS12Options) getServerCertOptions(KS.PKCS12);
    Buffer store = vertx.fileSystem().readFileSync(options.getPath());
    options.setPath(null).setValue(store);
    testKeyStore(options);
  }

  @Test
  public void testKeyCertPath() throws Exception {
    testKeyStore(getServerCertOptions(KS.PEM));
  }

  @Test
  public void testKeyCertValue() throws Exception {
    KeyCertOptions options = (KeyCertOptions) getServerCertOptions(KS.PEM);
    Buffer key = vertx.fileSystem().readFileSync(options.getKeyPath());
    options.setKeyValue(null).setKeyValue(key);
    Buffer cert = vertx.fileSystem().readFileSync(options.getCertPath());
    options.setCertValue(null).setCertValue(cert);
    testKeyStore(options);
  }

  @Test
  public void testCaPath() throws Exception {
    testTrustStore(getServerTrustOptions(TS.PEM));
  }

  @Test
  public void testCaPathValue() throws Exception {
    CaOptions options = (CaOptions) getServerTrustOptions(TS.PEM);
    options.getCertPaths().
        stream().
        map(vertx.fileSystem()::readFileSync).
        forEach(options::addCertValue);
    options.getCertPaths().clear();
    testTrustStore(options);
  }

  private void testKeyStore(KeyStoreOptions options) throws Exception {
    KeyStoreHelper helper = KeyStoreHelper.create((VertxInternal) vertx, options);
    KeyManager[] keyManagers = helper.getKeyMgrs((VertxInternal) vertx);
    assertTrue(keyManagers.length > 0);
  }

  private void testTrustStore(TrustStoreOptions options) throws Exception {
    KeyStoreHelper helper = KeyStoreHelper.create((VertxInternal) vertx, options);
    TrustManager[] keyManagers = helper.getTrustMgrs((VertxInternal) vertx);
    assertTrue(keyManagers.length > 0);
  }
}
