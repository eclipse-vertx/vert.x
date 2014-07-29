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
import io.vertx.core.net.CaOptions;
import io.vertx.core.spi.CaOptionsFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CaOptionsFactoryImpl implements CaOptionsFactory {

  @Override
  public CaOptions newOptions() {
    return new CaOptionsImpl();
  }

  @Override
  public CaOptions copiedOptions(CaOptions other) {
    return new CaOptionsImpl(other);
  }

  @Override
  public CaOptions optionsFromJson(JsonObject json) {
    return new CaOptionsImpl(json);
  }
}
