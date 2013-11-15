/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.platform;

import org.vertx.java.core.Future;
import org.vertx.java.core.Vertx;

/**
 * A verticle is the unit of execution in the Vert.x platform<p>
 * Vert.x code is packaged into Verticle's and then deployed and executed by the Vert.x platform.<p>
 * Verticles can be written in different languages.<p>
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
   */
  public void start() {
  }

  /**
   * Override this method to signify that start is complete sometime _after_ the start() method has returned
   * This is useful if your verticle deploys other verticles or modules and you don't want this verticle to
   * be considered started until the other modules and verticles have been started.
   * @param startedResult When you are happy your verticle is started set the result
   */
  public void start(Future<Void> startedResult) {
    start();
    startedResult.setResult(null);
  }

  /**
   * Vert.x calls the stop method when the verticle is undeployed.
   * Put any cleanup code for your verticle in here
   */
  public void stop() {
  }

}
