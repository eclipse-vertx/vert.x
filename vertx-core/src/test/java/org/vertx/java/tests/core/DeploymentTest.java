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

package org.vertx.java.tests.core;

import org.junit.Test;
import org.vertx.java.core.Verticle;
import org.vertx.java.core.VerticleDeployment;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DeploymentTest extends VertxTestBase {


  private VerticleDeployment deployment;
  private boolean startCalled;
  private boolean stopCalled;

  @Test
  public void testDeploy() throws Exception {
    Verticle verticle = new TestVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      VerticleDeployment theDeployment = ar.result();
      assertNotNull(theDeployment);
      assertNull(theDeployment.config());
      assertEquals(1, theDeployment.instances());
      assertEquals(deployment, theDeployment);
      assertTrue(startCalled);
      assertFalse(stopCalled);
      assertTrue(vertx.deployments().contains(deployment));
      testComplete();
    });
    await();
  }

  @Test
  public void testUndeploy() throws Exception {
    Verticle verticle = new TestVerticle();
    vertx.deployVerticle(verticle, ar -> {
      assertTrue(ar.succeeded());
      VerticleDeployment theDeployment = ar.result();
      theDeployment.undeploy(ar2 -> {
        assertTrue(ar2.succeeded());
        assertFalse(vertx.deployments().contains(theDeployment));
        testComplete();
      });
    });
    await();
  }

  class TestVerticle implements Verticle {

    @Override
    public void start(VerticleDeployment theDeployment) throws Exception {
      deployment = theDeployment;
      startCalled = true;
    }

    @Override
    public void stop() throws Exception {
      stopCalled = true;
    }
  }
}
