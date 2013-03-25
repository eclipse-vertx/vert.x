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

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Hashtable;

import java.util.Set;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;

import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;

import org.vertx.java.core.impl.management.VertxManagementException;
import org.vertx.java.platform.impl.DefaultPlatformManager;

import org.vertx.java.platform.impl.ModuleIdentifier;

/**
 * @author swilliams
 *
 */
public enum JMX {

  PLATFORM

  ;

  private final boolean MANAGEMENT_ENABLED = Boolean.getBoolean("vertx.management.jmx");

  private final String DOMAIN = "io.vertx.platform";

  private final MBeanServer mBeanServer;

  private JMX() {
    boolean custom = Boolean.getBoolean("vertx.management.jmx.custom");
    if (MANAGEMENT_ENABLED && custom) {
      try {
        JMXPlatformServerConfigurer jmxpsc = new JMXPlatformServerConfigurer();
        JMXConnectorServer connectorServer = jmxpsc.init();
        this.mBeanServer = connectorServer.getMBeanServer();
      } catch (IOException e) {
        throw new IllegalStateException(e);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
    else {
      this.mBeanServer = ManagementFactory.getPlatformMBeanServer();
    }
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        JMX.PLATFORM.stop();
      }
    });
  }

  public void stop() {
    // Unregister all beans on domain 'io.vertx.core'
    try {
      ObjectName query = new ObjectName(DOMAIN + ":type=*");
      Set<ObjectName> names = mBeanServer.queryNames(query, null);
      for (ObjectName name : names) {
        try {
          mBeanServer.unregisterMBean(name);
        } catch (MBeanRegistrationException e) {
          // Ignore
        } catch (InstanceNotFoundException e) {
          // Ignore
        }
      }

    } catch (MalformedObjectNameException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  /**
   * @param platformManager
   */
  public void registerPlatformManager(DefaultPlatformManager platformManager) {
    if (!MANAGEMENT_ENABLED) return;

    try {
      ObjectName objName = ObjectName.getInstance(DOMAIN, "type", "Platform");
      if (!mBeanServer.isRegistered(objName)) {
        PlatformMXBean platformMXBean = new PlatformMXBeanImpl(platformManager);
        mBeanServer.registerMBean(platformMXBean, objName);
      }
    } catch (MalformedObjectNameException | InstanceAlreadyExistsException
        | MBeanRegistrationException | NotCompliantMBeanException e) {
      throw new VertxManagementException(e);
    }
  }


  /**
   * @param deploymentID
   * @param theMain
   * @param modID
   * @param worker
   * @param multiThreaded
   * @param instances
   */
  public void registerVerticle(String deploymentID, String theMain, ModuleIdentifier modID, boolean worker, boolean multiThreaded, int instances) {
    if (!MANAGEMENT_ENABLED) return;
    if (deploymentID == null) {
      Thread.dumpStack();
      return;
    }

    try {
      Hashtable<String, String> keys = new Hashtable<>();
      keys.put("type", "Verticle");
      keys.put("id", deploymentID);

      ObjectName objName = ObjectName.getInstance(DOMAIN, keys);
      if (!mBeanServer.isRegistered(objName)) {
        VerticleMXBean verticleMXBean = new VerticleMXBeanImpl(deploymentID, theMain, modID, worker, multiThreaded, instances);
        mBeanServer.registerMBean(verticleMXBean, objName);
      }
    } catch (MalformedObjectNameException | InstanceAlreadyExistsException
        | MBeanRegistrationException | NotCompliantMBeanException e) {
      throw new VertxManagementException(e);
    }
  }

  /**
   * @param deploymentID
   */
  public void unregisterVerticle(String deploymentID) {

    if (!MANAGEMENT_ENABLED) return;

    Hashtable<String, String> keys = new Hashtable<>();
    keys.put("type", "Verticle");
    keys.put("address", deploymentID);
    unregister(keys);
  }

  private void unregister(Hashtable<String, String> keys) {
    try {
      ObjectName objName = ObjectName.getInstance(DOMAIN, keys);
      if (mBeanServer.isRegistered(objName)) {
        mBeanServer.unregisterMBean(objName);
      }
    } catch (MalformedObjectNameException | MBeanRegistrationException | InstanceNotFoundException e) {
      throw new VertxManagementException(e);
    }
  }

}
