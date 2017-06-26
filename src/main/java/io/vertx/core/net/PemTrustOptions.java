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

package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;

/**
 * Certificate Authority options configuring certificates based on
 * <i>Privacy-enhanced Electronic Email</i> (PEM) files. The options is configured with a list of
 * validating certificates.
 * <p>
 * Validating certificates must contain X.509 certificates wrapped in a PEM block:<p>
 *
 * <pre>
 * -----BEGIN CERTIFICATE-----
 * MIIDezCCAmOgAwIBAgIEVmLkwTANBgkqhkiG9w0BAQsFADBuMRAwDgYDVQQGEwdV
 * ...
 * z5+DuODBJUQst141Jmgq8bS543IU/5apcKQeGNxEyQ==
 * -----END CERTIFICATE-----
 * </pre>
 *
 * The certificates can either be loaded by Vert.x from the filesystem:
 * <p>
 * <pre>
 * HttpServerOptions options = new HttpServerOptions();
 * options.setPemTrustOptions(new PemTrustOptions().addCertPath("/cert.pem"));
 * </pre>
 *
 * Or directly provided as a buffer:
 * <p>
 *
 * <pre>
 * Buffer cert = vertx.fileSystem().readFileSync("/cert.pem");
 * HttpServerOptions options = new HttpServerOptions();
 * options.setPemTrustOptions(new PemTrustOptions().addCertValue(cert));
 * </pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
public class PemTrustOptions implements TrustOptions, Cloneable {

  private Map<String, List<String>> certPathsServerMap;
  private Map<String, List<Buffer>> certValuesServerMap;
  private ArrayList<String> certPaths;
  private ArrayList<Buffer> certValues;

  /**
   * Default constructor
   */
  public PemTrustOptions() {
    super();
    this.certPaths = new ArrayList<>();
    this.certValues = new ArrayList<>();
    this.certPathsServerMap = new HashMap<>();
    this.certValuesServerMap = new HashMap<>();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public PemTrustOptions(PemTrustOptions other) {
    super();
    this.certPaths = new ArrayList<>(other.getCertPaths());
    this.certValues = new ArrayList<>(other.getCertValues());
    this.certPathsServerMap = new HashMap<>(other.getCertPathsServerMap());
    this.certValuesServerMap = new HashMap<>(other.getCertValuesServerMap());
  }

  /**
   * Create options from JSON
   *
   * @param json the JSON
   */
  public PemTrustOptions(JsonObject json) {
    this();
    if (json.getJsonArray("certPaths") instanceof JsonArray) {
      json.getJsonArray("certPaths")
          .forEach(entry -> {
            if (entry instanceof String) {
              this.addCertPath((String) entry);
            }
          });
    }
    if (json.getValue("certValues") instanceof JsonArray) {
      json.getJsonArray("certValues")
          .forEach(item -> {
            if (item instanceof String)
              this.addCertValue(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)item)));
          });
    }
    JsonObject certPathMap = json.getJsonObject("certPathMap");
    if (certPathMap != null) {
      certPathMap.forEach(item -> {
        if (item.getValue() instanceof JsonArray) {
          ArrayList<String> paths = new ArrayList<>();
          ((JsonArray) item.getValue()).forEach(path -> {
            if (path instanceof String) {
              paths.add((String) path);
            }
          });
          this.certPathsServerMap.put(item.getKey(), paths);
        }
      });
    }
    JsonObject certValuesMap = json.getJsonObject("certValuesMap");
    if (certValuesMap != null) {
      certValuesMap.forEach(item -> {
        if (item.getValue() instanceof JsonArray) {
          ArrayList<Buffer> buffers = new ArrayList<>();
          ((JsonArray) item.getValue()).forEach(buffer -> {
            if (buffer instanceof String) {
              buffers.add(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)buffer)));
            }
          });
          this.certValuesServerMap.put(item.getKey(), buffers);
        }
      });
    }
  }

  /**
   * Convert to JSON
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (this.getCertPaths() != null) {
      JsonArray array = new JsonArray();
      this.getCertPaths()
          .forEach(array::add);
      json.put("certPaths", array);
    }
    if (this.getCertValues() != null) {
      JsonArray array = new JsonArray();
      this.getCertValues()
          .forEach(value -> array.add(value.getBytes()));
      json.put("certValues", array);
    }
    if (this.certPathsServerMap.size() != 0) {
      JsonObject pathMap = new JsonObject();
      json.put("certPathMap", pathMap);
      certPathsServerMap.forEach((key, value) -> pathMap.put(key, new JsonArray(value)));
    }
    if (this.certValuesServerMap.size() != 0) {
      JsonObject valuesMap = new JsonObject();
      json.put("certValuesMap", valuesMap);
      certValuesServerMap.forEach((key, values) -> {
        JsonArray valuesJson = new JsonArray();
        values.forEach(v -> valuesJson.add(v.getBytes()));
        valuesMap.put(key, valuesJson);
      });
    }
    return json;
  }

  /**
   * @return  the certificate paths used to locate certificates
   */
  public List<String> getCertPaths() {
    return certPaths;
  }

  /**
   * Add a certificate path
   *
   * @param certPath  the path to add
   * @return a reference to this, so the API can be used fluently
   * @throws NullPointerException
   */
  public PemTrustOptions addCertPath(String certPath) throws NullPointerException {
    addCertPath4Name(certPath, null);
    return this;
  }

  public Map<String, List<String>> getCertPathsServerMap(){
    return certPathsServerMap;
  }

  public List<String> getCertPath4Name(String serverName){
    return certPathsServerMap.getOrDefault(serverName, new ArrayList<>());
  }

  /**
   * Add a certificate path
   *
   * @param certPath  the path to add
   * @return a reference to this, so the API can be used fluently
   * @throws NullPointerException
   */
  public PemTrustOptions addCertPath4Name(String certPath, String serverName){
    Objects.requireNonNull(certPath, "No null certificate accepted");
    Arguments.require(!certPath.isEmpty(), "No empty certificate path accepted");
    if (serverName == null) {
      certPaths.add(certPath);
    } else {
      if (certPathsServerMap.containsKey(serverName)){
        certPathsServerMap.get(serverName).add(certPath);
      } else {
        ArrayList<String> paths = new ArrayList<>();
        paths.add(certPath);
        certPathsServerMap.put(serverName, paths);
      }
    }
    return this;
  }

  public List<String> getServerNames(){
    ArrayList<String> list = new ArrayList<>(certPathsServerMap.keySet());
    list.addAll(new ArrayList<>(certValuesServerMap.keySet()));
    return list;
  }

  public Map<String, List<Buffer>> getCertValuesServerMap(){
    return certValuesServerMap;
  }

  public List<Buffer> getCertValues4Name(String serverName){
    return certValuesServerMap.getOrDefault(serverName, new ArrayList<>());
  }

  public PemTrustOptions addCertValue4Name(Buffer certValue, String serverName){
    Objects.requireNonNull(certValue, "No null certificate accepted");
    if (serverName == null){
      certValues.add(certValue);
    } else {
      if (certValuesServerMap.containsKey(serverName)){
        certValuesServerMap.get(serverName).add(certValue);
      } else {
        ArrayList<Buffer> values = new ArrayList<>();
        values.add(certValue);
        certValuesServerMap.put(serverName, values);
      }
    }
    return  this;
  }

  /**
   *
   * @return the certificate values
   */
  public List<Buffer> getCertValues() {
    return certValues;
  }

  /**
   * Add a certificate value
   *
   * @param certValue  the value to add
   * @return a reference to this, so the API can be used fluently
   * @throws NullPointerException
   */
  public PemTrustOptions addCertValue(Buffer certValue) throws NullPointerException {
    Objects.requireNonNull(certValue, "No null certificate accepted");
    certValues.add(certValue);
    return this;
  }

  @Override
  public PemTrustOptions clone() {
    return new PemTrustOptions(this);
  }

}
