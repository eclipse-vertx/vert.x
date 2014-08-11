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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
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
import java.util.Collections;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class KeyStoreTest extends VertxTestBase {

  @Test
  public void testJKSOptions() throws Exception {
    JKSOptions options = JKSOptions.options();

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
  public void testDefaultJKSOptionsJson() {
    JKSOptions def = JKSOptions.options();
    JKSOptions json = JKSOptions.optionsFromJson(new JsonObject());
    assertEquals(def.getPassword(), json.getPassword());
    assertEquals(def.getPath(), json.getPath());
    assertEquals(def.getValue(), json.getValue());
  }

  @Test
  public void testJKSOptionsJson() throws Exception {
    JKSOptions options = JKSOptions.optionsFromJson(new JsonObject());
    assertEquals(null, options.getPassword());
    assertEquals(null, options.getPath());
    assertEquals(null, options.getValue());

    String password = TestUtils.randomAlphaString(100);
    String path = TestUtils.randomAlphaString(100);
    String value = TestUtils.randomAlphaString(100);
    options = JKSOptions.optionsFromJson(new JsonObject().
        putString("password", password).
        putString("path", path).
        putBinary("value", value.getBytes()));
    assertEquals(password, options.getPassword());
    assertEquals(path, options.getPath());
    assertEquals(Buffer.buffer(value), options.getValue());
  }

  @Test
  public void testCopyJKSOptions() throws Exception {
    JKSOptions options = JKSOptions.options();
    String password = TestUtils.randomAlphaString(100);
    String path = TestUtils.randomAlphaString(100);
    Buffer value = Buffer.buffer(TestUtils.randomAlphaString(100));
    options.setPassword(password);
    options.setPath(path);
    options.setValue(value);
    options = JKSOptions.copiedOptions(options);
    assertEquals(password, options.getPassword());
    assertEquals(path, options.getPath());
    assertEquals(value, options.getValue());
  }

  @Test
  public void testPKCS12Options() throws Exception {
    PKCS12Options options = PKCS12Options.options();

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
  public void testDefaultPKCS12OptionsJson() {
    PKCS12Options def = PKCS12Options.options();
    PKCS12Options json = PKCS12Options.optionsFromJson(new JsonObject());
    assertEquals(def.getPassword(), json.getPassword());
    assertEquals(def.getPath(), json.getPath());
    assertEquals(def.getValue(), json.getValue());
  }

  @Test
  public void testPKCS12OptionsJson() throws Exception {
    PKCS12Options options = PKCS12Options.optionsFromJson(new JsonObject());
    assertEquals(null, options.getPassword());
    assertEquals(null, options.getPath());
    assertEquals(null, options.getValue());

    String password = TestUtils.randomAlphaString(100);
    String path = TestUtils.randomAlphaString(100);
    String value = TestUtils.randomAlphaString(100);
    options = PKCS12Options.optionsFromJson(new JsonObject().
        putString("password", password).
        putString("path", path).
        putBinary("value", value.getBytes()));
    assertEquals(password, options.getPassword());
    assertEquals(path, options.getPath());
    assertEquals(Buffer.buffer(value), options.getValue());
  }

  @Test
  public void testCopyPKCS12Options() throws Exception {
    PKCS12Options options = PKCS12Options.options();
    String password = TestUtils.randomAlphaString(100);
    String path = TestUtils.randomAlphaString(100);
    Buffer value = Buffer.buffer(TestUtils.randomAlphaString(100));
    options.setPassword(password);
    options.setPath(path);
    options.setValue(value);
    options = PKCS12Options.copiedOptions(options);
    assertEquals(password, options.getPassword());
    assertEquals(path, options.getPath());
    assertEquals(value, options.getValue());
  }

  @Test
  public void testKeyCertOptions() throws Exception {
    KeyCertOptions options = KeyCertOptions.options();

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
  public void testDefaultKeyCertOptionsJson() throws Exception {
    KeyCertOptions def = KeyCertOptions.options();
    KeyCertOptions json = KeyCertOptions.optionsFromJson(new JsonObject());
    assertEquals(def.getKeyPath(), json.getKeyPath());
    assertEquals(def.getCertPath(), json.getCertPath());
    assertEquals(def.getKeyValue(), json.getKeyValue());
    assertEquals(def.getCertValue(), json.getCertValue());
  }

  @Test
  public void testKeyCertOptionsJson() throws Exception {
    KeyCertOptions options = KeyCertOptions.optionsFromJson(new JsonObject());
    assertEquals(null, options.getKeyPath());
    assertEquals(null, options.getKeyValue());
    assertEquals(null, options.getCertPath());
    assertEquals(null, options.getCertValue());

    String keyPath = TestUtils.randomAlphaString(100);
    String keyValue = TestUtils.randomAlphaString(100);
    String certPath = TestUtils.randomAlphaString(100);
    String certValue = TestUtils.randomAlphaString(100);
    options = KeyCertOptions.optionsFromJson(new JsonObject().
        putString("keyPath", keyPath).
        putBinary("keyValue", keyValue.getBytes()).
        putString("certPath", certPath).
        putBinary("certValue", certValue.getBytes()));
    assertEquals(keyPath, options.getKeyPath());
    assertEquals(Buffer.buffer(keyValue), options.getKeyValue());
    assertEquals(certPath, options.getCertPath());
    assertEquals(Buffer.buffer(certValue), options.getCertValue());
  }

  @Test
  public void testCopyKeyCertOptions() throws Exception {
    KeyCertOptions options = KeyCertOptions.optionsFromJson(new JsonObject());
    String keyPath = TestUtils.randomAlphaString(100);
    Buffer keyValue = Buffer.buffer(TestUtils.randomAlphaString(100));
    String certPath = TestUtils.randomAlphaString(100);
    Buffer certValue = Buffer.buffer(TestUtils.randomAlphaString(100));
    options.setKeyPath(keyPath);
    options.setKeyValue(keyValue);
    options.setCertPath(certPath);
    options.setCertValue(certValue);
    options = KeyCertOptions.copiedOptions(options);
    assertEquals(keyPath, options.getKeyPath());
    assertEquals(keyValue, options.getKeyValue());
    assertEquals(certPath, options.getCertPath());
    assertEquals(certValue, options.getCertValue());
  }

  @Test
  public void testCaOptions() throws Exception {
    CaOptions options = CaOptions.options();

    assertEquals(Collections.emptyList(), options.getCertPaths());
    String randString = TestUtils.randomAlphaString(100);
    options.addCertPath(randString);
    assertEquals(Collections.singletonList(randString), options.getCertPaths());

    assertEquals(Collections.emptyList(), options.getCertValues());
    randString = TestUtils.randomAlphaString(100);
    options.addCertValue(Buffer.buffer(randString));
    assertEquals(Collections.singletonList(Buffer.buffer(randString)), options.getCertValues());
  }

  @Test
  public void testCaOptionsJson() throws Exception {
    CaOptions options = CaOptions.optionsFromJson(new JsonObject());

    assertEquals(Collections.emptyList(), options.getCertPaths());
    assertEquals(Collections.emptyList(), options.getCertValues());

    String certPath = TestUtils.randomAlphaString(100);
    String certValue = TestUtils.randomAlphaString(100);
    JsonObject json = new JsonObject().
        putArray("certPaths", new JsonArray().addString(certPath)).
        putArray("certValues", new JsonArray().addBinary(certValue.getBytes()));
    options = CaOptions.optionsFromJson(json);
    assertEquals(Collections.singletonList(certPath), options.getCertPaths());
    assertEquals(Collections.singletonList(Buffer.buffer(certValue)), options.getCertValues());
  }

  @Test
  public void testDefaultCaOptionsJson() {
    CaOptions def = CaOptions.options();
    CaOptions json = CaOptions.optionsFromJson(new JsonObject());
    assertEquals(def.getCertPaths(), json.getCertPaths());
    assertEquals(def.getCertValues(), json.getCertValues());
  }

  @Test
  public void testCopyCaOptions() throws Exception {
    CaOptions options = CaOptions.optionsFromJson(new JsonObject());
    String certPath = TestUtils.randomAlphaString(100);
    Buffer certValue = Buffer.buffer(TestUtils.randomAlphaString(100));
    options.addCertPath(certPath);
    options.addCertValue(certValue);
    options = CaOptions.copiedOptions(options);
    assertEquals(Collections.singletonList(certPath), options.getCertPaths());
    assertEquals(Collections.singletonList(certValue), options.getCertValues());
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
