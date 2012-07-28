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

package org.vertx.java.deploy.impl.jython;

import org.vertx.java.deploy.Verticle;
import org.vertx.java.deploy.VerticleFactory;
import org.vertx.java.deploy.impl.VerticleManager;

/**
 * @author <a href="https://github.com/sjhorn">Scott Horn</a>
 */
public class JythonVerticleFactory implements VerticleFactory {

  private VerticleManager mgr;

  public JythonVerticleFactory() {
  }

  @Override
  public void init(VerticleManager mgr) {
      this.mgr = mgr;
  }

  @Override
  public String getLanguage() {
      return "python";
  }
  
  @Override
  public boolean isFactoryFor(String main) {
    if (main.endsWith(".py")) {
      return true;
    }
    return false;
  }

  public Verticle createVerticle(String main, ClassLoader cl) throws Exception {
    if (System.getProperty("jython.home") == null) {
      throw new IllegalStateException("In order to deploy Python applications you must set JYTHON_HOME to point " +
          "at your Jython installation");
    }
    Verticle app = new JythonVerticle(main, cl);
    return app;
  }

  public void reportException(Throwable t) {
    mgr.getLogger().error("Exception in Python verticle", t);
  }

}