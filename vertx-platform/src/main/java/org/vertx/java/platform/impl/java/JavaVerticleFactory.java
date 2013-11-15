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

package org.vertx.java.platform.impl.java;

import org.vertx.java.core.Vertx;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Container;
import org.vertx.java.platform.Verticle;
import org.vertx.java.platform.VerticleFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaVerticleFactory implements VerticleFactory {

  private ClassLoader cl;
  private Vertx vertx;
  private Container container;

  @Override
  public void init(Vertx vertx, Container container, ClassLoader cl) {
    this.cl = cl;
    this.vertx = vertx;
    this.container = container;
  }

  private static boolean isJavaSource(String main) {
    return main.endsWith(".java");
  }

  public Verticle createVerticle(String main) throws Exception {
    String className = main;
    Class<?> clazz;
    if (isJavaSource(main)) {
      // TODO - is this right???
      // Don't we want one CompilingClassloader per instance of this?
      CompilingClassLoader compilingLoader = new CompilingClassLoader(cl, main);
      className = compilingLoader.resolveMainClassName();
      clazz = compilingLoader.loadClass(className);
    } else {
      clazz = cl.loadClass(className);
    }
    Verticle verticle = (Verticle)clazz.newInstance();
    verticle.setVertx(vertx);
    verticle.setContainer(container);
    return verticle;
  }
    
  public void reportException(Logger logger, Throwable t) {
    logger.error("Exception in Java verticle", t);
  }

  public void close() {
  }
}
