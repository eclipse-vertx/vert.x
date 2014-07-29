/*
 * Copyright 2014 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
 */

package io.vertx.core.net.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.KeyCertOptions;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class KeyCertOptionsImpl implements KeyCertOptions {

  private String keyPath;
  private Buffer keyValue;
  private String certPath;
  private Buffer certValue;

  KeyCertOptionsImpl() {
    super();
  }

  KeyCertOptionsImpl(KeyCertOptions other) {
    super();
    this.keyPath = other.getKeyPath();
    this.keyValue = other.getKeyValue();
    this.certPath = other.getCertPath();
    this.certValue = other.getCertValue();
  }

  KeyCertOptionsImpl(JsonObject json) {
    super();
    keyPath = json.getString("keyPath");
    byte[] keyValue = json.getBinary("keyValue");
    this.keyValue = keyValue != null ? Buffer.buffer(keyValue) : null;
    certPath = json.getString("certPath");
    byte[] certValue = json.getBinary("certValue");
    this.certValue = certValue != null ? Buffer.buffer(certValue) : null;
  }

  public String getKeyPath() {
    return keyPath;
  }

  public KeyCertOptions setKeyPath(String keyPath) {
    this.keyPath = keyPath;
    return this;
  }

  public String getCertPath() {
    return certPath;
  }

  public Buffer getKeyValue() {
    return keyValue;
  }

  public KeyCertOptions setKeyValue(Buffer keyValue) {
    this.keyValue = keyValue;
    return this;
  }

  public KeyCertOptions setCertPath(String certPath) {
    this.certPath = certPath;
    return this;
  }

  public Buffer getCertValue() {
    return certValue;
  }

  public KeyCertOptions setCertValue(Buffer certValue) {
    this.certValue = certValue;
    return this;
  }

  @Override
  public KeyCertOptions clone() {
    return new KeyCertOptionsImpl(this);
  }
}
