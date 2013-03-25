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

import io.netty.channel.nio.NioEventLoopGroup;

import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Hashtable;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.vertx.java.core.net.impl.DefaultNetSocket;
import org.vertx.java.core.net.impl.ServerID;


/**
 * @author swilliams
 *
 */
public enum JMX {

  CORE

  ; // --------------------------------------------------------------

  private final boolean MANAGEMENT_ENABLED = Boolean.getBoolean("vertx.management.jmx");

  private final String DOMAIN = "io.vertx.core";

  private final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

  private JMX() {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        JMX.CORE.stop();
      }
    });
  }

  /**
   * 
   */
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

  private String md5(String... args) {
    try {
      StringBuilder s = new StringBuilder();
      for (String a : args) {
        s.append(a);
      }

      String raw = s.toString();

      MessageDigest md5 = MessageDigest.getInstance("MD5");
      md5.update(raw.getBytes(), 0, raw.length());
      byte[] output = md5.digest(raw.getBytes(Charset.forName("UTF-8")));
      return new BigInteger(1, output).toString(16);

    } catch (NoSuchAlgorithmException e) {
      throw new VertxManagementException(e);
    }
  }

  /**
   * @param name
   * @param service
   */
  public void registerThreadPool(String name, ExecutorService service) {
    if (!MANAGEMENT_ENABLED) return;

    Hashtable<String, String> keys = new Hashtable<>();
    keys.put("type", "ThreadPool");
    keys.put("name", name);

    try {
      ThreadPoolExecutor exec = (ThreadPoolExecutor) service;

      ObjectName objName = ObjectName.getInstance(DOMAIN, keys);
      if (!mBeanServer.isRegistered(objName)) {
        ThreadPoolMXBean eventBusMXBean = new ThreadPoolMXBeanImpl(exec);
        mBeanServer.registerMBean(eventBusMXBean, objName);
      }
    } catch (MalformedObjectNameException | InstanceAlreadyExistsException
        | MBeanRegistrationException | NotCompliantMBeanException e) {
      throw new VertxManagementException(e);
    }
  }

  /**
   * @param name
   * @param loop
   */
  public void registerNioEventLoopGroup(String name, NioEventLoopGroup loop) {
    if (!MANAGEMENT_ENABLED) return;

    Hashtable<String, String> keys = new Hashtable<>();
    keys.put("type", "EventLoopGroup");
    keys.put("name", name);

    try {
      ObjectName objName = ObjectName.getInstance(DOMAIN, keys);
      if (!mBeanServer.isRegistered(objName)) {
        EventLoopGroupMXBean eventLoopGroupMXBean = new EventLoopGroupMXBeanImpl(loop);
        mBeanServer.registerMBean(eventLoopGroupMXBean, objName);
      }
    } catch (MalformedObjectNameException | InstanceAlreadyExistsException
        | MBeanRegistrationException | NotCompliantMBeanException e) {
      throw new VertxManagementException(e);
    }
  }

  /**
   * @param serverID
   * @param sentMessages 
   * @param handlerMap 
   * @param connections 
   */
  public void registerEventBus(ServerID serverID, String prefix, AtomicLong sentMessages, ConcurrentMap<?, ?> connections, ConcurrentMap<?, ?> handlerMap) {
    if (!MANAGEMENT_ENABLED) return;

    Hashtable<String, String> keys = new Hashtable<>();
    keys.put("type", "EventBus");

    try {

      ObjectName objName = ObjectName.getInstance(DOMAIN, keys);

      if (!mBeanServer.isRegistered(objName)) {
        EventBusMXBean eventBusMXBean = new EventBusMXBeanImpl(serverID.host, serverID.port, prefix, sentMessages, connections, handlerMap);
        mBeanServer.registerMBean(eventBusMXBean, objName);
      }
    } catch (MalformedObjectNameException | InstanceAlreadyExistsException
        | MBeanRegistrationException | NotCompliantMBeanException e) {
      throw new VertxManagementException(e);
    }
  }

  /**
   * @param serverID
   */
  public void unregisterEventBus(ServerID serverID) {

    if (!MANAGEMENT_ENABLED) return;

    Hashtable<String, String> keys = new Hashtable<>();
    keys.put("type", "EventBus");
    unregister(keys);
  }

  /**
   * @param address
   * @param localOnly
   * @param received
   */
  public void registerEventBusHandler(String address, List<?> holders, boolean localOnly, AtomicLong received) {
    if (!MANAGEMENT_ENABLED) return;

    Hashtable<String, String> keys = new Hashtable<>();
    keys.put("type", "EventBus");
    keys.put("address", address);

    try {
      ObjectName objName = ObjectName.getInstance(DOMAIN, keys);
      if (!mBeanServer.isRegistered(objName)) {
        EventBusHandlerMXBean handlerMXBean = new EventBusHandlerMXBeanImpl(address, holders, localOnly, received);
        mBeanServer.registerMBean(handlerMXBean, objName);
      }
    } catch (MalformedObjectNameException | InstanceAlreadyExistsException
        | MBeanRegistrationException | NotCompliantMBeanException e) {
      throw new VertxManagementException(e);
    }

  }

  /**
   * @param address
   */
  public void unregisterEventBusHandler(String address) {

    if (!MANAGEMENT_ENABLED) return;

    Hashtable<String, String> keys = new Hashtable<>();
    keys.put("type", "EventBus");
    keys.put("address", address);
    unregister(keys);
  }

  /**
   * @param serverID
   */
  public void registerNetServer(ServerID serverID) {
    if (!MANAGEMENT_ENABLED) return;

    Hashtable<String, String> keys = new Hashtable<>();
    keys.put("type", "NetServer");
    keys.put("address", String.format("%s-%s", serverID.host, serverID.port));

    try {
      ObjectName objName = ObjectName.getInstance(DOMAIN, keys);
      if (!mBeanServer.isRegistered(objName)) {
        NetServerMXBean netServerMXBean = new NetServerMXBeanImpl(serverID.host, serverID.port);
        mBeanServer.registerMBean(netServerMXBean, objName);
      }
    } catch (MalformedObjectNameException | InstanceAlreadyExistsException
        | MBeanRegistrationException | NotCompliantMBeanException e) {
      throw new VertxManagementException(e);
    }
  }

  /**
   * @param serverID
   */
  public void unregisterNetServer(ServerID serverID) {
    if (!MANAGEMENT_ENABLED) return;

    Hashtable<String, String> keys = new Hashtable<>();
    keys.put("type", "NetServer");
    keys.put("address", String.format("%s-%s", serverID.host, serverID.port));
    unregister(keys);
  }

  /**
   * @param sock
   */
  public void registerNetSocket(DefaultNetSocket sock) {
    if (!MANAGEMENT_ENABLED) return;

    Hashtable<String, String> keys = new Hashtable<>();
    keys.put("type", "NetSocket");
    keys.put("id", String.format("%s", sock.writeHandlerID));

    try {
      ObjectName objName = ObjectName.getInstance(DOMAIN, keys);
      if (!mBeanServer.isRegistered(objName)) {
        NetSocketMXBean netSocketMXBean = new NetSocketMXBeanImpl(sock);
        mBeanServer.registerMBean(netSocketMXBean, objName);
      }
    } catch (MalformedObjectNameException | InstanceAlreadyExistsException
        | MBeanRegistrationException | NotCompliantMBeanException e) {
      throw new VertxManagementException(e);
    }
  }

  /**
   * @param sock
   */
  public void unregisterNetSocket(DefaultNetSocket sock) {
    if (!MANAGEMENT_ENABLED) return;

    Hashtable<String, String> keys = new Hashtable<>();
    keys.put("type", "NetSocket");
    keys.put("id", String.format("%s", sock.writeHandlerID));
    unregister(keys);
  }

  /**
   * @param serverID
   */
  public void registerHttpServer(ServerID serverID, AtomicLong received) {
    if (!MANAGEMENT_ENABLED) return;

    Hashtable<String, String> keys = new Hashtable<>();
    keys.put("type", "HttpServer");
    keys.put("address", String.format("%s-%s", serverID.host, serverID.port));

    try {
      ObjectName objName = ObjectName.getInstance(DOMAIN, keys);
      if (!mBeanServer.isRegistered(objName)) {
        HttpServerMXBean httpServerMXBean = new HttpServerMXBeanImpl(serverID.host, serverID.port, received);
        mBeanServer.registerMBean(httpServerMXBean, objName);
      }
    } catch (MalformedObjectNameException | InstanceAlreadyExistsException
        | MBeanRegistrationException | NotCompliantMBeanException e) {
      throw new VertxManagementException(e);
    }
  }

  /**
   * @param serverID
   */
  public void unregisterHttpServer(ServerID serverID) {
    if (!MANAGEMENT_ENABLED) return;

    Hashtable<String, String> keys = new Hashtable<>();
    keys.put("type", "HttpServer");
    keys.put("address", String.format("%s-%s", serverID.host, serverID.port));

    unregister(keys);
  }

  /**
   * @param path
   * @param binaryHandlerID
   * @param textHandlerID
   * @param readFrames
   * @param sentFrames
   * @param exceptions
   */
  public void registerWebSocket(String path, String binaryHandlerID, String textHandlerID, AtomicLong readFrames, AtomicLong sentFrames, AtomicLong exceptions) {
    if (!MANAGEMENT_ENABLED) return;

    Hashtable<String, String> keys = new Hashtable<>();
    if (path != null) {
      keys.put("type", "ServerWebSocket");
      keys.put("path", path);
    }
    else {
      keys.put("type", "WebSocket");
      // makes a shorter id than the two together to get a UID
      keys.put("id", md5(binaryHandlerID, textHandlerID));
    }

    try {
      ObjectName objName = ObjectName.getInstance(DOMAIN, keys);
      if (!mBeanServer.isRegistered(objName)) {
        ServerWebSocketMXBean serverWebSocketMXBean = new ServerWebSocketMXBeanImpl(path, binaryHandlerID, textHandlerID, readFrames, sentFrames, exceptions);
        mBeanServer.registerMBean(serverWebSocketMXBean, objName);
      }
    } catch (MalformedObjectNameException | InstanceAlreadyExistsException
        | MBeanRegistrationException | NotCompliantMBeanException e) {
      throw new VertxManagementException(e);
    }
  }

  /**
   * @param path
   */
  public void unregisterWebSocket(String path, String binaryHandlerID, String textHandlerID) {
    if (!MANAGEMENT_ENABLED) return;

    Hashtable<String, String> keys = new Hashtable<>();
    if (path != null) {
      keys.put("type", "ServerWebSocket");
      keys.put("path", path);
    }
    else {
      keys.put("type", "WebSocket");
      // makes a shorter id than the two together to get a UID
      keys.put("id", md5(binaryHandlerID, textHandlerID));
    }

    unregister(keys);
  }

  /**
   * @param id
   * @param pendingWrites 
   * @param pendingReads 
   */
  public void registerSockJSSocket(String id, Queue<String> pendingReads, Queue<String> pendingWrites) {
    if (!MANAGEMENT_ENABLED) return;
    if (id == null) return;

    Hashtable<String, String> keys = new Hashtable<>();
    keys.put("type", "SockJSSocket");
    keys.put("id", id);

    try {
      ObjectName objName = ObjectName.getInstance(DOMAIN, keys);
      if (!mBeanServer.isRegistered(objName)) {
        SockJSSocketMXBean sockJSSocketMXBean = new SockJSSocketMXBeanImpl(id, pendingReads, pendingWrites);
        mBeanServer.registerMBean(sockJSSocketMXBean, objName);
      }
    } catch (MalformedObjectNameException | InstanceAlreadyExistsException
        | MBeanRegistrationException | NotCompliantMBeanException e) {
      throw new VertxManagementException(e);
    }
  }

  /**
   * @param id
   */
  public void unregisterSockJSSocket(String id) {
    if (!MANAGEMENT_ENABLED) return;
    if (id == null) return;

    Hashtable<String, String> keys = new Hashtable<>();
    keys.put("type", "SockJSSocket");
    keys.put("id", id);

    unregister(keys);
  }

}
