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

import org.python.core.Options;
import org.python.core.PySystemState;
import org.python.util.PythonInterpreter;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.Verticle;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author <a href="https://github.com/sjhorn">Scott Horn</a>
 */
public class JythonVerticle extends Verticle {

  private static final Logger log = LoggerFactory.getLogger(JythonVerticle.class);

  private final PythonInterpreter py;
  private final ClassLoader cl;
  private final String scriptName;

  JythonVerticle(String scriptName, ClassLoader cl) {
    Options.includeJavaStackInExceptions = false;
    this.py = new PythonInterpreter(null, new PySystemState());
    this.cl = cl;
    this.scriptName = scriptName;
  }

  public void start() throws Exception {
    InputStream is = cl.getResourceAsStream(scriptName);
    if (is == null) {
      throw new IllegalArgumentException("Cannot find verticle: " + scriptName);
    }

    ClassLoader old = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(cl);

      // Inject vertx and container as a variable in the script
      py.set("vertx", getVertx());
      py.set("container", getContainer());
      py.execfile(is, scriptName);
      try {
        is.close();
      } catch (IOException ignore) {
      }
    } finally {
      Thread.currentThread().setContextClassLoader(old);
    }
  }

  public void stop() throws Exception {
    try {
      py.exec("vertx_stop()");
    } catch (org.python.core.PyException e) {
      // OK - method is not mandatory :)
    }
    py.cleanup();
  }
}
