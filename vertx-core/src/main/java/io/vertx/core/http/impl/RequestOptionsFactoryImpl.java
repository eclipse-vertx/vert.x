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

package io.vertx.core.http.impl;

import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.RequestOptionsFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RequestOptionsFactoryImpl implements RequestOptionsFactory {

  @Override
  public RequestOptions newOptions() {
    return new RequestOptionsImpl();
  }

  @Override
  public RequestOptions copiedOptions(RequestOptions other) {
    return new RequestOptionsImpl(other);
  }

  @Override
  public RequestOptions optionsFromJson(JsonObject json) {
    return new RequestOptionsImpl(json);
  }
}
