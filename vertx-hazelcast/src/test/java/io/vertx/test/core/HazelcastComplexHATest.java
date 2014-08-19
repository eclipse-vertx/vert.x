/*
 * Copyright 2014 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
 */

package io.vertx.test.core;

import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.impl.hazelcast.HazelcastClusterManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastComplexHATest extends ComplexHATest {

  static {
    System.setProperty("hazelcast.wait.seconds.before.join", "0");
    System.setProperty("hazelcast.local.localAddress", "127.0.0.1");
  }

  @Override
  protected ClusterManager getClusterManager() {
    return new HazelcastClusterManager();
  }

}
