/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core;

/**
 * A verticle is the unit of deployment in vert.x
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Verticle {

  /**
   * Vert.x calls thhe start method when the verticle is deployed
   * @throws Exception
   */
  void start() throws Exception;

  /**
   * Vert.x calls the stop method when the verticle is undeployed.
   * Put any cleanup code for your verticle in here
   * @throws Exception
   */
  void stop() throws Exception;
}
