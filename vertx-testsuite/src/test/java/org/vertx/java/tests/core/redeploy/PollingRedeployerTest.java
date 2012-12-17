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
package org.vertx.java.tests.core.redeploy;

import java.io.File;

import org.junit.BeforeClass;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.impl.ModuleReloader;
import org.vertx.java.deploy.impl.PollingRedeployer;
import org.vertx.java.deploy.impl.Redeployer;

/**
 * @author Juergen Donnerstag
 */
public class PollingRedeployerTest extends DefaultRedeployerTest {

	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(PollingRedeployerTest.class);

	@BeforeClass
  public static void oneTimeSetUp() throws Exception {
		DefaultRedeployerTest.oneTimeSetUp();
		
		// Most Linux filesystems only have sec resolution
		SLEEP = 1_000;
  }

	@Override
	protected Redeployer newRedeployer(final VertxInternal vertx,
			final File modRoot, final ModuleReloader reloader) {
		long sleep = SLEEP;
		return new PollingRedeployer(vertx, modRoot, reloader, sleep, sleep);
	}
}
