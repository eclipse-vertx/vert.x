/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core;

/**
 * @author <a href="http://www.ernestojpg.com">Ernesto J. Perez</a>
 */
public class ZipFileResolverTest extends FileResolverTestBase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // This is inside the jar webroot5.zip
    webRoot = "webroot5";
  }

}
