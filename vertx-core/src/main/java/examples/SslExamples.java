package examples;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.KeyStoreOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.ServerSSLOptions;

import java.util.Arrays;

public class SslExamples {

  public void example17(Vertx vertx) {
    ServerSSLOptions options = new ServerSSLOptions()
      .setKeyCertOptions(
      new JksOptions().
        setPath("/path/to/your/server-keystore.jks").
        setPassword("password-of-your-keystore")
    );
  }

  public void example18(Vertx vertx) {
    Buffer myKeyStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/server-keystore.jks");
    ServerSSLOptions options = new ServerSSLOptions()
      .setKeyCertOptions(
        new JksOptions().
          setValue(myKeyStoreAsABuffer).
          setPassword("password-of-your-keystore")
      );
  }

  public void example19(Vertx vertx) {
    ServerSSLOptions options = new ServerSSLOptions()
      .setKeyCertOptions(
        new PfxOptions().
          setPath("/path/to/your/server-keystore.pfx").
          setPassword("password-of-your-keystore")
      );
  }

  public void example20(Vertx vertx) {
    Buffer myKeyStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/server-keystore.pfx");
    ServerSSLOptions options = new ServerSSLOptions()
      .setKeyCertOptions(
        new PfxOptions().
          setValue(myKeyStoreAsABuffer).
          setPassword("password-of-your-keystore")
      );
  }

  public void example21(Vertx vertx) {
    ServerSSLOptions options = new ServerSSLOptions()
      .setKeyCertOptions(
        new PemKeyCertOptions().
          setKeyPath("/path/to/your/server-key.pem").
          setCertPath("/path/to/your/server-cert.pem")
      );
  }

  public void example22(Vertx vertx) {
    Buffer myKeyAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/server-key.pem");
    Buffer myCertAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/server-cert.pem");
    ServerSSLOptions options = new ServerSSLOptions()
      .setKeyCertOptions(
        new PemKeyCertOptions().
          setKeyValue(myKeyAsABuffer).
          setCertValue(myCertAsABuffer)
      );
  }

  public void exampleBKS(Vertx vertx) {
    ServerSSLOptions options = new ServerSSLOptions()
      .setKeyCertOptions(
        new KeyStoreOptions().
          setType("BKS").
          setPath("/path/to/your/server-keystore.bks").
          setPassword("password-of-your-keystore")
      );
  }

  public void example23(Vertx vertx) {
    ServerSSLOptions options = new ServerSSLOptions().
      setClientAuth(ClientAuth.REQUIRED).
      setTrustOptions(
        new JksOptions().
          setPath("/path/to/your/truststore.jks").
          setPassword("password-of-your-truststore")
      );
  }

  public void example24(Vertx vertx) {
    Buffer myTrustStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/truststore.jks");
    ServerSSLOptions options = new ServerSSLOptions().
      setClientAuth(ClientAuth.REQUIRED).
      setTrustOptions(
        new JksOptions().
          setValue(myTrustStoreAsABuffer).
          setPassword("password-of-your-truststore")
      );
  }

  public void example25(Vertx vertx) {
    ServerSSLOptions options = new ServerSSLOptions().
      setClientAuth(ClientAuth.REQUIRED).
      setTrustOptions(
        new PfxOptions().
          setPath("/path/to/your/truststore.pfx").
          setPassword("password-of-your-truststore")
      );
  }

  public void example26(Vertx vertx) {
    Buffer myTrustStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/truststore.pfx");
    ServerSSLOptions options = new ServerSSLOptions().
      setClientAuth(ClientAuth.REQUIRED).
      setTrustOptions(
        new PfxOptions().
          setValue(myTrustStoreAsABuffer).
          setPassword("password-of-your-truststore")
      );
  }

  public void example27(Vertx vertx) {
    ServerSSLOptions options = new ServerSSLOptions().
      setClientAuth(ClientAuth.REQUIRED).
      setTrustOptions(
        new PemTrustOptions().
          addCertPath("/path/to/your/server-ca.pem")
      );
  }

  public void example28(Vertx vertx) {
    Buffer myCaAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/server-ca.pfx");
    ServerSSLOptions options = new ServerSSLOptions().
      setClientAuth(ClientAuth.REQUIRED).
      setTrustOptions(
        new PemTrustOptions().
          addCertValue(myCaAsABuffer)
      );
  }

  public void example29(Vertx vertx) {
    ClientSSLOptions options = new ClientSSLOptions()
      .setTrustAll(true);
  }

  public void example30(Vertx vertx) {
    ClientSSLOptions options = new ClientSSLOptions()
      .setTrustOptions(new JksOptions().
        setPath("/path/to/your/truststore.jks").
        setPassword("password-of-your-truststore")
      );
  }

  public void example31(Vertx vertx) {
    Buffer myTrustStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/truststore.jks");
    ClientSSLOptions options = new ClientSSLOptions()
      .setTrustOptions(new JksOptions().
        setValue(myTrustStoreAsABuffer).
        setPassword("password-of-your-truststore")
      );
  }

