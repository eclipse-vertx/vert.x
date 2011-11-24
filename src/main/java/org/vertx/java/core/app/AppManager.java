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

//    String[] parts = classpath.split(":");
//    URL[] urls = new URL[parts.length];
//    try {
//      int index = 0;
//      for (String part: parts) {
//        File f = new File(part);
//        part = "file://" + f.getAbsolutePath();
//        if (!part.endsWith(".jar") && !part.endsWith(".zip") && !part.endsWith("/")) {
//          part += "/";
//        }
//        URL url = new URL(part);
//        log.info("url part is: " + part);
//        urls[index++] = url;
//      }
//    } catch (MalformedURLException e) {
//      return "Invalid classpath: " + classpath;
//    }

    if (appMeta.containsKey(appName)) {
      return "There is already a deployed application with name: " + appName;
    } else {
      for (int i = 0; i < instances; i++) {
        //Each instance has its own classloader for isolation
        ClassLoader cl = new ParentLastURLClassLoader(urls, getClass().getClassLoader());
        try {
          Class clazz = cl.loadClass(main);

          final VertxApp app;
          try {
            app = (VertxApp)clazz.newInstance();
          } catch (Exception e) {
            return "Failed to instantiate class: " + clazz;
          }

          //Sanity check - make sure each instance of class has its own classloader - this might not be true if
          //the class got loaded from the parent classpath, so we fail if this occurs

          List<AppHolder> list = apps.get(appName);
          if (list != null) {
            for (AppHolder holder: list) {
              if (holder.app.getClass().getClassLoader() == app.getClass().getClassLoader()) {
                throw new IllegalStateException("There is already an instance of the app with the same classloader." +
                    " Check that application classes aren't on the server classpath.");
              }
            }
          }

          VertxInternal.instance.go(new Runnable() {
            public void run() {
              addApp(appName, app);
              try {
                app.start();
              } catch (Exception e) {
                log.error("Unhandled exception in application start", e);
              }
            }
          });

        } catch (ClassNotFoundException e) {
          return "Cannot find class: " + main;
        }
      }
      appMeta.put(appName, new AppMetaData(urls, main));
      log.info("Started " + instances + " instances ok");
      return null;
    }
  }

  public synchronized String undeploy(String name) {
    log.info("Undeploying all instances of application: " + name);
    List<AppHolder> list = apps.get(name);
    for (final AppHolder holder: list) {
      VertxInternal.instance.executeOnContext(holder.contextID, new Runnable() {
        public void run() {
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
