/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.deploy.impl;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.impl.BlockingAction;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.Container;
import org.vertx.java.deploy.ModuleRepository;
import org.vertx.java.deploy.Verticle;
import org.vertx.java.deploy.VerticleFactory;

/**
 *
 * This class could benefit from some refactoring
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VerticleManager implements ModuleReloader {

  private static final Logger log = LoggerFactory.getLogger(VerticleManager.class);

  private final VertxInternal vertx;
  // deployment name --> deployment
  private final Map<String, Deployment> deployments = new ConcurrentHashMap<>();
  // The user mods dir
  private final File modRoot;
  private final CountDownLatch stopLatch = new CountDownLatch(1);
  private Map<String, String> factoryNames = new HashMap<>();
  private final Redeployer redeployer;
  
  // Allow multiple repositories to be registered
  private final List<ModuleRepository> repositories = new ArrayList<>();

  public VerticleManager(VertxInternal vertx) {
    this(vertx, null);
  }

  public VerticleManager(VertxInternal vertx, String repo) {
  	this(vertx, repo, null);
  }

  public VerticleManager(VertxInternal vertx, String repo, String modDir) {
    this.vertx = vertx;
    VertxLocator.vertx = vertx;
    VertxLocator.container = new Container(this);
    if (modDir == null) {
    	modDir = System.getProperty("vertx.mods");
    }
    if (modDir == null || modDir.trim().equals("")) {
    	modDir = "mods";
    } 
    modRoot = new File(modDir);
    if (modRoot.exists() && !modRoot.isDirectory()) {
    	throw new RuntimeException("modRoot must be a directory: " + modRoot.getAbsolutePath());
    }
   
    // Always initial with at least one repository
    ModuleRepository defRepo = newModuleRepository(vertx, repo, modRoot);
    if (defRepo == null) {
    	throw new NullPointerException("newModuleRepository() must not return null");
    }
    this.repositories.add(defRepo);
    
    this.redeployer = new Redeployer(vertx, modRoot, this);

    try (InputStream is = getClass().getClassLoader().getResourceAsStream("langs.properties")) {
      if (is == null) {
        log.warn("No language mappings found!");
      } else {
        Properties props = new Properties();
        props.load(new BufferedInputStream(is));
        Enumeration<?> en = props.propertyNames();
        while (en.hasMoreElements()) {
          String propName = (String)en.nextElement();
          factoryNames.put(propName, props.getProperty(propName));
        }
      }
    } catch (IOException e) {
      log.error("Failed to load langs.properties: " + e.getMessage());
    }
  }

  /**
   * Allow subclasses to provide their own default ModuleRepository
   * 
   * @param vertx
   * @param repo
   * @param modRoot
   * @return
   */
  protected ModuleRepository newModuleRepository(VertxInternal vertx, String repo, File modRoot) {
    return new DefaultModuleRepository(vertx, repo, modRoot);
  }

  /**
   * @return The list of registered repositories
   */
  public final List<ModuleRepository> getModuleRepositories() {
  	return this.repositories;
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
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.config;
  }

  public String getDeploymentName() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.deployment.name;
  }

  public URL[] getDeploymentURLs() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.deployment.urls;
  }

  public File getDeploymentModDir() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.deployment.modDir;
  }

  public Logger getLogger() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.logger;
  }

  public void deployVerticle(final boolean worker, final String main,
                             final JsonObject config, final URL[] urls,
                             final int instances, final File currentModDir,
                             final String includes,
                             final Handler<String> doneHandler) {

    BlockingAction<Void> deployModuleAction = new BlockingAction<Void>(vertx, null) {
      @Override
      public Void action() throws Exception {
        doDeployVerticle(worker, main, config, urls, instances, currentModDir,
            includes, wrapDoneHandler(doneHandler));
        return null;
      }
    };

    deployModuleAction.run();
  }

  private Handler<String> wrapDoneHandler(final Handler<String> doneHandler) {
    if (doneHandler == null) {
      return null;
    }
    final Context context = vertx.getContext();
    return new Handler<String>() {
      @Override
      public void handle(final String deploymentID) {
        if (context == null) {
          doneHandler.handle(deploymentID);
        } else {
          context.execute(new Runnable() {
            public void run() {
              doneHandler.handle(deploymentID);
            }
          });
        }
      }
    };
  }

  private void doDeployVerticle(boolean worker, final String main,
                                   final JsonObject config, final URL[] urls,
                                   int instances, File currentModDir,
                                   String includes, Handler<String> doneHandler)
  {
    checkWorkerContext();
    URL[] theURLs;
    // The user has specified a list of modules to include when deploying this verticle
    // so we walk the tree of modules adding tree of includes to classpath
    if (includes != null) {
      String[] includedMods = parseIncludes(includes, null);
      List<URL> includedURLs = new ArrayList<>(Arrays.asList(urls));
      for (String includedMod: includedMods) {
        File modDir = new File(modRoot, includedMod);
        JsonObject conf;
        inner: while (true) {
          conf = new ModuleConfig(modDir, includedMod).json();
          if (conf == null) {
            // Try and install the module
            if (!doInstallMod(includedMod)) {
              callDoneHandler(doneHandler, null);
            }
          } else {
            break inner;
          }
        }
        Map<String, String> includedJars = new HashMap<>();
        Set<String> includedModules = new HashSet<>();
        includedURLs = processIncludes(main, includedURLs, includedMod, modDir, conf,
            includedJars, includedModules);
      }
      theURLs = includedURLs.toArray(new URL[includedURLs.size()]);
    } else {
      theURLs = urls;
    }
    doDeploy(null, false, worker, main, null, config, theURLs, instances, currentModDir, doneHandler);
  }

  public synchronized void undeployAll(final Handler<Void> doneHandler) {
    final CountingCompletionHandler count = new CountingCompletionHandler(vertx.getOrAssignContext());
    if (!deployments.isEmpty()) {
      // We do it this way since undeploy is itself recursive - we don't want
      // to attempt to undeploy the same verticle twice if it's a child of
      // another
      while (!deployments.isEmpty()) {
        String name = deployments.keySet().iterator().next();
        count.incRequired();
        undeploy(name, new SimpleHandler() {
          public void handle() {
            count.complete();
          }
        });
      }
    }
    count.setHandler(doneHandler);
  }

  public synchronized Map<String, Integer> listInstances() {
    Map<String, Integer> map = new HashMap<>();
    for (Map.Entry<String, Deployment> entry: deployments.entrySet()) {
      map.put(entry.getKey(), entry.getValue().verticles.size());
    }
    return map;
  }

  public void deployMod(final String modName, final JsonObject config,
                        final int instances, final File currentModDir, final Handler<String> doneHandler) {

    BlockingAction<Void> deployModuleAction = new BlockingAction<Void>(vertx, null) {

      @Override
      public Void action() throws Exception {
        doDeployMod(false, null, modName, config, instances, currentModDir, wrapDoneHandler(doneHandler));
        return null;
      }
    };

    deployModuleAction.run();
  }

  public void installMod(final String moduleName) {
    final CountDownLatch latch = new CountDownLatch(1);
    AsyncResultHandler<Void> handler = new AsyncResultHandler<Void>() {
      public void handle(AsyncResult<Void> res) {
        if (res.succeeded()) {
          latch.countDown();
        } else {
          res.exception.printStackTrace();
        }
      }
    };

    BlockingAction<Void> deployModuleAction = new BlockingAction<Void>(vertx, handler) {
      @Override
      public Void action() throws Exception {
        doInstallMod(moduleName);
        return null;
      }
    };

    deployModuleAction.run();

    while (true) {
      try {
        if (!latch.await(30, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to install module");
        }
        break;
      } catch (InterruptedException ignore) {
      }
    }
  }

  public void uninstallMod(String moduleName) {
    log.info("Uninstalling module " + moduleName + " from directory " + modRoot);
    File modDir = new File(modRoot, moduleName);
    if (!modDir.exists()) {
      log.error("Cannot find module to uninstall");
    } else {
      try {
        vertx.fileSystem().deleteSync(modDir.getAbsolutePath(), true);
        log.info("Module " + moduleName + " successfully uninstalled");
      } catch (Exception e) {
        log.error("Failed to delete directory: " + e.getMessage());
      }
    }
  }

  private void checkWorkerContext() {
    Thread t = Thread.currentThread();
    if (!t.getName().startsWith("vert.x-worker-thread")) {
      throw new IllegalStateException("Not a worker thread");
    }
  }

  private void doDeployMod(final boolean redeploy, final String depName, final String modName,
                             final JsonObject config,
                             final int instances, final File currentModDir,
                             final Handler<String> doneHandler) {
    checkWorkerContext();

    File modDir = new File(modRoot, modName);
    ModuleConfig conf = new ModuleConfig(modDir, modName);
    if (conf.json() != null) {
      String main = conf.main();
      if (main == null) {
        log.error("Runnable module " + modName + " mod.json must contain a \"main\" field");
        callDoneHandler(doneHandler, null);
        return;
      }
      Boolean worker = conf.worker();
      if (worker == null) {
        worker = Boolean.FALSE;
      }
      Boolean preserveCwd = conf.preserveCwd();
      if (preserveCwd == null) {
        preserveCwd = Boolean.FALSE;
      }
      // If preserveCwd then use the current module directory instead, or the cwd if not in a module
      File modDirToUse = preserveCwd ? currentModDir : modDir;

      List<URL> urls = processIncludes(modName, new ArrayList<URL>(), modName, modDir, conf.json(),
                                       new HashMap<String, String>(), new HashSet<String>());
      if (urls == null) {
        callDoneHandler(doneHandler, null);
        return;
      }

      Boolean ar = conf.autoRedeploy();
      final boolean autoRedeploy = ar == null ? false : ar;

      doDeploy(depName, autoRedeploy, worker, main, modName, config,
               urls.toArray(new URL[urls.size()]), instances, modDirToUse, new Handler<String>() {
        @Override
        public void handle(String deploymentID) {
          if (deploymentID != null && !redeploy && autoRedeploy) {
            redeployer.moduleDeployed(deployments.get(deploymentID));
          }
          callDoneHandler(doneHandler, deploymentID);
        }
      });
    } else {
      if (doInstallMod(modName)) {
        doDeployMod(redeploy, depName, modName, config, instances, currentModDir, doneHandler);
      } else {
        callDoneHandler(doneHandler, null);
      }
    }
  }

  // We walk through the graph of includes making sure we only add each one once
  // We keep track of what jars have been included so we can flag errors if paths
  // are included more than once
  // We make sure we only include each module once in the case of loops in the
  // graph
  private List<URL> processIncludes(String runModule, List<URL> urls, String modName, File modDir,
                                    JsonObject conf,
                                    Map<String, String> includedJars,
                                    Set<String> includedModules) {
    checkWorkerContext();
    // Add the urls for this module
    try {
      urls.add(modDir.toURI().toURL());
      File libDir = new File(modDir, "lib");
      if (libDir.exists()) {
        File[] jars = libDir.listFiles();
        for (File jar: jars) {
          URL jarURL = jar.toURI().toURL();
          String sjarURL = jarURL.toString();
          String jarName = sjarURL.substring(sjarURL.lastIndexOf("/") + 1);
          String prevMod = includedJars.get(jarName);
          if (prevMod != null) {
            log.warn("Warning! jar file " + jarName + " is contained in module " +
                     prevMod + " and also in module " + modName +
                     " which are both included (perhaps indirectly) by module " +
                     runModule);
          }
          includedJars.put(jarName, modName);
          urls.add(jarURL);
        }
      }
    } catch (MalformedURLException e) {
      //Won't happen
      log.error("malformed url", e);
      return null;
    }

    includedModules.add(modName);

    String sincludes = conf.getString("includes");
    if (sincludes != null) {
      String[] sarr = parseIncludes(sincludes, modName);
      for (String include: sarr) {
        if (includedModules.contains(include)) {
          // Ignore - already included this one
        } else {
          File newmodDir = new File(modRoot, include);
          inner: while (true) {
            JsonObject newconf = new ModuleConfig(newmodDir, include).json();
            if (newconf != null) {
              urls = processIncludes(runModule, urls, include, newmodDir, newconf,
                                     includedJars, includedModules);
              if (urls == null) {
                return null;
              }
              break inner;
            } else {
              // Module not installed - let's try to install it
              if (!doInstallMod(include)) {
                return null;
              }
            }
          }
        }
      }
    }

    return urls;
  }

  private String[] parseIncludes(String sincludes, String modName) {
    sincludes = sincludes.trim();
    if ("".equals(sincludes)) {
      log.error("Empty include string " + ((modName != null) ? " in module " : ""));
      return null;
    }
    String[] arr = sincludes.split(",");
    if (arr != null) {
      for (int i = 0; i < arr.length; i++) {
        arr[i] = arr[i].trim();
      }
    }
    return arr;
  }

  /**
   * Try all registered repositories
   * 
   * @param moduleName
   * @return
   */
  private boolean doInstallMod(final String moduleName) {
  	for (ModuleRepository repo: this.repositories) {
    	if (repo.installMod(moduleName) == true) {
    		return true;
    	}
  	}
  	return false;
  }

  // We calculate a path adjustment that can be used by the fileSystem object
  // so that the *effective* working directory can be the module directory
  // this allows modules to read and write the file system as if they were
  // in the module dir, even though the actual working directory will be
  // wherever vertx run or vertx start was called from
  private void setPathAdjustment(File modDir) {
    Path cwd = Paths.get(".").toAbsolutePath().getParent();
    Path pmodDir = Paths.get(modDir.getAbsolutePath());
    Path relative = cwd.relativize(pmodDir);
    vertx.getContext().setPathAdjustment(relative);
  }

  private void callDoneHandler(Handler<String> doneHandler, String deploymentID) {
    if (doneHandler != null) {
      doneHandler.handle(deploymentID);
    }
  }

  private void doDeploy(String depName,
                          boolean autoRedeploy,
                          boolean worker, final String main,
                          final String modName,
                          final JsonObject config, final URL[] urls,
                          int instances,
                          final File modDir,
                          final Handler<String> doneHandler) {
    checkWorkerContext();
    //final AtomicReference<String> deploymentID = new AtomicReference<>();
    final CountDownLatch latch = new CountDownLatch(1);
    final String deploymentName =
        depName != null ? depName : "deployment-" + UUID.randomUUID().toString();

    log.debug("Deploying name : " + deploymentName + " main: " + main +
        " instances: " + instances);

    int dotIndex = main.lastIndexOf('.');
    String extension = dotIndex > -1 ? main.substring(dotIndex + 1) : null;
    String factoryName = null;
    if (extension != null) {
      factoryName = factoryNames.get(extension);
    }
    if (factoryName == null) {
      // Use the default
      factoryName = factoryNames.get("default");
      if (factoryName == null) {
        throw new IllegalArgumentException("No language mapping found and no default specified in langs.properties");
      }
    }

    final int instCount = instances;

    class AggHandler {
      AtomicInteger count = new AtomicInteger(0);
      boolean failed;

      void done(boolean res) {
        if (!res) {
          failed = true;
        }
        if (count.incrementAndGet() == instCount) {
          String deploymentID = failed ? null : deploymentName;
          callDoneHandler(doneHandler, deploymentID);
        }
      }
    }

    final AggHandler aggHandler = new AggHandler();

    String parentDeploymentName = getDeploymentName();
    final Deployment deployment = new Deployment(deploymentName, modName, instances,
        config == null ? new JsonObject() : config.copy(), urls, modDir, parentDeploymentName,
        autoRedeploy);
    deployments.put(deploymentName, deployment);
    if (parentDeploymentName != null) {
      Deployment parent = deployments.get(parentDeploymentName);
      parent.childDeployments.add(deploymentName);
    }

    // Workers share a single classloader with all instances in a deployment - this
    // enables them to use libraries that rely on caching or statics to share state
    // (e.g. JDBC connection pools)
    final ClassLoader sharedLoader = worker ? new ParentLastURLClassLoader(urls, getClass()
                .getClassLoader()): null;

    for (int i = 0; i < instances; i++) {

      // Launch the verticle instance

      final ClassLoader cl = sharedLoader != null ?
          sharedLoader: new ParentLastURLClassLoader(urls, getClass().getClassLoader());
      Thread.currentThread().setContextClassLoader(cl);

      // We load the VerticleFactory class using the verticle classloader - this allows
      // us to put language implementations in modules

      Class clazz;
      try {
        clazz = cl.loadClass(factoryName);
      } catch (ClassNotFoundException e) {
        log.error("Cannot find class " + factoryName + " to load");
        callDoneHandler(doneHandler, null);
        return;
      }

      final VerticleFactory verticleFactory;
      try {
        verticleFactory = (VerticleFactory)clazz.newInstance();
      } catch (Exception e) {
        log.error("Failed to instantiate VerticleFactory: " + e.getMessage());
        callDoneHandler(doneHandler, null);
        return;
      }

      verticleFactory.init(this);

      Runnable runner = new Runnable() {
        public void run() {

          Verticle verticle = null;
          boolean error = true;

          try {
            verticle = verticleFactory.createVerticle(main, cl);
            error = false;
          } catch (ClassNotFoundException e) {
            log.error("Cannot find verticle " + main);
          } catch (Throwable t) {
            log.error("Failed to create verticle", t);
          }

          if (error) {
            doUndeploy(deploymentName, new SimpleHandler() {
              public void handle() {
                aggHandler.done(false);
              }
            });
            return;
          }

          //Inject vertx
          verticle.setVertx(vertx);
          verticle.setContainer(new Container(VerticleManager.this));

          try {
            addVerticle(deployment, verticle, verticleFactory);
            if (modDir != null) {
              setPathAdjustment(modDir);
            }
            verticle.start();
            aggHandler.done(true);
          } catch (Throwable t) {
            t.printStackTrace();
            vertx.reportException(t);
            doUndeploy(deploymentName, new SimpleHandler() {
              public void handle() {
                aggHandler.done(false);
              }
            });
          }

        }
      };

      if (worker) {
        vertx.startInBackground(runner);
      } else {
        vertx.startOnEventLoop(runner);
      }
    }
  }

  // Must be synchronized since called directly from different thread
  private void addVerticle(Deployment deployment, Verticle verticle,
                                        VerticleFactory factory) {
    String loggerName = "org.vertx.deployments." + deployment.name + "-" + deployment.verticles.size();
    Logger logger = LoggerFactory.getLogger(loggerName);
    Context context = vertx.getContext();
    VerticleHolder holder = new VerticleHolder(deployment, context, verticle,
                                               loggerName, logger, deployment.config,
                                               factory);
    deployment.verticles.add(holder);
    context.setDeploymentHandle(holder);
  }

  private VerticleHolder getVerticleHolder() {
    Context context = vertx.getContext();
    if (context != null) {
      VerticleHolder holder = (VerticleHolder)context.getDeploymentHandle();
      return holder;
    } else {
      return null;
    }
  }

  private void doUndeploy(String name, final Handler<Void> doneHandler) {
     CountingCompletionHandler count = new CountingCompletionHandler(vertx.getOrAssignContext());
     doUndeploy(name, count);
     if (doneHandler != null) {
       count.setHandler(doneHandler);
     }
  }

  private void doUndeploy(String name, final CountingCompletionHandler count) {

    final Deployment deployment = deployments.remove(name);

    // Depth first - undeploy children first
    for (String childDeployment: deployment.childDeployments) {
      doUndeploy(childDeployment, count);
    }

    if (!deployment.verticles.isEmpty()) {
      for (final VerticleHolder holder: deployment.verticles) {
        count.incRequired();
        holder.context.execute(new Runnable() {
          public void run() {
            try {
              holder.verticle.stop();
            } catch (Throwable t) {
              vertx.reportException(t);
            }
            count.complete();
            LoggerFactory.removeLogger(holder.loggerName);
            holder.context.runCloseHooks();
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

  public void reloadModules(final Set<Deployment> deps) {
    for (final Deployment deployment: deps) {
      if (deployments.containsKey(deployment.name)) {
        doUndeploy(deployment.name, new SimpleHandler() {
          public void handle() {
            redeploy(deployment);
          }
        });
      } else {
        // This will be the case if the previous deployment failed, e.g.
        // a code error in a user verticle
        redeploy(deployment);
      }
    }
  }

  public synchronized void undeploy(String name, final Handler<Void> doneHandler) {
    final Deployment dep = deployments.get(name);
    if (dep == null) {
      throw new IllegalArgumentException("There is no deployment with name " + name);
    }
    Handler<Void> wrappedHandler = new SimpleHandler() {
      public void handle() {
        if (dep.modName != null && dep.autoRedeploy) {
          redeployer.moduleUndeployed(dep);
        }
        if (doneHandler != null) {
          doneHandler.handle(null);
        }
      }
    };
    doUndeploy(name, wrappedHandler);
  }

  private void redeploy(final Deployment deployment) {
    // Has to occur on a worker thread
    AsyncResultHandler<String> handler = new AsyncResultHandler<String>() {
      public void handle(AsyncResult<String> res) {
        if (!res.succeeded()) {
          res.exception.printStackTrace();
        }
      }
    };
    BlockingAction<String> redeployAction = new BlockingAction<String>(vertx, handler) {
      @Override
      public String action() throws Exception {
        doDeployMod(true, deployment.name, deployment.modName, deployment.config, deployment.instances,
            null, null);
        return null;
      }
    };
    redeployAction.run();
  }

  public void stop() {
    redeployer.close();
  }

}
