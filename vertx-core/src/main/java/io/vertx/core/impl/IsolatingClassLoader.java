/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.impl;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Before delegating to the parent, this classloader attempts to load the class first
 * (opposite of normal delegation model).
 * This allows multiple versions of the same class to be loaded by different classloaders which allows
 * us to isolate verticles so they can't easily interact
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class IsolatingClassLoader extends URLClassLoader {

  IsolatingClassLoader(URL[] urls, ClassLoader parent) {
    super(urls, parent);
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      Class<?> c = findLoadedClass(name);
      if (c == null) {
        // We don't want to load Vert.x (or Vert.x dependency) classes from an isolating loader
        if (isVertxOrSystemClass(name)) {
          try {
            c = super.loadClass(name, false);
          } catch (ClassNotFoundException e) {
            // Fall through
          }
        }
        if (c == null) {
          // Try and load with this classloader
          try {
            c = findClass(name);
          } catch (ClassNotFoundException e) {
            // Now try with parent
            c = super.loadClass(name, false);
          }
        }
      }
      if (resolve) {
        resolveClass(c);
      }
      return c;
    }
  }

  private boolean isVertxOrSystemClass(String name) {
    return
        name.startsWith("java.") ||
        name.startsWith("javax.") ||
        name.startsWith("com.sun.") ||
        name.startsWith("io.vertx.core") ||
        name.startsWith("com.hazelcast") ||
        name.startsWith("io.netty.") ||
        name.startsWith("com.fasterxml.jackson");
  }
}
