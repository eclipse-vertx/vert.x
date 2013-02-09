package org.vertx.java.platform.impl;/*
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
 * @author <a href="http://tfox.org">Tim Fox</a>
 */

import org.vertx.java.platform.PlatformManager;
import org.vertx.java.platform.PlatformManagerFactory;

/**
 * Don't use this class directly to create PlatformManager instances - instead use the
 * PlatformLocator class
 */
public class DefaultPlatformManagerFactory implements PlatformManagerFactory {

  public PlatformManager createPlatformManager() {
    return new DefaultPlatformManager();
  }

  public PlatformManager createPlatformManager(int clusterPort, String clusterHost) {
    return new DefaultPlatformManager(clusterPort, clusterHost);
  }
}
