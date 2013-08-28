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
package org.vertx.java.platform;

/**
 *
 * Factory for creating PlatformManager instances. Use this when embedding the Vert.x platform.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public interface PlatformManagerFactory {

  /**
   * Create a non clustered platform manager
   * @return The instance
   */
  PlatformManager createPlatformManager();

  /**
   * Create a clustered platform manager
   * @param clusterPort The cluster port to listen on
   * @param clusterHost The cluster host to listen on
   * @return The instance
   */
  PlatformManager createPlatformManager(int clusterPort, String clusterHost);

  /**
   * Create a clustered platform manager with HA enabled
   * @param clusterPort The cluster port to listen on
   * @param clusterHost The cluster host to listen on
   * @param quorumSize The minimum number of nodes in the cluster before deployments will be activated
   * @param haGroup The HA group this Vert.x instance participates in. If null defaults to __DEFAULT__
   * @return The instance
   */
  PlatformManager createPlatformManager(int clusterPort, String clusterHost, int quorumSize, String haGroup);
}
