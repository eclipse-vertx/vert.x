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

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.ActionFuture;
import org.vertx.java.core.impl.VertxConfig.ModuleManagerConfig;
import org.vertx.java.core.impl.VertxConfig.RepositoryConfig;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.Args;
import org.vertx.java.deploy.ModuleRepository;
import org.vertx.java.deploy.impl.ModuleWalker.ModuleVisitResult;
import org.vertx.java.deploy.impl.ModuleWalker.ModuleVisitor;

/**
 * The Module manager attempts to downloads missing Modules from registered
 * Repositories. Each Module gets installed in its own subdirectory and must
 * contain a file called 'mod.json', which is the module's config file. Besides
 * a few other attributes, it also defines dependencies on other modules
 * ('includes'). The Module manager make sure that all dependencies are
 * resolved.
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Juergen Donnerstag
 */
public class ModuleManager {

	private static final Logger log = LoggerFactory.getLogger(ModuleManager.class);

	private final VertxInternal vertx;

	private List<ModuleRepository> moduleRepositories = new ArrayList<>();

	// The directory where modules will be downloaded to
	private final File modRoot;

	/**
	 * Constructor
	 * 
	 * @param vertx
	 */
	public ModuleManager(final VertxInternal vertx) {
		this(vertx, (File) null);
	}

	/**
	 * Constructor
	 * 
	 * @param modRoot
	 *          The directory path where all the modules are deployed already or
	 *          will be installed after download from a repository.
	 * @param repository
	 *          Defaults to DEFAULT_REPO_HOST
	 */
	public ModuleManager(final VertxInternal vertx, final File modRoot, ModuleRepository... repos) {
		this.vertx = Args.notNull(vertx, "vertx");
		this.modRoot = initModRoot(modRoot);

		initRepositories(repos);
	}

	/**
	 * Initialize modRoot
	 * 
	 * @param modRoot
	 * @return
	 */
	private File initModRoot(File modRoot) {
		if (modRoot == null) {
			// This should never return null, or update vertx-default.cfg
			modRoot = new File(modulesManagerConfig().modRoot());
		}

		if (modRoot.exists() == false) {
			log.info("Module root directory does not exist => create it: " + modRoot.getAbsolutePath());
			if (modRoot.mkdir() == false) {
				throw new IllegalArgumentException("Unable to create directory: " + modRoot.getAbsolutePath());
			}
		} else if (modRoot.isDirectory() == false) {
			throw new IllegalArgumentException("Module root directory exists, but is not a directory: "
					+ modRoot.getAbsolutePath());
		}

		return modRoot;
	}

	/**
	 * @return Get the module manager's config
	 */
	protected ModuleManagerConfig modulesManagerConfig() {
		return vertx.vertxConfig().modulesManagerConfig();
	}
	
	/**
	 * Initialize the list of repositories.
	 * 
	 * @param repository
	 * @param moduleRepositories
	 */
	private void initRepositories(final ModuleRepository... repos) {
		for (ModuleRepository repo : repos) {
			moduleRepositories().add(repo);
		}

		// Read default list of repositories from VertxConfig
		for (RepositoryConfig repo: modulesManagerConfig().repoConfigs) {
			if (repo.enabled) {
				try {
					Class<?> clazz = getClass().getClassLoader().loadClass(repo.clazz);
					Method m = clazz.getDeclaredMethod("create", VertxInternal.class, RepositoryConfig.class);
					ModuleRepository r = (ModuleRepository) m.invoke(null, vertx, repo);
					moduleRepositories().add(r);
				} catch (ClassNotFoundException ex) {
					log.error("Module Repository implementation class not found: " + repo.clazz, ex);
				} catch (NoSuchMethodException | SecurityException | IllegalAccessException 
						| IllegalArgumentException | InvocationTargetException ex) {
					log.error("Class " + repo.clazz + 
							" is missing method: 'public static ModuleRepository create(VertxInternal vertx, JsonNode config", ex);
				}
			}
		}
		
		if (moduleRepositories().size() == 0) {
			moduleRepositories().add(new DefaultModuleRepository(vertx, null));
		}
	}

