package org.vertx.java.core.app;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.app.groovy.GroovyVerticleFactory;
import org.vertx.java.core.app.java.JavaVerticleFactory;
import org.vertx.java.core.app.jruby.JRubyVerticleFactory;
import org.vertx.java.core.app.rhino.RhinoVerticleFactory;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VerticleManager {

  private static final Logger log = Logger.getLogger(VerticleManager.class);

  public static VerticleManager instance = new VerticleManager();

  private Map<String, Deployment> deployments = new HashMap();
  private final Map<Long, String> contextMap = new ConcurrentHashMap<>();

  private CountDownLatch stopLatch = new CountDownLatch(1);

  private static class Deployment {
    final JsonObject config;
    final URL[] urls;
    final List<VerticleHolder> verticles = new ArrayList<>();
    final List<String> childDeployments = new ArrayList<>();
    final String parentDeploymentName;

    private Deployment(JsonObject config, URL[] urls, String parentDeploymentName) {
      this.config = config;
      this.urls = urls;
      this.parentDeploymentName = parentDeploymentName;
    }
  }

  private VerticleManager() {
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

  public JsonObject getConfig() {
    String deploymentName = getDeploymentName();
    if (deploymentName != null) {
      Deployment deployment = deployments.get(deploymentName);
      if (deployment != null) {
        return deployment.config;
      }
    }
    return null;
  }

  public String getDeploymentName() {
    Long contextID = Vertx.instance.getContextID();
    return contextID == null ? null : contextMap.get(contextID);
  }

  public URL[] getDeploymentURLs() {
    String deploymentName = getDeploymentName();
    if (deploymentName != null) {
      Deployment deployment = deployments.get(deploymentName);
      if (deployment != null) {
        return deployment.urls;
      }
    }
    return null;
  }

  public synchronized String deploy(boolean worker, String name, final String main,
                                    final JsonObject config, final URL[] urls,
                                    int instances,
                                    final Handler<Void> doneHandler)
  {
    if (deployments.containsKey(name)) {
      throw new IllegalStateException("There is already a deployment with name: " + name);
    }

    //final String path = thePath == null ? "." : thePath;

    //Infer the main type

    VerticleType type = VerticleType.JAVA;
    if (main.endsWith(".js")) {
      type = VerticleType.JS;
    } else if (main.endsWith(".rb")) {
      type = VerticleType.RUBY;
    } else if (main.endsWith(".groovy")) {
      type = VerticleType.GROOVY;
    }

    // Convert to URL[]

//    String[] parts;
//    if (path.contains(":")) {
//      parts = path.split(":");
//    } else {
//      parts = new String[] { path };
//    }
//    int index = 0;
//    final URL[] urls = new URL[parts.length];
//    for (String part: parts) {
//      File file = new File(part);
//      part = file.getAbsolutePath();
//      if (!part.endsWith(".jar") && !part.endsWith(".zip") && !part.endsWith("/")) {
//        //It's a directory - need to add trailing slash
//        part += "/";
//      }
//      URL url;
//      try {
//        url = new URL("file://" + part);
//      } catch (MalformedURLException e) {
//        throw new IllegalArgumentException("Invalid path: " + path) ;
//      }
//      urls[index++] = url;
//    }

    final String deploymentName = name == null ?  "deployment-" + UUID.randomUUID().toString() : name;

    log.debug("Deploying name : " + deploymentName  + " main: " + main +
                 " instances: " + instances);

    final VerticleFactory verticleFactory;
      switch (type) {
        case JAVA:
          verticleFactory = new JavaVerticleFactory();
          break;
        case RUBY:
          verticleFactory = new JRubyVerticleFactory();
          break;
        case JS:
          verticleFactory = new RhinoVerticleFactory();
          break;
        case GROOVY:
          verticleFactory = new GroovyVerticleFactory();
          break;
        default:
          throw new IllegalArgumentException("Unsupported type: " + type);
      }

    final int instCount = instances;

    class AggHandler {
      final AtomicInteger count = new AtomicInteger(0);

      void started() {
        if (count.incrementAndGet() == instCount) {
          if (doneHandler != null) {
            doneHandler.handle(null);
          }
        }
      }
    }

    final AggHandler aggHandler = new AggHandler();

    String parentDeploymentName = getDeploymentName();
    Deployment deployment = new Deployment(config == null ? null : config.copy(), urls, parentDeploymentName);
    deployments.put(deploymentName, deployment);
    if (parentDeploymentName != null) {
      Deployment parent = deployments.get(parentDeploymentName);
      parent.childDeployments.add(deploymentName);
    }

    for (int i = 0; i < instances; i++) {

      // Launch the verticle instance

      Runnable runner = new Runnable() {
        public void run() {

          Verticle verticle;
          try {
            verticle = verticleFactory.createVerticle(main, new ParentLastURLClassLoader(urls, getClass()
                .getClassLoader()));
          } catch (Throwable t) {
            log.error("Failed to create verticle", t);
            internalUndeploy(deploymentName, doneHandler);
            return;
          }

          try {
            addVerticle(deploymentName, verticle);
            verticle.start();
          } catch (Throwable t) {
            log.error("Unhandled exception in verticle start", t);
            internalUndeploy(deploymentName, doneHandler);
          }
          aggHandler.started();
        }
      };

      if (worker) {
        VertxInternal.instance.startInBackground(runner);
      } else {
        VertxInternal.instance.startOnEventLoop(runner);
      }

    }

    return deploymentName;
  }

  private static class AggHandler extends SimpleHandler {
    AggHandler(int count, Handler<Void> doneHandler) {
      this.count = count;
      this.doneHandler = doneHandler;
    }
    int count;
    Handler<Void> doneHandler;
    public void handle() {
      if (--count == 0) {
        doneHandler.handle(null); // All undeployed
      }
    }
  }

  public synchronized void undeployAll(final Handler<Void> doneHandler) {
    if (deployments.isEmpty()) {
      doneHandler.handle(null);
    } else {
      AggHandler aggHandler = new AggHandler(deployments.size(), doneHandler);
      Set<String> names = new HashSet<>(deployments.keySet()); // Avoid comod exception
      for (String name: names) {
        undeploy(name, aggHandler);
      }
    }
  }

  public synchronized void undeploy(String name, final Handler<Void> doneHandler) {
    if (deployments.get(name) == null) {
      throw new IllegalArgumentException("There is no deployment with name " + name);
    }
    internalUndeploy(name, doneHandler);
  }

  private void internalUndeploy(String name, final Handler<Void> doneHandler) {
    Deployment deployment = deployments.remove(name);

    // Depth first - undeploy children first        TODO
//    for (String childDeployment: deployment.childDeployments) {
//      internalUndeploy(childDeployment, null); //TODO agg handler doesn't wait for all children
//    }

    if (!deployment.verticles.isEmpty()) {
      final AggHandler aggHandler = doneHandler == null ? null : new AggHandler(deployment.verticles.size(), doneHandler);

      for (final VerticleHolder holder: deployment.verticles) {
        VertxInternal.instance.executeOnContext(holder.contextID, new Runnable() {
          public void run() {
            VertxInternal.instance.setContextID(holder.contextID);
            try {
              holder.verticle.stop();
            } catch (Exception e) {
              log.error("Unhandled exception in verticle stop", e);
            }
            //FIXME - we need to destroy the context, but not until after the deployment has fully stopped which may
            //be asynchronous, e.g. if the deployment needs to close servers
            //VertxInternal.instance.destroyContext(holder.contextID);
            if (aggHandler != null) {
              aggHandler.handle(null);
            }

            // Remove context mapping
            contextMap.remove(holder.contextID);
          }
        });
      }
    }

    if (deployment.parentDeploymentName != null) {
      Deployment parent = deployments.get(deployment.parentDeploymentName);
      if (parent != null) {
        parent.childDeployments.remove(name);
      }
    }
  }


  public synchronized Map<String, Integer> listInstances() {
    Map<String, Integer> map = new HashMap<>();
    for (Map.Entry<String, Deployment> entry: deployments.entrySet()) {
      map.put(entry.getKey(), entry.getValue().verticles.size());
    }
    return map;
  }

  // Must be synchronized since called directlyfrom different thread
  private synchronized void addVerticle(String name, Verticle verticle) {
    Deployment deployment = deployments.get(name);
    deployment.verticles.add(new VerticleHolder(Vertx.instance.getContextID(), verticle));
    contextMap.put(Vertx.instance.getContextID(), name);
  }

  private static class VerticleHolder {
    final long contextID;
    final Verticle verticle;

    private VerticleHolder(long contextID, Verticle verticle) {
      this.contextID = contextID;
      this.verticle = verticle;
    }
  }
}
