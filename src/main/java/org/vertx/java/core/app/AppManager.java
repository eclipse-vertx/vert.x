package org.vertx.java.core.app;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.app.groovy.GroovyAppFactory;
import org.vertx.java.core.app.java.JavaAppFactory;
import org.vertx.java.core.app.jruby.JRubyAppFactory;
import org.vertx.java.core.app.rhino.RhinoAppFactory;
import org.vertx.java.core.logging.Logger;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AppManager {

  private static final Logger log = Logger.getLogger(AppManager.class);

  public static AppManager instance = new AppManager();

  private Map<String, AppMetaData> appMeta = new HashMap<>();
  private Map<String, List<AppHolder>> apps = new HashMap();
  private CountDownLatch stopLatch = new CountDownLatch(1);

  private AppManager() {
  }

  public void block() {
    while (true) {
      try {
        stopLatch.await();
        break;
      } catch (InterruptedException e) {
        //Ignore
      }
    }
  }

  public void unblock() {
    stopLatch.countDown();
  }

  public synchronized void deploy(final AppType type, final String appName, final String main, final URL[] urls,
                                  int instances,
                                  final Handler<Void> doneHandler)
    throws Exception {

    if (instances == -1) {
      // Default to number of cores
      instances = Runtime.getRuntime().availableProcessors();
    }

    log.debug("Deploying application name : " + appName + " type: " + type + " main class: " + main +
             " instances: " + instances);

    if (appMeta.containsKey(appName)) {
      throw new IllegalStateException("There is already a deployed application with name: " + appName);
    }

    final AppFactory appFactory;
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

    final int instCount = instances;

    class AggHandler {
      final AtomicInteger count = new AtomicInteger(0);

      void started() {
        if (count.incrementAndGet() == instCount) {
          log.debug("Started " + instCount + " instances ok");
          if (doneHandler != null) {
            doneHandler.handle(null);
          }
        }
      }
    }

    final AggHandler aggHandler = new AggHandler();

    for (int i = 0; i < instances; i++) {

      // Launch the app instance

      VertxInternal.instance.go(new Runnable() {
        public void run() {

          VertxApp app;
          try {
            app = appFactory.createApp(main, new ParentLastURLClassLoader(urls, getClass()
              .getClassLoader()));
          } catch (Throwable t) {
            log.error("Failed to create application", t);
            internalUndeploy(appName, doneHandler);
            return;
          }

          try {
            app.start();
            addApp(appName, app);
          } catch (Throwable t) {
            log.error("Unhandled exception in application start", t);
            internalUndeploy(appName, doneHandler);
          }
          aggHandler.started();
        }
      });
    }
    appMeta.put(appName, new AppMetaData(urls, main));
  }

  public synchronized void undeployAll(final Handler<Void> doneHandler) {
    if (appMeta.isEmpty()) {
      doneHandler.handle(null);
    } else {
      Handler<Void> aggHandler = new SimpleHandler() {
        int count = appMeta.size();
        public void handle() {
          if (--count == 0) {
            doneHandler.handle(null); // All undeployed
          }
        }
      };
      Set<String> names = new HashSet<>(appMeta.keySet()); // Avoid comod exception
      for (String name: names) {
        undeploy(name, aggHandler);
      }
    }
  }

  public synchronized String undeploy(String name, final Handler<Void> doneHandler) {
    if (appMeta.get(name) == null) {
      return "There is no deployed app with name " + name;
    }
    internalUndeploy(name, doneHandler);
    return null;
  }

  private void internalUndeploy(String name, final Handler<Void> doneHandler) {
    List<AppHolder> list = apps.get(name);
    if (list != null) {
      log.debug("Undeploying " + list.size() + " instances of application: " + name);
      for (final AppHolder holder: list) {
        VertxInternal.instance.executeOnContext(holder.contextID, new Runnable() {
          public void run() {
            VertxInternal.instance.setContextID(holder.contextID);
            try {
              holder.app.stop();
            } catch (Exception e) {
              log.error("Unhandled exception in application stop", e);
            }
            if (doneHandler != null) {
              doneHandler.handle(null);
            }
          }
        });
      }
    }
    appMeta.remove(name);
    apps.remove(name);
  }


  public synchronized Map<String, Integer> listInstances() {
    Map<String, Integer> map = new HashMap<>();
    for (Map.Entry<String, List<AppHolder>> entry: apps.entrySet()) {
      map.put(entry.getKey(), entry.getValue().size());
    }
    return map;
  }

  // Must be sychronized since called directly after app is deployed from different thread
  private synchronized void addApp(String name, VertxApp app) {
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
