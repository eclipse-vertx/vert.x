/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
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
            root.getAbsolutePath() + File.separator + "FOO.bar")), next -> {
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
