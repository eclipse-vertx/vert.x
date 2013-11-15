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
package org.vertx.java.core.impl.management;

import org.vertx.java.core.net.impl.ServerID;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author swilliams
 *
 */
public class ManagementRegistry {

  private static final boolean MANAGEMENT_ENABLED = Boolean.getBoolean("vertx.management.jmx");

  private static final String DOMAIN = "org.vertx";

  private static final MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();

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
