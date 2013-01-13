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

import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.ActionFuture;
import org.vertx.java.core.impl.BlockingAction;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.impl.VertxThreadFactory;
import org.vertx.java.core.impl.VertxThreadFactory.VertxThread;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.Args;
import org.vertx.java.deploy.Container;
import org.vertx.java.deploy.Verticle;
import org.vertx.java.deploy.VerticleFactory;

import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class could benefit from some refactoring
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Juergen Donnerstag
 */
public class VerticleManager implements ModuleReloader {

	private static final Logger log = LoggerFactory.getLogger(VerticleManager.class);

	public static final String LANGS_PROPERTIES_FILE_NAME = "langs.properties";

	private final VertxInternal vertx;
	private final Deployments deployments = new Deployments();
	// TODO remove
	private final CountDownLatch stopLatch = new CountDownLatch(1);
	private Map<String, String> factoryNames = new HashMap<>();
	private final Redeployer redeployer;
	private final ModuleManager moduleManager;

	public VerticleManager(VertxInternal vertx) {
		this(vertx, new ModuleManager(vertx));
	}

	public VerticleManager(VertxInternal vertx, ModuleManager moduleManager) {
		this.vertx = Args.notNull(vertx, "vertx");
		this.moduleManager = Args.notNull(moduleManager, "moduleManager");
		this.redeployer = newRedeployer(vertx, this.moduleManager.modRoot());

		initFactoryNames(factoryNames);

		// TODO doesn't fit the explanation given in VertxLocator
		VertxLocator.vertx = vertx;
		VertxLocator.container = new Container(this);
	}

	protected Redeployer newRedeployer(final VertxInternal vertx, final File modRoot) {
		return new Redeployer(vertx, modRoot, this);
	}

	protected void initFactoryNames(final Map<String, String> factoryNames) {
		// TODO change to use VertxConfig
		try (InputStream is = getClass().getClassLoader().getResourceAsStream(LANGS_PROPERTIES_FILE_NAME)) {
			if (is == null) {
				log.warn("No language mappings found: file: " + LANGS_PROPERTIES_FILE_NAME);
			} else {
				Properties props = new Properties();
				props.load(new BufferedInputStream(is));
				Enumeration<?> en = props.propertyNames();
				while (en.hasMoreElements()) {
					String propName = (String) en.nextElement();
					factoryNames.put(propName, props.getProperty(propName));
				}
			}
		} catch (IOException e) {
			log.error("Failed to load language properties from: " + LANGS_PROPERTIES_FILE_NAME, e);
		}
	}

	public final Redeployer redeployer() {
		return redeployer;
	}

	public final ModuleManager moduleManager() {
		return moduleManager;
	}

	public void stop() {
		redeployer.close();
	}

	public final VertxInternal vertx() {
		return vertx;
	}

	public final Deployment deployment(String name) {
		return deployments.get(name);
	}

	// TODO remove
	public void block() {
		while (true) {
			try {
				stopLatch.await();
				break;
			} catch (InterruptedException e) {
				// Ignore
			}
		}
	}

	// TODO remove
	public void unblock() {
		stopLatch.countDown();
	}

	// TODO don't like
	public JsonObject getConfig() {
		VerticleHolder holder = getVerticleHolder();
		return holder == null ? null : holder.config;
	}

	public String getDeploymentName() {
		VerticleHolder holder = getVerticleHolder();
		return holder == null ? null : holder.deployment.name;
	}

	public List<URI> getDeploymentURLs() {
		VerticleHolder holder = getVerticleHolder();
		return holder == null ? null : holder.deployment.module.classPath();
	}

	public File getDeploymentModDir() {
		VerticleHolder holder = getVerticleHolder();
		return holder == null ? null : holder.deployment.module.modDir();
	}

	public Logger getLogger() {
		VerticleHolder holder = getVerticleHolder();
		return holder == null ? null : holder.logger;
	}

