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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.lang.Args;

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
	 * @param modDir
	 *          The directory path where all the modules are deployed already or
	 *          will be installed after download from a repository.
	 */
	public DefaultModuleRepository(final VertxInternal vertx, final String repo, final File modDir) {
		this.vertx = Args.notNull(vertx, "vertx");
		this.modRoot = Args.notNull(modDir, "modRoot");
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
	public boolean installMod(final String moduleName) {
		Args.notNull(moduleName, "moduleName");

    checkWorkerContext();
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Buffer> mod = new AtomicReference<>();
    HttpClient client = vertx.createHttpClient();
    if (proxyHost != null) {
      client.setHost(proxyHost);
      client.setPort(proxyPort);
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
    String uri = REPO_URI_ROOT + moduleName + MODULE_ZIP_FILENAME;
    String msg = "Attempting to install module " + moduleName + " from http://"
        + repoHost + ":" + repoPort + uri;
    if (proxyHost != null) {
      msg += " Using proxy host " + proxyHost + ":" + proxyPort;
    }
    log.info(msg);
    if (proxyHost != null) {
      uri = new StringBuffer("http://").append(DEFAULT_REPO_HOST).append(uri).toString();
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
    Thread t = Thread.currentThread();
    if (!t.getName().startsWith("vert.x-worker-thread")) {
      throw new IllegalStateException("Not a worker thread");
    }
  }
}
