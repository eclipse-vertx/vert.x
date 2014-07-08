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

/**
 * Key or trust store options configuring private key and/or certificates based on Java Keystore files.<p>
 *
 * When used as a key store, it should point to a store containing a private key and its certificate.
 * When used as a trust store, it should point to a store containing a list of accepted certificates.<p>
 *
 * The store can either be loaded by Vert.x from the filesystem:<p>
 * <pre>
 * HttpServerOptions options = new HttpServerOptions();
 * options.setKeyStore(new JKSOptions().setPath("/mykeystore.jks").setPassword("foo"));
 * </pre>
 *
 * Or directly provided as a buffer:<p>
 *
 * <pre>
 * Buffer store = vertx.fileSystem().readFileSync("/mykeystore.jks");
 * options.setKeyStore(new JKSOptions().setValue(store).setPassword("foo"));
 * </pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Options
public class JKSOptions implements KeyStoreOptions, TrustStoreOptions {

  private String password;
  private String path;
  private Buffer value;

  public JKSOptions() {
    super();
  }

  public JKSOptions(JKSOptions other) {
    super();
    this.password = other.password;
    this.path = other.path;
    this.value = other.value;
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
    return new JKSOptions(this);
  }
}
