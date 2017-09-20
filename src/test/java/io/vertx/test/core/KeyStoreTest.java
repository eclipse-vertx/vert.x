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
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.core.net.impl.KeyStoreHelper;
import io.vertx.test.core.tls.Cert;
import io.vertx.test.core.tls.Trust;
import org.junit.Test;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;

import static io.vertx.test.core.TestUtils.assertIllegalArgumentException;
import static io.vertx.test.core.TestUtils.assertNullPointerException;
import static org.junit.Assert.assertNotEquals;

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
    options = new JksOptions(options.toJson());
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
    options = new PfxOptions(options.toJson());
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
  public void testPemKeyCertOptionsJson() throws Exception {
    PemKeyCertOptions options = new PemKeyCertOptions(new JsonObject());
    assertEquals(null, options.getKeyPath());
    assertEquals(null, options.getKeyValue());
    assertEquals(null, options.getCertPath());
    assertEquals(null, options.getCertValue());

    String keyPath1 = TestUtils.randomAlphaString(100);
    Buffer keyValue1 = Buffer.buffer(TestUtils.randomAlphaString(100));
    String certPath1 = TestUtils.randomAlphaString(100);
    Buffer certValue1 = Buffer.buffer(TestUtils.randomAlphaString(100));
    options = new PemKeyCertOptions(new JsonObject().
        put("keyPath", keyPath1).
        put("keyValue", keyValue1.getBytes()).
        put("certPath", certPath1).
        put("certValue", certValue1.getBytes()));
    assertEquals(keyPath1, options.getKeyPath());
    assertEquals(keyValue1, options.getKeyValue());
    assertEquals(certPath1, options.getCertPath());
    assertEquals(certValue1, options.getCertValue());
    assertEquals(Collections.singletonList(keyPath1), options.getKeyPaths());
    assertEquals(Collections.singletonList(keyValue1), options.getKeyValues());
    assertEquals(Collections.singletonList(certPath1), options.getCertPaths());
    assertEquals(Collections.singletonList(certValue1), options.getCertValues());

    String keyPath2 = TestUtils.randomAlphaString(100);
    Buffer keyValue2 = Buffer.buffer(TestUtils.randomAlphaString(100));
    String certPath2 = TestUtils.randomAlphaString(100);
    Buffer certValue2 = Buffer.buffer(TestUtils.randomAlphaString(100));
    options = new PemKeyCertOptions(new JsonObject().
        put("keyPaths", new JsonArray().add(keyPath1).add(keyPath2)).
        put("keyValues", new JsonArray().add(keyValue1.getBytes()).add(keyValue2.getBytes())).
        put("certPaths", new JsonArray().add(certPath1).add(certPath2)).
        put("certValues", new JsonArray().add(certValue1.getBytes()).add(certValue2.getBytes())));
    assertEquals(keyPath1, options.getKeyPath());
    assertEquals(keyValue1, options.getKeyValue());
    assertEquals(certPath1, options.getCertPath());
    assertEquals(certValue1, options.getCertValue());
    assertEquals(Arrays.asList(keyPath1, keyPath2), options.getKeyPaths());
    assertEquals(Arrays.asList(keyValue1, keyValue2), options.getKeyValues());
    assertEquals(Arrays.asList(certPath1, certPath2), options.getCertPaths());
    assertEquals(Arrays.asList(certValue1, certValue2), options.getCertValues());
  }

  @Test
  public void testCopyPemKeyCertOptions() throws Exception {
    PemKeyCertOptions options = new PemKeyCertOptions(new JsonObject());
    String keyPath1 = TestUtils.randomAlphaString(100);
    Buffer keyValue1 = Buffer.buffer(TestUtils.randomAlphaString(100));
    String certPath1 = TestUtils.randomAlphaString(100);
    Buffer certValue1 = Buffer.buffer(TestUtils.randomAlphaString(100));
    options.setKeyPath(keyPath1);
    options.setKeyValue(keyValue1);
    options.setCertPath(certPath1);
    options.setCertValue(certValue1);
    options = new PemKeyCertOptions(options);
    assertEquals(keyPath1, options.getKeyPath());
    assertEquals(keyValue1, options.getKeyValue());
    assertEquals(certPath1, options.getCertPath());
    assertEquals(certValue1, options.getCertValue());
    assertEquals(Collections.singletonList(keyPath1), options.getKeyPaths());
    assertEquals(Collections.singletonList(keyValue1), options.getKeyValues());
    assertEquals(Collections.singletonList(certPath1), options.getCertPaths());
    assertEquals(Collections.singletonList(certValue1), options.getCertValues());
    options = new PemKeyCertOptions(options.toJson());
    assertEquals(keyPath1, options.getKeyPath());
    assertEquals(keyValue1, options.getKeyValue());
    assertEquals(certPath1, options.getCertPath());
    assertEquals(certValue1, options.getCertValue());
    assertEquals(Collections.singletonList(keyPath1), options.getKeyPaths());
    assertEquals(Collections.singletonList(keyValue1), options.getKeyValues());
    assertEquals(Collections.singletonList(certPath1), options.getCertPaths());
    assertEquals(Collections.singletonList(certValue1), options.getCertValues());

    String keyPath2 = TestUtils.randomAlphaString(100);
    Buffer keyValue2 = Buffer.buffer(TestUtils.randomAlphaString(100));
    String certPath2 = TestUtils.randomAlphaString(100);
    Buffer certValue2 = Buffer.buffer(TestUtils.randomAlphaString(100));
    options.addKeyPath(keyPath2);
    options.addKeyValue(keyValue2);
    options.addCertPath(certPath2);
    options.addCertValue(certValue2);
    options = new PemKeyCertOptions(options);
    assertEquals(keyPath1, options.getKeyPath());
    assertEquals(keyValue1, options.getKeyValue());
    assertEquals(certPath1, options.getCertPath());
    assertEquals(certValue1, options.getCertValue());
    assertEquals(Arrays.asList(keyPath1, keyPath2), options.getKeyPaths());
    assertEquals(Arrays.asList(keyValue1, keyValue2), options.getKeyValues());
    assertEquals(Arrays.asList(certPath1, certPath2), options.getCertPaths());
    assertEquals(Arrays.asList(certValue1, certValue2), options.getCertValues());
    options = new PemKeyCertOptions(options.toJson());
    assertEquals(keyPath1, options.getKeyPath());
    assertEquals(keyValue1, options.getKeyValue());
    assertEquals(certPath1, options.getCertPath());
    assertEquals(certValue1, options.getCertValue());
    assertEquals(Arrays.asList(keyPath1, keyPath2), options.getKeyPaths());
    assertEquals(Arrays.asList(keyValue1, keyValue2), options.getKeyValues());
    assertEquals(Arrays.asList(certPath1,certPath2), options.getCertPaths());
    assertEquals(Arrays.asList(certValue1, certValue2), options.getCertValues());
  }

  @Test
  public void testTrustOptions() throws Exception {
    PemTrustOptions options = new PemTrustOptions();

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
  public void testTrustOptionsJson() throws Exception {
    PemTrustOptions options = new PemTrustOptions(new JsonObject());

    assertEquals(Collections.emptyList(), options.getCertPaths());
    assertEquals(Collections.emptyList(), options.getCertValues());

    String certPath = TestUtils.randomAlphaString(100);
    String certValue = TestUtils.randomAlphaString(100);
    JsonObject json = new JsonObject().
        put("certPaths", new JsonArray().add(certPath)).
        put("certValues", new JsonArray().add(certValue.getBytes()));
    options = new PemTrustOptions(json);
    assertEquals(Collections.singletonList(certPath), options.getCertPaths());
    assertEquals(Collections.singletonList(Buffer.buffer(certValue)), options.getCertValues());
  }

  @Test
  public void testDefaultTrustOptionsJson() {
    PemTrustOptions def = new PemTrustOptions();
    PemTrustOptions json = new PemTrustOptions(new JsonObject());
    assertEquals(def.getCertPaths(), json.getCertPaths());
    assertEquals(def.getCertValues(), json.getCertValues());
  }

  @Test
  public void testCopyTrustOptions() throws Exception {
    PemTrustOptions options = new PemTrustOptions(new JsonObject());
    String certPath = TestUtils.randomAlphaString(100);
    Buffer certValue = Buffer.buffer(TestUtils.randomAlphaString(100));
    options.addCertPath(certPath);
    options.addCertValue(certValue);
    options = new PemTrustOptions(options);
    assertEquals(Collections.singletonList(certPath), options.getCertPaths());
    assertEquals(Collections.singletonList(certValue), options.getCertValues());
    options = new PemTrustOptions(options.toJson());
    assertEquals(Collections.singletonList(certPath), options.getCertPaths());
    assertEquals(Collections.singletonList(certValue), options.getCertValues());
  }

  @Test
  public void testTrustOptionsEquality() {
    String certPath1 = TestUtils.randomAlphaString(100);
    String certPath2 = TestUtils.randomAlphaString(100);
    Buffer certValue1 = Buffer.buffer(TestUtils.randomAlphaString(100));
    Buffer certValue2 = Buffer.buffer(TestUtils.randomAlphaString(100));

    PemTrustOptions options = new PemTrustOptions();
    PemTrustOptions otherOptions = new PemTrustOptions();
    assertEquals(options, otherOptions);
    assertEquals(options.hashCode(), otherOptions.hashCode());

    options.addCertPath(certPath1);
    options.addCertPath(certPath2);
    options.addCertValue(certValue1);
    options.addCertValue(certValue2);
    otherOptions.addCertPath(certPath1);
    otherOptions.addCertPath(certPath2);
    otherOptions.addCertValue(certValue1);
    otherOptions.addCertValue(certValue2);
    assertEquals(options, otherOptions);
    assertEquals(options.hashCode(), otherOptions.hashCode());

    otherOptions.addCertPath(TestUtils.randomAlphaString(100));
    assertNotEquals(options, otherOptions);

    PemTrustOptions reverseOrderOptions = new PemTrustOptions();
    reverseOrderOptions.addCertPath(certPath2);
    reverseOrderOptions.addCertPath(certPath1);
    reverseOrderOptions.addCertValue(certValue2);
    reverseOrderOptions.addCertValue(certValue1);
    assertNotEquals(options, reverseOrderOptions);
  }

  @Test
  public void testJKSPath() throws Exception {
    testKeyStore(Cert.SERVER_JKS.get());
  }

  @Test
  public void testJKSValue() throws Exception {
    JksOptions options = Cert.SERVER_JKS.get();
    Buffer store = vertx.fileSystem().readFileBlocking(options.getPath());
    options.setPath(null).setValue(store);
    testKeyStore(options);
  }

  @Test
  public void testPKCS12Path() throws Exception {
    testKeyStore(Cert.SERVER_PKCS12.get());
  }

  @Test
  public void testPKCS12Value() throws Exception {
    PfxOptions options = Cert.SERVER_PKCS12.get();
    Buffer store = vertx.fileSystem().readFileBlocking(options.getPath());
    options.setPath(null).setValue(store);
    testKeyStore(options);
  }

  @Test
  public void testKeyCertPath() throws Exception {
    testKeyStore(Cert.SERVER_PEM.get());
  }

  /**
   * Test RSA PKCS#1 PEM key
   * #1851 
   */
  @Test
  public void testRsaKeyCertPath() throws Exception {
    testKeyStore(Cert.SERVER_PEM_RSA.get());
  }
  
  @Test
  public void testKeyCertValue() throws Exception {
    PemKeyCertOptions options = Cert.SERVER_PEM.get();
    Buffer key = vertx.fileSystem().readFileBlocking(options.getKeyPath());
    options.setKeyValue(null).setKeyValue(key);
    Buffer cert = vertx.fileSystem().readFileBlocking(options.getCertPath());
    options.setCertValue(null).setCertValue(cert);
    testKeyStore(options);
  }

  @Test
  public void testCaPath() throws Exception {
    testTrustStore(Trust.SERVER_PEM.get());
  }

  @Test
  public void testCaPathValue() throws Exception {
    PemTrustOptions options = Trust.SERVER_PEM.get();
    options.getCertPaths().
        stream().
        map(vertx.fileSystem()::readFileBlocking).
        forEach(options::addCertValue);
    options.getCertPaths().clear();
    testTrustStore(options);
  }

  @Test
  public void testKeyOptionsEquality() {
    JksOptions jksOptions = Cert.SERVER_JKS.get();
    JksOptions jksOptionsCopy = new JksOptions(jksOptions);

    PfxOptions pfxOptions = Cert.SERVER_PKCS12.get();
    PfxOptions pfxOptionsCopy = new PfxOptions(pfxOptions);

    PemKeyCertOptions pemKeyCertOptions = Cert.SERVER_PEM.get();
    PemKeyCertOptions pemKeyCertOptionsCopy = new PemKeyCertOptions(pemKeyCertOptions);

    assertEquals(jksOptions, jksOptionsCopy);
    assertEquals(jksOptions.hashCode(), jksOptionsCopy.hashCode());

    assertEquals(pfxOptions, pfxOptionsCopy);
    assertEquals(pfxOptions.hashCode(), pfxOptionsCopy.hashCode());

    assertEquals(pemKeyCertOptions, pemKeyCertOptionsCopy);
    assertEquals(pemKeyCertOptions.hashCode(), pemKeyCertOptionsCopy.hashCode());
  }

  private void testKeyStore(KeyCertOptions options) throws Exception {
    KeyStoreHelper helper = KeyStoreHelper.create((VertxInternal) vertx, options);
    KeyStore keyStore = helper.store();
    Enumeration<String> aliases = keyStore.aliases();
    assertTrue(aliases.hasMoreElements());
    KeyManager[] keyManagers = helper.getKeyMgr();
    assertTrue(keyManagers.length > 0);
  }

  private void testTrustStore(TrustOptions options) throws Exception {
    KeyStoreHelper helper = KeyStoreHelper.create((VertxInternal) vertx, options);
    TrustManager[] keyManagers = helper.getTrustMgrs((VertxInternal) vertx);
    assertTrue(keyManagers.length > 0);
  }

/*
  @Test
  public void testFoo() throws Exception {

    KeyCertOptions jksOptions = new JksOptions().setPath("server-sni-keystore.jks").setPassword("wibble");
    KeyStoreHelper helper = KeyStoreHelper.create((VertxInternal) vertx, jksOptions);
    assertNull(helper.getKeyMgr("does.not.exist"));
    X509KeyManager abc = helper.getKeyMgr("host1");
    assertNotNull(abc);
  }
*/
}
