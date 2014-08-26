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
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.CaOptions;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CaOptionsImpl implements CaOptions, Cloneable {

  private ArrayList<String> certPaths;
  private ArrayList<Buffer> certValues;

  CaOptionsImpl() {
    super();
    this.certPaths = new ArrayList<>();
    this.certValues = new ArrayList<>();
  }

  CaOptionsImpl(CaOptions other) {
    super();
    this.certPaths = new ArrayList<>(other.getCertPaths());
    this.certValues = new ArrayList<>(other.getCertValues());
  }

  CaOptionsImpl(JsonObject json) {
    super();
    this.certPaths = new ArrayList<>();
    this.certValues = new ArrayList<>();
    for (Object certPath : json.getArray("certPaths", new JsonArray())) {
      certPaths.add((String) certPath);
    }
    for (Object certValue : json.getArray("certValues", new JsonArray())) {
      certValues.add(Buffer.buffer(Base64.getDecoder().decode((String) certValue)));
    }
  }

  public List<String> getCertPaths() {
    return certPaths;
  }

  public CaOptions addCertPath(String certPath) throws NullPointerException {
    Objects.requireNonNull(certPath, "No null certificate accepted");
    Arguments.require(!certPath.isEmpty(), "No empty certificate path accepted");
    certPaths.add(certPath);
    return this;
  }

  public List<Buffer> getCertValues() {
    return certValues;
  }

  public CaOptions addCertValue(Buffer certValue) throws NullPointerException {
    Objects.requireNonNull(certValue, "No null certificate accepted");
    certValues.add(certValue);
    return this;
  }

  @Override
  public CaOptions clone() {
    return new CaOptionsImpl(this);
  }

}
