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

package io.vertx.test.core;

import io.vertx.core.Starter;
import org.junit.Test;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class StarterTest extends VertxTestBase {

  public void setUp() throws Exception {
    super.setUp();
    TestVerticle.instanceCount.set(0);
  }

  @Test
  public void testVersion() throws Exception {
    String[] args = new String[] {"version"};
    Starter starter = new Starter();
    starter.run(args);
    // TODO some way of getting this from the version in pom.xml
    assertEquals("3.0.0-SNAPSHOT", starter.getVersion());
  }

  @Test
  public void testRunVerticle() throws Exception {
    Starter starter = new Starter();
    Thread t = new Thread(() -> {
      String[] args = new String[] {"run", "java:" + TestVerticle.class.getCanonicalName()};
      starter.run(args);
    });
    t.start();
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertTrue(t.isAlive()); // It's blocked
    // Now unblock it
    starter.unblock();
    waitUntil(() -> !t.isAlive());
  }

  @Test
  public void testRunVerticleClustered() throws Exception {
    Starter starter = new Starter();
    Thread t = new Thread(() -> {
      String[] args = new String[] {"run", "java:" + TestVerticle.class.getCanonicalName(), "-cluster"};
      starter.run(args);
    });
    t.start();
    waitUntil(() -> TestVerticle.instanceCount.get() == 1);
    assertTrue(t.isAlive()); // It's blocked
    // Now unblock it
    starter.unblock();
    waitUntil(() -> !t.isAlive());
  }
}
