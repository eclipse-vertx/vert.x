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
import java.net.URI;
import java.net.URISyntaxException;

import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.impl.ActionFuture;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.impl.VertxThreadFactory;
import org.vertx.java.core.impl.VertxConfig.RepositoryConfig;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.Args;
import org.vertx.java.core.utils.StringUtils;
import org.vertx.java.core.utils.UriUtils;
import org.vertx.java.deploy.ModuleRepository;

/**
 * A module repository that tries to load missing modules from
 * http://vert-x.github.com/vertx-mods/mods/
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Juergen Donnerstag
 */
public class DefaultModuleRepository implements ModuleRepository {

	private static final Logger log = LoggerFactory.getLogger(DefaultModuleRepository.class);

	// If a user provided URI has missing parts (e.g. http://localhost:9093), 
	// than defaults are added
	private static final String DEFAULT_REPO_URI = "http://vert-x.github.com:80/vertx-mods/mods/$$module$$/mod.zip";
	
	private final VertxInternal vertx;
	private final URI repoUri;
	private boolean enabled = true;

	/**
	 * Factory
	 */
	public static DefaultModuleRepository create(final VertxInternal vertx, final RepositoryConfig config) {
		Args.notNull(vertx, "vertx");
		Args.notNull(config, "config");
		
		String url = config.path("url").asText();
		if (StringUtils.isEmpty(url)) {
			throw new IllegalArgumentException("Repository URL is missing from vertx config. Param name: 'url'");
		}
		
		return new DefaultModuleRepository(vertx, url);
	}
	
	/**
	 * Constructor
	 * 
	 * @param platform
	 *          Must be != null
	 * @param defaultRepo
	 *          Defaults to DEFAULT_REPO_HOST
	 */
	public DefaultModuleRepository(final VertxInternal vertx, final String repo) {
		this.vertx = Args.notNull(vertx, "vertx");
		this.repoUri = repositoryUri(repo);
		if (this.repoUri == null) {
			enabled = false;
		}
	}

	/**
	 * If the repo has missing parts (e.g. http://localhost:90993), than extend
	 * with defaults
	 */
	private URI repositoryUri(final String repo) {
		try {
			if (repo == null) {
				return new URI(DEFAULT_REPO_URI);
			}

			URI def = repositoryUri(null);
			URI uri = new URI(repo);
			return UriUtils.merge(uri,  def);
			
		} catch (URISyntaxException ex) {
			log.error("Invalid URI for Module Repository: " + repo, ex);
		}
		return null;
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

		if (!enabled) {
			return new ActionFuture<Void>().countDown(log, "Repository is disabled");
		}
		
		DownloadHttpClient client = new DownloadHttpClient(vertx);
		client.setHost(repoUri.getHost());
		client.setPort(repoUri.getPort());

		final String uri = repoUri.toString().replace("$$module$$", moduleName);

		if (log.isInfoEnabled()) {
			log.info("Attempting to install module '" + moduleName + "' from " + uri);
		}

		// The job executed on a worker thread after the data have been downloaded
		client.backgroundHandler(new Handler<Buffer>() {
			@Override
			public void handle(Buffer buffer) {
				unzipModule(moduleName, modRoot, buffer);
			}
		});

		// The doneHandler gets executed in the caller context (not the background)
		client.doneHandler(new AsyncResultHandler<Void>() {
			@Override
			public void handle(final AsyncResult<Void> event) {
				if (event.succeeded()) {
					log.info("Successfully installed module '" + moduleName + "' from repository: " + uri, event.exception);
					if (doneHandler != null) {
						doneHandler.handle(new AsyncResult<String>(moduleName));
					}
				} else {
					log.error("Error while downloading module '" + moduleName + "' from repository: " + uri, event.exception);
					if (doneHandler != null) {
						doneHandler.handle(new AsyncResult<String>(event.exception));
					}
				}
			}
		});

		HttpClientRequest req = client.get(uri, new Handler<HttpClientResponse>() {
			public void handle(HttpClientResponse resp) {
				if (resp.statusCode == 200) {
					log.info("Downloading module...");
				}
			}
		});

		req.putHeader(HttpHeaders.Names.USER_AGENT, "Vert.x Module Installer");
		req.end();

		return client.future();
	}

	/**
	 * Unzip the data in the background
	 * 
	 * @param modName
	 * @param data
	 * @param doneHandler
	 */
	protected boolean unzipModule(final String modName, final File modRoot, final Buffer data) {

		checkWorkerContext();

		// We synchronize to prevent a race whereby it tries to unzip the same
		// module at the same time (e.g. deployModule for the same module name
		// has been called in parallel). This works because all worker threads
		// share a common ClassLoader.
		boolean rtn = false;
		synchronized (modName.intern()) {
			try {
				rtn = newUnzipper().unzipModule(modRoot, modName, data);
			} catch (Exception ex) {
				log.info("Failed to install Module: " + modName, ex);
				rtn = false;
			}
		}
		return rtn;
	}

	/**
	 * @return Create an unzipper
	 */
	protected Unzipper newUnzipper() {
		return new Unzipper();
	}

	private void checkWorkerContext() {
		if (VertxThreadFactory.isWorker(Thread.currentThread()) == false) {
			throw new IllegalStateException("Not a worker thread");
		}
	}

	@Override
	public String toString() {
		return this.repoUri.toString();
	}
}
