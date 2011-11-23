package org.vertx.java.core.appmanager;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxApp;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.parsetools.RecordParser;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
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

  private static final int DEFAULT_PORT = 25571;

  private Map<String, AppMetaData> appMeta = new HashMap<>();
  private Map<String, List<AppHolder>> apps = new HashMap();
  private volatile NetServer server;

  public static void main(String[] args) {
    AppManager mgr = new AppManager();
    mgr.start();
  }

  public void start() {
    start(true);
  }

  public void startNoBlock() {
    start(false);
  }

  private CountDownLatch mainLatch = new CountDownLatch(1);

  private long serverContextID;

  private void start(boolean block) {
    Vertx.instance.go(new Runnable() {
      public void run() {
        serverContextID = Vertx.instance.getContextID();
        server = new NetServer().connectHandler(new Handler<NetSocket>() {
          public void handle(final NetSocket socket) {
            socket.dataHandler(RecordParser.newDelimited("\n", new Handler<Buffer>() {
              public void handle(Buffer buff) {
                String line = buff.toString();
                if (line.startsWith("DEPLOY") || line.startsWith("deploy")) {
                  parseDeploy(socket, line);
                } else if (line.startsWith("UNDEPLOY") || line.startsWith("undeploy")) {
                  parseUndeploy(socket, line);
                } else {
                  log.error("Unknown command: " + line);
                }
              }
            }));
          }
        }).listen(DEFAULT_PORT);
      }
    });

    if (block) {
      while (true) {
        try {
          mainLatch.await();
          break;
        } catch (InterruptedException ignore) {
        }
      }
    }
  }

  public void stop(final Handler<Void> doneHandler) {
    VertxInternal.instance.executeOnContext(serverContextID, new Runnable() {
      public void run() {
        server.close(doneHandler);
        server = null;
        appMeta.clear();
        apps.clear();
        mainLatch.countDown();
      }
    });

  }

  private void parseDeploy(NetSocket socket, String line) {
    String[] parts = line.trim().split(" ");
    if (parts.length == 6 && parts[1].equalsIgnoreCase("java")) {
      String name = parts[2];
      String url = parts[3];
      String mainClass = parts[4];
      String sinstances = parts[5];
      try {
        int instances = Integer.parseInt(sinstances);
        String error = deploy(name, url, mainClass, instances);
        if (error != null) {
          log.error(error);
          socket.write("ERR: " + error + "\n");
        } else {
          socket.write("OK\n");
        }
      } catch (NumberFormatException e) {
        log.error("Invalid number of instances: " + sinstances);
      }

    } else {
      log.error("Invalid syntax:" + line);
    }
  }

  private void parseUndeploy(NetSocket socket, String line) {
    String[] parts = line.trim().split(" ");
    if (parts.length == 2) {
      String name = parts[1];
      String error = undeploy(name);
      if (error != null) {
        log.error(error);
        socket.write("ERR: " + error + "\n");
      } else {
        socket.write("OK\n");
      }
    } else {
      log.error("Invalid syntax:" + line);
    }
  }

  private void addApp(String name, VertxApp app) {
    List<AppHolder> list = apps.get(name);
    if (list == null) {
      list = new ArrayList<>();
      apps.put(name, list);
    }
    list.add(new AppHolder(Vertx.instance.getContextID(), app));
  }

  private synchronized String deploy(final String name, String surl, String mainClass, int instances) {
    log.info("Deploying name : " + name + " url: " + surl + " main class: " + mainClass);

    if (appMeta.containsKey(name)) {
      return "This is already a deployed application with name: " + name;
    } else {
      for (int i = 0; i < instances; i++) {
        try {
          URL url = new URL(surl);
          //Each instance has its own classloader
          URLClassLoader cl = new URLClassLoader(new URL[] { url });
          try {
            Class clazz = cl.loadClass(mainClass);
            if (!VertxApp.class.isAssignableFrom(clazz)) {
              return mainClass + " does not implement VertxApp";
            } else {
              try {
                final VertxApp app = (VertxApp)clazz.newInstance();

                Vertx.instance.go(new Runnable() {
                  public void run() {
                    addApp(name, app);
                    app.start();
                  }
                });
              } catch (Exception e) {
                return "Failed to instantiate class: " + clazz;
              }
            }
          } catch (ClassNotFoundException e) {
            return "Cannot find class: " + mainClass;
          }

        } catch (MalformedURLException e) {
          return "Invalid url: " + surl;
        }
      }
      appMeta.put(name, new AppMetaData(surl, mainClass));
      return null;
    }
  }

  private synchronized String undeploy(String name) {
    log.info("Undeploying all instances of application: " + name);
    List<AppHolder> list = apps.get(name);
    for (final AppHolder holder: list) {
      VertxInternal.instance.executeOnContext(holder.contextID, new Runnable() {
        public void run() {
          holder.app.stop();
        }
      });
    }
    appMeta.remove(name);
    apps.remove(name);
    return null;
  }

  private static class AppMetaData {
    final String url;
    final String mainClass;

    private AppMetaData(String url, String mainClass) {
      this.url = url;
      this.mainClass = mainClass;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AppMetaData that = (AppMetaData) o;

      if (mainClass != null ? !mainClass.equals(that.mainClass) : that.mainClass != null) return false;
      if (url != null ? !url.equals(that.url) : that.url != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = url != null ? url.hashCode() : 0;
      result = 31 * result + (mainClass != null ? mainClass.hashCode() : 0);
      return result;
    }
  }

  private static class AppHolder {
    final long contextID;
    final VertxApp app;

    private AppHolder(long contextID, VertxApp app) {
      this.contextID = contextID;
      this.app = app;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AppHolder appHolder = (AppHolder) o;

      if (contextID != appHolder.contextID) return false;
      if (app != null ? !app.equals(appHolder.app) : appHolder.app != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = (int) (contextID ^ (contextID >>> 32));
      result = 31 * result + (app != null ? app.hashCode() : 0);
      return result;
    }
  }
}
