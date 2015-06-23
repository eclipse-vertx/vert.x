/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.impl.IsolatingClassLoader;
import org.junit.Assert;

/**
* @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
*/
public class ExtraCPVerticleNotInParentLoader extends AbstractVerticle {
  @Override
  public void start() throws Exception {
    IsolatingClassLoader cl = (IsolatingClassLoader) Thread.currentThread().getContextClassLoader();
    Class extraCPClass = cl.loadClass("MyVerticle");
    Assert.assertSame(extraCPClass.getClassLoader(), cl);
    try {
      cl.getParent().loadClass("MyVerticle");
      Assert.fail("Parent classloader should not see this class");
    } catch (ClassNotFoundException expected) {
      //
    }
  }
}
