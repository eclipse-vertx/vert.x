package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * Key or trust store options delegating configuration of private key and/or certificates to previously setup
 * <a href="https://docs.oracle.com/javase/8/docs/api/java/security/Provider.html">Java Security Providers</a>.
 * <p>
 * This can be used for key and trust stores. The managerAlgorithm property references the algorithm name of
 * the chosen Provider service, whose KeyManagerFactory or TrustManagerFactory implementation is then used.<br/>
 * Additionally, a KeyStore implementation can be used in both cases by setting the keyStoreAlgorithm property
 * accordingly.
 * <p>
 * This may be useful in cases where Vert.x should use existing custom code based on the java.security API, e.g.
 * for use cases involving dynamic key updates.
 * <p>
 *
 *
 * @author <a href="mailto:mario@ellebrecht.com">Mario Ellebrecht</a>
 */
@DataObject(generateConverter = true)
public class SecurityProviderOptions implements KeyCertOptions, TrustOptions, Cloneable {

  private String keyStoreAlgorithm;
  private String managerAlgorithm;

  /**
   * Default constructor
   */
  public SecurityProviderOptions() {
    super();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public SecurityProviderOptions(SecurityProviderOptions other) {
    super();
    this.keyStoreAlgorithm = other.keyStoreAlgorithm;
    this.managerAlgorithm = other.managerAlgorithm;
  }

  /**
   * Create options from JSON
   *
   * @param json  the JSON
   */
  public SecurityProviderOptions(JsonObject json) {
    super();
    SecurityProviderOptionsConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    SecurityProviderOptionsConverter.toJson(this, json);
    return json;
  }

  public String getKeyStoreAlgorithm() {
    return keyStoreAlgorithm;
  }

  public SecurityProviderOptions setKeyStoreAlgorithm(String keyStoreAlgorithm) {
    this.keyStoreAlgorithm = keyStoreAlgorithm;
    return this;
  }

  public String getManagerAlgorithm() {
    return managerAlgorithm;
  }

  public SecurityProviderOptions setManagerAlgorithm(String managerAlgorithm) {
    this.managerAlgorithm = managerAlgorithm;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SecurityProviderOptions that = (SecurityProviderOptions) o;

    if (keyStoreAlgorithm != null ? !keyStoreAlgorithm.equals(that.keyStoreAlgorithm) : that.keyStoreAlgorithm != null)
      return false;
    return managerAlgorithm != null ? managerAlgorithm.equals(that.managerAlgorithm) : that.managerAlgorithm == null;

  }

  @Override
  public int hashCode() {
    int result = keyStoreAlgorithm != null ? keyStoreAlgorithm.hashCode() : 0;
    result = 31 * result + (managerAlgorithm != null ? managerAlgorithm.hashCode() : 0);
    return result;
  }

  @Override
  public SecurityProviderOptions clone() {
    return new SecurityProviderOptions(this);
  }

}
