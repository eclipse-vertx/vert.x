/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.file;

import org.junit.Test;

import java.io.File;

/**
 * Tests on file resolving in case of FileResolverImpl#unpackFromBundleURL(URL, String, boolean) is needed.
 *
 * The following test(testResolveURLBundle) works on both JDK8 (jar:) and JDK11(jrt:)
 *
 * @author <a href="mailto: aoingl@gmail.com">Lin Gao</a>
 */
public class URLBundleFileResolverTest extends JarFileResolverTest {

  @Test
  public void testResolveURLBundle() {
    String fileName = "java/lang/Object.class";
    assertFalse(resolver.getFileCache().getFile(fileName).exists());
    File file = resolver.resolve(fileName);
    assertTrue(file.exists());
    // cache.getFile should return the cached file.
    assertTrue(resolver.getFileCache().getFile(fileName).exists());
    // resolve again
    file = resolver.resolve(fileName);
    assertTrue(file.exists());
    assertTrue(resolver.getFileCache().getFile(fileName).exists());
  }

}
