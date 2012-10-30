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
import org.vertx.java.core.http.impl.DefaultHttpServer;
import org.vertx.java.core.impl.DefaultVertx;
//import org.vertx.java.core.http.ServerWebSocket;
import org.vertx.java.core.net.impl.DefaultNetServer;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.core.sockjs.impl.DefaultSockJSServer;


public class JmxUtil {

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

  public static void unregisterEventBus() {
    ObjectName name = name("type=EventBus");
    if (mbeanServer.isRegistered(name)) {
      unregisterMBean(name);
    }
  }

  public static void register(EventBus eventBus, int port, String host) {
    ObjectName name = name(String.format("type=EventBus-%s[%s]", host, port));
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

  public static void register(DefaultNetServer server, String host, int port) {
    NetServerProxy proxy = new NetServerProxy(server, host, port);
    ObjectName name = wrap(proxy.getObjectName());
    registerMBean(proxy, name);
  }

  public static void unregisterNetServer(String host, int port) {
    ObjectName name = name(String.format("type=NetServer,name=%s[%s]", host, port));
    unregisterMBean(name);
  }

  public static void register(DefaultHttpServer server, String host, int port) {
    HttpServerProxy proxy = new HttpServerProxy(server, host, port);
    ObjectName name = wrap(proxy.getObjectName());
    registerMBean(proxy, name);
  }

  public static void unregisterHttpServer(String host, int port) {
    ObjectName name = name(String.format("type=HttpServer,name=%s[%s]", host, port));
    unregisterMBean(name);
  }

  public static void register(DefaultSockJSServer server, String host, int port) {
    SockJSServerProxy proxy = new SockJSServerProxy(server, host, port);
    ObjectName name = wrap(proxy.getObjectName());
    registerMBean(proxy, name);
  }

  public static void unregister(SockJSServer server, String host, int port) {
    ObjectName name = name(String.format("type=SockJSServer,name='%s[%s]'", host, port));
    unregisterMBean(name);
  }

  public static ObjectName wrap(String keys) {
    try {
      return new ObjectName(keys);

    } catch (MalformedObjectNameException e) {
      throw new VertxRuntimeException(e);
    }
  }

  public static ObjectName name(String keys) {
    try {
      return new ObjectName(DOMAIN + ":" + keys);

    } catch (MalformedObjectNameException e) {
      throw new VertxRuntimeException(e);
    }
  }

  public static void registerMBean(Object bean, String name) {
    registerMBean(bean, wrap(name));
  }

  public static void registerMBean(Object bean, ObjectName name) {
    try {
      if (!mbeanServer.isRegistered(name)) {
        mbeanServer.registerMBean(bean, name);
      }
    } catch (InstanceAlreadyExistsException e) {
      throw new VertxRuntimeException(e);
    } catch (MBeanRegistrationException e) {
      throw new VertxRuntimeException(e);
    } catch (NotCompliantMBeanException e) {
      throw new VertxRuntimeException(e);
    }
  }

  public static void unregisterMBean(ObjectName name) {
    try {
      if (mbeanServer.isRegistered(name)) {
        mbeanServer.unregisterMBean(name);
      }
    } catch (InstanceNotFoundException e) {
      throw new VertxRuntimeException(e);
    } catch (MBeanRegistrationException e) {
      throw new VertxRuntimeException(e);
    }
  }

}
