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

package org.vertx.java.platform.impl;


import org.vertx.java.core.*;
import org.vertx.java.core.impl.*;
import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Container;
import org.vertx.java.platform.Verticle;
import org.vertx.java.platform.VerticleFactory;
import org.vertx.java.platform.impl.resolver.*;

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
public class DefaultPlatformManager implements PlatformManagerInternal, ModuleReloader {

  private static final Logger log = LoggerFactory.getLogger(DefaultPlatformManager.class);
  private static final int BUFFER_SIZE = 4096;
  private static final String MODS_DIR_PROP_NAME = "vertx.mods";
  private static final char COLON = ':';
  private static final String LANG_IMPLS_SYS_PROP_ROOT = "vertx.langs.";
  private static final String LANG_PROPS_FILE_NAME = "langs.properties";
  private static final String REPOS_FILE_NAME = "repos.txt";
  private static final String LOCAL_MODS_DIR = "mods";
  private static final String SYS_MODS_DIR = "sys-mods";
  private static final String VERTX_HOME_SYS_PROP = "vertx.home";
  private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
  private static final String FILE_SEP = System.getProperty("file.separator");

  private final VertxInternal vertx;
  // deployment name --> deployment
  private final Map<String, Deployment> deployments = new ConcurrentHashMap<>();
  // The user mods dir
  private final File modRoot;
  private final File systemModRoot;
  private final ConcurrentMap<String, ModuleReference> modules = new ConcurrentHashMap<>();
  private final Redeployer redeployer;
  private Map<String, LanguageImplInfo> languageImpls = new ConcurrentHashMap<>();
  private Map<String, String> extensionMappings = new ConcurrentHashMap<>();
  private String defaultLanguageImplName;
  private Map<String, List<RepoResolver>> defaultRepos = new HashMap<>();
  private Handler<Void> exitHandler;
  private final ClassLoader platformClassLoader;

  DefaultPlatformManager() {
    this(new DefaultVertx());
  }

  DefaultPlatformManager(String hostname) {
    this(new DefaultVertx(hostname));
  }

  DefaultPlatformManager(int port, String hostname) {
    this(new DefaultVertx(port, hostname));
  }

  private DefaultPlatformManager(VertxInternal vertx) {
    this.platformClassLoader = Thread.currentThread().getContextClassLoader();
    this.vertx = vertx;
    String modDir = System.getProperty(MODS_DIR_PROP_NAME);
    if (modDir != null && !modDir.trim().equals("")) {
      modRoot = new File(modDir);
    } else {
      // Default to local module directory
      modRoot = new File(LOCAL_MODS_DIR);
    }
    String vertxHome = System.getProperty(VERTX_HOME_SYS_PROP);
    if (vertxHome == null || modDir != null) {
      systemModRoot = modRoot;
    } else {
      systemModRoot = new File(vertxHome, SYS_MODS_DIR);
    }
    this.redeployer = new Redeployer(vertx, modRoot, this);
    loadLanguageMappings();
    loadRepos();
  }


  public void registerExitHandler(Handler<Void> handler) {
    this.exitHandler = handler;
  }

  public void deployVerticle(String main,
                             JsonObject config, URL[] classpath,
                             int instances,
                             String includes,
                             Handler<String> doneHandler) {
    if (main == null) {
      throw new NullPointerException("main cannot be null");
    }
    deployVerticle(false, false, main, config, classpath, instances, includes, doneHandler);
  }

  public void deployWorkerVerticle(boolean multiThreaded, String main,
                                   JsonObject config, URL[] classpath,
                                   int instances,
                                   String includes,
                                   Handler<String> doneHandler) {
    deployVerticle(true, multiThreaded, main, config, classpath, instances, includes, doneHandler);
  }

  public void deployModule(final String moduleName, final JsonObject config,
                           final int instances, final Handler<String> doneHandler) {
    final File currentModDir = getDeploymentModDir();
    BlockingAction<Void> deployModuleAction = new BlockingAction<Void>(vertx, createHandler(doneHandler)) {

      @Override
      public Void action() throws Exception {
        doDeployMod(false, null, moduleName, config, instances, currentModDir, wrapDoneHandler(doneHandler));
        return null;
      }
    };
    deployModuleAction.run();
  }

