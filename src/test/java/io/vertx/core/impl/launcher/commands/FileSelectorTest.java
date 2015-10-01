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

import org.junit.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class FileSelectorTest {

  @Test
  public void test() {
    String separator = File.separator;
    assertThat(FileSelector.matchPath("**" + separator + "*.js", "foo.js")).isTrue();
    assertThat(FileSelector.matchPath("**" + separator + "*.js", "target" + separator + "foo.js")).isTrue();
    assertThat(FileSelector.matchPath("**" + separator + "*.js", "src/main" + separator + "js" + separator + "foo.js")).isTrue();
    assertThat(FileSelector.matchPath("**" + separator + "*.js", "src" + separator + "main" + separator + "js" + separator + "dir" + separator + "foo.js")).isTrue();

    assertThat(FileSelector.matchPath("*" + separator + "*.js", "src" + separator + "main" + separator + "js" + separator + "dir" + separator + "foo.js")).isFalse();
    assertThat(FileSelector.matchPath("*" + separator + "*.js", "src" + separator + "js" + separator + "foo.js")).isFalse();
    assertThat(FileSelector.matchPath("*" + separator + "*.js", "src" + separator + "foo.js")).isTrue();
    assertThat(FileSelector.matchPath("*" + separator + "*.js", "foo.js")).isFalse();

    assertThat(FileSelector.matchPath("*.js", "foo.js")).isTrue();
    assertThat(FileSelector.matchPath("*.js", "foo" + separator + "foo.js")).isFalse();

    assertThat(FileSelector.matchPath("*.?s", "foo.js")).isTrue();
    assertThat(FileSelector.matchPath("*.?s", "foo.s")).isFalse();
    assertThat(FileSelector.matchPath("*.?s", "foo.ajs")).isFalse();

    assertThat(FileSelector.match("not" + separator + "*" + separator + "something.js", "foo" + separator + "bar" + separator + "something.js")).isFalse();
    assertThat(FileSelector.match("**" + separator + "not" + separator + "something.js", "foo" + separator + "bar" + separator + "something.js")).isFalse();
  }

  @Test
  public void testMatchPath_DefaultFileSeparator() {
    String separator = File.separator;

    // Pattern and target start with file separator
    assertTrue(FileSelector.matchPath(separator + "*" + separator + "a.txt", separator + "b" + separator
        + "a.txt"));
    // Pattern starts with file separator, target doesn't
    assertFalse(FileSelector.matchPath(separator + "*" + separator + "a.txt", "b" + separator + "a.txt"));
    // Pattern doesn't start with file separator, target does
    assertFalse(FileSelector.matchPath("*" + separator + "a.txt", separator + "b" + separator + "a.txt"));
    // Pattern and target don't start with file separator
    assertTrue(FileSelector.matchPath("*" + separator + "a.txt", "b" + separator + "a.txt"));
  }

  @Test
  public void testMatchPath_UnixFileSeparator() {
    String separator = "/";

    // Pattern and target start with file separator
    assertTrue(FileSelector.matchPath(separator + "*" + separator + "a.txt", separator + "b" + separator
        + "a.txt", separator, false));
    // Pattern starts with file separator, target doesn't
    assertFalse(FileSelector.matchPath(separator + "*" + separator + "a.txt", "b" + separator + "a.txt",
        separator, false));
    // Pattern doesn't start with file separator, target does
    assertFalse(FileSelector.matchPath("*" + separator + "a.txt", separator + "b" + separator + "a.txt",
        separator, false));
    // Pattern and target don't start with file separator
    assertTrue(FileSelector.matchPath("*" + separator + "a.txt", "b" + separator + "a.txt", separator, false));
  }

  @Test
  public void testMatchPath_WindowsFileSeparator() {
    String separator = "\\";

    // Pattern and target start with file separator
    assertTrue(FileSelector.matchPath(separator + "*" + separator + "a.txt", separator + "b" + separator
        + "a.txt", separator, false));
    // Pattern starts with file separator, target doesn't
    assertFalse(FileSelector.matchPath(separator + "*" + separator + "a.txt", "b" + separator + "a.txt",
        separator, false));
    // Pattern doesn't start with file separator, target does
    assertFalse(FileSelector.matchPath("*" + separator + "a.txt", separator + "b" + separator + "a.txt",
        separator, false));
    // Pattern and target don't start with file separator
    assertTrue(FileSelector.matchPath("*" + separator + "a.txt", "b" + separator + "a.txt", separator, false));
  }


}