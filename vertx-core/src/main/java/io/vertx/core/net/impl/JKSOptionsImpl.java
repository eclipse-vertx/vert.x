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
import io.vertx.core.net.JKSOptions;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JKSOptionsImpl implements JKSOptions, Cloneable {

  private String password;
  private String path;
  private Buffer value;

  JKSOptionsImpl() {
    super();
  }

  JKSOptionsImpl(JKSOptions other) {
    super();
    this.password = other.getPassword();
    this.path = other.getPath();
    this.value = other.getValue();
  }

  JKSOptionsImpl(JsonObject json) {
    super();
    this.password = json.getString("password");
    this.path = json.getString("path");
    byte[] value = json.getBinary("value");
    this.value = value != null ? Buffer.buffer(value) : null;
  }

  public String getPassword() {
    return password;
  }

  public JKSOptions setPassword(String password) {
    this.password = password;
    return this;
  }

  public String getPath() {
    return path;
  }

  public JKSOptions setPath(String path) {
    this.path = path;
    return this;
  }

  public Buffer getValue() {
    return value;
  }

  public JKSOptions setValue(Buffer value) {
    this.value = value;
    return this;
  }

  @Override
  public JKSOptions clone() {
    return new JKSOptionsImpl(this);
  }
}
