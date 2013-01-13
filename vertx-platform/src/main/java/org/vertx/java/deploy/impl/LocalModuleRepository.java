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
import java.io.FileNotFoundException;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.impl.ActionFuture;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.impl.VertxConfig.RepositoryConfig;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.Args;
import org.vertx.java.core.utils.StringUtils;
import org.vertx.java.deploy.ModuleRepository;

/**
 * A local module repository which searches for unzipped modules in a local
 * directory
 * 
 * @author Juergen Donnerstag
 */
public class LocalModuleRepository implements ModuleRepository {

	private static final Logger log = LoggerFactory.getLogger(LocalModuleRepository.class);

	private final VertxInternal vertx;
	private final File repoDir;

	/**
	 * Factory
	 */
	public static LocalModuleRepository create(final VertxInternal vertx, final RepositoryConfig config) {
		Args.notNull(vertx, "vertx");
		Args.notNull(config, "config");
		
		String path = config.path("root").asText();
		if (StringUtils.isEmpty(path)) {
			throw new IllegalArgumentException("Root directory for repository is missing from vertx config. Param name: 'root'");
		}
		File repoDir = new File(path);
		if (repoDir.exists() == false) {
			throw new IllegalStateException("Repository directory does not exist: " + repoDir.getAbsolutePath());
		}
		return new LocalModuleRepository(vertx, repoDir);
	}
	
	/**
	 * Constructor
	 */
	public LocalModuleRepository(final VertxInternal vertx, final File repoDir) {
		this.vertx = Args.notNull(vertx, "vertx");
		this.repoDir = Args.notNull(repoDir, "repoDir");
		if (repoDir.exists() == false) {
			throw new IllegalStateException("Repository directory does not exist: " + repoDir.getAbsolutePath());
		}
	}

	/**
	 * Install a module from a remote (http) repository.
	 * 
	 * TODO Local / file repositories etc. are not supported yet.
	 * 
	 * @param moduleName
	 * @param doneHandler
	 */
	@Override
	public ActionFuture<Void> installMod(final String moduleName, final File modRoot,
			final AsyncResultHandler<String> doneHandler) {
		Args.notNull(moduleName, "moduleName");

		ActionFuture<Void> future = new ActionFuture<Void>();
		AsyncResult<String> res = null;

		if (repoDir.getAbsolutePath().equals(modRoot.getAbsolutePath())) {
			res = new AsyncResult<String>(new RuntimeException("Repository directory must not be equal to module directory:"
					+ modRoot.getAbsolutePath()));
		} else {
			File modDir = new File(repoDir, moduleName);
			if (modDir.exists() == false) {
				res = new AsyncResult<String>(new FileNotFoundException("Module '" + moduleName + "' not found in Repository '"
						+ repoDir.getAbsolutePath() + "'"));
			} else {
				try {
					String from = new File(repoDir, moduleName).getAbsolutePath();
					String to = new File(modRoot, moduleName).getAbsolutePath();
					log.info("Copy from " + from + " to " + to);
					vertx.fileSystem().copySync(from, to, true);
					res = new AsyncResult<String>(moduleName);
				} catch (Exception ex) {
					res = new AsyncResult<String>(new RuntimeException("Error while installing module '" + moduleName
							+ "' from Repository '" + repoDir.getAbsolutePath() + "'", ex));
				}
			}
		}

		if (doneHandler != null) {
			doneHandler.handle(res);
		}
		future.countDown(new AsyncResult<Void>(res.exception));
		return future;
	}

	@Override
	public String toString() {
		return repoDir.getAbsolutePath();
	}
}
