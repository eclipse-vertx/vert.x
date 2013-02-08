/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */

package org.vertx.java.platform.impl;

import org.vertx.java.core.Vertx;
import org.vertx.java.platform.Container;
import org.vertx.java.platform.VerticleFactory;

/**
 *
 * This class could benefit from some refactoring
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
class ModuleReference {
  final PlatformManagerInternal mgr;
  final String moduleKey;
  final ModuleClassLoader mcl;
  int refCount = 0;
  private VerticleFactory factory;
  // Resident modules do not get unloaded when all referencing modules are unloaded.
  // They are used for modules such as language implementations, e.g. JRuby
  // Language impls often contain a lot of classes. If you continually load the classes
  // then throw away the classloader then you have a very large hit on permgen, and you can get OOM
  // even if you have permgen GC enabled.
  final boolean resident;

  ModuleReference(final PlatformManagerInternal mgr, final String moduleKey, final ModuleClassLoader mcl,
                  boolean resident) {
    this.mgr = mgr;
    this.moduleKey = moduleKey;
    this.mcl = mcl;
    this.resident = resident;
  }

  synchronized void incRef() {
    refCount++;
  }

  synchronized void decRef() {
    refCount--;
    if (!resident && refCount == 0) {
      mgr.removeModule(moduleKey);
      mcl.close();
      if (factory != null) {
        factory.close();
      }
    }
  }

  // We load the VerticleFactory class using the module classloader - this allows
  // us to put language implementations in modules
  // And we maintain a single VerticleFactory per classloader
  public synchronized VerticleFactory getVerticleFactory(String factoryName, Vertx vertx, Container container)
      throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    if (factory == null) {
      Class clazz = mcl.loadClass(factoryName);
      factory = (VerticleFactory)clazz.newInstance();
      factory.init(vertx, container, mcl);
    }
    return factory;
  }

}
