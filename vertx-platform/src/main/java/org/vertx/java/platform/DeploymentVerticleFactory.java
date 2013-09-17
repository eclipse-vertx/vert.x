/*
 * Copyright 2013 the original author or authors.
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

package org.vertx.java.platform;


import org.vertx.java.platform.impl.Deployment;

/**
 * Extends {@link VerticleFactory} to provide the {@link Deployment} instance when creating a {@link Verticle}.
 * <p/>
 * This makes all deployment settings available to the factory, including the configuration.
 */
public interface DeploymentVerticleFactory extends VerticleFactory {

  /**
   * Creates a {@link Verticle} instance for the provided {@link Deployment}
   *
   * @param deployment
   * @return the new {@link Verticle} instance
   * @throws Exception
   */
  Verticle createVerticle(Deployment deployment) throws Exception;

}