  public void example32(Vertx vertx) {
    ClientSSLOptions options = new ClientSSLOptions()
      .setTrustOptions(new PfxOptions().
        setPath("/path/to/your/truststore.pfx").
        setPassword("password-of-your-truststore")
      );
  }

  public void example33(Vertx vertx) {
    Buffer myTrustStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/truststore.pfx");
    ClientSSLOptions options = new ClientSSLOptions()
      .setTrustOptions(new PfxOptions().
        setValue(myTrustStoreAsABuffer).
        setPassword("password-of-your-truststore")
      );
  }

  public void example34(Vertx vertx) {
    ClientSSLOptions options = new ClientSSLOptions()
      .setTrustOptions(new PemTrustOptions().
        addCertPath("/path/to/your/ca-cert.pem")
      );
  }

  public void example35(Vertx vertx) {
    Buffer myTrustStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/ca-cert.pem");
    ClientSSLOptions options = new ClientSSLOptions()
      .setTrustOptions(new PemTrustOptions().
        addCertValue(myTrustStoreAsABuffer)
      );
  }

  public void example36(Vertx vertx) {
    ClientSSLOptions options = new ClientSSLOptions()
      .setKeyCertOptions(new JksOptions().
        setPath("/path/to/your/client-keystore.jks").
        setPassword("password-of-your-keystore")
      );
  }

  public void example37(Vertx vertx) {
    Buffer myKeyStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/client-keystore.jks");
    ClientSSLOptions options = new ClientSSLOptions()
      .setKeyCertOptions(new JksOptions().
        setValue(myKeyStoreAsABuffer).
        setPassword("password-of-your-keystore")
      );
  }

  public void example38(Vertx vertx) {
    ClientSSLOptions options = new ClientSSLOptions()
      .setKeyCertOptions(new PfxOptions().
        setPath("/path/to/your/client-keystore.pfx").
        setPassword("password-of-your-keystore")
      );
  }

  public void example39(Vertx vertx) {
    Buffer myKeyStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/client-keystore.pfx");
    ClientSSLOptions options = new ClientSSLOptions()
      .setKeyCertOptions(new PfxOptions().
        setValue(myKeyStoreAsABuffer).
        setPassword("password-of-your-keystore")
      );
  }

  public void example40(Vertx vertx) {
    ClientSSLOptions options = new ClientSSLOptions()
      .setKeyCertOptions(new PemKeyCertOptions().
        setKeyPath("/path/to/your/client-key.pem").
        setCertPath("/path/to/your/client-cert.pem")
      );
  }

  public void example41(Vertx vertx) {
    Buffer myKeyAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/client-key.pem");
    Buffer myCertAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/client-cert.pem");
    ClientSSLOptions options = new ClientSSLOptions()
      .setKeyCertOptions(new PemKeyCertOptions().
        setKeyValue(myKeyAsABuffer).
        setCertValue(myCertAsABuffer)
      );
  }

  public void example42(Vertx vertx, JksOptions trustOptions) {
    ClientSSLOptions options = new ClientSSLOptions()
      .setTrustOptions(trustOptions)
      .addCrlPath("/path/to/your/crl.pem");
  }

  public void example43(Vertx vertx, JksOptions trustOptions) {
    Buffer myCrlAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/crl.pem");
    ClientSSLOptions options = new ClientSSLOptions()
      .setTrustOptions(trustOptions)
      .addCrlValue(myCrlAsABuffer);
  }

  public void example44(Vertx vertx, JksOptions keyStoreOptions) {
    ServerSSLOptions options = new ServerSSLOptions().
      setKeyCertOptions(keyStoreOptions).
      addEnabledCipherSuite("ECDHE-RSA-AES128-GCM-SHA256").
      addEnabledCipherSuite("ECDHE-ECDSA-AES128-GCM-SHA256").
      addEnabledCipherSuite("ECDHE-RSA-AES256-GCM-SHA384").
      addEnabledCipherSuite("CDHE-ECDSA-AES256-GCM-SHA384");
  }

  public void addEnabledTLSPrococol(Vertx vertx, JksOptions keyStoreOptions) {
    ServerSSLOptions options = new ServerSSLOptions().
      setKeyCertOptions(keyStoreOptions).
      addEnabledSecureTransportProtocol("TLSv1.1");
  }

  public void removeEnabledTLSPrococol(Vertx vertx, JksOptions keyStoreOptions) {
    ServerSSLOptions options = new ServerSSLOptions().
      setKeyCertOptions(keyStoreOptions).
      removeEnabledSecureTransportProtocol("TLSv1.2");
  }

  public void configureSNIServer(Vertx vertx) {
    ServerSSLOptions options = new ServerSSLOptions()
      .setKeyCertOptions(new JksOptions()
        .setPath("keystore.jks")
        .setPassword("wibble"))
      .setSni(true);
  }

  public void configureSNIServerWithPems(Vertx vertx) {
    ServerSSLOptions options = new ServerSSLOptions()
      .setKeyCertOptions(new PemKeyCertOptions()
        .setKeyPaths(Arrays.asList("default-key.pem", "host1-key.pem", "etc..."))
        .setCertPaths(Arrays.asList("default-cert.pem", "host2-key.pem", "etc...")
        ))
      .setSni(true);
  }
}
