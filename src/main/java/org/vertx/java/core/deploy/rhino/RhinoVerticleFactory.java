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

package org.vertx.java.core.deploy.rhino;

import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.RhinoException;
import org.vertx.java.core.Verticle;
import org.vertx.java.core.deploy.VerticleFactory;
import org.vertx.java.core.deploy.VerticleManager;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RhinoVerticleFactory implements VerticleFactory {

  static {
    ContextFactory.initGlobal(new RhinoContextFactory());
  }

  public Verticle createVerticle(String main, ClassLoader cl) throws Exception {
    Verticle app = new RhinoVerticle(main, cl);
    return app;
  }

  public void reportException(Throwable t) {

    Logger logger = VerticleManager.instance.getLogger();

    if (t instanceof RhinoException) {
      RhinoException je = (RhinoException)t;

      logger.error("Exception in JavaScript verticle:\n"
                   + je.details() +
                   "\n" + je.getScriptStackTrace());
    } else {
      logger.error("Exception in JavaScript verticle", t);
    }
  }
}

