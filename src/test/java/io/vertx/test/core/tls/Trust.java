package io.vertx.test.core.tls;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.TrustOptions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface Trust<T extends TrustOptions> extends Supplier<T> {
  String DUMMY_PASSWORD = "dummy";

  Trust<TrustOptions> NONE = () -> null;
  Trust<JksOptions> SERVER_JKS = () -> new JksOptions().setPath("tls/client-truststore.jks").setPassword("wibble");
  Trust<JksOptions> CLIENT_JKS = () -> new JksOptions().setPath("tls/server-truststore.jks").setPassword("wibble");
  Trust<PfxOptions> SERVER_PKCS12 = () -> new PfxOptions().setPath("tls/client-truststore.p12").setPassword("wibble");
  Trust<PfxOptions> CLIENT_PKCS12 = () -> new PfxOptions().setPath("tls/server-truststore.p12").setPassword("wibble");
  Trust<PemTrustOptions> SERVER_PEM = () -> new PemTrustOptions().addCertPath("tls/server-cert.pem");
  Trust<PemTrustOptions> CLIENT_PEM = () -> new PemTrustOptions().addCertPath("tls/client-cert.pem");
  Trust<JksOptions> SERVER_JKS_ROOT_CA = () -> new JksOptions().setPath("tls/client-truststore-root-ca.jks").setPassword("wibble");
  Trust<PfxOptions> SERVER_PKCS12_ROOT_CA = () -> new PfxOptions().setPath("tls/client-truststore-root-ca.p12").setPassword("wibble");
  Trust<PemTrustOptions> SERVER_PEM_ROOT_CA = () -> new PemTrustOptions().addCertPath("tls/root-ca/ca-cert.pem");
  Trust<PemTrustOptions> CLIENT_PEM_ROOT_CA = () -> new PemTrustOptions().addCertPath("tls/root-ca/ca-cert.pem");
  Trust<PemTrustOptions> SERVER_PEM_ROOT_CA_AND_OTHER_CA = () -> new PemTrustOptions().addCertPath("tls/root-ca/ca-cert.pem").addCertPath("tls/other-ca/ca-cert.pem");
  Trust<JksOptions> SNI_JKS_HOST1 = () -> new JksOptions().setPath("tls/sni-truststore-host1.jks").setPassword("wibble");
  Trust<JksOptions> SNI_JKS_HOST2 = () -> new JksOptions().setPath("tls/sni-truststore-host2.jks").setPassword("wibble");
  Trust<JksOptions> SNI_JKS_HOST3 = () -> new JksOptions().setPath("tls/sni-truststore-host3.jks").setPassword("wibble");
  Trust<JksOptions> SNI_JKS_HOST4 = () -> new JksOptions().setPath("tls/sni-truststore-host4.jks").setPassword("wibble");
  Trust<JksOptions> SNI_JKS_HOST5 = () -> new JksOptions().setPath("tls/sni-truststore-host5.jks").setPassword("wibble");
  Trust<TrustOptions> SNI_SERVER_ROOT_CA_AND_OTHER_CA_1 = () -> {
    HashMap<String, String> certMap = new HashMap<>();
    certMap.put("host2.com", "src/test/resources/tls/root-ca/ca-cert.pem");
    certMap.put("host3.com", "src/test/resources/tls/other-ca/ca-cert.pem");
    return new JksOptions().setValue(loadCAs(certMap, DUMMY_PASSWORD)).setPassword(DUMMY_PASSWORD);
  };
  Trust<TrustOptions> SNI_SERVER_ROOT_CA_AND_OTHER_CA_2 = () -> {
    HashMap<String, String> certMap = new HashMap<>();
    certMap.put("host3.com", "src/test/resources/tls/root-ca/ca-cert.pem");
    certMap.put("host2.com", "src/test/resources/tls/other-ca/ca-cert.pem");
    return new JksOptions().setValue(loadCAs(certMap, DUMMY_PASSWORD)).setPassword(DUMMY_PASSWORD);
  };
  Trust<TrustOptions> SNI_SERVER_ROOT_CA_FALLBACK = () -> {
    HashMap<String, String> certMap = new HashMap<>();
    certMap.put("xxx", "src/test/resources/tls/root-ca/ca-cert.pem");
    return new JksOptions().setValue(loadCAs(certMap, DUMMY_PASSWORD)).setPassword(DUMMY_PASSWORD);
  };
  Trust<TrustOptions> SNI_SERVER_OTHER_CA_FALLBACK = () -> {
    HashMap<String, String> certMap = new HashMap<>();
    certMap.put("xxxx", "src/test/resources/tls/other-ca/ca-cert.pem");
    return new JksOptions().setValue(loadCAs(certMap, DUMMY_PASSWORD)).setPassword(DUMMY_PASSWORD);
  };

  static Buffer loadCAs(Map<String, String> certMap, String password) {
    KeyStore keyStore = null;
    try {
      keyStore = KeyStore.getInstance("jks");
      keyStore.load(null, null);
      int count = 0;
      for (Map.Entry<String, String> entry : certMap.entrySet()){
        Buffer certBuf = Buffer.buffer(Files.readAllBytes(Paths.get(entry.getValue()).toAbsolutePath()));
        keyStore.setCertificateEntry(entry.getKey(), loadCerts(certBuf)[0]);
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      keyStore.store(out, password.toCharArray());
      return Buffer.buffer(out.toByteArray());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static List<byte[]> loadPems(Buffer data, String delimiter) throws IOException {
    String pem = data.toString();
    String beginDelimiter = "-----BEGIN " + delimiter + "-----";
    String endDelimiter = "-----END " + delimiter + "-----";
    List<byte[]> pems = new ArrayList<>();
    int index = 0;
    while (true) {
      index = pem.indexOf(beginDelimiter, index);
      if (index == -1) {
        break;
      }
      index += beginDelimiter.length();
      int end = pem.indexOf(endDelimiter, index);
      if (end == -1) {
        throw new RuntimeException("Missing " + endDelimiter + " delimiter");
      }
      String content = pem.substring(index, end);
      content = content.replaceAll("\\s", "");
      if (content.length() == 0) {
        throw new RuntimeException("Empty pem file");
      }
      index = end + 1;
      pems.add(Base64.getDecoder().decode(content));
    }
    if (pems.isEmpty()) {
      throw new RuntimeException("Missing " + beginDelimiter + " delimiter");
    }
    return pems;
  }

  static X509Certificate[] loadCerts(Buffer buffer) throws Exception {
    if (buffer == null) {
      throw new RuntimeException("Missing X.509 certificate path");
    }
    List<byte[]> pems = loadPems(buffer, "CERTIFICATE");
    CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
    List<X509Certificate> certs = new ArrayList<>(pems.size());
    for (byte[] pem : pems) {
      for (Certificate cert : certFactory.generateCertificates(new ByteArrayInputStream(pem))) {
        certs.add((X509Certificate) cert);
      }
    }
    return certs.toArray(new X509Certificate[certs.size()]);
  }

}
