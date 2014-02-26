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

import org.vertx.java.testframework.TestClientBase;

public class MainA extends TestClientBase {

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  public void testTwoWayInclude() {
    Mod3Class m3 = new Mod3Class();
    tu.azzert("foo".equals(m3.bar()));
    tu.testComplete();
  }
}
