/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.test.core;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemCaOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.CaOptions;
import io.vertx.core.net.impl.KeyStoreHelper;
import org.junit.Test;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import java.util.Collections;

import static io.vertx.test.core.TestUtils.assertIllegalArgumentException;
import static io.vertx.test.core.TestUtils.assertNullPointerException;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class KeyStoreTest extends VertxTestBase {

  @Test
  public void testJKSOptions() throws Exception {
    JksOptions options = new JksOptions();

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
    JksOptions def = new JksOptions();
    JksOptions json = new JksOptions(new JsonObject());
    assertEquals(def.getPassword(), json.getPassword());
    assertEquals(def.getPath(), json.getPath());
    assertEquals(def.getValue(), json.getValue());
  }

  @Test
  public void testJKSOptionsJson() throws Exception {
    JksOptions options = new JksOptions(new JsonObject());
    assertEquals(null, options.getPassword());
    assertEquals(null, options.getPath());
    assertEquals(null, options.getValue());

    String password = TestUtils.randomAlphaString(100);
    String path = TestUtils.randomAlphaString(100);
    String value = TestUtils.randomAlphaString(100);
    options = new JksOptions(new JsonObject().
        put("password", password).
        put("path", path).
        put("value", value.getBytes()));
    assertEquals(password, options.getPassword());
    assertEquals(path, options.getPath());
    assertEquals(Buffer.buffer(value), options.getValue());
  }

  @Test
  public void testCopyJKSOptions() throws Exception {
    JksOptions options = new JksOptions();
    String password = TestUtils.randomAlphaString(100);
    String path = TestUtils.randomAlphaString(100);
    Buffer value = Buffer.buffer(TestUtils.randomAlphaString(100));
    options.setPassword(password);
    options.setPath(path);
    options.setValue(value);
    options = new JksOptions(options);
    assertEquals(password, options.getPassword());
    assertEquals(path, options.getPath());
    assertEquals(value, options.getValue());
  }

  @Test
  public void testPKCS12Options() throws Exception {
    PfxOptions options = new PfxOptions();

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
    PfxOptions def = new PfxOptions();
    PfxOptions json = new PfxOptions(new JsonObject());
    assertEquals(def.getPassword(), json.getPassword());
    assertEquals(def.getPath(), json.getPath());
    assertEquals(def.getValue(), json.getValue());
  }

  @Test
  public void testPKCS12OptionsJson() throws Exception {
    PfxOptions options = new PfxOptions(new JsonObject());
    assertEquals(null, options.getPassword());
    assertEquals(null, options.getPath());
    assertEquals(null, options.getValue());

    String password = TestUtils.randomAlphaString(100);
    String path = TestUtils.randomAlphaString(100);
    String value = TestUtils.randomAlphaString(100);
    options = new PfxOptions(new JsonObject().
        put("password", password).
        put("path", path).
        put("value", value.getBytes()));
    assertEquals(password, options.getPassword());
    assertEquals(path, options.getPath());
    assertEquals(Buffer.buffer(value), options.getValue());
  }

  @Test
  public void testCopyPKCS12Options() throws Exception {
    PfxOptions options = new PfxOptions();
    String password = TestUtils.randomAlphaString(100);
    String path = TestUtils.randomAlphaString(100);
    Buffer value = Buffer.buffer(TestUtils.randomAlphaString(100));
    options.setPassword(password);
    options.setPath(path);
    options.setValue(value);
    options = new PfxOptions(options);
    assertEquals(password, options.getPassword());
    assertEquals(path, options.getPath());
    assertEquals(value, options.getValue());
  }

  @Test
  public void testKeyCertOptions() throws Exception {
    PemKeyCertOptions options = new PemKeyCertOptions();

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
    PemKeyCertOptions def = new PemKeyCertOptions();
    PemKeyCertOptions json = new PemKeyCertOptions(new JsonObject());
    assertEquals(def.getKeyPath(), json.getKeyPath());
    assertEquals(def.getCertPath(), json.getCertPath());
    assertEquals(def.getKeyValue(), json.getKeyValue());
    assertEquals(def.getCertValue(), json.getCertValue());
  }

  @Test
  public void testKeyCertOptionsJson() throws Exception {
    PemKeyCertOptions options = new PemKeyCertOptions(new JsonObject());
    assertEquals(null, options.getKeyPath());
    assertEquals(null, options.getKeyValue());
    assertEquals(null, options.getCertPath());
    assertEquals(null, options.getCertValue());

    String keyPath = TestUtils.randomAlphaString(100);
    String keyValue = TestUtils.randomAlphaString(100);
    String certPath = TestUtils.randomAlphaString(100);
    String certValue = TestUtils.randomAlphaString(100);
    options = new PemKeyCertOptions(new JsonObject().
        put("keyPath", keyPath).
        put("keyValue", keyValue.getBytes()).
        put("certPath", certPath).
        put("certValue", certValue.getBytes()));
    assertEquals(keyPath, options.getKeyPath());
    assertEquals(Buffer.buffer(keyValue), options.getKeyValue());
    assertEquals(certPath, options.getCertPath());
    assertEquals(Buffer.buffer(certValue), options.getCertValue());
  }

  @Test
  public void testCopyKeyCertOptions() throws Exception {
    PemKeyCertOptions options = new PemKeyCertOptions(new JsonObject());
    String keyPath = TestUtils.randomAlphaString(100);
    Buffer keyValue = Buffer.buffer(TestUtils.randomAlphaString(100));
    String certPath = TestUtils.randomAlphaString(100);
    Buffer certValue = Buffer.buffer(TestUtils.randomAlphaString(100));
    options.setKeyPath(keyPath);
    options.setKeyValue(keyValue);
    options.setCertPath(certPath);
    options.setCertValue(certValue);
    options = new PemKeyCertOptions(options);
    assertEquals(keyPath, options.getKeyPath());
    assertEquals(keyValue, options.getKeyValue());
    assertEquals(certPath, options.getCertPath());
    assertEquals(certValue, options.getCertValue());
  }

  @Test
  public void testCaOptions() throws Exception {
    PemCaOptions options = new PemCaOptions();

    assertEquals(Collections.emptyList(), options.getCertPaths());
    assertNullPointerException(() -> options.addCertPath(null));
    assertIllegalArgumentException(() -> options.addCertPath(""));
    String randString = TestUtils.randomAlphaString(100);
    options.addCertPath(randString);
    assertEquals(Collections.singletonList(randString), options.getCertPaths());

    assertEquals(Collections.emptyList(), options.getCertValues());
    assertNullPointerException(() -> options.addCertValue(null));
    randString = TestUtils.randomAlphaString(100);
    options.addCertValue(Buffer.buffer(randString));
    assertEquals(Collections.singletonList(Buffer.buffer(randString)), options.getCertValues());
  }

  @Test
  public void testCaOptionsJson() throws Exception {
    PemCaOptions options = new PemCaOptions(new JsonObject());

    assertEquals(Collections.emptyList(), options.getCertPaths());
    assertEquals(Collections.emptyList(), options.getCertValues());

    String certPath = TestUtils.randomAlphaString(100);
    String certValue = TestUtils.randomAlphaString(100);
    JsonObject json = new JsonObject().
        put("certPaths", new JsonArray().add(certPath)).
        put("certValues", new JsonArray().add(certValue.getBytes()));
    options = new PemCaOptions(json);
    assertEquals(Collections.singletonList(certPath), options.getCertPaths());
    assertEquals(Collections.singletonList(Buffer.buffer(certValue)), options.getCertValues());
  }

  @Test
  public void testDefaultCaOptionsJson() {
    PemCaOptions def = new PemCaOptions();
    PemCaOptions json = new PemCaOptions(new JsonObject());
    assertEquals(def.getCertPaths(), json.getCertPaths());
    assertEquals(def.getCertValues(), json.getCertValues());
  }

  @Test
  public void testCopyCaOptions() throws Exception {
    PemCaOptions options = new PemCaOptions(new JsonObject());
    String certPath = TestUtils.randomAlphaString(100);
    Buffer certValue = Buffer.buffer(TestUtils.randomAlphaString(100));
    options.addCertPath(certPath);
    options.addCertValue(certValue);
    options = new PemCaOptions(options);
    assertEquals(Collections.singletonList(certPath), options.getCertPaths());
    assertEquals(Collections.singletonList(certValue), options.getCertValues());
  }

  @Test
  public void testJKSPath() throws Exception {
    testKeyStore(getServerCertOptions(KS.JKS));
  }

  @Test
  public void testJKSValue() throws Exception {
    JksOptions options = (JksOptions) getServerCertOptions(KS.JKS);
    Buffer store = vertx.fileSystem().readFileBlocking(options.getPath());
    options.setPath(null).setValue(store);
    testKeyStore(options);
  }

  @Test
  public void testPKCS12Path() throws Exception {
    testKeyStore(getServerCertOptions(KS.PKCS12));
  }

  @Test
  public void testPKCS12Value() throws Exception {
    PfxOptions options = (PfxOptions) getServerCertOptions(KS.PKCS12);
    Buffer store = vertx.fileSystem().readFileBlocking(options.getPath());
    options.setPath(null).setValue(store);
    testKeyStore(options);
  }

  @Test
  public void testKeyCertPath() throws Exception {
    testKeyStore(getServerCertOptions(KS.PEM));
  }

  @Test
  public void testKeyCertValue() throws Exception {
    PemKeyCertOptions options = (PemKeyCertOptions) getServerCertOptions(KS.PEM);
    Buffer key = vertx.fileSystem().readFileBlocking(options.getKeyPath());
    options.setKeyValue(null).setKeyValue(key);
    Buffer cert = vertx.fileSystem().readFileBlocking(options.getCertPath());
    options.setCertValue(null).setCertValue(cert);
    testKeyStore(options);
  }

  @Test
  public void testCaPath() throws Exception {
    testTrustStore(getServerTrustOptions(TS.PEM));
  }

  @Test
  public void testCaPathValue() throws Exception {
    PemCaOptions options = (PemCaOptions) getServerTrustOptions(TS.PEM);
    options.getCertPaths().
        stream().
        map(vertx.fileSystem()::readFileBlocking).
        forEach(options::addCertValue);
    options.getCertPaths().clear();
    testTrustStore(options);
  }

  private void testKeyStore(KeyCertOptions options) throws Exception {
    KeyStoreHelper helper = KeyStoreHelper.create((VertxInternal) vertx, options);
    KeyManager[] keyManagers = helper.getKeyMgrs((VertxInternal) vertx);
    assertTrue(keyManagers.length > 0);
  }

  private void testTrustStore(CaOptions options) throws Exception {
    KeyStoreHelper helper = KeyStoreHelper.create((VertxInternal) vertx, options);
    TrustManager[] keyManagers = helper.getTrustMgrs((VertxInternal) vertx);
    assertTrue(keyManagers.length > 0);
  }
}
