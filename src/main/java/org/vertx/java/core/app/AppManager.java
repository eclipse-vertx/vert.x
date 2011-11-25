package org.vertx.java.core.app;

import org.vertx.java.core.Vertx;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.logging.Logger;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AppManager {

  private static final Logger log = Logger.getLogger(AppManager.class);

  private Map<String, AppMetaData> appMeta = new HashMap<>();
  private Map<String, List<AppHolder>> apps = new HashMap();

  private CountDownLatch stopLatch = new CountDownLatch(1);
  private SocketDeployer deployer;

  public static void main(String[] sargs) {
    Args args = new Args(sargs);
    AppManager mgr = new AppManager(args.getPort());
    mgr.start();
  }

  public AppManager(int port) {
    deployer = new SocketDeployer(this, port);
  }

  public void start() {
    start(true);
  }

  private void start(boolean block) {

    deployer.start();

    if (block) {
      while (true) {
        try {
          stopLatch.await();
          break;
        } catch (InterruptedException e) {
          //Ignore
        }
      }
    }
  }

  public void stop() {
    deployer.stop();
    stopLatch.countDown();
  }

  public synchronized String deploy(final String appName, final AppType type, URL[] urls, String main, int instances) {

    if (instances == -1) {
      // Default to number of cores
      instances = Runtime.getRuntime().availableProcessors();
    }

    log.info("Deploying application name : " + appName + " type: " + type + " main class: " + main +
             " instances: " + instances);

    if (appMeta.containsKey(appName)) {
      return "There is already a deployed application with name: " + appName;
    }

    for (int i = 0; i < instances; i++) {

      ClassLoader cl = new ParentLastURLClassLoader(urls, getClass().getClassLoader());
      final VertxApp app;

      switch (type) {
        case JAVA:
          Class clazz;
          try {
            clazz = cl.loadClass(main);
          } catch (ClassNotFoundException e) {
            return "Cannot find class: " + main;
          }
          try {
            app = (VertxApp)clazz.newInstance();
          } catch (Exception e) {
            return "Failed to instantiate class: " + clazz;
          }

          //Sanity check - make sure each instance of class has its own classloader - this might not be true if
          //the class got loaded from the parent classpath, so we fail if this occurs

          //We only need to do this for Java since with JRuby the JRuby container ensures isolation (each
          //application instance is run in its own container

          List<AppHolder> list = apps.get(appName);
          if (list != null) {
            for (AppHolder holder: list) {
              if (holder.app.getClass().getClassLoader() == app.getClass().getClassLoader()) {
                throw new IllegalStateException("There is already an instance of the app with the same classloader." +
                    " Check that application classes aren't on the server classpath.");
              }
            }
          }

          break;
        case RUBY:
          if (System.getProperty("jruby.home") == null) {
            return "In order to deploy Ruby applications you must set JRUBY_HOME to point at the base of your JRuby install";
          }
          app = new JRubyApp(main, cl);
          break;
        case JS:
          app = new RhinoApp(main, cl);
          break;
        default:
          throw new IllegalArgumentException("Unsupported type: " + type);
      }

      // Launch the app instance

      VertxInternal.instance.go(new Runnable() {
        public void run() {
          try {
            app.start();
          } catch (Exception e) {
            log.error("Unhandled exception in application start", e);
          }
          addApp(appName, app);
        }
      });
    }
    appMeta.put(appName, new AppMetaData(urls, main));
    log.info("Started " + instances + " instances ok");
    return null;
  }

  public synchronized String undeploy(String name) {
    if (appMeta.get(name) == null) {
      return "There is no deployed app with name " + name;
    }
    log.info("Undeploying all instances of application: " + name);
    List<AppHolder> list = apps.get(name);
    log.info("There are " + list.size());
    for (final AppHolder holder: list) {
      VertxInternal.instance.executeOnContext(holder.contextID, new Runnable() {
        public void run() {
          VertxInternal.instance.setContextID(holder.contextID);
          try {
            holder.app.stop();
            log.info("Stopped app");
          } catch (Exception e) {
            log.error("Unhandled exception in application stop", e);
          }
        }
      });
    }
    appMeta.remove(name);
    apps.remove(name);
    log.info("Undeployed ok");
    return null;
  }

  public synchronized Map<String, Integer> listInstances() {
    Map<String, Integer> map = new HashMap<>();
    for (Map.Entry<String, List<AppHolder>> entry: apps.entrySet()) {
      map.put(entry.getKey(), entry.getValue().size());
    }
    return map;
  }

  private void addApp(String name, VertxApp app) {
    List<AppHolder> list = apps.get(name);
    if (list == null) {
      list = new ArrayList<>();
      apps.put(name, list);
    }
    list.add(new AppHolder(Vertx.instance.getContextID(), app));
  }

  private static class AppMetaData {
    final URL[] urls;
    final String main;

    private AppMetaData(URL[] urls, String mainClass) {
      this.urls = urls;
      this.main = mainClass;
    }
  }

  private static class AppHolder {
    final long contextID;
    final VertxApp app;

    private AppHolder(long contextID, VertxApp app) {
      this.contextID = contextID;
      this.app = app;
    }
  }
}
