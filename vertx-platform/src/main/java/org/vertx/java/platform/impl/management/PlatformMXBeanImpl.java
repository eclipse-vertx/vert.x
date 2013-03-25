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

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationBroadcasterSupport;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.impl.DefaultPlatformManager;

/**
 * @author swilliams
 *
 */
public class PlatformMXBeanImpl extends NotificationBroadcasterSupport implements PlatformMXBean, NotificationBroadcaster {

  private DefaultPlatformManager platformManager;

  private AtomicLong sequence = new AtomicLong(0L);

  /**
   * @param platformManager
   */
  public PlatformMXBeanImpl(DefaultPlatformManager platformManager) {
    this.platformManager = platformManager;
  }

  @Override
  public Map<String, Integer> getInstances() {
    return platformManager.listInstances();
  }

  @Override
  public int getInstanceCount() {
    return platformManager.listInstances().size();
  }

  @Override
  public void installModule(String moduleName) {
    platformManager.installModule(moduleName);
  }

  @Override
  public void uninstallModule(String moduleName) {
    platformManager.uninstallModule(moduleName);
  }

  @Override
  public void removeModule(String moduleKey) {
    platformManager.removeModule(moduleKey);
  }

  @Override
  public void deployModule(String moduleName, String json, int instances) {
    platformManager.deployModule(moduleName, new JsonObject(json), instances, new Handler<String>() {
      @Override
      public void handle(String event) {
        String type = "vertx.platform.module.deployed";
        String message = String.format("Deployed module ", event);
        Notification notification = new Notification(type, this, sequence.incrementAndGet(), message);
        PlatformMXBeanImpl.this.sendNotification(notification);
      }
    });
  }

  @Override
  public void deployModuleFromZip(String moduleName, String json, int instances) {
    platformManager.deployModuleFromZip(moduleName, new JsonObject(json), instances, new Handler<String>() {
      @Override
      public void handle(String event) {
        String type = "vertx.platform.module.deployed";
        String message = String.format("Deployed module ", event);
        Notification notification = new Notification(type, this, sequence.incrementAndGet(), message);
        PlatformMXBeanImpl.this.sendNotification(notification);
      }
    });
  }

  @Override
  public void undeployModule(final String deploymentID) {
    platformManager.undeploy(deploymentID, new Handler<Void>() {
      @Override
      public void handle(Void event) {
        String type = "vertx.platform.module.undeployed";
        String message = String.format("Undeployed module ", deploymentID);
        Notification notification = new Notification(type, this, sequence.incrementAndGet(), message);
        PlatformMXBeanImpl.this.sendNotification(notification);
      }
    });
  }

}
