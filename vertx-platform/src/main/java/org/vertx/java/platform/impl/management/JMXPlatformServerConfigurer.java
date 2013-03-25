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
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.Socket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.HashMap;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

/**
 * @see org.apache.catalina.mbeans.JmxRemoteLifecycleListener
 * 
 * Derived from Apache Tomcat class JmxRemoteLifecycleListener
 * 
 * 
 * @author swilliams
 *
 */
class JMXPlatformServerConfigurer {

  /**
   * @return MBeanServer
   */
  public JMXConnectorServer init() throws Exception {

    boolean useLocalPorts = Boolean.parseBoolean(System.getProperty("vertx.management.jmx.ports.localonly", "true"));
    String hostname = "localhost";
    if (!useLocalPorts) {
      hostname = System.getProperty("java.rmi.server.hostname");
    }
    else {
      // System.setProperty("java.rmi.server.hostname", "true");
    }

    int rmiServerPortPlatform = Integer.getInteger("vertx.management.jmx.ports.server", 10098);
    int rmiRegistryPortPlatform = Integer.getInteger("vertx.management.jmx.ports.registry", 10099);

    boolean rmiSSL = Boolean.getBoolean("com.sun.management.jmxremote.ssl");
    String ciphersValue = System.getProperty("com.sun.management.jmxremote.ssl.enabled.cipher.suites");
    String protocolsValue = System.getProperty("com.sun.management.jmxremote.ssl.enabled.protocols");

    boolean clientAuth = Boolean.getBoolean("com.sun.management.jmxremote.ssl.need.client.auth");
    boolean authenticate = Boolean.getBoolean("com.sun.management.jmxremote.authenticate");

    String accessFile = System.getProperty("com.sun.management.jmxremote.access.file", "jmxremote.access");
    String passwordFile = System.getProperty("com.sun.management.jmxremote.password.file", "jmxremote.password");
    String loginModuleName = System.getProperty("com.sun.management.jmxremote.login.config");

    // Prevent an attacker guessing the RMI object ID
    System.setProperty("java.rmi.server.randomIDs", "true");

    // Create the environment
    HashMap<String,Object> env = new HashMap<String,Object>();

    RMIClientSocketFactory csf = null;
    RMIServerSocketFactory ssf = null;

    // Configure SSL for RMI connection if required
    if (rmiSSL) {
      String[] ciphers = (ciphersValue != null) ? ciphersValue.split(",") : new String[0];
      String[] protocols = (protocolsValue != null) ? protocolsValue.split(",") : new String[0];
      csf = new SslRMIClientSocketFactory();
      ssf = new SslRMIServerSocketFactory(ciphers, protocols, clientAuth);
    }

    // Force the use of local ports if required
    if (useLocalPorts) {
      csf = new RmiClientLocalhostSocketFactory(csf);
    }

    // Populate the env properties used to create the server
    if (csf != null) {
      env.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE, csf);
    }
    if (ssf != null) {
      env.put(RMIConnectorServer.RMI_SERVER_SOCKET_FACTORY_ATTRIBUTE, ssf);
    }

    // Configure authentication
    if (authenticate) {
      env.put("jmx.remote.x.password.file", passwordFile);
      env.put("jmx.remote.x.access.file", accessFile);
      env.put("jmx.remote.x.login.config", loginModuleName);
    }

    // Create the Platform server
    JMXConnectorServer connectorServer = createServer(hostname, rmiRegistryPortPlatform,
        rmiServerPortPlatform, env,
        ManagementFactory.getPlatformMBeanServer());

    return connectorServer;
  }


  private JMXConnectorServer createServer(String hostname, int registryPort, int serverPort, HashMap<String,Object> env, MBeanServer mBeanServer) {

    try {
      LocateRegistry.createRegistry(registryPort);
      String url = String.format("service:jmx:rmi://%s:%d/jndi/rmi://%s:%d/jmxrmi", hostname, serverPort, hostname, registryPort);
      JMXServiceURL serviceUrl = new JMXServiceURL(url);
      JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(serviceUrl, env, mBeanServer);
      cs.start();
      return cs;

    } catch (RemoteException e) {
      throw new IllegalArgumentException(e);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static class RmiClientLocalhostSocketFactory implements RMIClientSocketFactory, Serializable {
    private static final long serialVersionUID = 1L;

    private static final String FORCED_HOST = "localhost";

    private RMIClientSocketFactory factory = null;

    public RmiClientLocalhostSocketFactory(RMIClientSocketFactory theFactory) {
      this.factory = theFactory;
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
      if (factory == null) {
        return new Socket(FORCED_HOST, port);
      } else {
        return factory.createSocket(FORCED_HOST, port);
      }
    }

  }
}
