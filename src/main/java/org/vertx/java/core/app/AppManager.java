package org.vertx.java.core.app;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.app.groovy.GroovyAppFactory;
import org.vertx.java.core.app.java.JavaAppFactory;
import org.vertx.java.core.app.jruby.JRubyAppFactory;
import org.vertx.java.core.app.rhino.RhinoAppFactory;
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

  public synchronized String deploy(final AppType type, final String appName, String main, URL[] urls, int instances)
    throws Exception {

    if (instances == -1) {
      // Default to number of cores
      instances = Runtime.getRuntime().availableProcessors();
    }

    log.info("Deploying application name : " + appName + " type: " + type + " main class: " + main +
             " instances: " + instances);

    if (appMeta.containsKey(appName)) {
      throw new IllegalStateException("There is already a deployed application with name: " + appName);
    }

    for (int i = 0; i < instances; i++) {

      AppFactory appFactory;
      switch (type) {
        case JAVA:
          appFactory = new JavaAppFactory();
          break;
        case RUBY:
          appFactory = new JRubyAppFactory();
          break;
        case JS:
          appFactory = new RhinoAppFactory();
          break;
        case GROOVY:
          appFactory = new GroovyAppFactory();
          break;
        default:
          throw new IllegalArgumentException("Unsupported type: " + type);
      }

      final VertxApp app = appFactory.createApp(main, urls, getClass().getClassLoader());

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

  public synchronized void undeployAll() {
    for (String name: appMeta.keySet()) {
      undeploy(name);
    }
  }

  public synchronized String undeploy(String name) {
    if (appMeta.get(name) == null) {
      return "There is no deployed app with name " + name;
    }
    List<AppHolder> list = apps.get(name);
    log.info("Undeploying " + list.size() + " instances of application: " + name);
    for (final AppHolder holder: list) {
      VertxInternal.instance.executeOnContext(holder.contextID, new Runnable() {
        public void run() {
          VertxInternal.instance.setContextID(holder.contextID);
          try {
            holder.app.stop();
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
