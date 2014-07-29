/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core;

import io.vertx.codegen.annotations.Options;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.DeploymentOptionsFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public interface DeploymentOptions {

  static DeploymentOptions options() {
    return factory.newOptions();
  }

  static DeploymentOptions copiedOptions(DeploymentOptions other) {
    return factory.copiedOptions(other);
  }

  static DeploymentOptions optionsFromJson(JsonObject json) {
    return factory.optionsFromJson(json);
  }

  JsonObject getConfig();

  DeploymentOptions setConfig(JsonObject config);

  boolean isWorker();

  DeploymentOptions setWorker(boolean worker);

  boolean isMultiThreaded();

  DeploymentOptions setMultiThreaded(boolean multiThreaded);

  String getIsolationGroup();

  DeploymentOptions setIsolationGroup(String isolationGroup);

  static final DeploymentOptionsFactory factory = ServiceHelper.loadFactory(DeploymentOptionsFactory.class);

}
