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

package io.vertx.core.datagram.impl;

import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.DatagramSocketOptionsFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DatagramSocketOptionsFactoryImpl implements DatagramSocketOptionsFactory {

  @Override
  public DatagramSocketOptions newOptions() {
    return new DatagramSocketOptionsImpl();
  }

  @Override
  public DatagramSocketOptions copiedOptions(DatagramSocketOptions other) {
    return new DatagramSocketOptionsImpl(other);
  }

  @Override
  public DatagramSocketOptions fromJson(JsonObject json) {
    return new DatagramSocketOptionsImpl(json);
  }
}
