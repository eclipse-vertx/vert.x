package io.vertx.test.core.tls;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.TrustOptions;

import java.util.function.Supplier;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface Trust<T extends TrustOptions> extends Supplier<T> {

  Trust<TrustOptions> NONE = () -> null;
  Trust<JksOptions> SERVER_JKS = () -> new JksOptions().setPath("tls/client-truststore.jks").setPassword("wibble");
  Trust<JksOptions> CLIENT_JKS = () -> new JksOptions().setPath("tls/server-truststore.jks").setPassword("wibble");
  Trust<PfxOptions> SERVER_PKCS12 = () -> new PfxOptions().setPath("tls/client-truststore.p12").setPassword("wibble");
  Trust<PfxOptions> CLIENT_PKCS12 = () -> new PfxOptions().setPath("tls/server-truststore.p12").setPassword("wibble");
  Trust<PemTrustOptions> SERVER_PEM = () -> new PemTrustOptions().addCertPath("tls/server-cert.pem");
  Trust<PemTrustOptions> CLIENT_PEM = () -> new PemTrustOptions().addCertPath("tls/client-cert.pem");
  Trust<JksOptions> SERVER_JKS_ROOT_CA = () -> new JksOptions().setPath("tls/client-truststore-root-ca.jks").setPassword("wibble");
  Trust<JksOptions> SERVER_JKS_ROOT_CA_VALUES = () -> {
    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    Buffer rootCa = vertx.fileSystem()
        .readFileBlocking(vertx.resolveFile("tls/client-truststore-root-ca.jks")
            .getAbsolutePath());
    return new JksOptions().setValue(rootCa)
        .setPassword("wibble");
  };
  Trust<JksOptions> SERVER_JKS_OTHER_CA_HOST2_ROOT_CA = () -> new JksOptions().setPath("tls/truststore-other-ca.jks").setPassword("wibble").addPathForName("tls/client-truststore-root-ca.jks", "wibble","host2.com");
  Trust<JksOptions> SERVER_JKS_OTHER_CA_HOST2_ROOT_CA_VALUES = () -> {
    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    Buffer otherCa = vertx.fileSystem()
        .readFileBlocking(vertx.resolveFile("tls/truststore-other-ca.jks")
            .getAbsolutePath());
    Buffer rootCa = vertx.fileSystem()
        .readFileBlocking(vertx.resolveFile("tls/client-truststore-root-ca.jks")
            .getAbsolutePath());
    return new JksOptions().setValue(otherCa)
        .setPassword("wibble")
        .addValueForName(rootCa, "wibble", "host2.com");
  };
  Trust<JksOptions> SNI_HOST2_JKS_ROOT_CA = () -> new JksOptions().addPathForName("tls/client-truststore-root-ca.jks", "wibble","host2.com");
  Trust<JksOptions> SNI_HOST2_JKS_ROOT_CA_VALUES = () -> {
    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    Buffer rootCa = vertx.fileSystem()
        .readFileBlocking(vertx.resolveFile("tls/client-truststore-root-ca.jks")
            .getAbsolutePath());
    return new JksOptions().addValueForName(rootCa, "wibble", "host2.com");
  };
  Trust<PfxOptions> SERVER_PKCS12_ROOT_CA = () -> new PfxOptions().setPath("tls/client-truststore-root-ca.p12").setPassword("wibble");
  Trust<PemTrustOptions> SERVER_PEM_ROOT_CA = () -> new PemTrustOptions().addCertPath("tls/root-ca/ca-cert.pem");
  Trust<PemTrustOptions> CLIENT_PEM_ROOT_CA = () -> new PemTrustOptions().addCertPath("tls/root-ca/ca-cert.pem");
  Trust<PemTrustOptions> SERVER_PEM_ROOT_CA_AND_OTHER_CA = () -> new PemTrustOptions().addCertPath("tls/root-ca/ca-cert.pem").addCertPath("tls/other-ca/ca-cert.pem");
  Trust<PemTrustOptions> SNI_SERVER_PEM_ROOT_CA_AND_OTHER_CA = () -> new PemTrustOptions().addCertPathForName("tls/root-ca/ca-cert.pem", "host2.com").addCertPathForName("tls/other-ca/ca-cert.pem", "host3.com");
  Trust<PemTrustOptions> SNI_SERVER_PEM_ROOT_CA_AND_OTHER_CA_FALLBACK = () -> new PemTrustOptions().addCertPath("tls/root-ca/ca-cert.pem").addCertPathForName("tls/other-ca/ca-cert.pem", "host3.com");
  Trust<JksOptions> SNI_JKS_HOST1 = () -> new JksOptions().setPath("tls/sni-truststore-host1.jks").setPassword("wibble");
  Trust<JksOptions> SNI_JKS_HOST2 = () -> new JksOptions().setPath("tls/sni-truststore-host2.jks").setPassword("wibble");
  Trust<JksOptions> SNI_JKS_HOST3 = () -> new JksOptions().setPath("tls/sni-truststore-host3.jks").setPassword("wibble");
  Trust<JksOptions> SNI_JKS_HOST4 = () -> new JksOptions().setPath("tls/sni-truststore-host4.jks").setPassword("wibble");
  Trust<JksOptions> SNI_JKS_HOST5 = () -> new JksOptions().setPath("tls/sni-truststore-host5.jks").setPassword("wibble");

}
