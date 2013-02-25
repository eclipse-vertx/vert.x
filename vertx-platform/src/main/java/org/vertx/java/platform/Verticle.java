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

package org.vertx.java.platform;

import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidResult;

/**
 * A verticle is the unit of deployment in vert.x<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class Verticle {

  /**
   * A reference to the vert.x runtime
   */
  protected Vertx vertx;

  /**
   * A reference to the vert.x container
   */
  protected Container container;

  /**
   * @return a reference to the Container
   */
  public Container getContainer() {
    return container;
  }

  /**
   * Inject the container
   */
  public void setContainer(Container container) {
    this.container = container;
  }

  /**
   * @return A reference to a Vertx
   */
  public Vertx getVertx() {
    return vertx;
  }

  /**
   * Inject vertx
   */
  public void setVertx(Vertx vertx) {
    this.vertx = vertx;
  }

  /**
   * Vert.x calls the start method when the verticle is deployed
   * @throws Exception
   */
  public void start() throws Exception {
  }

  /**
   * Override this method to signify that start is complete sometime _after_ the start() method has returned
   * This is useful if your verticle deploys other verticles or modules and you don't want this verticle to
   * be considered started until the other modules and verticles have been started.
   * @param startedResult When you are happy your verticle is started set the result
   * @throws Exception
   */
  public void start(VoidResult startedResult) throws Exception {
    start();
    startedResult.setResult();
  }

  /**
   * Vert.x calls the stop method when the verticle is undeployed.
   * Put any cleanup code for your verticle in here
   * @throws Exception
   */
  public void stop() throws Exception {
  }

}
