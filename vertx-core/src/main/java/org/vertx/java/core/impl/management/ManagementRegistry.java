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
package org.vertx.java.core.impl.management;

import java.lang.management.ManagementFactory;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.vertx.java.core.net.impl.ServerID;

/**
 * @author swilliams
 *
 */
public class ManagementRegistry {

  private static final boolean MANAGEMENT_ENABLED = Boolean.getBoolean("vertx.management.jmx");

  private static final String DOMAIN = "org.vertx";

  private static final MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();

  /**
   * @param serverID
   */
  public static void registerEventBus(ServerID serverID) {
    if (!MANAGEMENT_ENABLED) return;

    try {
      ObjectName eventBusName = ObjectName.getInstance(DOMAIN, "Name", "EventBus");
      if (!platformMBeanServer.isRegistered(eventBusName)) {
        EventBusMXBean eventBusMXBean = new EventBusMXBeanImpl(serverID.host, serverID.port);
        platformMBeanServer.registerMBean(eventBusMXBean, eventBusName);
      }
    } catch (MalformedObjectNameException | InstanceAlreadyExistsException
        | MBeanRegistrationException | NotCompliantMBeanException e) {
      throw new VertxManagementException(e);
    }
  }

  public static void registerThreadPool(String name, ExecutorService service) {
    if (!MANAGEMENT_ENABLED) return;

    try {
      ThreadPoolExecutor exec = (ThreadPoolExecutor) service;

      Hashtable<String, String> table = new Hashtable<>();
      table.put("type", "ThreadPool");
      table.put("name", name);

      ObjectName poolName = ObjectName.getInstance(DOMAIN, table);
      if (!platformMBeanServer.isRegistered(poolName)) {
        ThreadPoolMXBean eventBusMXBean = new ThreadPoolMXBeanImpl(exec);
        platformMBeanServer.registerMBean(eventBusMXBean, poolName);
      }
    } catch (MalformedObjectNameException | InstanceAlreadyExistsException
        | MBeanRegistrationException | NotCompliantMBeanException e) {
      throw new VertxManagementException(e);
    }
  }

}
