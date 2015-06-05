/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core;

import java.util.ServiceLoader;

/**
 * A helper class for loading factories from the classpath and from the vert.x OSGi bundle.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ServiceHelper {

  public static <T> T loadFactory(Class<T> clazz) {
    ServiceLoader<T> factories = ServiceLoader.load(clazz);
    if (factories.iterator().hasNext()) {
      return factories.iterator().next();
    } else {
      // By default ServiceLoader.load uses the TCCL, this may not be enough in environment deading with
      // classloaders differently such as OSGi. So we should try to use the  classloader having loaded this
      // class. In OSGi it would be the bundle exposing vert.x and so have access to all its classes.
      factories = ServiceLoader.load(clazz, ServiceHelper.class.getClassLoader());
      if (factories.iterator().hasNext()) {
        return factories.iterator().next();
      } else {
        throw new IllegalStateException("Cannot find META-INF/services/" + clazz.getName() + " on classpath");
      }
    }
  }
}
