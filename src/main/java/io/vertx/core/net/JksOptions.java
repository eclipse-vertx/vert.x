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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;

/**
 * Key or trust store options configuring private key and/or certificates based on Java Keystore files.
 * <p>
 * When used as a key store, it should point to a store containing a private key and its certificate.
 * When used as a trust store, it should point to a store containing a list of trusted certificates.
 * <p>
 * The store can either be loaded by Vert.x from the filesystem:
 * <p>
 * <pre>
 * HttpServerOptions options = HttpServerOptions.httpServerOptions();
 * options.setKeyStore(new JKSOptions().setPath("/mykeystore.jks").setPassword("foo"));
 * </pre>
 *
 * Or directly provided as a buffer:
 * <p>
 *
 * <pre>
 * Buffer store = vertx.fileSystem().readFileSync("/mykeystore.jks");
 * options.setKeyStore(new JKSOptions().setValue(store).setPassword("foo"));
 * </pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
public class JksOptions implements KeyCertOptions, TrustOptions, Cloneable {

  private String password;
  private String path;
  private Buffer value;
  private Map<String, ValueHolder> valueServerMap;
  private Map<String, PathHolder> pathServerMap;
  private Set<String> serverNames;

  /**
   * Default constructor
   */
  public JksOptions() {
    super();
    valueServerMap = new HashMap<>();
    pathServerMap = new HashMap<>();
    serverNames = new HashSet<>();
  }

  /**
   * Copy constructor
   *
   * @param other the options to copy
   */
  public JksOptions(JksOptions other) {
    super();
    this.password = other.getPassword();
    this.path = other.getPath();
    this.value = other.getValue();
    this.valueServerMap = other.getValueServerMap();
    this.pathServerMap = other.getPathServerMap();
    this.serverNames = new HashSet<>(other.getServerNames());
  }

  /**
   * Create options from JSON
   *
   * @param json  the JSON
   */
  public JksOptions(JsonObject json) {
    this();
    if (json.getValue("password") instanceof String) {
      this.setPassword(json.getString("password"));
    }
    if (json.getValue("path") instanceof String) {
      this.setPath(json.getString("path"));
    }
    if (json.getValue("value") instanceof String) {
      this.setValue(Buffer.buffer(Base64.getDecoder()
          .decode(json.getString("value"))));
    }
    if (json.getValue("pathServerMap") instanceof JsonObject) {
      json.getJsonObject("pathServerMap")
          .forEach(item -> {
            if (item.getValue() instanceof JsonObject) {
              JsonObject holder = (JsonObject) item.getValue();
              Object path = holder.getValue("path");
              if (path instanceof String) {
                Object pw = holder.getValue("password");
                String password = null;
                if (pw instanceof String) {
                  password = (String) pw;
                }
                this.pathServerMap.put(item.getKey(), new PathHolder((String) path, password));
              }
            }
          });
    }
    if (json.getValue("valueServerMap") instanceof JsonObject) {
      json.getJsonObject("valueServerMap")
          .forEach(item -> {
            if (item.getValue() instanceof JsonObject) {
              JsonObject holder = (JsonObject) item.getValue();
              Object value = holder.getValue("value");
              if (value instanceof String) {
                Object pw = holder.getValue("password");
                String password = null;
                if (pw instanceof String) {
                  password = (String) pw;
                }
                this.valueServerMap.put(item.getKey(), new ValueHolder(Buffer.buffer(Base64.getDecoder()
                    .decode((String) value)), password));
              }
            }
          });
    }
    if (json.getValue("serverNames") instanceof JsonArray){
      json.getJsonArray("serverNames").forEach(item -> {
        if (item instanceof String){
          this.serverNames.add((String) item);
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
    if (path != null){
      json.put("path", path);
    }
    if (value != null){
      json.put("value", value.getBytes());
    }
    if (password != null){
      json.put("password", password);
    }
    if (serverNames.size() != 0){
      JsonArray serverNamesJson = new JsonArray();
      serverNames.forEach(serverNamesJson::add);
      json.put("serverNames", serverNamesJson);
    }
    if (valueServerMap.size() != 0){
      JsonObject valueMap = new JsonObject();
      valueServerMap.forEach((serverName, holderObj) ->{
        JsonObject holder = new JsonObject();
        holder.put("value", holderObj.getValue());
        if (holderObj.getPassword() != null){
          holder.put("password", holderObj.getPassword());
        }
        valueMap.put(serverName, holder);
      });
    }
    if (pathServerMap.size() != 0){
      JsonObject pathMap = new JsonObject();
      pathServerMap.forEach((serverName, holderObj) ->{
        JsonObject holder = new JsonObject();
        holder.put("path", holderObj.getPath());
        if (holderObj.getPassword() != null){
          holder.put("password", holderObj.getPassword());
        }
        pathMap.put(serverName, holder);
      });
    }
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
   * @param password  the password
   * @return a reference to this, so the API can be used fluently
   */
  public JksOptions setPassword(String password) {
    this.password = password;
    return this;
  }

  /**
   * Get the path to the ksy store
   *
   * @return the path
   */
  public String getPath() {
    return path;
  }

  /**
   * Set the path to the key store
   *
   * @param path  the path
   * @return a reference to this, so the API can be used fluently
   */
  public JksOptions setPath(String path) {
    this.path = path;
    return this;
  }

  public JksOptions addPathForName(String path, String password, String serverName){
    pathServerMap.put(serverName, new PathHolder(path, password));
    serverNames.add(serverName);
    return this;
  }

  public String getPathForName(String serverName){
    if (pathServerMap.containsKey(serverName)){
      return pathServerMap.get(serverName).getPath();
    }
    return null;
  }

  public String getPathPasswordForName(String serverName){
    if (pathServerMap.containsKey(serverName)){
      return pathServerMap.get(serverName).getPassword();
    }
    return null;
  }

  /**
   * Get the key store as a buffer
   *
   * @return  the key store as a buffer
   */
  public Buffer getValue() {
    return value;
  }

  /**
   * Set the key store as a buffer
   *
   * @param value  the key store as a buffer
   * @return a reference to this, so the API can be used fluently
   */
  public JksOptions setValue(Buffer value) {
    this.value = value;
    return this;
  }

  public JksOptions addValueForName(Buffer value, String password, String serverName){
    valueServerMap.put(serverName, new ValueHolder(value, password));
    serverNames.add(serverName);
    return this;
  }

  public Buffer getValueForName(String serverName){
    if (valueServerMap.containsKey(serverName)){
      return valueServerMap.get(serverName).getValue();
    }
    return null;
  }

  public String getValuePasswordForName(String serverName){
    if (valueServerMap.containsKey(serverName)){
      return valueServerMap.get(serverName).getPassword();
    }
    return null;
  }

  public Map<String, ValueHolder> getValueServerMap() {
    return valueServerMap;
  }

  public Map<String, PathHolder> getPathServerMap() {
    return pathServerMap;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof JksOptions)) {
      return false;
    }

    JksOptions that = (JksOptions) o;

    if (password != null ? !password.equals(that.password) : that.password != null) {
      return false;
    }
    if (path != null ? !path.equals(that.path) : that.path != null) {
      return false;
    }
    if (value != null ? !value.equals(that.value) : that.value != null) {
      return false;
    }
    if (! Objects.equals(valueServerMap, that.valueServerMap)){
      return false;
    }
    if (! Objects.equals(pathServerMap, that.pathServerMap)){
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(password, path, value, valueServerMap, pathServerMap);
  }

  @Override
  public JksOptions clone() {
    return new JksOptions(this);
  }

  public List<String> getServerNames() {
    return new ArrayList<>(serverNames);
  }

  private class ValueHolder {
    private final String password;
    private final Buffer value;

    private ValueHolder(Buffer value, String password) {
      this.password = password;
      this.value = value;
    }

    public String getPassword() {
      return password;
    }

    public Buffer getValue() {
      return value;
    }
  }

  private class PathHolder {
    private final String password;
    private final String path;

    private PathHolder(String path, String password) {
      this.password = password;
      this.path = path;
    }

    public String getPassword() {
      return password;
    }

    public String getPath() {
      return path;
    }
  }


}
