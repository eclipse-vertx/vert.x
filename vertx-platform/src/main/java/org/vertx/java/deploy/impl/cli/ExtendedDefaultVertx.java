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
package org.vertx.java.deploy.impl.cli;

import java.io.Closeable;
import java.io.File;
import java.util.concurrent.TimeUnit;

import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.core.impl.VertxCountDownLatch;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.ModuleRepository;
import org.vertx.java.deploy.impl.DefaultModuleRepository;
import org.vertx.java.deploy.impl.ModuleManager;
import org.vertx.java.deploy.impl.VerticleManager;

/**
 * A small extension to DefaultVertx most useful for Starter
 * 
 * @author Juergen Donnerstag
 */
public class ExtendedDefaultVertx extends DefaultVertx implements Closeable {

	private static final Logger log = LoggerFactory.getLogger(ExtendedDefaultVertx.class);
	
	private ModuleManager moduleManager;
	private VerticleManager verticleManager;
	
	public ExtendedDefaultVertx() {
		super();
	}

	public ExtendedDefaultVertx(String hostname) {
		super(hostname);
	}

	public ExtendedDefaultVertx(String hostname, int port) {
		super(port, hostname);
	}

	public ModuleRepository newModuleRepository(String repo) {
		return new DefaultModuleRepository(this, repo);
	}
	
	// TODO to padd modRoot is kind of akward. Should only be for initialisation
	public final ModuleManager moduleManager(File modRoot) {
		if (moduleManager == null) {
			moduleManager = newModuleManager(modRoot);
		}
		return moduleManager;
	}

	public ModuleManager newModuleManager(final File modRoot) {
		return new ModuleManager(this, modRoot);
	}
	
	public final VerticleManager verticleManager() {
		if (verticleManager == null) {
			verticleManager = newVerticleManager();
		}
		return verticleManager;
	}

	public VerticleManager newVerticleManager() {
		return new VerticleManager(this, moduleManager(null));
	}
	
	@Override
	public void stop() {

		if (verticleManager != null) {
			final VertxCountDownLatch latch = new VertxCountDownLatch(1);
			verticleManager.undeployAll(new SimpleHandler() {
				public void handle() {
					latch.countDown();
				}
			});
			if (!latch.await(30, TimeUnit.SECONDS)) {
				log.error("Timed out waiting to undeploy all");
			}
		}

		super.stop();
	}

	@Override
	public void close() {
		stop();
	}
}