  public synchronized void undeploy(String deploymentID, final Handler<Void> doneHandler) {
    if (deploymentID == null) {
      throw new NullPointerException("deploymentID is null");
    }
    final Deployment dep = deployments.get(deploymentID);
    if (dep == null) {
      throw new IllegalArgumentException("There is no deployment with id " + deploymentID);
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
    doUndeploy(deploymentID, wrappedHandler);
  }

  public synchronized void undeployAll(final Handler<Void> doneHandler) {
    final CountingCompletionHandler count = new CountingCompletionHandler(vertx);
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

  public void installModule(final String moduleName) {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Exception> result = new AtomicReference<>();
    AsyncResultHandler<Void> handler = new AsyncResultHandler<Void>() {
      public void handle(AsyncResult<Void> res) {
        result.set(res.exception);
        latch.countDown();
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
        if (!latch.await(300, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to install module");
        }
        break;
      } catch (InterruptedException ignore) {
      }
    }
    Exception e = result.get();
    if (e != null) {
      log.error("Failed to install module", e);
    }
  }

  public synchronized void uninstallModule(String moduleName) {
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

  public boolean pullInDependencies(String moduleName) {
    log.info("Attempting to pull in dependencies for module: " + moduleName);
    return doPullInDependencies(modRoot, moduleName);
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

  public Vertx getVertx() {
    return this.vertx;
  }

  public void deployModuleFromZip(String zipFileName, JsonObject config,
                                  int instances, Handler<String> doneHandler) {
    final String modName = zipFileName.substring(0, zipFileName.length() - 4);
    if (unzipModule(modName, new ModuleZipInfo(false, zipFileName), false)) {
      deployModule(modName, config, instances, doneHandler);
    } else {
      doneHandler.handle(null);
    }
  }

  public void exit() {
    if (exitHandler != null) {
      exitHandler.handle(null);
    }
  }

  public JsonObject getConfig() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.config;
  }

  public Logger getLogger() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.logger;
  }

  private void deployVerticle(final boolean worker, final boolean multiThreaded, final String main,
                              final JsonObject config, URL[] classpath,
                              final int instances,
                              final String includes,
                              final Handler<String> doneHandler) {
    final File currentModDir = getDeploymentModDir();
    final URL[] cp;
    if (classpath == null) {
      // Use the current modules/verticle's classpath
      cp = getDeploymentURLs();
    } else {
      cp = classpath;
    }
    BlockingAction<Void> deployModuleAction = new BlockingAction<Void>(vertx, createHandler(doneHandler)) {
      @Override
      public Void action() throws Exception {
        doDeployVerticle(worker, multiThreaded, main, config, cp, instances, currentModDir,
            includes, wrapDoneHandler(doneHandler));
        return null;
      }
    };
    deployModuleAction.run();
  }

  private String getDeploymentName() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.deployment.name;
  }

  private URL[] getDeploymentURLs() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.deployment.urls;
  }

  private File getDeploymentModDir() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.deployment.modDir;
  }

