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
import io.vertx.core.net.PKCS12Options;
import io.vertx.core.spi.PKCS12OptionsFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class PKCS12OptionsFactoryImpl implements PKCS12OptionsFactory {

  @Override
  public PKCS12Options options() {
    return new PCKS12OptionsImpl();
  }

  @Override
  public PKCS12Options options(PKCS12Options other) {
    return new PCKS12OptionsImpl(other);
  }

  @Override
  public PKCS12Options options(JsonObject json) {
    return new PCKS12OptionsImpl(json);
  }
}
