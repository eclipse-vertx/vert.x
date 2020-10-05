package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * The store can either be loaded by Vert.x:
 * <p>
 * HttpServerOptions options = new HttpServerOptions().setSsl(true);
 * options.setPkcs11KeyOptions(new Pkcs11Options().setPassword("vertx"));
 * <p>
 * Password is optional.
 * <p>
 * Configuration:
 * <p>
 * Add SunPKCS11 provider. Add the provider to the Java Security properties file ($JAVA_HOME/lib/security/java.security)
 * or create a new Java Security properties file (ex: java.security.pkcs11).
 * <p>
 * Add this line:
 * security.provider.6=sun.security.pkcs11.SunPKCS11 /opt/bar/cfg/pkcs11.cfg
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

  private String password;

  /**
   * Default constructor
   */
  public Pkcs11Options() {
    super();
  }

  /**
   * Copy constructor
   *
   * @param other the options to copy
   */
  public Pkcs11Options(Pkcs11Options other) {
    super();
    this.password = other.getPassword();
  }

  /**
   * Create options from JSON
   *
   * @param json the JSON
   */
  public Pkcs11Options(JsonObject json) {
    super();
    Pkcs11OptionsConverter.fromJson(json, this);
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    Pkcs11OptionsConverter.toJson(this, json);
    return json;
  }

  /**
   * @return the password for the key store
   */
  public String getPassword() {
    return password;
  }

  /**
   * Set the password for the key store
   *
   * @param password the password
   * @return a reference to this, so the API can be used fluently
   */
  public Pkcs11Options setPassword(String password) {
    this.password = password;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Pkcs11Options)) {
      return false;
    }

    Pkcs11Options that = (Pkcs11Options) o;

    return password != null ? password.equals(that.password) : that.password == null;
  }

  @Override
  public int hashCode() {
    int result = 1;
    result += 31 * result + (password != null ? password.hashCode() : 0);

    return result;
  }

  @Override
  public Pkcs11Options clone() {
    return new Pkcs11Options();
  }
}
