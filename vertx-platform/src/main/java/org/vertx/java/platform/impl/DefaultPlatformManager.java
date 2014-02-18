/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.platform.impl;


import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.impl.ClasspathPathResolver;
import org.vertx.java.core.file.impl.ModuleFileSystemPathResolver;
import org.vertx.java.core.impl.*;
import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.spi.cluster.ClusterManager;
import org.vertx.java.platform.PlatformManagerException;
import org.vertx.java.platform.Verticle;
import org.vertx.java.platform.VerticleFactory;
import org.vertx.java.platform.impl.resolver.*;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
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
  private static final String DEFAULT_LANG_PROPS_FILE_NAME = "default-langs.properties";
  private static final String REPOS_FILE_NAME = "repos.txt";
  private static final String DEFAULT_REPOS_FILE_NAME = "default-repos.txt";
  private static final String LOCAL_MODS_DIR = "mods";
  private static final String SYS_MODS_DIR = "sys-mods";
  private static final String VERTX_HOME_SYS_PROP = "vertx.home";
  private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
  private static final String FILE_SEP = System.getProperty("file.separator");
  private static final String MODULE_NAME_SYS_PROP = System.getProperty("vertx.modulename");
  private static final String CLASSPATH_FILE = "vertx_classpath.txt";
  private static final String SERIALISE_BLOCKING_PROP_NAME = "vertx.serialiseBlockingActions";
  private static final String MODULE_LINK_FILE = "module.link";
  private static final String MOD_JSON_FILE = "mod.json";

  private final VertxInternal vertx;
  // deployment name --> deployment
  protected final Map<String, Deployment> deployments = new ConcurrentHashMap<>();
  // The user mods dir
  private File modRoot;
  private File systemModRoot;
  private String vertxHomeDir;
  private final ConcurrentMap<String, ModuleReference> moduleRefs = new ConcurrentHashMap<>();
  private Redeployer redeployer;
  private final Map<String, LanguageImplInfo> languageImpls = new ConcurrentHashMap<>();
  private final Map<String, String> extensionMappings = new ConcurrentHashMap<>();
  private String defaultLanguageImplName;
  private final List<RepoResolver> repos = new ArrayList<>();
  private Handler<Void> exitHandler;
  private ClassLoader platformClassLoader;
  private boolean disableMavenLocal;
  protected final ClusterManager clusterManager;
  protected HAManager haManager;
  private boolean stopped;
  private final Queue<String> tempDeployments = new ConcurrentLinkedQueue<>();
  private Executor backgroundExec;

  protected DefaultPlatformManager() {
    DefaultVertx v = new DefaultVertx();
    this.vertx = new WrappedVertx(v);
    this.clusterManager = v.clusterManager();
    init();
  }

  protected DefaultPlatformManager(String hostname) {
    DefaultVertx v = new DefaultVertx(hostname);
    this.vertx = new WrappedVertx(v);
    this.clusterManager = v.clusterManager();
    init();
  }

  protected DefaultPlatformManager(int port, String hostname) {
    this.vertx = createVertxSynchronously(port, hostname);
    this.clusterManager = vertx.clusterManager();
    init();
  }

  protected DefaultPlatformManager(int port, String hostname, int quorumSize, String haGroup) {
    this.vertx = createVertxSynchronously(port, hostname);
    this.clusterManager = vertx.clusterManager();
    init();
    this.haManager = new HAManager(vertx, this, clusterManager, quorumSize, haGroup);
  }

  private DefaultPlatformManager(DefaultVertx vertx) {
    this.vertx = new WrappedVertx(vertx);
    this.clusterManager = vertx.clusterManager();
    init();
  }

  private VertxInternal createVertxSynchronously(int port, String hostname) {
    final CountDownLatch latch = new CountDownLatch(1);
    DefaultVertx v = new DefaultVertx(port, hostname, new Handler<AsyncResult<Vertx>>() {
      @Override
      public void handle(AsyncResult<Vertx> result) {
        latch.countDown();
      }
    });
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return new WrappedVertx(v);
  }

  private void init() {
    ClassLoader tccl = Thread.currentThread().getContextClassLoader();
    this.platformClassLoader = tccl != null ? tccl: getClass().getClassLoader();

    if (System.getProperty(SERIALISE_BLOCKING_PROP_NAME, "false").equalsIgnoreCase("true")) {
      this.backgroundExec = new OrderedExecutorFactory(vertx.getBackgroundPool()).getExecutor();
    } else {
      this.backgroundExec = vertx.getBackgroundPool();
    }

    String modDir = System.getProperty(MODS_DIR_PROP_NAME);
    if (modDir != null && !modDir.trim().equals("")) {
      modRoot = new File(modDir);
    } else {
      // Default to local module directory
      modRoot = new File(LOCAL_MODS_DIR);
    }
    vertxHomeDir = System.getProperty(VERTX_HOME_SYS_PROP);
    if (vertxHomeDir == null || modDir != null) {
      systemModRoot = modRoot;
    } else {
      systemModRoot = new File(vertxHomeDir, SYS_MODS_DIR);
    }
    this.redeployer = new Redeployer(vertx, this);
    // If running on CI we don't want to use maven local to get any modules - this is because they can
    // get stale easily - we must always get them from external repos
    this.disableMavenLocal = System.getenv("VERTX_DISABLE_MAVENLOCAL") != null;
    loadLanguageMappings();
    loadRepos();
  }

  @Override
  public void registerExitHandler(Handler<Void> handler) {
    this.exitHandler = handler;
  }

  @Override
  public void deployVerticle(String main,
                             JsonObject config, URL[] classpath,
                             int instances,
                             String includes,
                             Handler<AsyncResult<String>> doneHandler) {
    deployVerticle(false, false, main, config, classpath, instances, includes, doneHandler);
  }

  @Override
  public void deployWorkerVerticle(boolean multiThreaded, String main,
                                   JsonObject config, URL[] classpath,
                                   int instances,
                                   String includes,
                                   Handler<AsyncResult<String>> doneHandler) {
    deployVerticle(true, multiThreaded, main, config, classpath, instances, includes, doneHandler);
  }

  @Override
  public void deployModule(final String moduleName, final JsonObject config,
                           final int instances, final Handler<AsyncResult<String>> doneHandler) {
    deployModuleInternal(moduleName, config, instances, false, doneHandler);
  }

  @Override
  public synchronized void deployModule(final String moduleName, final JsonObject config, final int instances, final boolean ha,
                                        final Handler<AsyncResult<String>> doneHandler) {

    if (ha && haManager != null) {
      final File currentModDir = getDeploymentModDir();
      if (currentModDir != null) {
        throw new IllegalStateException("Only top-level modules can be deployed with HA");
      }
      haManager.deployModule(moduleName, config, instances, doneHandler);
    } else {
      deployModule(moduleName, config, instances, doneHandler);
    }
  }

  @Override
  public void deployModuleFromClasspath(final String moduleName, final JsonObject config,
                                        final int instances, final URL[] classpath,
                                        final Handler<AsyncResult<String>> doneHandler) {
    final Handler<AsyncResult<String>> wrapped = wrapDoneHandler(doneHandler);
    runInBackground(new Runnable() {
      public void run() {
        ModuleIdentifier modID = new ModuleIdentifier(moduleName);
        deployModuleFromCP(null, modID, config, instances, classpath, wrapped);
      }
    }, wrapped);
  }

  @Override
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
        doUndeploy(dep, doneHandler);
      }
    }, wrapDoneHandler(doneHandler));
  }

  @Override
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
          if (res.failed()) {
            count.failed(res.cause());
          } else {
            count.complete();
          }
        }
      });
    }
  }

  @Override
  public Map<String, Integer> listInstances() {
    Map<String, Integer> map = new HashMap<>();
    for (Map.Entry<String, Deployment> entry: deployments.entrySet()) {
      map.put(entry.getKey(), entry.getValue().verticles.size());
    }
    return map;
  }

  @Override
  public void installModule(final String moduleName, final Handler<AsyncResult<Void>> doneHandler) {
    final Handler<AsyncResult<Void>> wrapped = wrapDoneHandler(doneHandler);
    runInBackground(new Runnable() {
      public void run() {
        ModuleIdentifier modID = new ModuleIdentifier(moduleName);
        doInstallMod(modID);
        doneHandler.handle(new DefaultFutureResult<>((Void) null));
      }
    }, wrapped);
  }

  @Override
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
        doneHandler.handle(new DefaultFutureResult<>((Void) null));
      }
    }, wrapped);
  }

  @Override
  public void pullInDependencies(final String moduleName, final Handler<AsyncResult<Void>> doneHandler) {
    final Handler<AsyncResult<Void>> wrapped = wrapDoneHandler(doneHandler);
    runInBackground(new Runnable() {
      public void run() {
        ModuleIdentifier modID = new ModuleIdentifier(moduleName); // Validates it
        doPullInDependencies(modRoot, modID);
        doneHandler.handle(new DefaultFutureResult<>((Void) null));
      }
    }, wrapped);
  }

  @Override
  public void makeFatJar(final String moduleName, final String directory, final Handler<AsyncResult<Void>> doneHandler) {
    final Handler<AsyncResult<Void>> wrapped = wrapDoneHandler(doneHandler);
    runInBackground(new Runnable() {
      public void run() {
        ModuleIdentifier modID = new ModuleIdentifier(moduleName); // Validates it
        doMakeFatJar(modRoot, modID, directory);
        doneHandler.handle(new DefaultFutureResult<>((Void) null));
      }
    }, wrapped);
  }

  @Override
  public void createModuleLink(final String moduleName, final Handler<AsyncResult<Void>> doneHandler) {
    final Handler<AsyncResult<Void>> wrapped = wrapDoneHandler(doneHandler);
    runInBackground(new Runnable() {
      public void run() {
        ModuleIdentifier modID = new ModuleIdentifier(moduleName); // Validates it
        doCreateModuleLink(modRoot, modID);
        doneHandler.handle(new DefaultFutureResult<>((Void) null));
      }
    }, wrapped);
  }

  @Override
  public void reloadModules(final Set<Deployment> deps) {

    // If the module that has changed has been deployed by another module then we redeploy the parent module not
    // the module itself
    // So we must now resolve the set of parent deployments
    final Set<Deployment> parents = new HashSet<>();
    for (Deployment dep: deps) {
      parents.add(getTopMostDeployment(dep));
    }

    runInBackground(new Runnable() {
      public void run() {
        for (final Deployment deployment : parents) {
          if (deployments.containsKey(deployment.name)) {
            doUndeploy(deployment, new Handler<AsyncResult<Void>>() {
              public void handle(AsyncResult<Void> res) {
                if (res.succeeded()) {
                  doRedeploy(deployment);
                } else {
                  log.error("Failed to undeploy", res.cause());
                }
              }
            });
          } else {
            // This might be the case if the previous deployment failed, e.g.
            // a code error in a user verticle
            doRedeploy(deployment);
          }
        }
      }
    }, null);
  }

  @Override
  public Vertx vertx() {
    return this.vertx;
  }

  @Override
  public void deployModuleFromZip(final String zipFileName, final JsonObject config,
                                  final int instances, Handler<AsyncResult<String>> doneHandler) {
    final Handler<AsyncResult<String>> wrapped = wrapDoneHandler(doneHandler);
    runInBackground(new Runnable() {
      public void run() {
        if (zipFileName == null) {
          throw new NullPointerException("zipFileName cannot be null");
        }
        // We unzip the module into a temp directory
        ModuleZipInfo info = new ModuleZipInfo(false, zipFileName);
        ModuleIdentifier modID = new ModuleIdentifier("__vertx~" + UUID.randomUUID().toString() + "~__vertx");
        File modRoot = new File(TEMP_DIR + FILE_SEP + "vertx-zip-mods");
        File tempDir = new File(modRoot, modID.toString());
        tempDir.mkdirs();
        unzipModuleData(tempDir, info, false);
        // And run it from there
        deployModuleFromFileSystem(modRoot, null, modID, config, instances, null, false, wrapped);
        addTmpDeployment(tempDir.getAbsolutePath());
      }
    }, wrapped);
  }

  @Override
  public void exit() {
    // We tell the cluster manager to leave - this is because Hazelcast uses non daemon threads which will prevent
    // JVM exit and shutdown hooks to be called
    vertx.clusterManager().leave();
    if (exitHandler != null) {
      exitHandler.handle(null);
    }
  }

  @Override
  public JsonObject config() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.config;
  }

  @Override
  public Logger logger() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.logger;
  }

  @Override
  public Map<String, Deployment> deployments() {
    return new HashMap<>(deployments);
  }

  @Override
  public void deployModuleInternal(final String moduleName, final JsonObject config,
                                   final int instances, final boolean ha, final Handler<AsyncResult<String>> doneHandler) {
    final File currentModDir = getDeploymentModDir();
    final Handler<AsyncResult<String>> wrapped = wrapDoneHandler(doneHandler);
    runInBackground(new Runnable() {
      public void run() {
        ModuleIdentifier modID = new ModuleIdentifier(moduleName);
        deployModuleFromFileSystem(modRoot, null, modID, config, instances, currentModDir, ha, wrapped);
      }
    }, wrapped);
  }

  private void doRedeploy(final Deployment deployment) {
    runInBackground(new Runnable() {
      public void run() {
        if (deployment.modDir != null) {
          deployModuleFromFileSystem(modRoot, deployment.name, deployment.modID, deployment.config,
              deployment.instances,
              null, deployment.ha, null);
        } else {
          deployModuleFromCP(deployment.name, deployment.modID, deployment.config, deployment.instances, deployment.classpath, null);
        }
      }
    }, null);
  }

  private <T> void runInBackground(final Runnable runnable, final Handler<AsyncResult<T>> doneHandler) {
    final DefaultContext context = vertx.getOrCreateContext();
    backgroundExec.execute(new Runnable() {
      public void run() {
        try {
          vertx.setContext(context);
          runnable.run();
        } catch (Throwable t) {
          if (doneHandler != null) {
            doneHandler.handle(new DefaultFutureResult<T>(t));
          } else {
            log.error("Failed to run task", t);
          }
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
      // Use the current module's/verticle's classpath
      cp = getClasspath();
      if (cp == null) {
        throw new IllegalStateException("Cannot find parent classpath. Perhaps you are deploying the verticle from a non Vert.x thread?");
      }
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

  private URL[] getClasspath() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.deployment.classpath;
  }

  private File getDeploymentModDir() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.deployment.modDir;
  }

  private void doPullInDependencies(File modRoot, ModuleIdentifier modID) {
    File modDir = new File(modRoot, modID.toString());
    if (!modDir.exists()) {
      throw new PlatformManagerException("Cannot find module");
    }
    JsonObject conf = loadModuleConfig(createModJSONFile(modDir), modID);
    if (conf == null) {
      throw new PlatformManagerException("Module " + modID + " does not contain a mod.json");
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

  private String locateJar(String name, ClassLoader cl) {
    if (cl instanceof URLClassLoader) {
      URLClassLoader urlc = (URLClassLoader)cl;
      for (URL url: urlc.getURLs()) {
        String surl = url.toString();
        if (surl.contains("/" + name)) {  // The extra / is critical so we don't confuse hazelcast jar with vertx-hazelcast jar
          String filename = url.getFile();
          if (filename == null || "".equals(filename)) {
            throw new IllegalStateException("Vert.x jars not available as file urls");
          }
          String correctedFileName;
          try {
            correctedFileName = new File(URLDecoder.decode(filename, "UTF-8")).getPath();
          } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
          }
          return correctedFileName;
        }
      }
    }
    if (cl.getParent() != null) {
      return locateJar(name, cl.getParent());
    } else {
      return null;
    }
  }

  private String[] locateJars(ClassLoader cl, String... names) {
    List<String> ljars = new ArrayList<>();
    for (int i = 0; i < names.length; i++) {
      String jar = locateJar(names[i], cl);
      if (jar != null) {
        ljars.add(jar);
      }
    }
    return ljars.toArray(new String[ljars.size()]);
  }

  private void doMakeFatJar(File modRoot, ModuleIdentifier modID, String directory) {
    File modDir = new File(modRoot, modID.toString());
    if (!modDir.exists()) {
      throw new PlatformManagerException("Cannot find module");
    }

    // We need to name the jars explicitly - we can't just grab every jar from the classloader hierarchy
    String[] jars = locateJars(platformClassLoader, "vertx-core", "vertx-platform", "vertx-hazelcast",
                               "netty-all", "jackson-core", "jackson-annotations", "jackson-databind", "hazelcast");

    if (directory == null) {
      directory = ".";
    }

    String tdir = generateTmpFileName();
    File vertxHome = new File(tdir);
    File libDir = new File(vertxHome, "lib");
    vertx.fileSystem().mkdirSync(libDir.getAbsolutePath(), true);
    File modHome = new File(vertxHome, "mods");
    File modDest = new File(modHome, modID.toString());
    vertx.fileSystem().mkdirSync(modDest.getAbsolutePath(), true);

    // Copy module in
    vertx.fileSystem().copySync(modDir.getAbsolutePath(), modDest.getAbsolutePath(), true);

    //Copy vert.x libs in
    for (String jar: jars) {
      Path path = Paths.get(jar);
      String jarName = path.getFileName().toString();
      vertx.fileSystem().copySync(jar, new File(libDir, jarName).getAbsolutePath());
      if (jarName.startsWith("vertx-platform")) {
        // Extract FatJarStarter and put it at the top of the executable jar - this is our main class
        File fatClassDir = new File(vertxHome, "org/vertx/java/platform/impl");
        vertx.fileSystem().mkdirSync(fatClassDir.getAbsolutePath(), true);
        try {
          FileInputStream fin = new FileInputStream(jar);
          BufferedInputStream bin = new BufferedInputStream(fin);
          ZipInputStream zin = new ZipInputStream(bin);
          ZipEntry ze;
          while ((ze = zin.getNextEntry()) != null) {
            String entryName = ze.getName();
            if (entryName.contains("FatJarStarter")) {
              entryName = entryName.substring(entryName.lastIndexOf('/') + 1);
              File fatClassFile = new File(fatClassDir, entryName);
              OutputStream out = new FileOutputStream(fatClassFile);
              byte[] buffer = new byte[4096];
              int len;
              while ((len = zin.read(buffer)) != -1) {
                out.write(buffer, 0, len);
              }
              out.close();
            }
          }
        } catch (Exception e) {
          throw new PlatformManagerException(e);
        }
      }
    }
    // Now create a manifest
    String manifest =
        "Manifest-Version: 1.0\n" +
        "Main-Class: " + FatJarStarter.class.getName() + "\n" +
        "Vertx-Module-ID: " + modID.toString() + "\n";
    vertx.fileSystem().mkdirSync(new File(vertxHome, "META-INF").getAbsolutePath());
    vertx.fileSystem().writeFileSync(new File(vertxHome, "META-INF/MANIFEST.MF").getAbsolutePath(), new Buffer(manifest));

    // Now zip it all up
    File jarName = new File(directory, modID.getName() + "-" + modID.getVersion() + "-fat.jar");
    zipDir(jarName.getPath(), vertxHome.getAbsolutePath());

    // And delete temp dir
    vertx.fileSystem().deleteSync(vertxHome.getAbsolutePath(), true);
  }

  private void doCreateModuleLink(File modRoot, ModuleIdentifier modID) {
    File cpFile = new File(CLASSPATH_FILE);
    if (!cpFile.exists()) {
      throw new PlatformManagerException("Must create a vertx_classpath.txt file first before creating a module link");
    }
    File modDir = new File(modRoot, modID.toString());
    if (modDir.exists()) {
      throw new PlatformManagerException("Module directory " + modDir + " already exists.");
    }
    if (!modDir.mkdirs()) {
      throw new PlatformManagerException("Failed to make directory " + modDir);
    }
    try {
      File currentDir = new File(".").getCanonicalFile();
      vertx.fileSystem().writeFileSync(modDir.getPath() + "/" + MODULE_LINK_FILE, new Buffer(currentDir.getCanonicalPath() + "\n"));
    } catch (IOException e) {
      throw new PlatformManagerException(e);
    }
  }

  private void zipDir(String zipFile, String dirToZip) {
    File dir = new File(dirToZip);
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile))) {
      addDirToZip(dir, dir, out);
    } catch (Exception e) {
      throw new PlatformManagerException("Failed to unzip module", e);
    }
  }

  void addDirToZip(File topDir, File dir, ZipOutputStream out) throws Exception {

    Path top = Paths.get(topDir.getAbsolutePath());

    File[] files = dir.listFiles();
    byte[] buffer = new byte[4096];

    for (int i = 0; i < files.length; i++) {
      Path entry = Paths.get(files[i].getAbsolutePath());
      Path rel = top.relativize(entry);
      String entryName = rel.toString();
      if (files[i].isDirectory()) {
        entryName += FILE_SEP;
      }

      if (!files[i].isDirectory()) {
        out.putNextEntry(new ZipEntry(entryName.replace('\\', '/')));
        try (FileInputStream in = new FileInputStream(files[i])) {
          int bytesRead;
          while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
          }
        }
        out.closeEntry();
      }

      if (files[i].isDirectory()) {
        addDirToZip(topDir, files[i], out);
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

  // Get top-most module deployment for a deployment
  private Deployment getTopMostDeployment(Deployment dep) {
    Deployment topMostMod = dep;
    while (true) {
      String parentDep = dep.parentDeploymentName;
      if (parentDep != null) {
        dep = deployments.get(parentDep);
        if (dep == null) {
          throw new IllegalStateException("Cannot find deployment " + parentDep);
        }
        if (dep.modID != null) {
          topMostMod = dep;
        }
      } else {
        return topMostMod;
      }
    }
  }

  private ModuleReference getModuleReference(String moduleKey, URL[] urls, boolean loadFromModuleFirst) {
    ModuleReference mr = moduleRefs.get(moduleKey);
    if (mr == null) {
      mr = new ModuleReference(this, moduleKey, new ModuleClassLoader(moduleKey, platformClassLoader, urls,
                               loadFromModuleFirst), false);
      ModuleReference prev = moduleRefs.putIfAbsent(moduleKey, mr);
      if (prev != null) {
        mr = prev;
      }
    }
    return mr;
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
    if (urls == null) {
      throw new IllegalStateException("deployment classpath for deploy is null");
    }

    // There is one module class loader per enclosing module + the name of the verticle.
    // If there is no enclosing module, there is one per top level verticle deployment
    // E.g. if a module A deploys "foo.js" as a verticle then all instances of foo.js deployed by the enclosing
    // module will share a module class loader
    String depName = genDepName();
    ModuleIdentifier enclosingModName = getEnclosingModID();
    String moduleKey;
    boolean loadFromModuleFirst;
    if (enclosingModName == null) {
      // We are at the top level - just use the deployment name as the key
      moduleKey = ModuleIdentifier.createInternalModIDForVerticle(depName).toString();
      loadFromModuleFirst = false;
    } else {
      // Use the enclosing module name / or enclosing verticle PLUS the main
      moduleKey = enclosingModName.toString() + "#" + main;
      VerticleHolder holder = getVerticleHolder();
      Deployment dep = holder.deployment;
      loadFromModuleFirst = dep.loadFromModuleFirst;
    }

    ModuleReference mr = getModuleReference(moduleKey, urls, loadFromModuleFirst);

    if (enclosingModName != null) {
      // Add the enclosing module as a parent
      ModuleReference parentRef = moduleRefs.get(enclosingModName.toString());

      if (mr.mcl.addReference(parentRef)) {
        parentRef.incRef();
      }
    }

    if (includes != null) {
      loadIncludedModules(modRoot, currentModDir, mr, includes);
    }
    doDeploy(depName, false, worker, multiThreaded, null, main, null, config, urls, null, instances, currentModDir, mr, modRoot, false,
             loadFromModuleFirst, doneHandler);
  }


  private static void checkWorkerContext() {
    Thread t = Thread.currentThread();
    if (!t.getName().startsWith("vert.x-worker-thread")) {
      throw new IllegalStateException("Not a worker thread");
    }
  }

  private InputStream findLangsFile() {
    // First we look for langs.properties on the classpath
    InputStream is = platformClassLoader.getResourceAsStream(LANG_PROPS_FILE_NAME);
    if (is == null) {
      // Now we look for default-langs.properties which is included in the vertx-platform.jar
      is = platformClassLoader.getResourceAsStream(DEFAULT_LANG_PROPS_FILE_NAME);
    }
    return is;
  }

  private void loadLanguageMappings() {
    // The only language that Vert.x understands out of the box is Java, so we add the default runtime and
    // extension mapping for that. This can be overridden in langs.properties
    languageImpls.put("java", new LanguageImplInfo(null, "org.vertx.java.platform.impl.java.JavaVerticleFactory"));
    extensionMappings.put("java", "java");
    extensionMappings.put("class", "java");
    defaultLanguageImplName = "java";

   /*
    First try loading mappings from the LANG_PROPS_FILE_NAMEs file
    This file is structured as follows:
       It should contain one line for every language implementation that is to be used with Vert.x
         That line should be structured as follows:
           <lang_impl_name>=[module_name:]<factory_name>
         Where:
           <lang_impl_name> is the name you want to give to the language implementation, e.g. 'jython'
           module_name is the (optional) name of a module that contains the language implementation
             if ommitted it will be assumed the language implementation is included as part of the Vert.x installation
             - this is only true for the Java implementation
             if included the module_name should be followed by a colon
           factory_name is the FQCN of a VerticleFactory for the language implementation
         Examples:
           rhino=vertx.lang-rhino-v1.0.0:org.vertx.java.platform.impl.rhino.RhinoVerticleFactory
           java=org.vertx.java.platform.impl.java.JavaVerticleFactory
       The file should also contain one line for every extension mapping - this maps a file extension to
       a <lang_impl_name> as specified above
         Examples:
           .js=rhino
           .rb=jruby
       The file can also contain a line representing the default language runtime to be used when no extension or
       prefix maps, e.g.
         .=java
    */

    try (InputStream is = findLangsFile()) {
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
        LanguageImplInfo langImpl = parseLangImplInfo(propVal);
        languageImpls.put(propName, langImpl);
        extensionMappings.put(propName, propName); // automatically register the name as a mapping
      }
    }
  }

  private LanguageImplInfo parseLangImplInfo(String line) {
    // value is made up of an optional module name followed by colon followed by the
    // FQCN of the factory
    int colonIndex = line.lastIndexOf(COLON);
    String moduleName;
    String factoryName;
    if (colonIndex != -1) {
      moduleName = line.substring(0, colonIndex);
      factoryName = line.substring(colonIndex + 1);
    } else {
      throw new PlatformManagerException("Language mapping: " + line + " does not specify an implementing module");
    }
    return new LanguageImplInfo(moduleName, factoryName);
  }

  private File locateModule(File modRoot, File currentModDir, ModuleIdentifier modID) {
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

  private void deployModuleFromModJson(JsonObject modJSON, String depName, ModuleIdentifier modID,
                                       JsonObject config,
                                       int instances,
                                       File modDir,
                                       File currentModDir,
                                       List<URL> moduleClasspath,
                                       File modRoot,
                                       boolean ha,
                                       final Handler<AsyncResult<String>> doneHandler) {
    ModuleFields fields = new ModuleFields(modJSON);
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

    ModuleReference mr = moduleRefs.get(modID.toString());
    if (mr == null) {
      boolean res = fields.isResident();
      mr = new ModuleReference(this, modID.toString(),
          new ModuleClassLoader(modID.toString(), platformClassLoader, moduleClasspath.toArray(new URL[moduleClasspath.size()]),
                                fields.isLoadFromModuleFirst()),
          res);
      ModuleReference prev = moduleRefs.putIfAbsent(modID.toString(), mr);
      if (prev != null) {
        mr = prev;
      }
    }
    ModuleIdentifier enclosingModID = getEnclosingModID();
    if (enclosingModID != null) {
      // If enclosed in another module then the enclosing module classloader becomes a parent of this one
      ModuleReference parentRef = moduleRefs.get(enclosingModID.toString());
      if (mr.mcl.addReference(parentRef)) {
        parentRef.incRef();
      }
    }

    // Now load any included moduleRefs
    String includes = fields.getIncludes();
    // We also need to get the total set of urls for any included modules as these will have to watched too
    // for auto-redeploy
    URL[] includedCP = null;
    if (includes != null) {
      List<URL> includedCPList = loadIncludedModules(modRoot, modDir, mr, includes);
      if (!includedCPList.isEmpty()) {
        includedCP = includedCPList.toArray(new URL[includedCPList.size()]);
      }
    }

    final boolean autoRedeploy = fields.isAutoRedeploy();

    doDeploy(depName, autoRedeploy, worker, multiThreaded, fields.getLangMod(), main, modID, config,
        moduleClasspath.toArray(new URL[moduleClasspath.size()]), includedCP, instances, modDirToUse, mr,
        modRoot, ha, fields.isLoadFromModuleFirst(), new Handler<AsyncResult<String>>() {
      @Override
      public void handle(AsyncResult<String> res) {
        if (res.succeeded()) {
          String deploymentID = res.result();
          if (deploymentID != null && autoRedeploy) {
            redeployer.moduleDeployed(deployments.get(deploymentID));
          }
        }
        if (doneHandler != null) {
          doneHandler.handle(res);
        } else if (res.failed()) {
          log.error("Failed to deploy", res.cause());
        }
      }
    });
  }

  private JsonObject loadModJSONFromClasspath(ModuleIdentifier modID, ClassLoader cl) {
    URL url = cl.getResource(MOD_JSON_FILE);
    if (url == null) {
      return null;
    }
    return loadModJSONFromURL(modID, url);
  }

  private JsonObject loadModJSONFromURL(ModuleIdentifier modID, URL url) {
    try {
      try (Scanner scanner = new Scanner(url.openStream(), "UTF-8").useDelimiter("\\A")) {
        String conf = scanner.next();
        return new JsonObject(conf);
      } catch (NoSuchElementException e) {
        throw new PlatformManagerException("Module " + modID + " contains an empty mod.json file");
      } catch (DecodeException e) {
        throw new PlatformManagerException("Module " + modID + " mod.json contains invalid json");
      }
    } catch (IOException e) {
      return null;
    }
  }

  private void deployModuleFromCP(String depName, ModuleIdentifier modID,
                                  JsonObject config,
                                  int instances,
                                  URL[] classpath,
                                  final Handler<AsyncResult<String>> doneHandler) {
    checkWorkerContext();
    JsonObject modJSON = loadModJSONFromClasspath(modID, new URLClassLoader(classpath, platformClassLoader));
    if (modJSON == null) {
      throw new PlatformManagerException("Failed to find mod.json on classpath");
    }
    List<URL> cpList = new ArrayList<>(Arrays.asList(classpath));
    deployModuleFromModJson(modJSON, depName, modID, config, instances, null, null, cpList, modRoot, false,
        doneHandler);
  }

  private static class ModuleInfo {
    final JsonObject modJSON;
    final List<URL> cp;
    final File modDir;

    private ModuleInfo(JsonObject modJSON, List<URL> cp, File modDir) {
      this.modJSON = modJSON;
      this.cp = cp;
      this.modDir = modDir;
    }
  }

  private ModuleInfo loadModuleInfo(File modDir, ModuleIdentifier modID) {
    List<URL> cpList;
    JsonObject modJSON;
    File modJSONFile = createModJSONFile(modDir);
    if (!modJSONFile.exists()) {
      // Look for link file
      File linkFile = new File(modDir, MODULE_LINK_FILE);
      if (linkFile.exists()) {
        // Load the path from the file
        try (Scanner scanner = new Scanner(linkFile, "UTF-8").useDelimiter("\\A")) {
          String path = scanner.next().trim();
          File cpFile = new File(path, CLASSPATH_FILE);
          if (!cpFile.exists()) {
            throw new PlatformManagerException("Module link file: " + linkFile + " points to path without vertx_classpath.txt");
          }
          // Load the cp
          cpList = new ArrayList<>();
          try (Scanner scanner2 = new Scanner(cpFile, "UTF-8")) {
            while (scanner2.hasNextLine()) {
              String entry = scanner2.nextLine().trim();
              if (!entry.startsWith("#") && !entry.equals("")) {  // Skip blanks lines and comments
                File fentry = new File(entry);
                if (!fentry.isAbsolute()) {
                  fentry = new File(path, entry);
                }
                URL url = fentry.toURI().toURL();
                cpList.add(url);
                if (fentry.exists() && fentry.isDirectory()) {
                  File[] files = fentry.listFiles();
                  for (File file : files) {
                    String fPath = file.getCanonicalPath();
                    if (fPath.endsWith(".jar") || fPath.endsWith(".zip")) {
                      cpList.add(file.getCanonicalFile().toURI().toURL());
                    }
                  }
                }
              }
            }
          } catch (Exception e) {
            e.printStackTrace();
            throw new PlatformManagerException(e);
          }
          ClassLoader cl = new ModJSONClassLoader(cpList.toArray(new URL[cpList.size()]), platformClassLoader);
          URL url = cl.getResource(MOD_JSON_FILE);
          if (url != null) {
            // Find the file for the url
            Path p = ClasspathPathResolver.urlToPath(url);
            Path parent = p.getParent();
            // And we set the directory where mod.json is as the module directory
            modDir = parent.toFile();
            modJSON = loadModJSONFromURL(modID, url);
          } else {
            throw new PlatformManagerException("Cannot find mod.json");
          }
        } catch (Exception e) {
          throw new PlatformManagerException(e);
        }
      } else {
        throw new PlatformManagerException("Module directory " + modDir + " contains no mod.json nor module.link file");
      }
    } else {
      modJSON = loadModuleConfig(modJSONFile, modID);
      cpList = getModuleClasspath(modDir);
    }
    return new ModuleInfo(modJSON, cpList, modDir);
  }


  private void deployModuleFromFileSystem(File modRoot, String depName, ModuleIdentifier modID,
                                          JsonObject config,
                                          int instances, File currentModDir, boolean ha,
                                          Handler<AsyncResult<String>> doneHandler) {
    checkWorkerContext();
    File modDir = locateModule(modRoot, currentModDir, modID);
    if (modDir != null) {
      // The module exists on the file system
      ModuleInfo info = loadModuleInfo(modDir, modID);
      deployModuleFromModJson(info.modJSON, depName, modID, config, instances, info.modDir, currentModDir, info.cp, modRoot, ha, doneHandler);
    } else {
      JsonObject modJSON;
      if (modID.toString().equals(MODULE_NAME_SYS_PROP)) {
        // This vertx.modulename sys prop will be set if running a test using the testtools custom JUnit class runner
        // In this case, if we're trying to deploy the module that's being tested then we can look for it on the
        // platform classloader - this is useful for example when we
        // are running tests in an IDE and want to do this without having to build the module into the mods dir first
        modJSON = loadModJSONFromClasspath(modID, platformClassLoader);
      } else {
        modJSON = null;
      }
      if (modJSON != null) {
        deployModuleFromModJson(modJSON, depName, modID, config, instances, modDir, currentModDir,
            new ArrayList<URL>(), modRoot, ha, doneHandler);
      } else {
        // Try and install it then deploy from file system
        doInstallMod(modID);
        deployModuleFromFileSystem(modRoot, depName, modID, config, instances, currentModDir, ha, doneHandler);
      }
    }
  }

  private JsonObject loadModuleConfig(File modJSON, ModuleIdentifier modID) {
    // Checked the byte code produced, .close() is called correctly, so the warning can be suppressed
    try (Scanner scanner = new Scanner(modJSON, "UTF-8").useDelimiter("\\A")) {
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

  private List<URL> loadIncludedModules(File modRoot, File currentModuleDir, ModuleReference mr, String includesString) {
    Set<String> included = new HashSet<>();
    List<URL> includedCP = new ArrayList<>();
    included.add(mr.moduleKey);
    doLoadIncludedModules(modRoot, currentModuleDir, mr, includesString, included, includedCP);
    return includedCP;
  }

  private void doLoadIncludedModules(File modRoot, File currentModuleDir, ModuleReference mr, String includesString,
                                     Set<String> included, List<URL> includedCP) {
    checkWorkerContext();
    for (String moduleName: parseIncludeString(includesString)) {
      ModuleIdentifier modID = new ModuleIdentifier(moduleName);
      if (included.contains(modID.toString())) {
        log.warn("Module " + modID + " is included more than once in chain of includes");
      } else {
        included.add(modID.toString());
        ModuleReference includedMr = moduleRefs.get(moduleName);
        if (includedMr == null) {
          File modDir = locateModule(modRoot, currentModuleDir, modID);
          if (modDir == null) {
            doInstallMod(modID);
          }
          modDir = locateModule(modRoot, currentModuleDir, modID);
          ModuleInfo info = loadModuleInfo(modDir, modID);
          ModuleFields fields = new ModuleFields(info.modJSON);
          includedCP.addAll(info.cp);

          boolean res = fields.isResident();
          includedMr = new ModuleReference(this, moduleName,
              new ModuleClassLoader(modID.toString(), platformClassLoader,
                  info.cp.toArray(new URL[info.cp.size()]), fields.isLoadFromModuleFirst()),
              res);
          ModuleReference prev = moduleRefs.putIfAbsent(moduleName, includedMr);
          if (prev != null) {
            includedMr = prev;
          }
          String includes = fields.getIncludes();
          if (includes != null) {
            doLoadIncludedModules(modRoot, modDir, includedMr, includes, included, includedCP);
          }
        }
        if (mr.mcl.addReference(includedMr)) {
          includedMr.incRef();
        }

      }
    }
  }

  private List<URL> getModuleClasspath(File modDir) {
    List<URL> urls = new ArrayList<>();
    // Add the classpath for this module
    try {
      if (modDir.exists()) {
        urls.add(modDir.toURI().toURL());
        File libDir = new File(modDir, "lib");
        if (libDir.exists()) {
          File[] jars = libDir.listFiles();
          for (File jar: jars) {
            URL jarURL = jar.toURI().toURL();
            urls.add(jarURL);
          }
        }
      }
      return urls;
    } catch (MalformedURLException e) {
      //Won't happen
      throw new PlatformManagerException(e);
    }
  }

  private static String[] parseIncludeString(String sincludes) {
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

  private InputStream findReposFile() {
    // First we look for repos.txt on the classpath
    InputStream is = platformClassLoader.getResourceAsStream(REPOS_FILE_NAME);
    if (is == null) {
      // Now we look for repos-default.txt which is included in the vertx-platform.jar
      is = platformClassLoader.getResourceAsStream(DEFAULT_REPOS_FILE_NAME);
    }
    return is;
  }

  private void loadRepos() {
    try (InputStream is = findReposFile()) {
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
      log.error("Failed to load repos file ",e);
    }
  }

  private void doInstallMod(final ModuleIdentifier modID) {
    checkWorkerContext();
    if (repos.isEmpty()) {
      throw new PlatformManagerException("No repositories configured!");
    }
    if (locateModule(modRoot, null, modID) != null) {
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

  private static String generateTmpFileName() {
    return TEMP_DIR + FILE_SEP + "vertx-" + UUID.randomUUID().toString();
  }

  static File unzipIntoTmpDir(ModuleZipInfo zipInfo, boolean deleteZip) {
    String tdir = generateTmpFileName();
    File tdest = new File(tdir);
    if (!tdest.mkdir()) {
      throw new PlatformManagerException("Failed to create directory " + tdest);
    }
    unzipModuleData(tdest, zipInfo, deleteZip);
    return tdest;
  }

  private void checkCreateModDirs() {
    checkCreateRoot(modRoot);
    checkCreateRoot(systemModRoot);
  }

  private void checkCreateRoot(File modRoot) {
    if (!modRoot.exists()) {
      String smodRoot;
      try {
        smodRoot = modRoot.getCanonicalPath();
      } catch (IOException e) {
        throw new PlatformManagerException(e);
      }
      vertx.fileSystem().mkdirSync(smodRoot, true);
    }
  }

  private File createModJSONFile(File modDir) {
    return new File(modDir, MOD_JSON_FILE);
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
      JsonObject conf = loadModuleConfig(createModJSONFile(tdest), modID);
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

  private static String removeTopDir(String entry) {
    int pos = entry.indexOf(FILE_SEP);
    if (pos != -1) {
      entry = entry.substring(pos + 1);
    }
    return entry;
  }

  static private void unzipModuleData(final File directory, final ModuleZipInfo zipinfo, boolean deleteZip) {
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
  // wherever vertx run or vertx runmod was called from
  private void setPathResolver(File modDir) {
    DefaultContext context = vertx.getContext();
    if (modDir != null) {
      // Module deployed from file system or verticle deployed by module deployed from file system
      Path pmodDir = Paths.get(modDir.getAbsolutePath());
      context.setPathResolver(new ModuleFileSystemPathResolver(pmodDir));
    } else  {
      // Module deployed from classpath or verticle deployed from module deployed from classpath
      context.setPathResolver(new ClasspathPathResolver());
    }
  }

  private static String genDepName() {
    return "deployment-" + UUID.randomUUID().toString();
  }

  private void doDeploy(final String depID,
                        boolean autoRedeploy,
                        boolean worker, boolean multiThreaded,
                        String langMod,
                        String theMain,
                        final ModuleIdentifier modID,
                        final JsonObject config,
                        final URL[] urls,
                        final URL[] includedURLs,
                        int instances,
                        final File modDir,
                        final ModuleReference mr,
                        final File modRoot,
                        final boolean ha,
                        final boolean loadFromModuleFirst,
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
    // 1. Look for the optional 'lang-impl' field - this can be used to specify the exact language module to use
    // 2. Look for a prefix on the main, e.g. 'groovy:org.foo.myproject.MyGroovyMain' would force the groovy
    //    language impl to be used
    // 3. If there is no prefix, then look at the extension, if any. If there is an extension mapping for that
    //    extension, use that.
    // 4. No prefix and no extension mapping - use the default runtime

    LanguageImplInfo langImplInfo = null;

    // Get language from lang-mod field if provided
    if (langMod != null) {
      langImplInfo = parseLangImplInfo(langMod);
    }

    final String main;
    // Look for a prefix
    int prefixMarker = theMain.indexOf(COLON);
    if (prefixMarker != -1) {
      String prefix = theMain.substring(0, prefixMarker);
      if (langImplInfo == null) {
        langImplInfo = languageImpls.get(prefix);
        if (langImplInfo == null) {
          throw new IllegalStateException("No language implementation known for prefix " + prefix);
        }
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
      loadIncludedModules(modRoot, modDir, mr, langImplInfo.moduleName);
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

    ClassLoader oldTCCL = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(mr.mcl);

    try {
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
        config == null ? new JsonObject() : config.copy(), urls, includedURLs, modDir, parentDeploymentName,
        mr, autoRedeploy, ha, loadFromModuleFirst);
    mr.incRef();

    deployments.put(deploymentID, deployment);

    try {
      for (int i = 0; i < instances; i++) {
        // Launch the verticle instance
        Runnable runner = new Runnable() {
          public void run() {
            Verticle verticle;
            try {
              verticle = verticleFactory.createVerticle(main);
            } catch (Throwable t) {
              handleDeployFailure(t, deployment, aggHandler);
              return;
            }
            try {
              addVerticle(deployment, verticle, verticleFactory, modID, main);
              setPathResolver(modDir);
              DefaultFutureResult<Void> vr = new DefaultFutureResult<>();

              verticle.start(vr);
              vr.setHandler(new Handler<AsyncResult<Void>>() {
                @Override
                public void handle(AsyncResult<Void> ar) {
                  if (ar.succeeded()) {
                    aggHandler.complete();
                  } else {
                    handleDeployFailure(ar.cause(), deployment, aggHandler);
                  }
                }
              });
            } catch (Throwable t) {
              handleDeployFailure(t, deployment, aggHandler);
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

  private void handleDeployFailure(final Throwable t, Deployment dep, final CountingCompletionHandler<Void> handler) {
    // First we undeploy
    doUndeploy(dep, new Handler<AsyncResult<Void>>() {
      public void handle(AsyncResult<Void> res) {
        if (res.failed()) {
          vertx.reportException(res.cause());
        }
        // And pass the *deploy* (not undeploy) Throwable back to the original handler
        handler.failed(t);
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

  private void doUndeploy(final Deployment dep, final Handler<AsyncResult<Void>> doneHandler) {
    CountingCompletionHandler<Void> count = new CountingCompletionHandler<>(vertx);
    doUndeploy(dep.name, count);
    count.setHandler(doneHandler);
  }

  private void doUndeploy(String name, final CountingCompletionHandler<Void> parentCount) {
    if (name == null) {
      throw new NullPointerException("deployment id is null");
    }

    final Deployment deployment = deployments.remove(name);
    if (deployment == null) {
      // OK - already undeployed
      parentCount.incRequired();
      parentCount.complete();
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
            } catch (Throwable t) {
              // If an exception is thrown from stop() it's possible the logger system has already shut down so we must
              // report it on stderr too
              System.err.println("Failure in stop()");
              t.printStackTrace();
            }
            LoggerFactory.removeLogger(holder.loggerName);
            holder.context.runCloseHooks(new AsyncResultHandler<Void>() {
              @Override
              public void handle(AsyncResult<Void> asyncResult) {
                holder.context.close();
                runInBackground(new Runnable() {
                  public void run() {
                    if (deployment.modID != null && deployment.autoRedeploy) {
                      redeployer.moduleUndeployed(deployment);
                    }
                    if (deployment.ha && haManager != null) {
                      haManager.removeFromHA(deployment.name);
                    }
                    count.complete();
                  }
                }, new Handler<AsyncResult<Void>>() {
                     public void handle(AsyncResult<Void> res) {
                       if (res.failed()) {
                         count.failed(res.cause());
                       } else {
                         count.complete();
                       }
                     }
                 });
              }
            });
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
        if (res.failed()) {
          parentCount.failed(res.cause());
        } else {
          parentCount.complete();
        }
      }
    });
  }

  private void addTmpDeployment(String tmp) {
    tempDeployments.add(tmp);
  }

  private void deleteTmpDeployments() {
    String tmp;
    while ((tmp = tempDeployments.poll()) != null) {
      try {
        vertx.fileSystem().deleteSync(tmp, true);
      } catch (Throwable t) {
        log.error("Failed to delete temp deployment", t);
      }
    }
  }

  public void stop() {
    if (stopped) {
      return;
    }
    if (haManager != null) {
      haManager.stop();
    }
    redeployer.close();
    deleteTmpDeployments();
    vertx.stop();
    stopped = true;
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

  static final class ModuleZipInfo {
    final boolean oldStyle;
    final String filename;

    ModuleZipInfo(boolean oldStyle, String filename) {
      this.oldStyle = oldStyle;
      this.filename = filename;
    }
  }

  private static class ModJSONClassLoader extends URLClassLoader {

    private final ClassLoader parent;

    private ModJSONClassLoader(URL[] urls, ClassLoader parent) {
      super(urls, parent);
      this.parent = parent;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
      List<URL> resources = new ArrayList<>(Collections.list(findResources(name)));
      if (parent != null) {
        resources.addAll(Collections.list(parent.getResources(name)));
      }
      return Collections.enumeration(resources);
    }
  }

}