	/**
	 * This methods provides full unsynchronized access to the list of
	 * repositories. You can remove the default entry, add new repositories, etc..
	 * It's a little bit dangerous because it's unsynchronized. At the same time
	 * we don't expect the list to be modified very often. Adding repos right
	 * after ModuleManager was created, in the same thread, is absolutely safe and
	 * likely the standard use case.
	 * 
	 * @return
	 */
	public final List<ModuleRepository> moduleRepositories() {
		return this.moduleRepositories;
	}

	/**
	 * @return The modules root directory
	 */
	public final File modRoot() {
		return modRoot;
	}

	/**
	 * (Sync) Gets the config for the module
	 * 
	 * @param modName
	 * @return
	 */
	public VertxModule module(final String modName) {
		Args.notNull(modName, "modName");
		return new VertxModule(this, modName);
	}

	/**
	 * (Async) Install a single module without its dependencies: download from a
	 * repository and unzip into modRoot. Existing files will be replaced,
	 * extraneous files will not be removed. Uninstall first, if you want to
	 * delete all associated files.
	 * 
	 * @param modName
	 */
	public ActionFuture<String> installOne(final String modName, final AsyncResultHandler<String> doneHandler) {
		Args.notNull(modName, "modName");
		log.info("Install module '" + modName + "' into " + modRoot.getAbsolutePath());

		final ActionFuture<String> future = new ActionFuture<>();
		final Iterator<ModuleRepository> iter = this.moduleRepositories.iterator();

		final AsyncResultHandler<String> handler = new AsyncResultHandler<String>() {
			@Override
			public void handle(AsyncResult<String> res) {
				if (res.failed()) {
					if (iter.hasNext()) {
						iter.next().installMod(modName, modRoot, this);
					} else {
						if (doneHandler != null) {
							doneHandler.handle(res);
						}
						future.countDown(log, "Module with name '" + modName + "' not found in any of the repositories");
					}
				} else {
					if (doneHandler != null) {
						doneHandler.handle(res);
					}
					future.countDown(res);
				}
			}
		};

		handler.handle(new AsyncResult<String>(new RuntimeException()));

		return future;
	}

	/**
	 * Install a Module including all of its dependencies
	 * 
	 * @param modName
	 * @param doneHandler
	 * @return
	 */
	public ActionFuture<Void> install(final String modName, final Handler<Void> doneHandler) {
		final CountingCompletionHandler<Void> count = new CountingCompletionHandler<Void>(vertx.getContext(), doneHandler);
		count.incRequired();
		final Set<String> done = new HashSet<>();
		AsyncResultHandler<String> handler = new AsyncResultHandler<String>() {
			@Override
			public void handle(AsyncResult<String> res) {
				if (res.succeeded()) {
					String name = res.result;
					if (!done.contains(name)) {
						done.add(name);
						VertxModule module = module(name);
						if (module.exists() == false) {
							log.error("Ups. The module should really be available: " + name);
						}
						for (String inc : module.config().includes()) {
							VertxModule m = module(inc);
							count.incRequired();
							if (!m.exists()) {
								installOne(inc, this);
							} else {
								handle(new AsyncResult<String>(inc));
							}
						}
					}
					count.incCompleted();
				}
			}
		};

		VertxModule module = module(modName);
		if (module.exists()) {
			log.info("Module is already installed: " + modName);
			handler.handle(new AsyncResult<String>(modName));
		} else {
			installOne(modName, handler);
		}

		return count.future();
	}

	/**
	 * Collect the aggregated config data such as classpath, jars etc.. And
	 * validate that all required modules are installed.
	 * 
	 * @param modName
	 * @return
	 */
	public ModuleDependencies checkModuleDependencies(final String modName) {
		return checkModuleDependencies(modName, new ModuleDependencies(modName));
	}

