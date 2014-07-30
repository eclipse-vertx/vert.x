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

import io.vertx.core.json.JsonObject;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.spi.KeyCertOptionsFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class KeyCertOptionsFactoryImpl implements KeyCertOptionsFactory {

  @Override
  public KeyCertOptions options() {
    return new KeyCertOptionsImpl();
  }

  @Override
  public KeyCertOptions options(KeyCertOptions other) {
    return new KeyCertOptionsImpl(other);
  }

  @Override
  public KeyCertOptions options(JsonObject json) {
    return new KeyCertOptionsImpl(json);
  }
}
