/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.deploy.impl.java;

import org.vertx.java.deploy.Verticle;
import org.vertx.java.deploy.VerticleFactory;
import org.vertx.java.deploy.impl.VerticleManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaVerticleFactory implements VerticleFactory {

  private VerticleManager mgr;
  
  public JavaVerticleFactory() {
	  super();
  }

  @Override
  public void init(VerticleManager mgr) {
	  this.mgr = mgr;
  }

  private boolean isJavaSource(String main) {
    return main.endsWith(".java");
  }

  public Verticle createVerticle(String main, ClassLoader loader) throws Exception {

    ClassLoader cl = loader;
    String className = main;
    if (isJavaSource(main)) {
      CompilingClassLoader compilingLoader = new CompilingClassLoader(loader, main);
      className = compilingLoader.resolveMainClassName();
      cl = compilingLoader;
    }
    Class<?> clazz = cl.loadClass(className);

    Verticle verticle = (Verticle) clazz.newInstance();

    // Sanity check - make sure app class didn't get loaded by the parent or system classloader
    // This might happen if it's been put on the server classpath
    // Out of the box busmods are ok though
    ClassLoader system = ClassLoader.getSystemClassLoader();
    ClassLoader appCL = clazz.getClassLoader();
    if (!main.startsWith("org.vertx.java.busmods") && (appCL == cl.getParent() || (system != null && appCL == system))) {
      throw new IllegalStateException("Do not add application classes to the vert.x classpath");
    }

    return verticle;
  }
    
  public void reportException(Throwable t) {
    mgr.getLogger().error("Exception in Java verticle script", t);
  }
}
