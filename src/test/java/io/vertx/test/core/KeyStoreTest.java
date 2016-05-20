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
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.SecurityProviderOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.core.net.impl.KeyStoreHelper;
import org.junit.Test;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactorySpi;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.KeyStore;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Enumeration;

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
  public void testKeyProviderOptions() throws Exception {
    SecurityProviderOptions options = new SecurityProviderOptions();

    assertNull(options.getKeyStoreAlgorithm());
    String randString = TestUtils.randomAlphaString(100);
    assertEquals(options, options.setKeyStoreAlgorithm(randString));
    assertEquals(randString, options.getKeyStoreAlgorithm());

    assertNull(options.getManagerAlgorithm());
    randString = TestUtils.randomAlphaString(100);
    assertEquals(options, options.setManagerAlgorithm(randString));
    assertEquals(randString, options.getManagerAlgorithm());
  }

  @Test
  public void testDefaultKeyProviderOptionsJson() throws Exception {
    SecurityProviderOptions def = new SecurityProviderOptions();
    SecurityProviderOptions json = new SecurityProviderOptions(new JsonObject());
    assertEquals(def.getKeyStoreAlgorithm(), json.getKeyStoreAlgorithm());
    assertEquals(def.getManagerAlgorithm(), json.getManagerAlgorithm());
  }

  @Test
  public void testKeyProviderOptionsJson() throws Exception {
    SecurityProviderOptions options = new SecurityProviderOptions(new JsonObject());
    assertEquals(null, options.getKeyStoreAlgorithm());
    assertEquals(null, options.getManagerAlgorithm());

    String keyStoreAlgorithm = TestUtils.randomAlphaString(100);
    String managerAlgorithm = TestUtils.randomAlphaString(100);
    options = new SecurityProviderOptions(new JsonObject().
            put("keyStoreAlgorithm", keyStoreAlgorithm).
            put("managerAlgorithm", managerAlgorithm));
    assertEquals(keyStoreAlgorithm, options.getKeyStoreAlgorithm());
    assertEquals(managerAlgorithm, options.getManagerAlgorithm());
  }

  @Test
  public void testCopyKeyProviderOptions() throws Exception {
    SecurityProviderOptions options = new SecurityProviderOptions(new JsonObject());
    String keyStoreAlgorithm = TestUtils.randomAlphaString(100);
    String managerAlgorithm = TestUtils.randomAlphaString(100);
    options.setKeyStoreAlgorithm(keyStoreAlgorithm);
    options.setManagerAlgorithm(managerAlgorithm);
    options = new SecurityProviderOptions(options);
    assertEquals(keyStoreAlgorithm, options.getKeyStoreAlgorithm());
    assertEquals(managerAlgorithm, options.getManagerAlgorithm());
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
  }

  @Test
  public void testJKSPath() throws Exception {
    testKeyStore(TLSCert.JKS.getServerKeyCertOptions());
  }

  @Test
  public void testJKSValue() throws Exception {
    JksOptions options = (JksOptions) TLSCert.JKS.getServerKeyCertOptions();
    Buffer store = vertx.fileSystem().readFileBlocking(options.getPath());
    options.setPath(null).setValue(store);
    testKeyStore(options);
  }

  @Test
  public void testPKCS12Path() throws Exception {
    testKeyStore(TLSCert.PKCS12.getServerKeyCertOptions());
  }

  @Test
  public void testPKCS12Value() throws Exception {
    PfxOptions options = (PfxOptions) TLSCert.PKCS12.getServerKeyCertOptions();
    Buffer store = vertx.fileSystem().readFileBlocking(options.getPath());
    options.setPath(null).setValue(store);
    testKeyStore(options);
  }

  @Test
  public void testKeyCertPath() throws Exception {
    testKeyStore(TLSCert.PEM.getServerKeyCertOptions());
  }

  @Test
  public void testKeyCertValue() throws Exception {
    PemKeyCertOptions options = (PemKeyCertOptions) TLSCert.PEM.getServerKeyCertOptions();
    Buffer key = vertx.fileSystem().readFileBlocking(options.getKeyPath());
    options.setKeyValue(null).setKeyValue(key);
    Buffer cert = vertx.fileSystem().readFileBlocking(options.getCertPath());
    options.setCertValue(null).setCertValue(cert);
    testKeyStore(options);
  }

  @Test
  public void testCaPath() throws Exception {
    testTrustStore(TLSCert.PEM.getServerTrustOptions());
  }

  @Test
  public void testCaPathValue() throws Exception {
    PemTrustOptions options = (PemTrustOptions) TLSCert.PEM.getServerTrustOptions();
    options.getCertPaths().
        stream().
        map(vertx.fileSystem()::readFileBlocking).
        forEach(options::addCertValue);
    options.getCertPaths().clear();
    testTrustStore(options);
  }

  @Test
  public void testKeySecurityProvider() throws Exception {
    Provider prov = new TestSecurityProvider();
    Security.addProvider(prov);
    SecurityProviderOptions options = new SecurityProviderOptions().setManagerAlgorithm("testkeymgr").setKeyStoreAlgorithm("testkeystore");
    testKeyStore(options);
    assertTrue(TestKeyManager.checked);
    assertTrue(TestKeyStore.checked);
    Security.removeProvider(prov.getName());
  }

  @Test
  public void testTrustSecurityProvider() throws Exception {
    Provider prov = new TestSecurityProvider();
    Security.addProvider(prov);
    SecurityProviderOptions options = new SecurityProviderOptions().setManagerAlgorithm("testtrustmgr").setKeyStoreAlgorithm("testkeystore");
    testTrustStore(options);
    assertTrue(TestTrustManager.checked);
    assertTrue(TestKeyStore.checked);
    Security.removeProvider(prov.getName());
  }

  @Test
  public void testKeyOptionsEquality() {
    JksOptions jksOptions = (JksOptions) TLSCert.JKS.getServerKeyCertOptions();
    JksOptions jksOptionsCopy = new JksOptions(jksOptions);

    PfxOptions pfxOptions = (PfxOptions) TLSCert.PKCS12.getServerKeyCertOptions();
    PfxOptions pfxOptionsCopy = new PfxOptions(pfxOptions);

    PemKeyCertOptions pemKeyCertOptions = (PemKeyCertOptions) TLSCert.PEM.getServerKeyCertOptions();
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
    KeyStore keyStore = helper.loadStore((VertxInternal) vertx);
    Enumeration<String> aliases = keyStore.aliases();
    assertTrue(aliases.hasMoreElements());
    KeyManager[] keyManagers = helper.getKeyMgrs((VertxInternal) vertx);
    assertTrue(keyManagers.length > 0);
  }

  private void testTrustStore(TrustOptions options) throws Exception {
    KeyStoreHelper helper = KeyStoreHelper.create((VertxInternal) vertx, options);
    TrustManager[] keyManagers = helper.getTrustMgrs((VertxInternal) vertx);
    assertTrue(keyManagers.length > 0);
  }

  private static class TestSecurityProvider extends Provider {
    TestSecurityProvider() {
      super("test", 1.0, "test");
      put("KeyStore.testkeystore", TestKeyStore.class.getName());
      put("KeyManagerFactory.testkeymgr", TestKeyManager.class.getName());
      put("TrustManagerFactory.testtrustmgr", TestTrustManager.class.getName());
    }
  }

  public static class TestKeyManager extends KeyManagerFactorySpi {
    static boolean checked;
    @Override
    protected void engineInit(KeyStore ks, char[] password) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
    }
    @Override
    protected void engineInit(ManagerFactoryParameters spec) throws InvalidAlgorithmParameterException {
    }
    @Override
    protected KeyManager[] engineGetKeyManagers() {
      checked = true;
      return new KeyManager[] { null };
    }
  }

  public static class TestTrustManager extends TrustManagerFactorySpi {
    static boolean checked;
    @Override
    protected void engineInit(KeyStore ks) throws KeyStoreException {
    }
    @Override
    protected void engineInit(ManagerFactoryParameters spec) throws InvalidAlgorithmParameterException {
    }
    @Override
    protected TrustManager[] engineGetTrustManagers() {
      checked = true;
      return new TrustManager[] { null };
    }
  }

  public static class TestKeyStore extends KeyStoreSpi {
    static boolean checked;
    public TestKeyStore() {
      super();
      checked = true;
    }
    @Override
    public Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
      return null;
    }
    @Override
    public Certificate[] engineGetCertificateChain(String alias) {
      return new Certificate[0];
    }
    @Override
    public Certificate engineGetCertificate(String alias) {
      return null;
    }
    @Override
    public Date engineGetCreationDate(String alias) {
      return null;
    }
    @Override
    public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException {
    }
    @Override
    public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
    }
    @Override
    public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
    }
    @Override
    public void engineDeleteEntry(String alias) throws KeyStoreException {
    }
    @Override
    public Enumeration<String> engineAliases() {
      return Collections.enumeration(Collections.singletonList("testalias"));
    }
    @Override
    public boolean engineContainsAlias(String alias) {
      return false;
    }
    @Override
    public int engineSize() {
      return 0;
    }
    @Override
    public boolean engineIsKeyEntry(String alias) {
      return false;
    }
    @Override
    public boolean engineIsCertificateEntry(String alias) {
      return false;
    }
    @Override
    public String engineGetCertificateAlias(Certificate cert) {
      return null;
    }
    @Override
    public void engineStore(OutputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
    }
    @Override
    public void engineLoad(InputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
    }
  };

}
