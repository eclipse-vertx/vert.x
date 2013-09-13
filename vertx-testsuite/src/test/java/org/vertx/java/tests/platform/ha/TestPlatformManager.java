package org.vertx.java.tests.platform.ha;

import org.vertx.java.core.Handler;
import org.vertx.java.platform.impl.DefaultPlatformManager;
import org.vertx.java.platform.impl.Deployment;

import java.util.Map;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */
public class TestPlatformManager extends DefaultPlatformManager {

  public TestPlatformManager(int port, String hostname, int quorumSize, String haGroup) {
    super(port, hostname, quorumSize, haGroup);
  }

  void failDuringFailover(boolean fail) {
    haManager.failDuringFailover(fail);
  }

  public void simulateKill() {
    if (clusterManager != null) {
      clusterManager.leave();
    }
  }

  // For testing only
  public Map<String, Deployment> getDeployments() {
    return deployments;
  }

  public void failoverCompleteHandler(Handler<Boolean> handler) {
    if (haManager != null) {
      haManager.failoverCompleteHandler(handler);
    }
  }
}
