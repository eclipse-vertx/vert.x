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
import org.vertx.java.deploy.impl.ModuleClassLoader;
import org.vertx.java.deploy.impl.VerticleFactory;
import org.vertx.java.deploy.impl.VerticleManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaVerticleFactory implements VerticleFactory {

  private VerticleManager mgr;
  private ModuleClassLoader mcl;

  public JavaVerticleFactory() {
	  super();
  }

  @Override
  public void init(VerticleManager mgr, ModuleClassLoader mcl) {
	  this.mgr = mgr;
  }

  private boolean isJavaSource(String main) {
    return main.endsWith(".java");
  }

  public Verticle createVerticle(String main) throws Exception {

    String className = main;
    Class<?> clazz;
    if (isJavaSource(main)) {
      // TODO - is this right???
      // Don't we want one CompilingClassloader per instance of this?
      CompilingClassLoader compilingLoader = new CompilingClassLoader(mcl, main);
      className = compilingLoader.resolveMainClassName();
      clazz = compilingLoader.loadClass(className);
    } else {
      clazz = mcl.loadClass(className);
    }

    return (Verticle)clazz.newInstance();
  }
    
  public void reportException(Throwable t) {
    mgr.getLogger().error("Exception in Java verticle script", t);
  }
}
