/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.core.net;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.gen.Options;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class ClientOptions extends TCPOptions {

  private static final int DEFAULT_CONNECTTIMEOUT = 60000;

  // Client specific TCP stuff

  private int connectTimeout;

  // Client specific SSL stuff

  private boolean trustAll;
  private ArrayList<String> crlPaths;
  private ArrayList<Buffer> crlValues;

  public ClientOptions() {
    super();
    this.connectTimeout = DEFAULT_CONNECTTIMEOUT;
    this.crlPaths = new ArrayList<>();
    this.crlValues = new ArrayList<>();
  }

  public ClientOptions(ClientOptions other) {
    super(other);
    this.connectTimeout = other.connectTimeout;
    this.trustAll = other.trustAll;
    this.crlPaths = new ArrayList<>(other.crlPaths);
    this.crlValues = new ArrayList<>(other.crlValues);
  }

  public ClientOptions(JsonObject json) {
    super(json);
    this.connectTimeout = json.getInteger("connectTimeout", DEFAULT_CONNECTTIMEOUT);
    this.trustAll = json.getBoolean("trustAll", false);
    JsonArray arr = json.getArray("crlPaths");
    this.crlPaths = arr == null ? new ArrayList<>() : new ArrayList<String>(arr.toList());
  }

  public boolean isTrustAll() {
    return trustAll;
  }

  public ClientOptions setTrustAll(boolean trustAll) {
    this.trustAll = trustAll;
    return this;
  }

  public List<String> getCrlPaths() {
    return crlPaths;
  }

  public ClientOptions addCrlPath(String crlPath) throws NullPointerException {
    if (crlPath == null) {
      throw new NullPointerException("No null crl accepted");
    }
    crlPaths.add(crlPath);
    return this;
  }

  public List<Buffer> getCrlValues() {
    return crlValues;
  }

  public ClientOptions addCrlValue(Buffer crlValue) throws NullPointerException {
    if (crlValue == null) {
      throw new NullPointerException("No null crl accepted");
    }
    crlValues.add(crlValue);
    return this;
  }

  public int getConnectTimeout() {
    return connectTimeout;
  }

  public ClientOptions setConnectTimeout(int connectTimeout) {
    if (connectTimeout < 0) {
      throw new IllegalArgumentException("connectTimeout must be >= 0");
    }
    this.connectTimeout = connectTimeout;
    return this;
  }
}