	private VerticleHolder getVerticleHolder() {
		Context context = vertx.getContext();
		if (context != null) {
			return (VerticleHolder) context.getDeploymentHandle();
		}
		return null;
	}

	/**
	 * (Async) Deploy ... Old API
	 */
	@Deprecated
	public ActionFuture<Void> deployVerticle(final boolean worker, final String main, final JsonObject config,
			final List<URI> urls, final int instances, final File currentModDir, final String includes,
			final Handler<String> doneHandler) {

		BlockingAction<Void> deployModuleAction = new BlockingAction<Void>(vertx, null) {
			@Override
			public Void action() throws Exception {
				doDeployVerticle(worker, main, config, urls, instances, currentModDir, includes, wrapDoneHandler(doneHandler));
				return null;
			}

			@Override
			protected void handle(AsyncResult<Void> result) {
				if (result.failed()) {
					log.error("Failed to install verticle: " + main, result.exception);
				}
			}
		};

		return deployModuleAction.run();
	}

	/**
	 * Old API.
	 */
	@Deprecated
	private ActionFuture<String> doDeployVerticle(boolean worker, final String main, final JsonObject config,
			final List<URI> urls, final int instances, final File currentModDir, final String includes,
			final Handler<String> doneHandler) {

		VertxModule module = new VertxModule(moduleManager, null);
		module.config(new ModuleConfig(config));
		module.config().worker(worker);
		module.config().main(main);
		module.classPath(urls, false);
		module.config().includes(includes);

		return deploy(null, module, instances, currentModDir, doneHandler);
	}

	/**
	 * (Async) Deploy a Module
	 */
	// @TODO Not sure what this old API is good for. It kind of doesn't make sense
	// to replace the config
	@Deprecated
	public final ActionFuture<Void> deployMod(final String modName, final JsonObject config, final int instances,
			final File currentModDir, final Handler<String> doneHandler) {

		// TODO This is ugly. Make install() async as well
		BlockingAction<Void> deployModuleAction = new BlockingAction<Void>(vertx()) {
			@Override
			public Void action() throws Exception {
				doDeployMod(false, null, modName, config, instances, currentModDir, wrapDoneHandler(doneHandler));
				return null;
			}

			@Override
			protected void handle(AsyncResult<Void> result) {
				if (result.failed()) {
					log.error("Failed to install module: " + modName, result.exception);
				}
			}
		};

		return deployModuleAction.run();
	}

	/**
	 * Deploy a Module identified by name. If config is not null, it'll
	 * <b>replace</b> the one found on the filesystem
	 */
	// @TODO Not sure what this old API is good for. It kind of doesn't make sense
	// to replace the config
	@Deprecated
	public ActionFuture<String> doDeployMod(final boolean redeploy, final String depName, final String modName,
			final JsonObject config, final int instances, final File currentModDir, final Handler<String> doneHandler) {

		VertxModule module = new VertxModule(moduleManager, modName);
		if (config != null) {
			module.config(new ModuleConfig(config));
		}
		module.config().autoRedeploy(redeploy);

		return deploy(depName, module, instances, currentModDir, doneHandler);
	}

	/**
	 * Deploy a Module identified by name. All module information are assumed to
	 * be in the 'mods' directory. If not, the module will searched and downloaded
	 * from any of the registered repositories.
	 */
	public ActionFuture<String> deploy(final String depName, final String modName, final int instances,
			final File currentModDir, final Handler<String> doneHandler) {

		VertxModule module = moduleManager.module(modName);
		return deploy(depName, module, instances, currentModDir, doneHandler);
	}

