/*
 * Copyright 2014 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
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
class IsolatingClassLoader extends URLClassLoader {

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
        name.startsWith("io.netty.");
  }
}
