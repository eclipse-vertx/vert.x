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

package org.vertx.java.core;

import org.vertx.java.core.json.JsonObject;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface VerticleDeployment {

  String getID();

  void undeploy(Handler<AsyncResult<Void>> doneHandler);

  JsonObject config();

  /**
   * Get an unmodifiable map of system, environment variables.
   * @return The map
   */
  Map<String, String> env();

  void setAsyncStart();

  void setAsyncStop();

  void startComplete();

  void stopComplete();

  void setFailure(Throwable t);

  void setFailure(String message);
}
