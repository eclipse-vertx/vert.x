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

  private final Map<String, Deployment> deployments = new HashMap();
  private final Map<Long, String> contextDeploymentNameMap = new ConcurrentHashMap<>();
  private final Map<Long, VerticleHolder> contextVerticleMap = new ConcurrentHashMap<>();

  private CountDownLatch stopLatch = new CountDownLatch(1);

  private static class Deployment {
    final VerticleFactory factory;
    final JsonObject config;
    final URL[] urls;
    final List<VerticleHolder> verticles = new ArrayList<>();
    final List<String> childDeployments = new ArrayList<>();
    final String parentDeploymentName;

    private Deployment(VerticleFactory factory, JsonObject config, URL[] urls, String parentDeploymentName) {
      this.factory = factory;
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
    return contextID == null ? null : contextDeploymentNameMap.get(contextID);
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

  public Logger getLogger() {
    VerticleHolder holder = contextVerticleMap.get(Vertx.instance.getContextID());
    if (holder != null) {
      return holder.logger;
    } else {
      return null;
    }
  }

  public void reportException(Throwable t) {
    String deploymentName = getDeploymentName();
    if (deploymentName != null) {
      Deployment deployment = deployments.get(deploymentName);
      if (deployment != null) {
        deployment.factory.reportException(t);
        return;
      }
    }
    log.error("Unhandled exception", t);
  }

  public synchronized String deploy(boolean worker, String name, final String main,
                                    final JsonObject config, final URL[] urls,
                                    int instances,
                                    final Handler<Void> doneHandler)
  {
    if (deployments.containsKey(name)) {
      throw new IllegalStateException("There is already a deployment with name: " + name);
    }

    //Infer the main type

    VerticleType type = VerticleType.JAVA;
    if (main.endsWith(".js")) {
      type = VerticleType.JS;
    } else if (main.endsWith(".rb")) {
      type = VerticleType.RUBY;
    } else if (main.endsWith(".groovy")) {
      type = VerticleType.GROOVY;
    }

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
    Deployment deployment = new Deployment(verticleFactory, config == null ? null : config.copy(), urls, parentDeploymentName);
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
            doUndeploy(deploymentName, doneHandler);
            return;
          }

          try {
            addVerticle(deploymentName, verticle);
            verticle.start();
          } catch (Throwable t) {
            reportException(t);
            doUndeploy(deploymentName, doneHandler);
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
    public synchronized void handle() {
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
    doUndeploy(name, doneHandler);
  }

  class UndeployCount {
    int count;
    int required;
    Handler<Void> doneHandler;

    synchronized void undeployed() {
      count++;
      checkDone();
    }

    synchronized void incRequired() {
      required++;
    }

    synchronized void setHandler(Handler<Void> doneHandler) {
      this.doneHandler = doneHandler;
      checkDone();
    }

    void checkDone() {
      if (doneHandler != null && count == required) {
        doneHandler.handle(null);
      }
    }

  }

  private void doUndeploy(String name, final Handler<Void> doneHandler) {
    UndeployCount count = new UndeployCount();
    doUndeploy(name, count);
    if (doneHandler != null) {
      count.setHandler(doneHandler);
    }
  }

  private void doUndeploy(String name, final UndeployCount count) {
    final Deployment deployment = deployments.remove(name);

    // Depth first - undeploy children first
    for (String childDeployment: deployment.childDeployments) {
      doUndeploy(childDeployment, count);
    }

    if (!deployment.verticles.isEmpty()) {

      for (final VerticleHolder holder: deployment.verticles) {
        count.incRequired();
        VertxInternal.instance.executeOnContext(holder.contextID, new Runnable() {
          public void run() {
            VertxInternal.instance.setContextID(holder.contextID);
            try {
              holder.verticle.stop();
            } catch (Throwable t) {
              reportException(t);
            }
            //FIXME - we need to destroy the context, but not until after the deployment has fully stopped which may
            //be asynchronous, e.g. if the deployment needs to close servers
            //VertxInternal.instance.destroyContext(holder.contextID);
            count.undeployed();

            // Remove context mapping
            contextDeploymentNameMap.remove(holder.contextID);
            contextVerticleMap.remove(holder.contextID);

            Logger.removeLogger(holder.loggerName);
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

  // Must be synchronized since called directly from different thread
  private synchronized void addVerticle(String name, Verticle verticle) {
    Deployment deployment = deployments.get(name);
    String loggerName = name + "-" + deployment.verticles.size();
    Logger logger = Logger.getLogger(loggerName);
    Long contextID = Vertx.instance.getContextID();
    VerticleHolder holder = new VerticleHolder(contextID, verticle,
                                               loggerName, logger);
    deployment.verticles.add(holder);
    contextDeploymentNameMap.put(contextID, name);
    contextVerticleMap.put(contextID, holder);
  }

  private static class VerticleHolder {
    final long contextID;
    final Verticle verticle;
    final String loggerName;
    final Logger logger;

    private VerticleHolder(long contextID, Verticle verticle, String loggerName,
                           Logger logger) {
      this.contextID = contextID;
      this.verticle = verticle;
      this.loggerName = loggerName;
      this.logger = logger;
    }
  }
}
