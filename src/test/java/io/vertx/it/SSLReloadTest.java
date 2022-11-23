/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.it;

import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpTestBase;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.TrustOptions;
import nl.altindag.ssl.util.CertificateUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author <a href="mailto:hakangoudberg@hotmail.com">Hakan Altindag</a>
 */
public class SSLReloadTest extends HttpTestBase {

  private static final String HOME_DIRECTORY = System.getProperty("user.home");
  private static final String SERVER_URL = String.format("https://%s:%d/somepath", DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT);

  public SSLReloadTest() {
  }

  @Test
  public void reloadServerCertificatesWithPfxFiles() throws Exception {
    Path initialKeystorePath = copyFileToHomeDirectory("server-keystore-vertx-eclipse-short-validity.p12");
    Path initialTruststorePath = copyFileToHomeDirectory("server-truststore-vertx-eclipse-short-validity.p12");

    Path newKeystorePath = copyFileToHomeDirectory("server-keystore-vertx-eclipse-long-validity.p12");
    Path newtTruststorePath = copyFileToHomeDirectory("server-truststore-vertx-eclipse-long-validity.p12");

    KeyCertOptions keyCertOptions = new PfxOptions().setPath(initialKeystorePath.toString()).setPassword("wibble");
    TrustOptions trustOptions = new PfxOptions().setPath(initialTruststorePath.toString()).setPassword("wibble");

    assertReloadServerCertificates(keyCertOptions, trustOptions, () -> {
      Files.copy(newKeystorePath, initialKeystorePath, REPLACE_EXISTING);
      Files.copy(newtTruststorePath, initialTruststorePath, REPLACE_EXISTING);
      return null;
    });

    for (Path tempFile : Arrays.asList(initialKeystorePath, initialTruststorePath, newKeystorePath, newtTruststorePath)) {
      Files.delete(tempFile);
    }
  }

  @Test
  public void reloadServerCertificatesWithJksFiles() throws Exception {
    Path initialKeystorePath = copyFileToHomeDirectory("server-keystore-vertx-eclipse-short-validity.jks");
    Path initialTruststorePath = copyFileToHomeDirectory("server-truststore-vertx-eclipse-short-validity.jks");

    Path newKeystorePath = copyFileToHomeDirectory("server-keystore-vertx-eclipse-long-validity.jks");
    Path newtTruststorePath = copyFileToHomeDirectory("server-truststore-vertx-eclipse-long-validity.jks");

    KeyCertOptions keyCertOptions = new JksOptions().setPath(initialKeystorePath.toString()).setPassword("wibble");
    TrustOptions trustOptions = new JksOptions().setPath(initialTruststorePath.toString()).setPassword("wibble");

    assertReloadServerCertificates(keyCertOptions, trustOptions, () -> {
      Files.copy(newKeystorePath, initialKeystorePath, REPLACE_EXISTING);
      Files.copy(newtTruststorePath, initialTruststorePath, REPLACE_EXISTING);
      return null;
    });

    for (Path tempFile : Arrays.asList(initialKeystorePath, initialTruststorePath, newKeystorePath, newtTruststorePath)) {
      Files.delete(tempFile);
    }
  }

  @Test
  public void reloadServerCertificatesWithPemFiles() throws Exception {
    Path initialServerKeyPath = copyFileToHomeDirectory("server-key-vertx-eclipse-short-validity.pem");
    Path initialServerCertPath = copyFileToHomeDirectory("server-cert-vertx-eclipse-short-validity.pem");
    Path initialServerTrustPath = copyFileToHomeDirectory("server-trust-vertx-eclipse-short-validity.pem");

    Path newServerKeyPath = copyFileToHomeDirectory("server-key-vertx-eclipse-long-validity.pem");
    Path newServerCertPath = copyFileToHomeDirectory("server-cert-vertx-eclipse-long-validity.pem");
    Path newServerTrustPath = copyFileToHomeDirectory("server-trust-vertx-eclipse-long-validity.pem");

    KeyCertOptions keyCertOptions = new PemKeyCertOptions().addKeyPath(initialServerKeyPath.toString()).addCertPath(initialServerCertPath.toString());
    TrustOptions trustOptions = new PemTrustOptions().addCertPath(initialServerTrustPath.toString());

    assertReloadServerCertificates(keyCertOptions, trustOptions, () -> {
      Files.copy(newServerKeyPath, initialServerKeyPath, REPLACE_EXISTING);
      Files.copy(newServerCertPath, initialServerCertPath, REPLACE_EXISTING);
      Files.copy(newServerTrustPath, initialServerTrustPath, REPLACE_EXISTING);
      return null;
    });

    for (Path tempFile : Arrays.asList(initialServerKeyPath, initialServerCertPath, initialServerTrustPath, newServerKeyPath, newServerCertPath, newServerTrustPath)) {
      Files.delete(tempFile);
    }
  }

  private void assertReloadServerCertificates(KeyCertOptions keyCertOptions, TrustOptions trustOptions, Callable<Void> replaceCertificateOnTheFileSystem) throws Exception {
    server.close();
    HttpServerOptions options = new HttpServerOptions()
      .setPort(DEFAULT_HTTPS_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setKeyCertOptions(keyCertOptions)
      .setTrustOptions(trustOptions)
      .setSsl(true);
    server = vertx.createHttpServer(options);
    server.requestHandler(req -> {
      assertTrue(req.isSSL());
      req.response().end();
    });

    startServer();

    Map<String, List<X509Certificate>> urlsToCertificates = CertificateUtils.getCertificate(SERVER_URL);
    List<X509Certificate> certificateCollector = urlsToCertificates.get(SERVER_URL);

    assertEquals(1, certificateCollector.size());
    assertEquals("CN=Vertx-server,OU=Eclipse,O=Eclipse,C=NL", certificateCollector.get(0).getSubjectX500Principal().getName());
    assertEquals("2024-10-30T01:32:48Z", certificateCollector.get(0).getNotAfter().toInstant().toString());

    replaceCertificateOnTheFileSystem.call();
    server.reloadSsl();

    urlsToCertificates = CertificateUtils.getCertificate(SERVER_URL);
    certificateCollector = urlsToCertificates.get(SERVER_URL);

    assertEquals(1, certificateCollector.size());
    assertEquals("CN=Vertx-server,OU=Eclipse,O=Eclipse,C=NL", certificateCollector.get(0).getSubjectX500Principal().getName());
    assertEquals("2032-10-27T20:37:43Z", certificateCollector.get(0).getNotAfter().toInstant().toString());
  }

  private static Path copyFileToHomeDirectory(String fileName) throws IOException {
    try (InputStream inputStream = getResourceAsStream("tls/" + fileName)) {
      Path destination = Paths.get(HOME_DIRECTORY, fileName);
      Files.copy(Objects.requireNonNull(inputStream), destination, REPLACE_EXISTING);
      return destination;
    }
  }

  private static InputStream getResourceAsStream(String path) {
    return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
  }

}
