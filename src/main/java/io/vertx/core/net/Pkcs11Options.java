package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;

/**
 * The store can either be loaded by Vert.x:
 * <p>
 * HttpServerOptions options = new HttpServerOptions().setSsl(true);
 * options.setPkcs11KeyOptions(new Pkcs11Options());
 * <p>
 * Configuration:
 * <p>
 * Add SunPKCS11 provider. Add the provider to the Java Security properties file ($JAVA_HOME/lib/security/java.security)
 * or create a new Java Security properties file (ex: java.security.pkcs11).
 * <p>
 * Add this line:
 * security.provider.7=sun.security.pkcs11.SunPKCS11 /opt/bar/cfg/pkcs11.cfg
 * <p>
 * pkcs11.cfg example:
 * <p>
 * library = /opt/foo/lib/libpkcs11.so
 * name = vertxPKCS11
 * slot = 1
 * <p>
 * if you create your own Java Security properties file (ex: java.security.pkcs11), you must add this JVM parameter:
 * -Djava.security.properties=/opt/bar/cfg/java.security.pkcs11
 */
@DataObject(generateConverter = true)
public class Pkcs11Options implements KeyCertOptions, TrustOptions, Cloneable {

  /**
   * Default constructor
   */
  public Pkcs11Options() {
    super();
  }

  @Override
  public Pkcs11Options clone() {
    return new Pkcs11Options();
  }
}