	/**
	 * (Sync) Install a module and all its declared dependencies.
	 * 
	 * @param modName
	 * @return
	 */
	public ModuleDependencies checkModuleDependencies(final String modName, final ModuleDependencies pdata) {
		Args.notNull(modName, "modName");
		Args.notNull(pdata, "pdata");

		pdata.runModule = modName;

		/**
		 * We walk through the graph of includes making sure we only add each one
		 * once. We keep track of what jars have been included so we can flag errors
		 * if paths are included more than once. We make sure we only include each
		 * module once in the case of loops in the graph.
		 */
		moduleWalker(modName, new ModuleVisitor<Void>() {
			@Override
			protected boolean onMissingModule(final VertxModule module, final ModuleWalker<Void> walker) throws Exception {
				pdata.failed("Missing module: " + module.name() + ". Please install.");
				return false;
			}

			@Override
			protected ModuleVisitResult visit(final VertxModule module, final ModuleWalker<Void> walker) {

				if (!module.exists()) {
					pdata.failed("Failed to install module: " + module.name());
					return ModuleVisitResult.TERMINATE;
				}

				if (pdata.includedModules.contains(module.name())) {
					return ModuleVisitResult.SKIP_SUBTREE;
				}

				pdata.includedModules.add(module.name());

				// Add the urls for this module
				pdata.urls.add(module.modDir().toURI());
				for (File jar : module.files(modulesManagerConfig().lib)) {
					String prevMod = pdata.includedJars.get(jar.getName());
					if (prevMod != null) {
						log.warn("Warning! jar file " + jar.getName() + " is contained in module(s) [" + prevMod
								+ "] and also in module " + modName + " which are both included (perhaps indirectly) by module "
								+ pdata.runModule);

						pdata.includedJars.put(jar.getName(), prevMod + ", " + modName);
					} else {
						pdata.includedJars.put(jar.getName(), modName);
					}
					pdata.urls.add(jar.toURI());
				}

				return ModuleVisitResult.CONTINUE;
			}
		});

		VertxModule module = module(modName);

		String main = module.config().main();
		if (main == null) {
			return pdata.failed("Runnable module " + modName + " mod.json must contain a \"main\" field");
		}

		return pdata;
	}

	/**
	 * (Sync) Uninstall a module (but not its dependencies): delete the directory
	 * 
	 * @param modName
	 */
	public void uninstall(final String modName) {
		log.info("Uninstalling module " + modName + " from directory " + modRoot.getAbsolutePath());
		File modDir = new File(modRoot, modName);
		if (!modDir.exists()) {
			log.error("Cannot find module directory to delete: " + modDir.getAbsolutePath());
		} else {
			try {
				vertx.fileSystem().deleteSync(modDir.getAbsolutePath(), true);
				log.info("Module " + modName + " successfully uninstalled (directory deleted)");
			} catch (Exception e) {
				log.error("Failed to delete directory: " + modDir.getAbsoluteFile(), e);
			}
		}
	}

	/**
	 * Walk the module tree
	 */
	public final <T> T moduleWalker(String modName, ModuleVisitor<T> visitor) {
		return new ModuleWalker<T>(this).visit2(modName, visitor);
	}

	/**
	 * Print out the module tree
	 * 
	 * @param modName
	 * @param out
	 */
	public void printModuleTree(final String modName, final PrintStream out) {
		try {
			moduleWalker(modName, new ModuleVisitor<Void>() {
				@Override
				protected ModuleVisitResult visit(VertxModule module, ModuleWalker<Void> walker) {
					StringBuilder buf = new StringBuilder();
					buf.append("-");
					for (int i = 0; i < (walker.stack().size() - 1) * 2; i++) {
						buf.append("-");
					}
					buf.append(" ");
					buf.append(module.name());
					if (!module.exists()) {
						buf.append(" (missing)");
					}
					out.println(buf.toString());
					return ModuleVisitResult.CONTINUE;
				}
			});
		} catch (Exception ex) {
			log.error(ex);
		}
	}
}