  private boolean doPullInDependencies(File modRoot, String moduleName) {
    File modDir = new File(modRoot, moduleName);
    if (!modDir.exists()) {
      log.error("Cannot find module to uninstall");
    }
    JsonObject conf = loadModuleConfig(moduleName, modDir);
    if (conf == null) {
      log.error("Module " + moduleName + " does not contain a mod.json");
    }
    ModuleFields fields = new ModuleFields(conf);
    List<String> mods = new ArrayList<>();
    String includes = fields.getIncludes();
    if (includes != null) {
      mods.addAll(Arrays.asList(parseIncludeString(includes)));
    }
    String deploys = fields.getDeploys();
    if (deploys != null) {
      mods.addAll(Arrays.asList(parseIncludeString(deploys)));
    }
    if (!mods.isEmpty()) {
      File internalModsDir = new File(modDir, "mods");
      if (!internalModsDir.exists()) {
        internalModsDir.mkdir();
      }
      for (String modName: mods) {
        File internalModDir = new File(internalModsDir, modName);
        if (!internalModDir.exists()) {
          ModuleZipInfo zipInfo = getModule(modName);
          if (zipInfo.filename != null) {
            internalModDir.mkdir();
            if (!unzipModuleData(internalModDir, zipInfo, true)) {
              return false;
            } else {
              log.info("Module " + modName + " successfully installed in mods dir of " + modName);
              // Now recurse so we bring in all of the deps
              doPullInDependencies(internalModsDir, modName);
            }
          }
        }
      }
    }
    return true;
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
          return dep.name + "." + dep.main;
        }
      }
    }
    return null;
  }

  private void doDeployVerticle(boolean worker, boolean multiThreaded, final String main,
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
      mr = new ModuleReference(this, moduleKey, new ModuleClassLoader(platformClassLoader, urls), false);
      ModuleReference prev = modules.putIfAbsent(moduleKey, mr);
      if (prev != null) {
        mr = prev;
      }
    }

    if (includes != null) {
      loadIncludedModules(currentModDir, mr, includes);
    }
    doDeploy(depName, false, worker, multiThreaded, main, null, config, urls, instances, currentModDir, mr, doneHandler);
  }


  private void checkWorkerContext() {
    Thread t = Thread.currentThread();
    if (!t.getName().startsWith("vert.x-worker-thread")) {
      throw new IllegalStateException("Not a worker thread");
    }
  }

  private void loadLanguageMappings() {
    // The only language that Vert.x understands out of the box is Java, so we add the default runtime and
    // extension mapping for that. This can be overridden in langs.properties
    languageImpls.put("java", new LanguageImplInfo(null, "org.vertx.java.platform.impl.java.JavaVerticleFactory"));
    extensionMappings.put("java", "java");
    extensionMappings.put("class", "java");
    defaultLanguageImplName = "java";

    // First try loading mappings from the LANG_PROPS_FILE_NAMEs file
    // This file is structured as follows:
    //   It should contain one line for every language implementation that is to be used with Vert.x
    //     That line should be structured as follows:
    //       <lang_impl_name>=[module_name:]<factory_name>
    //     Where:
    //       <lang_impl_name> is the name you want to give to the language implementation, e.g. 'jython'
    //       module_name is the (optional) name of a module that contains the language implementation
    //         if ommitted it will be assumed the language implementation is included as part of the Vert.x installation
    //         - this is only true for the Java implementation
    //         if included the module_name should be followed by a colon
    //       factory_name is the FQCN of a VerticleFactory for the language implementation
    //     Examples:
    //       rhino=vertx.lang-rhino-v1.0.0:org.vertx.java.platform.impl.rhino.RhinoVerticleFactory
    //       java=org.vertx.java.platform.impl.java.JavaVerticleFactory
    //   The file should also contain one line for every extension mapping - this maps a file extension to
    //   a <lang_impl_name> as specified above
    //     Examples:
    //       .js=rhino
    //       .rb=jruby
    //   The file can also contain a line representing the default language runtime to be used when no extension or
    //   prefix maps, e.g.
    //     .=java

    try (InputStream is = getClass().getClassLoader().getResourceAsStream(LANG_PROPS_FILE_NAME)) {
      if (is != null) {
        Properties props = new Properties();
        props.load(new BufferedInputStream(is));
        loadLanguageMappings(props);
      }
    } catch (IOException e) {
      log.error("Failed to load " + LANG_PROPS_FILE_NAME + " " + e.getMessage());
    }

    // Then override any with system properties
    Properties sysProps = new Properties();
    Set<String> propertyNames = System.getProperties().stringPropertyNames();
    for (String propertyName : propertyNames) {
      if (propertyName.startsWith(LANG_IMPLS_SYS_PROP_ROOT)) {
        String lang = propertyName.substring(LANG_IMPLS_SYS_PROP_ROOT.length());
        String value = System.getProperty(propertyName);
        sysProps.put(lang, value);
      }
    }
    loadLanguageMappings(sysProps);
  }

  private void loadLanguageMappings(Properties props) {
    Enumeration<?> en = props.propertyNames();
    while (en.hasMoreElements()) {
      String propName = (String)en.nextElement();
      String propVal = props.getProperty(propName);
      if (propName.startsWith(".")) {
        // This is an extension mapping
        if (propName.equals(".")) {
          // The default mapping
          defaultLanguageImplName = propVal;
        } else {
          propName = propName.substring(1);
          extensionMappings.put(propName, propVal);
        }
      } else {
        // value is made up of an optional module name followed by colon followed by the
        // FQCN of the factory
        int colonIndex = propVal.lastIndexOf(COLON);
        String moduleName;
        String factoryName;
        if (colonIndex != -1) {
          moduleName = propVal.substring(0, colonIndex);
          factoryName = propVal.substring(colonIndex + 1);
        } else {
          moduleName = null;
          factoryName = propVal;
        }
        LanguageImplInfo langImpl = new LanguageImplInfo(moduleName, factoryName);
        languageImpls.put(propName, langImpl);
        extensionMappings.put(propName, propName); // automatically register the name as a mapping
      }
    }
  }

  private File locateModule(File currentModDir, String modName) {
    if (currentModDir != null) {
      // Nested modules - look inside current module dir
      File modDir = new File(new File(currentModDir, LOCAL_MODS_DIR), modName);
      if (modDir.exists()) {
        return modDir;
      }
    }
    File modDir = new File(modRoot, modName);
    if (modDir.exists()) {
      return modDir;
    } else if (!systemModRoot.equals(modRoot)) {
      modDir = new File(systemModRoot, modName);
      if (modDir.exists()) {
        return modDir;
      }
    }
    return null;
  }


  private void doDeployMod(final boolean redeploy, final String depName, final String modName,
                           final JsonObject config,
                           final int instances, final File currentModDir,
                           final Handler<String> doneHandler) {
    checkWorkerContext();
    File modDir = locateModule(currentModDir, modName);
    if (modDir != null) {
      JsonObject conf = loadModuleConfig(modName, modDir);
      ModuleFields fields = new ModuleFields(conf);
      String main = fields.getMain();
      if (main == null) {
        log.error("Runnable module " + modName + " mod.json must contain a \"main\" field");
        callDoneHandler(doneHandler, null);
        return;
      }
      boolean worker = fields.isWorker();
      boolean multiThreaded = fields.isMultiThreaded();
      if (multiThreaded && !worker) {
          throw new IllegalArgumentException("Multi-threaded modules must be workers");
      }
      boolean preserveCwd = fields.isPreserveCurrentWorkingDirectory();

      // If preserveCwd then use the current module directory instead, or the cwd if not in a module
      File modDirToUse = preserveCwd ? currentModDir : modDir;

      List<URL> urls = getModuleClasspath(modDir);
      if (urls == null) {
        callDoneHandler(doneHandler, null);
        return;
      }

      ModuleReference mr = modules.get(modName);
      if (mr == null) {
        boolean res = fields.isResident();
        mr = new ModuleReference(this, modName,
                                 new ModuleClassLoader(platformClassLoader, urls.toArray(new URL[urls.size()])), res);
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
      String includes = fields.getIncludes();
      if (includes != null) {
        if (!loadIncludedModules(modDir, mr, includes)) {
          callDoneHandler(doneHandler, null);
          return;
        }
      }

      final boolean autoRedeploy = fields.isAutoRedeploy();

      doDeploy(depName, autoRedeploy, worker, multiThreaded, main, modName, config,
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
    // Checked the byte code produced, .close() is called correctly, so the warning can be suppressed
    try (@SuppressWarnings("resource") Scanner scanner = new Scanner(new File(modDir, "mod.json")).useDelimiter("\\A")) {
      String conf = scanner.next();
      return new JsonObject(conf);
    } catch (FileNotFoundException e) {
      throw new IllegalStateException("Module " + modName + " does not contain a mod.json file");
    } catch (NoSuchElementException e) {
      throw new IllegalStateException("Module " + modName + " contains an empty mod.json file");
    } catch (DecodeException e) {
      throw new IllegalStateException("Module " + modName + " mod.json contains invalid json");
    }
  }

  private boolean loadIncludedModules(File currentModuleDir, ModuleReference mr, String includesString) {
    checkWorkerContext();
    for (String moduleName: parseIncludeString(includesString)) {
      ModuleReference includedMr = modules.get(moduleName);
      if (includedMr == null) {
        File modDir = locateModule(currentModuleDir, moduleName);
        if (modDir == null) {
          if (!doInstallMod(moduleName)) {
            return false;
          }
        }
        modDir = locateModule(currentModuleDir, moduleName);
        List<URL> urls = getModuleClasspath(modDir);
        JsonObject conf = loadModuleConfig(moduleName, modDir);
        ModuleFields fields = new ModuleFields(conf);

        boolean res = fields.isResident();
        includedMr = new ModuleReference(this, moduleName,
                                         new ModuleClassLoader(platformClassLoader, urls.toArray(new URL[urls.size()])),
                                         res);
        ModuleReference prev = modules.putIfAbsent(moduleName, includedMr);
        if (prev != null) {
          includedMr = prev;
        }
        String includes = fields.getIncludes();
        if (includes != null) {
          loadIncludedModules(modDir, includedMr, includes);
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

  private void loadRepos() {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(REPOS_FILE_NAME)) {
      if (is != null) {
        BufferedReader rdr = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = rdr.readLine()) != null) {
          line = line.trim();
          if (line.isEmpty() || line.startsWith("#")) {
            // blank line or comment
            continue;
          }
          int colonPos = line.indexOf(':');
          if (colonPos == -1 || colonPos == line.length() - 1) {
            throw new IllegalArgumentException("Invalid repo: " + line);
          }
          String type = line.substring(0, colonPos);
          String repoID = line.substring(colonPos + 1);
          RepoResolver resolver;
          switch (type) {
            case "mavenLocal":
              resolver = new MavenLocalRepoResolver(repoID);
              type = "maven";
              break;
            case "maven":
              resolver = new MavenRepoResolver(vertx, repoID);
              break;
            case "bintray":
              resolver = new BintrayRepoResolver(vertx, repoID);
              break;
            case "old":
              resolver = new OldRepoResolver(vertx, repoID);
              break;
            default:
              throw new IllegalArgumentException("Unknown repo type: " + type);
          }
          List<RepoResolver> resolvers = defaultRepos.get(type);
          if (resolvers == null) {
            resolvers = new ArrayList<>();
            defaultRepos.put(type, resolvers);
          }
          resolvers.add(resolver);
        }
      }
    } catch (IOException e) {
      log.error("Failed to load " + LANG_PROPS_FILE_NAME + " " + e.getMessage());
    }
  }

  private boolean doInstallMod(final String moduleName) {
    checkWorkerContext();
    if (defaultRepos.isEmpty()) {
      log.warn("No repositories configured!");
      return false;
    }
    if (locateModule(null, moduleName) != null) {
      log.error("Module is already installed");
      return false;
    }
    ModuleZipInfo info = getModule(moduleName);
    if (info != null) {
      return unzipModule(moduleName, info, true);
    }
    return false;
  }

  private ModuleZipInfo getModule(String moduleName) {
    int colonPos = moduleName.indexOf(COLON);
    if (colonPos == moduleName.length() - 1) {
      throw new IllegalArgumentException("Invalid module name, no name after prefix. " + moduleName);
    }
    String prefix;
    if (colonPos == -1) {
      // Default to old Vert.x 1.x repo
      prefix = "old";
    } else {
      prefix = moduleName.substring(0, colonPos);
    }
    String rest = moduleName.substring(colonPos + 1);
    List<RepoResolver> resolvers = defaultRepos.get(prefix);
    if (resolvers == null) {
      throw new IllegalArgumentException("No resolvers for prefix: " + prefix);
    }
    String fileName = generateTmpFileName() + ".zip";
    for (RepoResolver resolver: resolvers) {
      if (resolver.getModule(fileName, rest)) {
        return new ModuleZipInfo(resolver.isOldStyle(), fileName);
      }
    }
    log.error("Module " + moduleName + " not found in any repositories");
    return null;
  }

  private String generateTmpFileName() {
    return TEMP_DIR + FILE_SEP + "vertx-" + UUID.randomUUID().toString();
  }

  private File unzipIntoTmpDir(ModuleZipInfo zipInfo, boolean deleteZip) {
    String tdir = generateTmpFileName();
    File tdest = new File(tdir);
    tdest.mkdir();

    if (!unzipModuleData(tdest, zipInfo, deleteZip)) {
      return null;
    } else {
      return tdest;
    }
  }

  private boolean checkModDirs() {
    if (!modRoot.exists()) {
      if (!modRoot.mkdir()) {
        log.error("Failed to create mods dir " + modRoot);
        return false;
      }
    }
    if (!systemModRoot.exists()) {
      if (!systemModRoot.mkdir()) {
        log.error("Failed to create sys mods dir " + modRoot);
        return false;
      }
    }
    return true;
  }


  private boolean unzipModule(final String modName, final ModuleZipInfo zipInfo, boolean deleteZip) {
    // We synchronize to prevent a race whereby it tries to unzip the same module at the
    // same time (e.g. deployModule for the same module name has been called in parallel)
    synchronized (modName.intern()) {

      if (!checkModDirs()) {
        return false;
      }

      File fdest = new File(modRoot, modName);
      File sdest = new File(systemModRoot, modName);
      if (fdest.exists() || sdest.exists()) {
        // This can happen if the same module is requested to be installed
        // at around the same time
        // It's ok if this happens
        log.warn("Module " + modName + " is already installed");
        return true;
      }

      // Unzip into temp dir first
      File tdest = unzipIntoTmpDir(zipInfo, deleteZip);
      if (tdest == null) {
        return false;
      }

      // Check if it's a system module
      JsonObject conf = loadModuleConfig(modName, tdest);
      ModuleFields fields = new ModuleFields(conf);

      boolean system = fields.isSystem();

      // Now copy it to the proper directory
      String moveFrom = tdest.getAbsolutePath();
      try {
        vertx.fileSystem().moveSync(moveFrom, system ? sdest.getAbsolutePath() : fdest.getAbsolutePath());
      } catch (Exception e) {
        log.error("Failed to move module", e);
        return false;
      }

      log.info("Module " + modName +" successfully installed");
      return true;
    }
  }

  private String removeTopDir(String entry) {
    int pos = entry.indexOf(FILE_SEP);
    if (pos != -1) {
      entry = entry.substring(pos + 1);
    }
    return entry;
  }

  private boolean unzipModuleData(final File directory, final ModuleZipInfo zipinfo, boolean deleteZip) {
    try (InputStream is = new BufferedInputStream(new FileInputStream(zipinfo.filename)); ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        String entryName = zipinfo.oldStyle ? removeTopDir(entry.getName()) : entry.getName();
        if (!entryName.isEmpty()) {
          if (entry.isDirectory()) {
            new File(directory, entryName).mkdir();
          } else {
            int count;
            byte[] buff = new byte[BUFFER_SIZE];
            BufferedOutputStream dest = null;
            try {
              OutputStream fos = new FileOutputStream(new File(directory, entryName));
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
      }
    } catch (Exception e) {
      log.error("Failed to unzip module", e);
      return false;
    } finally {
      directory.delete();
      if (deleteZip) {
        new File(zipinfo.filename).delete();
      }
    }
    return true;
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
                        boolean worker, boolean multiThreaded,
                        String theMain,
                        final String modName,
                        final JsonObject config, final URL[] urls,
                        int instances,
                        final File modDir,
                        final ModuleReference mr,
                        final Handler<String> doneHandler) {
    checkWorkerContext();

    final String deploymentName =
        depName != null ? depName : genDepName();

    log.debug("Deploying name : " + deploymentName + " main: " + theMain +
              " instances: " + instances);

    // How we determine which language implementation to use:
    // 1. Look for a prefix on the main, e.g. 'groovy:org.foo.myproject.MyGroovyMain' would force the groovy
    //    language impl to be used
    // 2. If there is no prefix, then look at the extension, if any. If there is an extension mapping for that
    //    extension, use that.
    // 3. No prefix and no extension mapping - use the default runtime

    LanguageImplInfo langImplInfo = null;

    final String main;
    // Look for a prefix
    int prefixMarker = theMain.indexOf(COLON);
    if (prefixMarker != -1) {
      String prefix = theMain.substring(0, prefixMarker);
      langImplInfo = languageImpls.get(prefix);
      if (langImplInfo == null) {
        throw new IllegalStateException("No language implementation known for prefix " + prefix);
      }
      main = theMain.substring(prefixMarker + 1);
    } else {
      main = theMain;
    }
    if (langImplInfo == null) {
      // No prefix - now look at the extension
      int extensionMarker = main.lastIndexOf('.');
      if (extensionMarker != -1) {
        String extension = main.substring(extensionMarker + 1);
        String langImplName = extensionMappings.get(extension);
        if (langImplName != null) {
          langImplInfo = languageImpls.get(langImplName);
          if (langImplInfo == null) {
            throw new IllegalStateException("Extension mapping for " + extension + " specified as " + langImplName +
                                            ", but no language implementation known for that name");
          }
        }
      }
    }
    if (langImplInfo == null) {
      // Use the default
      langImplInfo = languageImpls.get(defaultLanguageImplName);
      if (langImplInfo == null) {
        throw new IllegalStateException("Default language implementation is " + defaultLanguageImplName +
                                        " but no language implementation known for that name");
      }
    }

    // Include the language impl module as a parent of the classloader
    if (langImplInfo.moduleName != null) {
      if (!loadIncludedModules(modDir, mr, langImplInfo.moduleName)) {
        log.error("Failed to load module: " + langImplInfo.moduleName);
        doneHandler.handle(null);
        return;
      }
    }

    final VerticleFactory verticleFactory;

    final Container container = new DefaultContainer(this);

    try {
      // TODO not one verticle factory per module ref, but one per language per module ref
      verticleFactory = mr.getVerticleFactory(langImplInfo.factoryName, vertx, container);
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
    final Deployment deployment = new Deployment(deploymentName, main, modName, instances,
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
              log.error("Cannot find verticle " + main + " in " + verticleFactory.getClass().getName(), e);
            } catch (Throwable t) {
              log.error("Failed to create verticle " + main + " in " + verticleFactory.getClass().getName(), t);
            }

            if (error) {
              doUndeploy(deploymentName, new SimpleHandler() {
                public void handle() {
                  aggHandler.done(false);
                }
              });
              return;
            }
            verticle.setContainer(container);
            verticle.setVertx(vertx);

            try {
              addVerticle(deployment, verticle, verticleFactory);
              if (modDir != null) {
                setPathAdjustment(modDir);
              }
              VoidResult vr = new VoidResult();
              verticle.start(vr);
              vr.setHandler(new AsyncResultHandler<Void>() {
                @Override
                public void handle(AsyncResult<Void> ar) {
                  if (ar.succeeded()) {
                    aggHandler.done(true);
                  } else {
                    log.error("Failed to deploy verticle " + main + " in " + verticleFactory.getClass().getName(), ar.exception);
                    aggHandler.done(false);
                  }
                }
              });
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
          vertx.startInBackground(runner, multiThreaded);
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
    CountingCompletionHandler count = new CountingCompletionHandler(vertx);
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
    final CountingCompletionHandler count = new CountingCompletionHandler(vertx);
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
            try {
              holder.verticle.stop();
            } catch (Throwable t) {
              vertx.reportException(t);
            }
            LoggerFactory.removeLogger(holder.loggerName);
            holder.context.runCloseHooks();
            holder.context.close();
            count.complete();
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

  private void redeploy(final Deployment deployment) {
    // Has to occur on a worker thread
    AsyncResultHandler<String> handler = new AsyncResultHandler<String>() {
      public void handle(AsyncResult<String> res) {
        if (!res.succeeded()) {
          log.error("Failed to redeploy", res.exception);
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
    int count = 0;
    for (Map.Entry<String, ModuleReference> entry: modules.entrySet()) {
      if (!entry.getValue().resident) {
        System.out.println("Module remains: " + entry.getKey());
        count++;
      }
    }
    return count;
  }

  public void removeModule(String moduleKey) {
    modules.remove(moduleKey);
  }

  private static class LanguageImplInfo {
    final String moduleName;
    final String factoryName;
    private LanguageImplInfo(String moduleName, String factoryName) {
      this.moduleName = moduleName;
      this.factoryName = factoryName;
    }
    public String toString() {
      return (moduleName == null ? ":" : (moduleName + ":")) + factoryName;
    }
  }

  private static final class ModuleZipInfo {
    final boolean oldStyle;
    final String filename;

    private ModuleZipInfo(boolean oldStyle, String filename) {
      this.oldStyle = oldStyle;
      this.filename = filename;
    }
  }


}
