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


import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.impl.*;
import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.PlatformManagerException;
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
  private final ConcurrentMap<String, ModuleReference> moduleRefs = new ConcurrentHashMap<>();
  private final Redeployer redeployer;
  private Map<String, LanguageImplInfo> languageImpls = new ConcurrentHashMap<>();
  private Map<String, String> extensionMappings = new ConcurrentHashMap<>();
  private String defaultLanguageImplName;
  private List<RepoResolver> repos = new ArrayList<>();
  private Handler<Void> exitHandler;
  private final ClassLoader platformClassLoader;
  private boolean disableMavenLocal;

  DefaultPlatformManager() {
    this(new DefaultVertx());
  }

  DefaultPlatformManager(String hostname) {
    this(new DefaultVertx(hostname));
  }

  DefaultPlatformManager(int port, String hostname) {
    this(new DefaultVertx(port, hostname));
  }

  private DefaultPlatformManager(DefaultVertx vertx) {
    this.platformClassLoader = Thread.currentThread().getContextClassLoader();
    this.vertx = new WrappedVertx(vertx);
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
    // If running on CI we don't want to use maven local to get any modules - this is because they can
    // get stale easily - we must always get them from external repos
    this.disableMavenLocal = System.getenv("VERTX_DISABLE_MAVENLOCAL") != null;
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
                             Handler<AsyncResult<String>> doneHandler) {
    deployVerticle(false, false, main, config, classpath, instances, includes, doneHandler);
  }

  public void deployWorkerVerticle(boolean multiThreaded, String main,
                                   JsonObject config, URL[] classpath,
                                   int instances,
                                   String includes,
                                   Handler<AsyncResult<String>> doneHandler) {
    deployVerticle(true, multiThreaded, main, config, classpath, instances, includes, doneHandler);
  }

  public void deployModule(final String moduleName, final JsonObject config,
                           final int instances, final Handler<AsyncResult<String>> doneHandler) {
    final File currentModDir = getDeploymentModDir();
    final Handler<AsyncResult<String>> wrapped = wrapDoneHandler(doneHandler);
    runInBackground(new Runnable() {
      public void run() {
        ModuleIdentifier modID = new ModuleIdentifier(moduleName);
        doDeployMod(false, null, modID, config, instances, currentModDir, wrapped);
      }
    }, wrapped);
  }

  public synchronized void undeploy(final String deploymentID, final Handler<AsyncResult<Void>> doneHandler) {
    runInBackground(new Runnable() {
      public void run() {
        if (deploymentID == null) {
          throw new NullPointerException("deploymentID cannot be null");
        }
        final Deployment dep = deployments.get(deploymentID);
        if (dep == null) {
          throw new PlatformManagerException("There is no deployment with id " + deploymentID);
        }
        Handler<AsyncResult<Void>> wrappedHandler = wrapDoneHandler(new Handler<AsyncResult<Void>>() {
          public void handle(AsyncResult<Void> res) {
            if (res.succeeded() && dep.modID != null && dep.autoRedeploy) {
              redeployer.moduleUndeployed(dep);
            }
            if (doneHandler != null) {
              doneHandler.handle(res);
            }
          }
        });
        doUndeploy(deploymentID, wrappedHandler);
      }
    }, wrapDoneHandler(doneHandler));
  }

  public synchronized void undeployAll(final Handler<AsyncResult<Void>> doneHandler) {
    List<String> parents = new ArrayList<>();
    for (Map.Entry<String, Deployment> entry: deployments.entrySet()) {
      if (entry.getValue().parentDeploymentName == null) {
        parents.add(entry.getKey());
      }
    }
    
    final CountingCompletionHandler<Void> count = new CountingCompletionHandler<>(vertx, parents.size());
    count.setHandler(doneHandler);
    
    for (String name: parents) {
      undeploy(name, new Handler<AsyncResult<Void>>() {
        public void handle(AsyncResult<Void> res) {
          count.complete(res);
        }
      });
    }
  }

  public Map<String, Integer> listInstances() {
    Map<String, Integer> map = new HashMap<>();
    for (Map.Entry<String, Deployment> entry: deployments.entrySet()) {
      map.put(entry.getKey(), entry.getValue().verticles.size());
    }
    return map;
  }

  public void installModule(final String moduleName, final Handler<AsyncResult<Void>> doneHandler) {
    final Handler<AsyncResult<Void>> wrapped = wrapDoneHandler(doneHandler);
    runInBackground(new Runnable() {
      public void run() {
        ModuleIdentifier modID = new ModuleIdentifier(moduleName);
        doInstallMod(modID);
        doneHandler.handle(new DefaultFutureResult<>((Void)null));
      }
    }, wrapped);
  }

  public void uninstallModule(final String moduleName, final Handler<AsyncResult<Void>> doneHandler) {
    final Handler<AsyncResult<Void>> wrapped = wrapDoneHandler(doneHandler);
    runInBackground(new Runnable() {
      public void run() {
        ModuleIdentifier modID = new ModuleIdentifier(moduleName); // Validates it
        File modDir = new File(modRoot, modID.toString());
        if (!modDir.exists()) {
          throw new PlatformManagerException("Cannot find module to uninstall: " + moduleName);
        } else {
          vertx.fileSystem().deleteSync(modDir.getAbsolutePath(), true);
        }
        doneHandler.handle(new DefaultFutureResult<>((Void)null));
      }
    }, wrapped);
  }

  public void pullInDependencies(final String moduleName, final Handler<AsyncResult<Void>> doneHandler) {
    final Handler<AsyncResult<Void>> wrapped = wrapDoneHandler(doneHandler);
    runInBackground(new Runnable() {
      public void run() {
        ModuleIdentifier modID = new ModuleIdentifier(moduleName); // Validates it
        doPullInDependencies(modRoot, modID);
        doneHandler.handle(new DefaultFutureResult<>((Void)null));
      }
    }, wrapped);
  }

  public void reloadModules(final Set<Deployment> deps) {
    runInBackground(new Runnable() {
      public void run() {
        for (final Deployment deployment : deps) {
          if (deployments.containsKey(deployment.name)) {
            doUndeploy(deployment.name, new Handler<AsyncResult<Void>>() {
              public void handle(AsyncResult<Void> res) {
                if (res.succeeded()) {
                  doRedeploy(deployment);
                } else {
                  log.error("Failed to undeploy", res.cause());
                }
              }
            });
          } else {
            // This will be the case if the previous deployment failed, e.g.
            // a code error in a user verticle
            doRedeploy(deployment);
          }
        }
      }
    }, null);
  }

  public Vertx vertx() {
    return this.vertx;
  }

  public void deployModuleFromZip(final String zipFileName, final JsonObject config,
                                  final int instances, Handler<AsyncResult<String>> doneHandler) {
    final Handler<AsyncResult<String>> wrapped = wrapDoneHandler(doneHandler);
    runInBackground(new Runnable() {
      public void run() {
        if (zipFileName == null) {
          throw new NullPointerException("zipFileName cannot be null");
        }
        final String modName = zipFileName.substring(0, zipFileName.length() - 4);
        ModuleIdentifier modID = new ModuleIdentifier("__vertx_tmp#" + modName + "#__vertx_tmp");
        unzipModule(modID, new ModuleZipInfo(false, zipFileName), false);
        doDeployMod(false, null, modID, config, instances, null, wrapped);
      }
    }, wrapped);
  }

  public void exit() {
    if (exitHandler != null) {
      exitHandler.handle(null);
    }
  }

  public JsonObject config() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.config;
  }

  public Logger logger() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.logger;
  }

  private void doRedeploy(Deployment deployment) {
    doDeployMod(true, deployment.name, deployment.modID, deployment.config, deployment.instances,
        null, null);
  }

  private <T> void runInBackground(final Runnable runnable, final Handler<AsyncResult<T>> doneHandler) {
    final DefaultContext context = vertx.getOrAssignContext();
    vertx.getBackgroundPool().execute(new Runnable() {
      public void run() {
        try {
          vertx.setContext(context);
          runnable.run();
        } catch (Throwable t) {
          doneHandler.handle(new DefaultFutureResult<T>(t));
        } finally {
          vertx.setContext(null);
        }
      }
    });
  }

  private void deployVerticle(final boolean worker, final boolean multiThreaded, final String main,
                              final JsonObject config, URL[] classpath,
                              final int instances,
                              final String includes,
                              final Handler<AsyncResult<String>> doneHandler) {
    final File currentModDir = getDeploymentModDir();
    final URL[] cp;
    if (classpath == null) {
      // Use the current moduleRefs/verticle's classpath
      cp = getDeploymentURLs();
    } else {
      cp = classpath;
    }
    final Handler<AsyncResult<String>> wrapped = wrapDoneHandler(doneHandler);
    runInBackground(new Runnable() {
      public void run() {
        doDeployVerticle(worker, multiThreaded, main, config, cp, instances, currentModDir,
            includes, wrapped);
      }
    }, wrapped);
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

  private void doPullInDependencies(File modRoot, ModuleIdentifier modID) {
    File modDir = new File(modRoot, modID.toString());
    if (!modDir.exists()) {
      log.error("Cannot find module to uninstall");
    }
    JsonObject conf = loadModuleConfig(modID, modDir);
    if (conf == null) {
      log.error("Module " + modID + " does not contain a mod.json");
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
        if (!internalModsDir.mkdir()) {
          throw new PlatformManagerException("Failed to create directory " + internalModsDir);
        }
      }
      for (String modName: mods) {
        File internalModDir = new File(internalModsDir, modName);
        if (!internalModDir.exists()) {
          ModuleIdentifier theModID = new ModuleIdentifier(modName);
          ModuleZipInfo zipInfo = getModule(theModID);
          if (zipInfo.filename != null) {
            if (!internalModDir.mkdir()) {
              throw new PlatformManagerException("Failed to create directory " + internalModDir);
            }
            unzipModuleData(internalModDir, zipInfo, true);
            log.info("Module " + modName + " successfully installed in mods dir of " + modName);
            // Now recurse so we bring in all of the deps
            doPullInDependencies(internalModsDir, theModID);
          }
        }
      }
    }
  }

  // This makes sure the result is handled on the calling context
  private <T> Handler<AsyncResult<T>> wrapDoneHandler(final Handler<AsyncResult<T>> doneHandler) {
    if (doneHandler == null) {
      // Just create one which logs out any failure, otherwise we will have silent failures when no handler
      // is specified
      return new AsyncResultHandler<T>() {
        @Override
        public void handle(AsyncResult<T> res) {
          if (res.failed()) {
            vertx.reportException(res.cause());
          }
        }
      };
    } else {
      final DefaultContext context = vertx.getContext();
      return new AsyncResultHandler<T>() {
        @Override
        public void handle(final AsyncResult<T> res) {
          if (context == null) {
            doneHandler.handle(res);
          } else {
            context.execute(new Runnable() {
              public void run() {
                doneHandler.handle(res);
              }
            });
          }
        }
      };
    }
  }

  // Recurse up through the parent deployments and return the the module name for the first one
  // which has one, or if there is no enclosing module just use the deployment name
  private ModuleIdentifier getEnclosingModID() {
    VerticleHolder holder = getVerticleHolder();
    Deployment dep = holder == null ? null : holder.deployment;
    while (dep != null) {
      if (dep.modID != null) {
        return dep.modID;
      } else {
        String parentDepName = dep.parentDeploymentName;
        if (parentDepName != null) {
          dep = deployments.get(parentDepName);
        } else {
          // Top level - deployed as verticle not module
          // Just use the deployment name
          return ModuleIdentifier.createInternalModIDForVerticle(dep.name);
        }
      }
    }
    return null; // We are at the top level already
  }

  private void doDeployVerticle(boolean worker, boolean multiThreaded, final String main,
                                final JsonObject config, final URL[] urls,
                                int instances, File currentModDir,
                                String includes, Handler<AsyncResult<String>> doneHandler)
  {
    checkWorkerContext();

    if (main == null) {
      throw new NullPointerException("main cannot be null");
    }

    // There is one module class loader per enclosing module + the name of the verticle.
    // If there is no enclosing module, there is one per top level verticle deployment
    // E.g. if a module A deploys "foo.js" as a verticle then all instances of foo.js deployed by the enclosing
    // module will share a module class loader
    String depName = genDepName();
    ModuleIdentifier enclosingModName = getEnclosingModID();
    String moduleKey;
    if (enclosingModName == null) {
      // We are at the top level - just use the deployment name as the key
      moduleKey = ModuleIdentifier.createInternalModIDForVerticle(depName).toString();
    } else {
      // Use the enclosing module name / or enclosing verticle PLUS the main
      moduleKey = enclosingModName.toString() + "#" + main;
    }

    ModuleReference mr = moduleRefs.get(moduleKey);
    if (mr == null) {
      mr = new ModuleReference(this, moduleKey, new ModuleClassLoader(platformClassLoader, urls), false);
      ModuleReference prev = moduleRefs.putIfAbsent(moduleKey, mr);
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
      throw new PlatformManagerException(e);
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
          throw new PlatformManagerException("Language mapping: " + propVal + " does not specify an implementing module");
        }
        LanguageImplInfo langImpl = new LanguageImplInfo(moduleName, factoryName);
        languageImpls.put(propName, langImpl);
        extensionMappings.put(propName, propName); // automatically register the name as a mapping
      }
    }
  }

  private File locateModule(File currentModDir, ModuleIdentifier modID) {
    if (currentModDir != null) {
      // Nested moduleRefs - look inside current module dir
      File modDir = new File(new File(currentModDir, LOCAL_MODS_DIR), modID.toString());
      if (modDir.exists()) {
        return modDir;
      }
    }
    File modDir = new File(modRoot, modID.toString());
    if (modDir.exists()) {
      return modDir;
    } else if (!systemModRoot.equals(modRoot)) {
      modDir = new File(systemModRoot, modID.toString());
      if (modDir.exists()) {
        return modDir;
      }
    }
    return null;
  }


  private void doDeployMod(final boolean redeploy, final String depName, final ModuleIdentifier modID,
                           final JsonObject config,
                           final int instances, final File currentModDir,
                           final Handler<AsyncResult<String>> doneHandler) {
    checkWorkerContext();
    File modDir = locateModule(currentModDir, modID);
    if (modDir != null) {
      JsonObject conf = loadModuleConfig(modID, modDir);
      ModuleFields fields = new ModuleFields(conf);
      String main = fields.getMain();
      if (main == null) {
        throw new PlatformManagerException("Runnable module " + modID + " mod.json must contain a \"main\" field");
      }
      boolean worker = fields.isWorker();
      boolean multiThreaded = fields.isMultiThreaded();
      if (multiThreaded && !worker) {
        throw new PlatformManagerException("Multi-threaded modules must be workers");
      }
      boolean preserveCwd = fields.isPreserveCurrentWorkingDirectory();

      // If preserveCwd then use the current module directory instead, or the cwd if not in a module
      File modDirToUse = preserveCwd ? currentModDir : modDir;

      List<URL> urls = getModuleClasspath(modDir);

      ModuleReference mr = moduleRefs.get(modID.toString());
      if (mr == null) {
        boolean res = fields.isResident();
        mr = new ModuleReference(this, modID.toString(),
                                 new ModuleClassLoader(platformClassLoader, urls.toArray(new URL[urls.size()])), res);
        ModuleReference prev = moduleRefs.putIfAbsent(modID.toString(), mr);
        if (prev != null) {
          mr = prev;
        }
      }
      ModuleIdentifier enclosingModID = getEnclosingModID();
      if (enclosingModID != null) {
        //If enclosed in another module then the enclosing module classloader becomes a parent of this one
        ModuleReference parentRef = moduleRefs.get(enclosingModID.toString());
        mr.mcl.addParent(parentRef);
        parentRef.incRef();
      }

      // Now load any included moduleRefs
      String includes = fields.getIncludes();
      if (includes != null) {
        loadIncludedModules(modDir, mr, includes);
      }

      final boolean autoRedeploy = fields.isAutoRedeploy();

      doDeploy(depName, autoRedeploy, worker, multiThreaded, main, modID, config,
          urls.toArray(new URL[urls.size()]), instances, modDirToUse, mr, new Handler<AsyncResult<String>>() {
        @Override
        public void handle(AsyncResult<String> res) {
          if (res.succeeded()) {
            String deploymentID = res.result();
            if (deploymentID != null && !redeploy && autoRedeploy) {
              redeployer.moduleDeployed(deployments.get(deploymentID));
            }
          }
          if (doneHandler != null) {
            doneHandler.handle(res);
          } else {
            log.error("Failed to deploy", res.cause());
          }
        }
      });
    } else {
      doInstallMod(modID);
      doDeployMod(redeploy, depName, modID, config, instances, currentModDir, doneHandler);
    }
  }

  private JsonObject loadModuleConfig(ModuleIdentifier modID, File modDir) {
    // Checked the byte code produced, .close() is called correctly, so the warning can be suppressed
    try (Scanner scanner = new Scanner(new File(modDir, "mod.json")).useDelimiter("\\A")) {
      String conf = scanner.next();
      return new JsonObject(conf);
    } catch (FileNotFoundException e) {
      throw new PlatformManagerException("Module " + modID + " does not contain a mod.json file");
    } catch (NoSuchElementException e) {
      throw new PlatformManagerException("Module " + modID + " contains an empty mod.json file");
    } catch (DecodeException e) {
      throw new PlatformManagerException("Module " + modID + " mod.json contains invalid json");
    }
  }

  private void loadIncludedModules(File currentModuleDir, ModuleReference mr, String includesString) {
    checkWorkerContext();
    for (String moduleName: parseIncludeString(includesString)) {
      ModuleIdentifier modID = new ModuleIdentifier(moduleName);
      ModuleReference includedMr = moduleRefs.get(moduleName);
      if (includedMr == null) {
        File modDir = locateModule(currentModuleDir, modID);
        if (modDir == null) {
          doInstallMod(modID);
        }
        modDir = locateModule(currentModuleDir, modID);
        List<URL> urls = getModuleClasspath(modDir);
        JsonObject conf = loadModuleConfig(modID, modDir);
        ModuleFields fields = new ModuleFields(conf);

        boolean res = fields.isResident();
        includedMr = new ModuleReference(this, moduleName,
                                         new ModuleClassLoader(platformClassLoader, urls.toArray(new URL[urls.size()])),
                                         res);
        ModuleReference prev = moduleRefs.putIfAbsent(moduleName, includedMr);
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
      throw new PlatformManagerException(e);
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
              if (disableMavenLocal) {
                continue;
              }
              resolver = new MavenLocalRepoResolver(repoID);
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
          repos.add(resolver);
        }
      }
    } catch (IOException e) {
      log.error("Failed to load " + LANG_PROPS_FILE_NAME + " " + e.getMessage());
    }
  }

  private void doInstallMod(final ModuleIdentifier modID) {
    checkWorkerContext();
    if (repos.isEmpty()) {
      throw new PlatformManagerException("No repositories configured!");
    }
    if (locateModule(null, modID) != null) {
      throw new PlatformManagerException("Module is already installed");
    }
    ModuleZipInfo info = getModule(modID);
    unzipModule(modID, info, true);
  }

  private ModuleZipInfo getModule(ModuleIdentifier modID) {
    String fileName = generateTmpFileName() + ".zip";
    for (RepoResolver resolver: repos) {
      if (resolver.getModule(fileName, modID)) {
        return new ModuleZipInfo(resolver.isOldStyle(), fileName);
      }
    }
    throw new PlatformManagerException("Module " + modID + " not found in any repositories");
  }

  private String generateTmpFileName() {
    return TEMP_DIR + FILE_SEP + "vertx-" + UUID.randomUUID().toString();
  }

  private File unzipIntoTmpDir(ModuleZipInfo zipInfo, boolean deleteZip) {
    String tdir = generateTmpFileName();
    File tdest = new File(tdir);
    if (!tdest.mkdir()) {
      throw new PlatformManagerException("Failed to create directory " + tdest);
    }
    unzipModuleData(tdest, zipInfo, deleteZip);
    return tdest;
  }

  private void checkCreateModDirs() {
    if (!modRoot.exists()) {
      if (!modRoot.mkdir()) {
        throw new PlatformManagerException("Failed to create mods dir " + modRoot);
      }
    }
    if (!systemModRoot.exists()) {
      if (!systemModRoot.mkdir()) {
        throw new PlatformManagerException("Failed to create sys mods dir " + modRoot);
      }
    }
  }


  private void unzipModule(final ModuleIdentifier modID, final ModuleZipInfo zipInfo, boolean deleteZip) {
    // We synchronize to prevent a race whereby it tries to unzip the same module at the
    // same time (e.g. deployModule for the same module name has been called in parallel)
    String modName = modID.toString();
    synchronized (modName.intern()) {

      checkCreateModDirs();

      File fdest = new File(modRoot, modName);
      File sdest = new File(systemModRoot, modName);
      if (fdest.exists() || sdest.exists()) {
        // This can happen if the same module is requested to be installed
        // at around the same time
        // It's ok if this happens
        log.warn("Module " + modID + " is already installed");
        return;
      }

      // Unzip into temp dir first
      File tdest = unzipIntoTmpDir(zipInfo, deleteZip);

      // Check if it's a system module
      JsonObject conf = loadModuleConfig(modID, tdest);
      ModuleFields fields = new ModuleFields(conf);

      boolean system = fields.isSystem();

      // Now copy it to the proper directory
      String moveFrom = tdest.getAbsolutePath();
      safeMove(moveFrom, system ? sdest.getAbsolutePath() : fdest.getAbsolutePath());
      log.info("Module " + modID +" successfully installed");
    }
  }

  // We actually do a copy and delete since move doesn't always work across volumes
  private void safeMove(String source, String dest) {
    // Try and move first - it's more efficient
    try {
      vertx.fileSystem().moveSync(source, dest);
    } catch (Exception e) {
      // And fall back to copying
      try {
        vertx.fileSystem().copySync(source, dest, true);
        vertx.fileSystem().deleteSync(source, true);
      } catch (Exception e2) {
        throw new PlatformManagerException("Failed to copy module", e2);
      }
    }
  }

  private String removeTopDir(String entry) {
    int pos = entry.indexOf(FILE_SEP);
    if (pos != -1) {
      entry = entry.substring(pos + 1);
    }
    return entry;
  }

  private void unzipModuleData(final File directory, final ModuleZipInfo zipinfo, boolean deleteZip) {
    try (InputStream is = new BufferedInputStream(new FileInputStream(zipinfo.filename)); ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        String entryName = zipinfo.oldStyle ? removeTopDir(entry.getName()) : entry.getName();
        if (!entryName.isEmpty()) {
          if (entry.isDirectory()) {
            if (!new File(directory, entryName).mkdir()) {
              throw new PlatformManagerException("Failed to create directory");
            }
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
      throw new PlatformManagerException("Failed to unzip module", e);
    } finally {
      if (deleteZip) {
        if (!new File(zipinfo.filename).delete()) {
          log.error("Failed to delete zip");
        }
      }
    }
  }

  // We calculate a path adjustment that can be used by the fileSystem object
  // so that the *effective* working directory can be the module directory
  // this allows moduleRefs to read and write the file system as if they were
  // in the module dir, even though the actual working directory will be
  // wherever vertx run or vertx start was called from
  private void setPathAdjustment(File modDir) {
    Path cwd = Paths.get(".").toAbsolutePath().getParent();
    Path pmodDir = Paths.get(modDir.getAbsolutePath());
    Path relative = cwd.relativize(pmodDir);
    vertx.getContext().setPathAdjustment(relative);
  }

  private String genDepName() {
    return "deployment-" + UUID.randomUUID().toString();
  }

  private void doDeploy(final String depID,
                        boolean autoRedeploy,
                        boolean worker, boolean multiThreaded,
                        String theMain,
                        final ModuleIdentifier modID,
                        final JsonObject config, final URL[] urls,
                        int instances,
                        final File modDir,
                        final ModuleReference mr,
                        Handler<AsyncResult<String>> dHandler) {
    checkWorkerContext();

    if (dHandler == null) {
      // Add a simple one that just logs, so deploy failures aren't lost if the user doesn't specify a handler
      dHandler = new Handler<AsyncResult<String>>() {
        @Override
        public void handle(AsyncResult<String> ar) {
          if (ar.failed()) {
            log.error("Failed to deploy", ar.cause());
          }
        }
      };
    }
    final Handler<AsyncResult<String>> doneHandler = dHandler;

    final String deploymentID = depID != null ? depID : genDepName();

    log.debug("Deploying name : " + deploymentID + " main: " + theMain + " instances: " + instances);

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
      loadIncludedModules(modDir, mr, langImplInfo.moduleName);
    }

    String parentDeploymentName = getDeploymentName();

    if (parentDeploymentName != null) {
      Deployment parentDeployment = deployments.get(parentDeploymentName);
      if (parentDeployment == null) {
        // This means the parent has already been undeployed - we must not deploy the child
        throw new PlatformManagerException("Parent has already been undeployed!");
      }
      parentDeployment.childDeployments.add(deploymentID);
    }

    final VerticleFactory verticleFactory;

    try {
      // TODO not one verticle factory per module ref, but one per language per module ref
      verticleFactory = mr.getVerticleFactory(langImplInfo.factoryName, vertx, new DefaultContainer(this));
    } catch (Throwable t) {
      throw new PlatformManagerException("Failed to instantiate verticle factory", t);
    }

    final CountingCompletionHandler<Void> aggHandler = new CountingCompletionHandler<>(vertx, instances);
    aggHandler.setHandler(new Handler<AsyncResult<Void>>() {
      @Override
      public void handle(AsyncResult<Void> res) {
        if (res.failed()) {
          doneHandler.handle(new DefaultFutureResult<String>(res.cause()));
        } else {
          doneHandler.handle(new DefaultFutureResult<>(deploymentID));
        }
      }
    });

    final Deployment deployment = new Deployment(deploymentID, main, modID, instances,
        config == null ? new JsonObject() : config.copy(), urls, modDir, parentDeploymentName,
        mr, autoRedeploy);
    mr.incRef();
    deployments.put(deploymentID, deployment);

    ClassLoader oldTCCL = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(mr.mcl);
    try {
      for (int i = 0; i < instances; i++) {
        // Launch the verticle instance
        Runnable runner = new Runnable() {
          public void run() {
            Verticle verticle;
            try {
              verticle = verticleFactory.createVerticle(main);
            } catch (Throwable t) {
              handleDeployFailure(t, deploymentID, aggHandler);
              return;
            }
            try {
              addVerticle(deployment, verticle, verticleFactory, modID, main);
              if (modDir != null) {
                setPathAdjustment(modDir);
              }
              DefaultFutureResult<Void> vr = new DefaultFutureResult<>();
              verticle.start(vr);
              vr.setHandler(new Handler<AsyncResult<Void>>() {
                @Override
                public void handle(AsyncResult<Void> ar) {
                  if (ar.succeeded()) {
                    aggHandler.complete(ar);
                  } else {
                    handleDeployFailure(ar.cause(), deploymentID, aggHandler);
                  }
                }
              });
            } catch (Throwable t) {
              handleDeployFailure(t, deploymentID, aggHandler);
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

  private void handleDeployFailure(final Throwable t, String deploymentID, final CountingCompletionHandler<Void> handler) {
    // First we undeploy
    doUndeploy(deploymentID, new Handler<AsyncResult<Void>>() {
      public void handle(AsyncResult<Void> res) {
        if (res.failed()) {
          vertx.reportException(res.cause());
        }
        // And pass the *deploy* (not undeploy) Throwable back to the original handler
        handler.complete(new DefaultFutureResult<Void>(t));
      }
    });
  }


  // Must be synchronized since called directly from different thread
  private void addVerticle(Deployment deployment, Verticle verticle,
                           VerticleFactory factory, ModuleIdentifier modID, String main) {
    String loggerName = modID + "-" + main + "-" + System.identityHashCode(verticle);
    Logger logger = LoggerFactory.getLogger(loggerName);
    DefaultContext context = vertx.getContext();
    VerticleHolder holder = new VerticleHolder(deployment, context, verticle,
                                               loggerName, logger, deployment.config,
                                               factory);
    deployment.verticles.add(holder);
    context.setDeploymentHandle(holder);
  }

  private VerticleHolder getVerticleHolder() {
    DefaultContext context = vertx.getContext();
    if (context != null) {
      return (VerticleHolder)context.getDeploymentHandle();
    } else {
      return null;
    }
  }

  private void doUndeploy(String name, final Handler<AsyncResult<Void>> doneHandler) {
    CountingCompletionHandler<Void> count = new CountingCompletionHandler<>(vertx);
    doUndeploy(name, count);
    if (doneHandler != null) {
      count.setHandler(doneHandler);
    }
  }

  private void doUndeploy(String name, final CountingCompletionHandler<Void> parentCount) {
    if (name == null) {
      throw new NullPointerException("deployment id is null");
    }

    final Deployment deployment = deployments.remove(name);
    if (deployment == null) {
      // OK - already undeployed
      parentCount.incRequired();
      parentCount.complete(new DefaultFutureResult<>((Void)null));
      return;
    }

    final CountingCompletionHandler<Void> count = new CountingCompletionHandler<>(vertx);
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
              LoggerFactory.removeLogger(holder.loggerName);
              holder.context.runCloseHooks();
              holder.context.close();
              count.complete(new DefaultFutureResult<>((Void)null));
            } catch (Throwable t) {
              count.complete(new DefaultFutureResult<Void>(t));
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

    count.setHandler(new Handler<AsyncResult<Void>>() {
      public void handle(AsyncResult<Void> res) {
        deployment.moduleReference.decRef();
        parentCount.complete(res);
      }
    });
  }

  public void stop() {
    redeployer.close();
  }

  // For debug only
  public int checkNoModules() {
    int count = 0;
    for (Map.Entry<String, ModuleReference> entry: moduleRefs.entrySet()) {
      if (!entry.getValue().resident) {
        System.out.println("Module remains: " + entry.getKey());
        count++;
      }
    }
    return count;
  }

  public void removeModule(String moduleKey) {
    moduleRefs.remove(moduleKey);
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
