package io.vertx.test.core.tls;

import io.vertx.core.net.JksOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PfxOptions;

import java.util.function.Supplier;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface Cert<K extends KeyCertOptions> extends Supplier<K> {

  Cert<KeyCertOptions> NONE = () -> null;
  Cert<JksOptions> SERVER_JKS = () -> new JksOptions().setPath("tls/server-keystore.jks").setPassword("wibble");
  Cert<JksOptions> CLIENT_JKS = () -> new JksOptions().setPath("tls/client-keystore.jks").setPassword("wibble");
  Cert<PfxOptions> SERVER_PKCS12 = () -> new PfxOptions().setPath("tls/server-keystore.p12").setPassword("wibble");
  Cert<PfxOptions> CLIENT_PKCS12 = () -> new PfxOptions().setPath("tls/client-keystore.p12").setPassword("wibble");
  Cert<PemKeyCertOptions> SERVER_PEM = () -> new PemKeyCertOptions().setKeyPath("tls/server-key.pem").setCertPath("tls/server-cert.pem");
  Cert<PemKeyCertOptions> SERVER_PEM_RSA = () -> new PemKeyCertOptions().setKeyPath("tls/server-key-pkcs1.pem").setCertPath("tls/server-cert.pem");
  Cert<PemKeyCertOptions> CLIENT_PEM = () -> new PemKeyCertOptions().setKeyPath("tls/client-key.pem").setCertPath("tls/client-cert.pem");
  Cert<JksOptions> SERVER_JKS_ROOT_CA = () -> new JksOptions().setPath("tls/server-keystore-root-ca.jks").setPassword("wibble");
  Cert<PfxOptions> SERVER_PKCS12_ROOT_CA = () -> new PfxOptions().setPath("tls/server-keystore-root-ca.p12").setPassword("wibble");
  Cert<PemKeyCertOptions> SERVER_PEM_ROOT_CA = () -> new PemKeyCertOptions().setKeyPath("tls/server-key.pem").setCertPath("tls/server-cert-root-ca.pem");
  Cert<PemKeyCertOptions> CLIENT_PEM_ROOT_CA = () -> new PemKeyCertOptions().setKeyPath("tls/client-key.pem").setCertPath("tls/client-cert-root-ca.pem");
  Cert<PemKeyCertOptions> SERVER_PEM_INT_CA = () -> new PemKeyCertOptions().setKeyPath("tls/server-key.pem").setCertPath("tls/server-cert-int-ca.pem");
  Cert<PemKeyCertOptions> SERVER_PEM_CA_CHAIN = () -> new PemKeyCertOptions().setKeyPath("tls/server-key.pem").setCertPath("tls/server-cert-ca-chain.pem");
  Cert<PemKeyCertOptions> SERVER_PEM_OTHER_CA = () -> new PemKeyCertOptions().setKeyPath("tls/server-key.pem").setCertPath("tls/server-cert-other-ca.pem");
  Cert<JksOptions> SERVER_MIM = () -> new JksOptions().setPath("tls/mim-server-keystore.jks").setPassword("wibble");
  Cert<JksOptions> SNI_JKS = () -> new JksOptions().setPath("tls/sni-keystore.jks").setPassword("wibble");
  Cert<PfxOptions> SNI_PKCS12 = () -> new PfxOptions().setPath("tls/sni-keystore.p12").setPassword("wibble");
  Cert<PemKeyCertOptions> SNI_PEM = () -> new PemKeyCertOptions()
      .addKeyPath("tls/server-key.pem").addCertPath("tls/server-cert.pem")
      .addKeyPath("tls/host1-key.pem").addCertPath("tls/host1-cert.pem")
      .addKeyPath("tls/host2-key.pem").addCertPath("tls/host2-cert.pem")
      .addKeyPath("tls/host3-key.pem").addCertPath("tls/host3-cert.pem")
      .addKeyPath("tls/host4-key.pem").addCertPath("tls/host4-cert.pem")
      .addKeyPath("tls/host5-key.pem").addCertPath("tls/host5-cert.pem");

}
