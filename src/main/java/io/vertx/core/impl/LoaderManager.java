/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl;

import io.vertx.core.DeploymentOptions;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Supports isolated classloader for Java 8.
 * <br/>
 * For Java 11 and above, the JVM uses a no-op implementation thanks to Multi-Release Jar.
 */
class LoaderManager {

  private final Map<String, ClassLoaderHolder> classLoaders = new HashMap<>();

  /**
   * <strong>IMPORTANT</strong> - Isolation groups are not supported on Java 9+ because the application classloader is not
   * an URLClassLoader anymore. Thus we can't extract the list of jars to configure the IsolatedClassLoader.
   */
  ClassLoaderHolder getClassLoader(DeploymentOptions options) {
    String isolationGroup = options.getIsolationGroup();
    ClassLoaderHolder holder;
    if (isolationGroup == null) {
      return null;
    } else {
      // IMPORTANT - Isolation groups are not supported on Java 9+, because the system classloader is not an URLClassLoader
      // anymore. Thus we can't extract the paths from the classpath and isolate the loading.
      synchronized (this) {
        holder = classLoaders.get(isolationGroup);
        if (holder == null) {
          ClassLoader current = VerticleManager.getCurrentClassLoader();
          if (!(current instanceof URLClassLoader)) {
            throw new IllegalStateException("Current classloader must be URLClassLoader");
          }
          holder = new ClassLoaderHolder(isolationGroup, LoaderManager.buildLoader((URLClassLoader) current, options));
          classLoaders.put(isolationGroup, holder);
        }
        holder.refCount++;
      }
    }
    return holder;
  }

  void release(ClassLoaderHolder holder) {
    synchronized (this) {
      if (--holder.refCount == 0) {
        classLoaders.remove(holder.group);
        if (holder.loader instanceof Closeable) {
          try {
            ((Closeable)holder.loader).close();
          } catch (IOException e) {
            // log.debug("Issue when closing isolation group loader", e);
          }
        }
      }
    }
  }

  private static ClassLoader buildLoader(URLClassLoader parent, DeploymentOptions options) {
    List<URL> urls = new ArrayList<>();
    // Add any extra URLs to the beginning of the classpath
    List<String> extraClasspath = options.getExtraClasspath();
    if (extraClasspath != null) {
      for (String pathElement: extraClasspath) {
        File file = new File(pathElement);
        try {
          URL url = file.toURI().toURL();
          urls.add(url);
        } catch (MalformedURLException e) {
          throw new IllegalStateException(e);
        }
      }
    }
    // And add the URLs of the Vert.x classloader
    urls.addAll(Arrays.asList(parent.getURLs()));
    return new IsolatingClassLoader(urls.toArray(new URL[urls.size()]), parent,
      options.getIsolatedClasses());
  }
}
