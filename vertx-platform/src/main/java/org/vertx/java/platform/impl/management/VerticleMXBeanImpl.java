/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.platform.impl.management;


import org.vertx.java.platform.impl.ModuleIdentifier;

/**
 * @author swilliams
 *
 */
public class VerticleMXBeanImpl implements VerticleMXBean {

  private String deploymentID;

  private boolean worker;

  private String theMain;

  private String modID;

  private boolean multiThreaded;

  private int instances;

  /**
   * @param deploymentID
   * @param worker 
   */
  public VerticleMXBeanImpl(String deploymentID, String theMain, ModuleIdentifier modID, boolean worker, boolean multiThreaded, int instances) {
    this.deploymentID = deploymentID;
    this.theMain = theMain;
    this.modID = (modID != null) ? modID.toString() : "<none>";
    this.worker = worker;
    this.multiThreaded = multiThreaded;
    this.instances = instances;
  }

  @Override
  public String getID() {
    return deploymentID;
  }

  @Override
  public boolean isWorker() {
    return worker;
  }

  @Override
  public String getMain() {
    return theMain;
  }

  @Override
  public String getModID() {
    return modID;
  }

  @Override
  public boolean isMultiThreaded() {
    return multiThreaded;
  }

  @Override
  public int getInstances() {
    return instances;
  }

}
