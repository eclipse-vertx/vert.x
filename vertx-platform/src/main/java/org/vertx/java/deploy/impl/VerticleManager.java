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


import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.impl.BlockingAction;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.Container;
import org.vertx.java.deploy.Verticle;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 *
 * This class could benefit from some refactoring
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public class VerticleManager implements ModuleReloader {

  private static final Logger log = LoggerFactory.getLogger(VerticleManager.class);
  private static final String REPO_URI_ROOT = "/vertx-mods/mods/";
  private static final String DEFAULT_REPO_HOST = "vert-x.github.com";
  private static final int BUFFER_SIZE = 4096;
  private static final String HTTP_PROXY_HOST_PROP_NAME = "http.proxyHost";
  private static final String HTTP_PROXY_PORT_PROP_NAME = "http.proxyPort";
  private static final char COLON = ':';

  private final VertxInternal vertx;
  // deployment name --> deployment
  private final Map<String, Deployment> deployments = new ConcurrentHashMap<>();
  // The user mods dir
  private final File modRoot;
  private final CountDownLatch stopLatch = new CountDownLatch(1);
  private Map<String, LanguageImplInfo> factoryNames = new HashMap<>();
  private final String repoHost;
  private final int repoPort;
  private final String proxyHost;
  private final int proxyPort;
  final ConcurrentMap<String, ModuleReference> modules = new ConcurrentHashMap<>();
  private final Redeployer redeployer;

  private static class LanguageImplInfo {
    final String moduleName;
    final String factoryName;
    private LanguageImplInfo(String moduleName, String factoryName) {
      this.moduleName = moduleName;
      this.factoryName = factoryName;
    }
  }

  public VerticleManager(VertxInternal vertx) {
    this(vertx, null);
  }

  public VerticleManager(VertxInternal vertx, String repo) {
    this.vertx = vertx;
    if (repo != null) {
      int colonIndex = repo.indexOf(COLON);
      if (colonIndex != -1) {
        this.repoHost = repo.substring(0, colonIndex);
        this.repoPort = Integer.parseInt(repo.substring(colonIndex + 1));
      } else {
        this.repoHost = repo;
        this.repoPort = 80;
      }
    } else {
      this.repoHost = DEFAULT_REPO_HOST;
      this.repoPort = 80;
    }
    this.proxyHost = System.getProperty(HTTP_PROXY_HOST_PROP_NAME);
    String tmpPort = System.getProperty(HTTP_PROXY_PORT_PROP_NAME);
    this.proxyPort = tmpPort != null ? Integer.parseInt(tmpPort) : 80;
    VertxLocator.vertx = vertx;
    VertxLocator.container = new Container(this);
    String modDir = System.getProperty("vertx.mods");
    if (modDir != null && !modDir.trim().equals("")) {
      modRoot = new File(modDir);
    } else {
      // Default to local module directory called 'mods'
      modRoot = new File("mods");
    }
    this.redeployer = new Redeployer(vertx, modRoot, this);
    loadFactoryNames();
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

  public String getModuleName() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.deployment.modName;
  }

  public void deployVerticle(final boolean worker, final String main,
                             final JsonObject config, final URL[] urls,
                             final int instances, final File currentModDir,
                             final String includes,
                             final Handler<String> doneHandler) {
    BlockingAction<Void> deployModuleAction = new BlockingAction<Void>(vertx, createHandler(doneHandler)) {
      @Override
      public Void action() throws Exception {
        doDeployVerticle(worker, main, config, urls, instances, currentModDir,
            includes, wrapDoneHandler(doneHandler));
        return null;
      }
    };

    deployModuleAction.run();
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

  public Map<String, Integer> listInstances() {
    Map<String, Integer> map = new HashMap<>();
    for (Map.Entry<String, Deployment> entry: deployments.entrySet()) {
      map.put(entry.getKey(), entry.getValue().verticles.size());
    }
    return map;
  }

  public void deployMod(final String modName, final JsonObject config,
                        final int instances, final File currentModDir, final Handler<String> doneHandler) {
    BlockingAction<Void> deployModuleAction = new BlockingAction<Void>(vertx, createHandler(doneHandler)) {

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

    BlockingAction<Void> installModuleAction = new BlockingAction<Void>(vertx, handler) {
      @Override
      public Void action() throws Exception {
        doInstallMod(moduleName);
        return null;
      }
    };

    installModuleAction.run();

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

  public synchronized void uninstallMod(String moduleName) {
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

  private AsyncResultHandler<Void> createHandler(final Handler<String> doneHandler) {
    return new AsyncResultHandler<Void>() {
      @Override
      public void handle(AsyncResult<Void> ar) {
        if (ar.failed()) {
          log.error("Failed to deploy verticle", ar.exception);
          if (doneHandler != null) {
            doneHandler.handle(null);
          }
        }
      }
    };
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

  // Recurse up through the parent deployments and return the the module name for the first one
  // which has one, or if there is no enclosing module just use the deployment name
  private String getEnclosingModuleName() {
    VerticleHolder holder = getVerticleHolder();
    Deployment dep = holder == null ? null : holder.deployment;
    while (dep != null) {
      if (dep.modName != null) {
        return dep.modName;
      } else {
        String parentDepName = dep.parentDeploymentName;
        if (parentDepName != null) {
          dep = deployments.get(parentDepName);
        } else {
          // Top level - deployed as verticle not module
          // Just use the deployment name
          return dep.name;
        }
      }
    }
    return null;
  }

  private void doDeployVerticle(boolean worker, final String main,
                                final JsonObject config, final URL[] urls,
                                int instances, File currentModDir,
                                String includes, Handler<String> doneHandler)
  {
    checkWorkerContext();

    // There is one module class loader per enclosing module + the name of the verticle.
    // If there is no enclosing module, there is one per top level verticle deployment.
    // E.g. if a module A deploys "foo.js" as a verticle then all instances of foo.js deployed by the enclosing
    // module will share a module class loader
    String depName = genDepName();
    String enclosingModName = getEnclosingModuleName();
    String moduleKey = (enclosingModName == null ? depName : enclosingModName) + "." + main;

    ModuleReference mr = modules.get(moduleKey);
    if (mr == null) {
      mr = new ModuleReference(this, moduleKey, new ModuleClassLoader(urls));
      ModuleReference prev = modules.putIfAbsent(moduleKey, mr);
      if (prev != null) {
        mr = prev;
      }
    }

    if (includes != null) {
      loadIncludedModules(mr, includes);
    }
    doDeploy(depName, false, worker, main, null, config, urls, instances, currentModDir, mr, doneHandler);
  }


  private void checkWorkerContext() {
    Thread t = Thread.currentThread();
    if (!t.getName().startsWith("vert.x-worker-thread")) {
      throw new IllegalStateException("Not a worker thread");
    }
  }

  private void loadFactoryNames() {
    // First try loading them from the langs.properties file
    InputStream is = null;
    try {
      is = getClass().getClassLoader().getResourceAsStream("langs.properties");
      if (is == null) {
        log.warn("No language mappings found!");
      } else {
        Properties props = new Properties();
        props.load(new BufferedInputStream(is));
        loadFactoryNames(props);
      }
    } catch (IOException e) {
      log.error("Failed to load langs.properties: " + e.getMessage());
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (IOException ignore) {
        }
      }
    }

    // Then override any with system properties
    Properties sysProps = new Properties();
    Set<String> propertyNames = System.getProperties().stringPropertyNames();
    for (String propertyName : propertyNames) {
      if (propertyName.startsWith("vertx.langs.")) {
        String lang = propertyName.replaceFirst("vertx.langs.", "");
        String value = System.getProperty(propertyName);
        sysProps.put(lang, value);
      }
    }
    loadFactoryNames(sysProps);
  }

  private void loadFactoryNames(Properties props) {
    Enumeration<?> en = props.propertyNames();
    while (en.hasMoreElements()) {
      String propName = (String)en.nextElement();
      String propVal = props.getProperty(propName);
      // value is made up of an optional module name followed by colon followed by the
      // FQCN of the factory
      int colonIndex = propVal.indexOf(COLON);
      String moduleName;
      String factoryName;
      if (colonIndex != -1) {
        moduleName = propVal.substring(0, colonIndex);
        factoryName = propVal.substring(colonIndex + 1);
      } else {
        moduleName = null;
        factoryName = propVal;
      }
      factoryNames.put(propName, new LanguageImplInfo(moduleName, factoryName));
    }
  }



  private void doDeployMod(final boolean redeploy, final String depName, final String modName,
                           final JsonObject config,
                           final int instances, final File currentModDir,
                           final Handler<String> doneHandler) {
    checkWorkerContext();
    File modDir = new File(modRoot, modName);
    JsonObject conf = loadModuleConfig(modName, modDir);
    if (conf != null) {
      String main = conf.getString("main");
      if (main == null) {
        log.error("Runnable module " + modName + " mod.json must contain a \"main\" field");
        callDoneHandler(doneHandler, null);
        return;
      }
      Boolean worker = conf.getBoolean("worker");
      if (worker == null) {
        worker = Boolean.FALSE;
      }
      Boolean preserveCwd = conf.getBoolean("preserve-cwd");
      if (preserveCwd == null) {
        preserveCwd = Boolean.FALSE;
      }
      // If preserveCwd then use the current module directory instead, or the cwd if not in a module
      File modDirToUse = preserveCwd ? currentModDir : modDir;

      List<URL> urls = getModuleClasspath(modDir);
      if (urls == null) {
        callDoneHandler(doneHandler, null);
        return;
      }

      Boolean ar = conf.getBoolean("auto-redeploy");
      final boolean autoRedeploy = ar == null ? false : ar;

      ModuleReference mr = modules.get(modName);
      if (mr == null) {
        mr = new ModuleReference(this, modName, new ModuleClassLoader(urls.toArray(new URL[urls.size()])));
        ModuleReference prev = modules.putIfAbsent(modName, mr);
        if (prev != null) {
          mr = prev;
        }
      }
      String enclosingModName = getEnclosingModuleName();
      if (enclosingModName != null) {
        //If enclosed in another module then the enclosing module classloader becomes a parent of this one
        ModuleReference parentRef = modules.get(enclosingModName);
        mr.mcl.addParent(parentRef);
        parentRef.incRef();
      }

      // Now load any included modules
      String includes = conf.getString("includes");
      if (includes != null) {
        if (!loadIncludedModules(mr, includes)) {
          callDoneHandler(doneHandler, null);
          return;
        }
      }

      doDeploy(depName, autoRedeploy, worker, main, modName, config,
          urls.toArray(new URL[urls.size()]), instances, modDirToUse, mr, new Handler<String>() {
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

  private JsonObject loadModuleConfig(String modName, File modDir) {
    checkWorkerContext();
    if (modDir.exists()) {
      try (Scanner scanner = new Scanner(new File(modDir, "mod.json")).useDelimiter("\\A")) {
        String conf;
        try {
          conf = scanner.next();
        } catch (NoSuchElementException e) {
          throw new IllegalStateException("Module " + modName + " contains an empty mod.json file");
        }
        JsonObject json;
        try {
          json = new JsonObject(conf);
        } catch (DecodeException e) {
          throw new IllegalStateException("Module " + modName + " mod.json contains invalid json");
        }
        return json;
      } catch (FileNotFoundException e) {
        throw new IllegalStateException("Module " + modName + " does not contain a mod.json file");
      }
    } else {
      return null;
    }
  }

  private boolean loadIncludedModules(ModuleReference mr, String includesString) {
    checkWorkerContext();
    for (String moduleName: parseIncludeString(includesString)) {
      ModuleReference includedMr = modules.get(moduleName);
      if (includedMr == null) {
        File modDir = new File(modRoot, moduleName);
        if (!modDir.exists()) {
          if (!doInstallMod(moduleName)) {
            return false;
          }
        }
        List<URL> urls = getModuleClasspath(modDir);
        includedMr = new ModuleReference(this, moduleName, new ModuleClassLoader(urls.toArray(new URL[urls.size()])));
        ModuleReference prev = modules.putIfAbsent(moduleName, includedMr);
        if (prev != null) {
          includedMr = prev;
        }
        JsonObject conf = loadModuleConfig(moduleName, modDir);
        String includes = conf.getString("includes");
        if (includes != null) {
          loadIncludedModules(includedMr, includes);
        }
      }
      includedMr.incRef();
      mr.mcl.addParent(includedMr);
    }
    return true;
  }

  private List<URL> getModuleClasspath(File modDir) {
    List<URL> urls = new ArrayList<>();
    // Add the urls for this module
    try {
      urls.add(modDir.toURI().toURL());
      File libDir = new File(modDir, "lib");
      if (libDir.exists()) {
        File[] jars = libDir.listFiles();
        for (File jar: jars) {
          URL jarURL = jar.toURI().toURL();
          urls.add(jarURL);
        }
      }
      return urls;
    } catch (MalformedURLException e) {
      //Won't happen
      log.error("malformed url", e);
      return null;
    }
  }

  private String[] parseIncludeString(String sincludes) {
    sincludes = sincludes.trim();
    if ("".equals(sincludes)) {
      log.error("Empty include string");
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

  private boolean doInstallMod(final String moduleName) {
    checkWorkerContext();
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Buffer> mod = new AtomicReference<>();
    HttpClient client = vertx.createHttpClient();
    if (proxyHost != null) {
      client.setHost(proxyHost);
      if (proxyPort != 80) {
        client.setPort(proxyPort);
      } else {
        client.setPort(80);
      }
    } else {
      client.setHost(repoHost);
      client.setPort(repoPort);
    }
    client.exceptionHandler(new Handler<Exception>() {
      public void handle(Exception e) {
        log.error("Unable to connect to repository");
        latch.countDown();
      }
    });
    String uri = REPO_URI_ROOT + moduleName + "/mod.zip";
    String msg = "Attempting to install module " + moduleName + " from http://"
        + repoHost + ":" + repoPort + uri;
    if (proxyHost != null) {
      msg += " Using proxy host " + proxyHost + ":" + proxyPort;
    }
    log.info(msg);
    if (proxyHost != null) {
      uri = new StringBuilder("http://").append(DEFAULT_REPO_HOST).append(uri).toString();
    }
    HttpClientRequest req = client.get(uri, new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        if (resp.statusCode == 200) {
          log.info("Downloading module...");
          resp.bodyHandler(new Handler<Buffer>() {
            public void handle(Buffer buffer) {
              mod.set(buffer);
              latch.countDown();
            }
          });
        } else if (resp.statusCode == 404) {
          log.error("Can't find module " + moduleName + " in repository");
          latch.countDown();
        } else {
          log.error("Failed to download module: " + resp.statusCode);
          latch.countDown();
        }
      }
    });
    if(proxyHost != null){
      req.putHeader("host", proxyHost);
    } else {
      req.putHeader("host", repoHost);
    }
    req.putHeader("user-agent", "Vert.x Module Installer");
    req.end();
    while (true) {
      try {
        if (!latch.await(30, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to download module");
        }
        break;
      } catch (InterruptedException ignore) {
      }
    }
    Buffer modZipped = mod.get();
    if (modZipped != null) {
      return unzipModule(moduleName, modZipped);
    } else {
      return false;
    }
  }

  private boolean unzipModule(final String modName, final Buffer data) {
    checkWorkerContext();

    // We synchronize to prevent a race whereby it tries to unzip the same module at the
    // same time (e.g. deployModule for the same module name has been called in parallel)
    synchronized (modName.intern()) {

      if (!modRoot.exists()) {
        if (!modRoot.mkdir()) {
          log.error("Failed to create directory " + modRoot);
          return false;
        }
      }
      log.info("Installing module into directory '" + modRoot + "'");
      File fdest = new File(modRoot, modName);
      if (fdest.exists()) {
        // This can happen if the same module is requested to be installed
        // at around the same time
        // It's ok if this happens
        return true;
      }
      try {
        InputStream is = new ByteArrayInputStream(data.getBytes());
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is));
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
          if (!entry.getName().startsWith(modName)) {
            log.error("Module must contain zipped directory with same name as module");
            fdest.delete();
            return false;
          }
          if (entry.isDirectory()) {
            new File(modRoot, entry.getName()).mkdir();
          } else {
            int count;
            byte[] buff = new byte[BUFFER_SIZE];
            BufferedOutputStream dest = null;
            try {
              OutputStream fos = new FileOutputStream(new File(modRoot, entry.getName()));
              dest = new BufferedOutputStream(fos, BUFFER_SIZE);
              while ((count = zis.read(buff, 0, BUFFER_SIZE)) != -1) {
                 dest.write(buff, 0, count);
              }
              dest.flush();
            } finally {
              if (dest != null) {
                dest.close();
              }
            }
          }
        }
        zis.close();
      } catch (IOException e) {
        log.error("Failed to unzip module", e);
        return false;
      }
      log.info("Module " + modName +" successfully installed");
      return true;
    }
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

  private String genDepName() {
    return "deployment-" + UUID.randomUUID().toString();
  }

  private void doDeploy(final String depName,
                        boolean autoRedeploy,
                        boolean worker, final String main,
                        final String modName,
                        final JsonObject config, final URL[] urls,
                        int instances,
                        final File modDir,
                        final ModuleReference mr,
                        final Handler<String> doneHandler) {
    checkWorkerContext();

    final String deploymentName =
        depName != null ? depName : genDepName();

    log.debug("Deploying name : " + deploymentName + " main: " + main +
        " instances: " + instances);

    LanguageImplInfo langImplInfo = null;
    int marker = -1;
    if ((marker = main.indexOf(':')) > -1) {
      // prefix
      langImplInfo = factoryNames.get(main.substring(0, marker));
    } else if ((marker = main.lastIndexOf('.')) > -1) {
      // suffix
      langImplInfo = factoryNames.get(main.substring(marker + 1));
    }

    if (langImplInfo == null) {
      // default
      langImplInfo = factoryNames.get("default");
      if (langImplInfo == null) {
        // double check
        throw new IllegalArgumentException("No language mapping found in " + factoryNames + " and no default specified in langs.properties for '" + main + "'");
      }
    }

    // Include the language impl module as a parent of the classloader
    if (langImplInfo.moduleName != null) {
      loadIncludedModules(mr, langImplInfo.moduleName);
    }

    final VerticleFactory verticleFactory;

    try {
      // TODO not one verticle factory per module ref, but one per language per module ref
      verticleFactory = mr.getVerticleFactory(langImplInfo.factoryName, this);
    } catch (Exception e) {
      log.error("Failed to instantiate verticle factory", e);
      doneHandler.handle(null);
      return;
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
        mr, autoRedeploy);
    mr.incRef();
    deployments.put(deploymentName, deployment);

    if (parentDeploymentName != null) {
      Deployment parentDeployment = deployments.get(parentDeploymentName);
      parentDeployment.childDeployments.add(deploymentName);
    }

    ClassLoader oldTCCL = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(mr.mcl);
    try {

      for (int i = 0; i < instances; i++) {

        // Launch the verticle instance

        Runnable runner = new Runnable() {
          public void run() {

            Verticle verticle = null;
            boolean error = true;

            try {
              verticle = verticleFactory.createVerticle(main);
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
    } finally {
      Thread.currentThread().setContextClassLoader(oldTCCL);
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

  private void doUndeploy(String name, final CountingCompletionHandler parentCount) {
    if (name == null) {
      throw new NullPointerException("deployment id is null");
    }
    final Deployment deployment = deployments.remove(name);

    final CountingCompletionHandler count = new CountingCompletionHandler(vertx.getOrAssignContext());
    parentCount.incRequired();

    // Depth first - undeploy children first
    for (String childDeployment: deployment.childDeployments) {
      doUndeploy(childDeployment, count);
    }

    if (!deployment.verticles.isEmpty()) {
      for (final VerticleHolder holder: deployment.verticles) {
        count.incRequired();
        holder.context.execute(new Runnable() {
          public void run() {
            // The closed handler will be called when there are no more outstanding tasks for the context
            // Remember that the vertxStop() method might schedule other async operations and they in turn might schedule
            // others
            // We need to set it from inside the stop task to prevent race conditions
            holder.context.closedHandler(new SimpleHandler() {
              @Override
              protected void handle() {
                LoggerFactory.removeLogger(holder.loggerName);
                holder.context.runCloseHooks();
                count.complete();
              }
            });
            try {
              holder.verticle.stop();
            } catch (Throwable t) {
              vertx.reportException(t);
            }
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

    count.setHandler(new SimpleHandler() {
      protected void handle() {
        deployment.moduleReference.decRef();
        parentCount.complete();
      }
    });
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
    BlockingAction<Void> redeployAction = new BlockingAction<Void>(vertx, handler) {
      @Override
      public Void action() throws Exception {
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

  // For debug only
  public int checkNoModules() {
    for (String key: modules.keySet()) {
      System.out.println("Module remains: " + key);
    }
    return modules.size();
  }


}