	/**
	 * Deploy a Module / Verticle.
	 * <p>
	 * If the deployment name is <code>null</code>, one will be automatically
	 * assigned.
	 * <p>
	 * Parameter <code>module</code> must not be <code>null</code>. If the module
	 * name is not null, than it is assume to be a loadable module from the
	 * filesystem. If not found, registered repositories will be searched. If
	 * module name is null, only the required modules (includes) are checked and
	 * downloaded if needed.
	 * <p>
	 */
	public final ActionFuture<String> deploy(final String depName, final VertxModule module, final int instances,
			File modDir, final Handler<String> doneHandler) {

		Args.notNull(module, "module");

		checkWorkerContext();

		final ActionFuture<String> future = new ActionFuture<>();

		if (module.name() != null) {
			AsyncResult<Void> res = module.install(null).get(30, TimeUnit.SECONDS);
			if (res == null) {
				callDoneHandler(doneHandler, null);
				return future.countDown(log, "Module install failed: " + module.name());
			} else if (res.failed()) {
				callDoneHandler(doneHandler, null);
				return future.countDown(log, "Module install failed: " + module.name());
			}

			ModuleDependencies deps = module.checkDependencies();
			if (deps.failed()) {
				callDoneHandler(doneHandler, null);
				return future.countDown(log, "Module install failed: " + module.name());
			}

			if (!module.exists()) {
				String msg = "Installed the module '" + module.name() + "'. But still unable to load config";
				callDoneHandler(doneHandler, null);
				return future.countDown(log, msg);
			}
		}

		ModuleConfig conf = module.config();
		final String main = conf.main();
		if (main == null) {
			String msg = "Runnable module " + module.name() + " mod.json must contain a \"main\" field";
			callDoneHandler(doneHandler, null);
			return future.countDown(log, msg);
		}

		final File currentModDir;
		if (conf.preserveCwd()) {
			currentModDir = modDir;
		} else {
			currentModDir = conf.modDir();
		}

		String factoryName = getLanguageFactoryName(module.config().main());
		String parentDepName = getDeploymentName();
		final Deployment deployment = createDeployment(depName, instances, currentModDir, parentDepName, module);
		if (deployment == null) {
			callDoneHandler(doneHandler, null);
			return future.countDown(log, "createDeployment() must not return null");
		}

		final boolean worker = deployment.module.config().worker();

		if (log.isInfoEnabled()) {
			log.info("New Deployment: " + deployment.name + "; main: " + main + "; instances: " + instances);
		}

		deployments.add(deployment);

		class AggHandler {
			AtomicInteger count = new AtomicInteger(0);
			boolean failed;

			void done(boolean res) {
				if (!res) {
					failed = true;
				}
				if (count.incrementAndGet() == instances) {
					if (failed) {
						callDoneHandler(doneHandler, null);
						future.countDown(log, "Failure while deploying Verticle / Module");
					} else {
						callDoneHandler(doneHandler, deployment.name);
						future.countDown(new AsyncResult<String>(deployment.name));
					}
				}
			}
		}

		final AggHandler aggHandler = new AggHandler();

		// Workers share a single classloader with all instances in a deployment -
		// this
		// enables them to use libraries that rely on caching or statics to share
		// state
		// (e.g. JDBC connection pools)
		URL[] urls = deployment.module.classPath2();
		@SuppressWarnings("resource")
		final ClassLoader sharedLoader = worker ? createParentLastURLClassLoader(urls) : null;

		// Launch verticle instances
		for (int i = 0; i < instances; i++) {
			// One classloader per Verticle, except for workers
			final ClassLoader cl = sharedLoader != null ? sharedLoader : createParentLastURLClassLoader(urls);
			if (cl == null) {
				return future.countDown(log, "Verticle class loader must not be null");
			}

			Thread.currentThread().setContextClassLoader(cl);

			// We load the VerticleFactory class using the verticle classloader - this
			// allows
			// us to put language implementations in modules
			final VerticleFactory verticleFactory = getVerticleFactory(cl, factoryName);
			if (verticleFactory == null) {
				return future.countDown(log, "getVerticleFactory must not return null");
			}

			Runnable runner = new Runnable() {
				public void run() {
					Verticle verticle = null;
					try {
						verticle = verticleFactory.createVerticle(main, cl);
					} catch (Exception e) {
						log.error("Failed to create verticle: " + main, e);
					}

					if (verticle == null) {
						undeploy(deployment.name, new Handler<Deployment>() {
							@Override
							public void handle(final Deployment dep) {
								aggHandler.done(false);
							}
						});
						return;
					}

					// Inject vertx
					verticle.setVertx(vertx);
					// TODO not sure we need one Container for each Verticle. It looks
					// more like one per
					// VerticleManager, but that would not be necessary if VerticleManager
					// were accessible from Vertx
					verticle.setContainer(new Container(VerticleManager.this));

					try {
						addVerticle(deployment, verticle, verticleFactory);
						// TODO per instance? Context are not even per Verticle. Doesn't it
						// fail with 2 verticles with modDir in the same Context?
						if (currentModDir != null) {
							setPathAdjustment(currentModDir);
						}
						verticle.start();
						aggHandler.done(true);
					} catch (Throwable t) {
						vertx.reportException(t);
						undeploy(deployment.name, new Handler<Deployment>() {
							@Override
							public void handle(final Deployment dep) {
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

		return future;
	}

	/**
	 * Extension point: In case somebody requires a different verticle class
	 * loader
	 */
	protected ParentLastURLClassLoader createParentLastURLClassLoader(final URL[] urls) {
		return new ParentLastURLClassLoader(urls, getClass().getClassLoader());
	}

	/**
	 * Extension point: In case somebody requires an extended Deployment
	 * implementation
	 */
	protected Deployment createDeployment(final String depName, final int instances, final File modDir,
			String parentDeploymentName, VertxModule module) {

		return new Deployment(depName, module, instances, modDir, parentDeploymentName);
	}

	private VerticleFactory getVerticleFactory(final ClassLoader cl, final String factoryName) {
		Class<?> clazz = null;
		try {
			clazz = cl.loadClass(factoryName);
			VerticleFactory factory = (VerticleFactory) clazz.newInstance();
			factory.init(this);
			return factory;
		} catch (Exception e) {
			String clazzName = (clazz != null ? clazz.getName() : "<unknown>");
			log.error("Failed to instantiate VerticleFactory: " + factoryName + "; class: " + clazzName, e);
		}
		return null;
	}

	/**
	 * Extension point: for user who whish to use their own resolution process
	 * 
	 * @param main
	 * @return
	 */
	protected String getLanguageFactoryName(final String main) {
		String factoryName = null;
		int dotIndex = main.lastIndexOf('.');
		if (dotIndex != -1) {
			String extension = main.substring(dotIndex + 1);
			factoryName = factoryNames.get(extension);
		}
		if (factoryName == null) {
			// Use the default
			factoryName = factoryNames.get("default");
			if (factoryName == null) {
				throw new IllegalArgumentException(
						"No language mapping found and no default specified in langs.properties for: " + main);
			}
		}
		return factoryName;
	}

	private void addVerticle(final Deployment deployment, final Verticle verticle, final VerticleFactory factory) {

		String loggerName = "org.vertx.deployments." + deployment.name + "-" + deployment.verticles.size();
		Logger logger = LoggerFactory.getLogger(loggerName);
		Context context = vertx.getContext();
		VerticleHolder holder = new VerticleHolder(deployment, context, verticle, loggerName, logger, deployment.module
				.config().json(), factory);
		deployment.verticles.add(holder);
		context.setDeploymentHandle(holder);
	}

	/**
	 * First undeploy all Deployments provided, than redeploy them
	 */
	public void reloadModules(final Set<Deployment> deps) {
		for (final Deployment deployment : deps) {
			if (deployments.get(deployment.name) != null) {
				undeploy(deployment.name, new Handler<Deployment>() {
					@Override
					public void handle(Deployment event) {
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

	/**
	 * Undeploy all Deployments
	 * 
	 * @param doneHandler
	 */
	public ActionFuture<Void> undeployAll(final Handler<Void> doneHandler) {
		final CountingCompletionHandler<Void> count = new CountingCompletionHandler<Void>(vertx.getOrAssignContext(),
				doneHandler);

		for (String name : deployments) {
			// Already deployed?
			if (deployments.get(name) != null) {
				count.incRequired();
				undeploy(name, new Handler<Deployment>() {
					@Override
					public void handle(final Deployment dep) {
						count.incCompleted();
					}
				});
			}
		}

		return count.future();
	}

	/**
	 * Undeploy the Deployment
	 */
	public final ActionFuture<Deployment> undeploy(final String depName, final Handler<Deployment> doneHandler) {
		CountingCompletionHandler<Deployment> count = new CountingCompletionHandler<>(vertx.getOrAssignContext(),
				doneHandler);
		undeploy(depName, count);
		return count.future();
	}

	/**
	 * Undeploy the Deployment
	 */
	private void undeploy(final String depName, final CountingCompletionHandler<Deployment> count) {
		log.info("Undeploy Deployment: " + depName);

		final Deployment deployment = deployments.remove(depName);
		count.result(deployment);
		if (deployment == null) {
			log.error("Deployment not found. Already undeployed?? Name: " + depName);
			return;
		}

		// Depth first - undeploy children first
		if (!deployment.childDeployments.isEmpty()) {
			for (String childDeployment: new ArrayList<>(deployment.childDeployments)) {
				if (log.isDebugEnabled()) {
					log.debug("Undeploy child: " + childDeployment);
				}
				undeploy(childDeployment, count);
			}
		}

		// Stop all instances of the Verticle
		for (final VerticleHolder holder : deployment.verticles) {
			count.incRequired();
			holder.context.execute(new Runnable() {
				public void run() {
					try {
						holder.verticle.stop();
					} catch (Throwable t) {
						// Vertx -> Context -> VerticleHolder ->
						// VerticleFactory.reportException(t)
						vertx.reportException(t);
					}
					// TODO ?? Shouldn't the close hook only be called if nothing is
					// attached to the context? I don't think context and Verticle have a
					// 1:1 relationship
					holder.context.runCloseHooks();
					LoggerFactory.removeLogger(holder.loggerName);
					if (log.isDebugEnabled()) {
						log.debug("Undeployment completed: " + depName);
					}
					count.incCompleted();
				}
			});
		}

		// Remove deployment from parent child list
		deployments.remove(depName);

		if (redeployer != null && deployment.module.config().autoRedeploy()) {
			redeployer.moduleUndeployed(deployment);
		}
	}

	/**
	 * (Async) (Re-)deploy the Deployment
	 * 
	 * @param deployment
	 */
	private ActionFuture<Void> redeploy(final Deployment deployment) {
		// TODO replace with doDeployXXX
		// Has to occur on a worker thread
		BlockingAction<Void> redeployAction = new BlockingAction<Void>(vertx) {
			@Override
			public Void action() throws Exception {
				// TODO need to have currentModDir in deployment
				doDeployMod(deployment.module.config().autoRedeploy(), deployment.name, deployment.module.name(),
						deployment.module.config().json(), deployment.instances, null, null);
				return null;
			}

			@Override
			protected void handle(final AsyncResult<Void> result) {
				if (result.failed()) {
					log.error("Failed to redeploy: " + deployment.name + "; module: " + deployment.module.name(),
							result.exception);
				}
			}
		};
		return redeployAction.run();
	}

	private void checkWorkerContext() {
		Thread t = Thread.currentThread();
		if ((t instanceof VertxThread) && !VertxThreadFactory.isWorker(t)) {
			throw new IllegalStateException("Not a worker thread");
		}
	}

	/**
	 * We calculate a path adjustment that can be used by the fileSystem object so
	 * that the *effective* working directory can be the module directory this
	 * allows modules to read and write the file system as if they were in the
	 * module dir, even though the actual working directory will be wherever vertx
	 * run or vertx start was called from
	 */
	private void setPathAdjustment(final File modDir) {
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
}
