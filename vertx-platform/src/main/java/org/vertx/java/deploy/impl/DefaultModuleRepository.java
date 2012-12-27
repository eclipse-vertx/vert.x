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
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.lang.Args;
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

	private static final String DEFAULT_REPO_HOST = "vert-x.github.com";
	private static final int DEFAULT_PORT = 80;
	private static final String REPO_URI_ROOT = "/vertx-mods/mods/";
	private static final String MODULE_ZIP_FILENAME = "/mod.zip";

  private static final String HTTP_PROXY_HOST_PROP_NAME = "http.proxyHost";
  private static final String HTTP_PROXY_PORT_PROP_NAME = "http.proxyPort";
  private static final String COLON = ":";

	private final VertxInternal vertx;

	// The root directory where we expect to find the modules, resp. where
	// downloaded modules will be deployed
	private final File modRoot;

	// Repository Host and Port
	private final String repoHost;
	private final int repoPort;
	
	// Proxy Host and Port
	private final String proxyHost;
  private final int proxyPort;

	/**
	 * Constructor
	 * 
	 * @param platform
	 *          Must be != null
	 * @param defaultRepo
	 *          Defaults to DEFAULT_REPO_HOST
	 * @param modRoot
	 *          The directory path where all the modules are deployed already or
	 *          will be installed after download from a repository.
	 */
	public DefaultModuleRepository(final VertxInternal vertx, final String repo, final File modRoot) {
		this.vertx = Args.notNull(vertx, "vertx");
		this.modRoot = Args.notNull(modRoot, "modRoot");
    if (repo != null) {
      if (repo.contains(COLON)) {
        this.repoHost = repo.substring(0, repo.indexOf(COLON));
        this.repoPort = Integer.parseInt( repo.substring(repo.indexOf(COLON)+1));
      } else {
        this.repoHost = repo;
        this.repoPort = DEFAULT_PORT;
      }
    } else {
      this.repoHost = DEFAULT_REPO_HOST;
      this.repoPort = DEFAULT_PORT;
    }
    this.proxyHost = getProxyHost();
    this.proxyPort = getProxyPort();
	}

	protected String getProxyHost() {
		return System.getProperty(HTTP_PROXY_HOST_PROP_NAME);
	}

	protected int getProxyPort() {
    String tmpPort = System.getProperty(HTTP_PROXY_PORT_PROP_NAME);
    return tmpPort != null ? Integer.parseInt(tmpPort) : DEFAULT_PORT;
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
	public ActionFuture<Void> installMod(final String moduleName, final AsyncResultHandler<Void> doneHandler) {
		Args.notNull(moduleName, "moduleName");
    
    DownloadHttpClient client = new DownloadHttpClient(vertx, proxyHost, proxyPort);
    client.setHost(repoHost);
    client.setPort(repoPort);
    
    final String uri = REPO_URI_ROOT + moduleName + MODULE_ZIP_FILENAME;
    
    if (log.isInfoEnabled()) {
	    String msg = "Attempting to install module '" + moduleName + "' from http://" + repoHost + ":" + repoPort + uri;
	    if (proxyHost != null) {
	      msg += ". Using proxy host " + proxyHost + ":" + proxyPort;
	    }
	    log.info(msg);
    }

    // The job executed on a worker thread after the data have been downloaded
    client.backgroundHandler(new Handler<Buffer>() {
			@Override
			public void handle(Buffer buffer) {
	    	log.info("Thread: " + Thread.currentThread().getName() + "; Context: " + vertx.getContext());
	      unzipModule(moduleName, buffer);
			}
    });

    // The doneHandler gets executed in the caller context (not the background)  
    client.doneHandler(new AsyncResultHandler<Void>() {
			@Override
			public void handle(final AsyncResult<Void> event) {
				if (event.succeeded()) {
	        log.info("Successfully installed module '" + moduleName + "' from repository: " + uri, event.exception);
	        if (doneHandler != null) {
	        	doneHandler.handle(event);
	        }
				} else {
	        log.error("Error while downloading module '" + moduleName + "' from repository: " + uri, event.exception);
				}
			}
		});
		
    HttpClientRequest req = client.get(uri, new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        if (resp.statusCode == 200) {
          log.info("Downloading module...");
        	log.info("Thread: " + Thread.currentThread().getName() + "; Context: " + vertx.getContext());
        }
      }
    });

    // TODO I'd prefer req.headers().setUserAgent("Vert.x Module Installer");
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
	protected boolean unzipModule(final String modName, final Buffer data) {

		checkWorkerContext();

		// We synchronize to prevent a race whereby it tries to unzip the same
		// module at the same time (e.g. deployModule for the same module name
		// has been called in parallel). This works because all worker threads
		// share a common ClassLoader.
		boolean rtn = false;
		synchronized (modName.intern()) {
			try {
				rtn = newUnzipper().unzipModule(modRoot, modName, data);
				if (rtn == true) {
					log.info("Module successfully installed: " + modName);
				}
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
  	return "http://" + repoHost + ":" + repoPort + REPO_URI_ROOT + MODULE_ZIP_FILENAME;
  }
}
