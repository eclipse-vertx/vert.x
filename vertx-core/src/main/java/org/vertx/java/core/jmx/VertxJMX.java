package org.vertx.java.core.jmx;

import java.lang.management.ManagementFactory;
import java.util.concurrent.ExecutorService;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.vertx.java.core.Handler;
import org.vertx.java.core.VertxRuntimeException;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.impl.DefaultVertx;
//import org.vertx.java.core.http.ServerWebSocket;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.sockjs.SockJSServer;

public class VertxJMX {

  private static final String DOMAIN = "org.vertx";

  private static MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();

  public static void register(DefaultVertx vertx) {
    ObjectName name = name("type=Vertx");
    if (!mbeanServer.isRegistered(name)) {
      registerMBean(vertx, name);
    }
  }

  public static void register(EventBus eventBus) {
    ObjectName name = name("type=EventBus");
    if (!mbeanServer.isRegistered(name)) {
      registerMBean(eventBus, name);
    }
  }

  public static void register(EventBus eventBus, int port, String host) {
    ObjectName name = name("type=EventBus,host=" + host + ",port=" + port);
    if (!mbeanServer.isRegistered(name)) {
      registerMBean(eventBus, name);
    }
  }

  public static void register(ExecutorService bean, String keys) {
    ObjectName name = name("type=Executors," + keys);
    if (!mbeanServer.isRegistered(name)) {
      registerMBean(bean, name);
    }
  }

  public static void register(Handler<?> handler, String address) {
    // TODO Auto-generated method stub
    
  }

  public static void unregister(Handler<?> handler, String address) {
    // TODO Auto-generated method stub
    
  }

  public static void register(NetServer server, String host, int port) {
    ObjectName name = name("type=NetServer,host="+host+",port="+port);
    if (!mbeanServer.isRegistered(name)) {
      registerMBean(new NetServerProxy(server, host, port), name);
    }
  }

  public static void unregisterNetServer(String host, int port) {
    ObjectName name = name("type=NetServer,host="+host+",port="+port);
    if (mbeanServer.isRegistered(name)) {
      unregisterMBean(name);
    }
  }

  public static void register(HttpServer server, String host, int port) {
    ObjectName name = name("type=HttpServer,host="+host+",port="+port);
    if (!mbeanServer.isRegistered(name)) {
      registerMBean(new HttpServerProxy(server, host, port), name);
    }
  }

  public static void unregisterHttpServer(String host, int port) {
    ObjectName name = name("type=HttpServer,host="+host+",port="+port);
    if (mbeanServer.isRegistered(name)) {
      unregisterMBean(name);
    }
  }

  public static void register(SockJSServer server, String host, int port) {
//    Map<String, String> keys = new HashMap<>();
//    keys.put("type", "Vertx");
//    keys.put("server", "SockJSServer");
//    keys.put("port", String.valueOf(port));
//    ObjectName name = name(keys);
//    if (!mbeanServer.isRegistered(name)) {
//      registerMBean(server, name);
//    }
  }

  public static void unregister(SockJSServer server, String host, int port) {
    
  }

  private static ObjectName name(String keys) {
    try {
      return new ObjectName(DOMAIN + ":" + keys);

    } catch (MalformedObjectNameException e) {
      throw new VertxRuntimeException(e);
    }
  }

  private static void registerMBean(Object bean, ObjectName name) {
    try {
      mbeanServer.registerMBean(bean, name);
    } catch (InstanceAlreadyExistsException e) {
      throw new VertxRuntimeException(e);
    } catch (MBeanRegistrationException e) {
      throw new VertxRuntimeException(e);
    } catch (NotCompliantMBeanException e) {
      throw new VertxRuntimeException(e);
    }
  }

  private static void unregisterMBean(ObjectName name) {
    try {
      mbeanServer.unregisterMBean(name);
    } catch (InstanceNotFoundException e) {
      throw new VertxRuntimeException(e);
    } catch (MBeanRegistrationException e) {
      throw new VertxRuntimeException(e);
    }
  }

}
