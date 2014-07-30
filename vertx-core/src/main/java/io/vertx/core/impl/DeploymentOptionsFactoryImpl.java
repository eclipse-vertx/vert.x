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

package io.vertx.core.impl;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.DeploymentOptionsFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DeploymentOptionsFactoryImpl implements DeploymentOptionsFactory {

  @Override
  public DeploymentOptions options() {
    return new DeploymentOptionsImpl();
  }

  @Override
  public DeploymentOptions options(DeploymentOptions other) {
    return new DeploymentOptionsImpl(other);
  }

  @Override
  public DeploymentOptions options(JsonObject json) {
    return new DeploymentOptionsImpl(json);
  }
}
