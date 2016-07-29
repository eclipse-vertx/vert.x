/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.impl.launcher.commands;

import org.junit.Before;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test the watching service behavior when using absolute path in the include list.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class WatcherAbsolutePathTest extends WatcherTest {

  @Before
  public void prepare() {
    root = new File("target/junk/watcher");
    File otherRoot = new File(root.getParentFile(), "abs-test");
    deleteRecursive(otherRoot);
    deleteRecursive(root);
    otherRoot.mkdirs();
    root.mkdirs();

    deploy = new AtomicInteger();
    undeploy = new AtomicInteger();

    watcher = new Watcher(otherRoot, Collections.unmodifiableList(
        Arrays.asList(
            root.getAbsolutePath() + File.separator + "**" + File.separator + "*.txt",
            root.getAbsolutePath() + File.separator + "windows\\*.win",
            root.getAbsolutePath() + File.separator + "unix/*.nix",
            "FOO.bar")), next -> {
      deploy.incrementAndGet();
      if (next != null) {
        next.handle(null);
      }
    }, next -> {
      undeploy.incrementAndGet();
      if (next != null) {
        next.handle(null);
      }
    }, null, 10, 10);
  }
}