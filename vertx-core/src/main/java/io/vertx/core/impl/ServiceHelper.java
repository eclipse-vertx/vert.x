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

import java.util.*;

/**
 * A helper class for loading factories from the classpath.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ServiceHelper {

  @Deprecated
  public static <T> T loadFactory(Class<T> clazz) {
    T factory = loadFactoryOrNull(clazz);
    if (factory == null) {
      throw new IllegalStateException("Cannot find META-INF/services/" + clazz.getName() + " on classpath");
    }
    return factory;
  }

  @Deprecated
  public static <T> T loadFactoryOrNull(Class<T> clazz) {
    Collection<T> collection = loadFactories(clazz);
    if (!collection.isEmpty()) {
      return collection.iterator().next();
    } else {
      return null;
    }
  }

  @Deprecated
  public static <T> List<T> loadFactories(Class<T> clazz) {
    return loadFactories(clazz, null);
  }

  @Deprecated
  public static <T> List<T> loadFactories(Class<T> clazz, ClassLoader classLoader) {
    ServiceLoader<T> factories;
    if (classLoader != null) {
      factories = ServiceLoader.load(clazz, classLoader);
    } else {
      // this is equivalent to:
      // ServiceLoader.load(clazz, TCCL);
      factories = ServiceLoader.load(clazz);
    }
    List<T> list = loadFactories(factories);
    if (list.isEmpty()) {
      // By default ServiceLoader.load uses the TCCL, this may not be enough in environment dealing with
      // classloaders differently such as OSGi. So we should try to use the  classloader having loaded this
      // class. In OSGi it would be the bundle exposing vert.x and so have access to all its classes.
      factories = ServiceLoader.load(clazz, ServiceHelper.class.getClassLoader());
      list = loadFactories(factories);
    }
    return list;
  }

  public static <T> T loadFactory(ServiceLoader<T> factories) {
    T factory = loadFactoryOrNull(factories);
    if (factory == null) {
      throw new IllegalStateException("Cannot find service on the classpath or module path");
    }
    return factory;
  }

  public static <T> T loadFactoryOrNull(ServiceLoader<T> factories) {
    Collection<T> collection = loadFactories(factories);
    if (!collection.isEmpty()) {
      return collection.iterator().next();
    } else {
      return null;
    }
  }

  public static <T> List<T> loadFactories(ServiceLoader<T> factories) {
    List<T> list = new ArrayList<>();
    if (factories.iterator().hasNext()) {
      factories.iterator().forEachRemaining(list::add);
      return list;
    }
    return list;
  }
}
