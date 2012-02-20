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

package org.vertx.java.core.deploy.groovy;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import org.vertx.java.core.Verticle;
import org.vertx.java.core.deploy.VerticleFactory;
import org.vertx.java.core.deploy.VerticleManager;

import java.lang.reflect.Method;
import java.net.URL;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class GroovyVerticleFactory implements VerticleFactory {

  public Verticle createVerticle(String main, ClassLoader cl) throws Exception {

    URL url = cl.getResource(main);
    GroovyCodeSource gcs = new GroovyCodeSource(url);
    GroovyClassLoader gcl = new GroovyClassLoader(cl);
    Class clazz = gcl.parseClass(gcs);

    Method stop;
    try {
      stop = clazz.getMethod("vertxStop", (Class<?>[])null);
    } catch (NoSuchMethodException e) {
      stop = null;
    }
    final Method mstop = stop;

    Method run;
    try {
      run = clazz.getMethod("run", (Class<?>[])null);
    } catch (NoSuchMethodException e) {
      run = null;
    }
    final Method mrun = run;

    if (run == null) {
      throw new IllegalStateException("Groovy script must have run() method [whether implicit or not]");
    }

    final Object verticle = clazz.newInstance();

    return new Verticle() {
      public void start() {
        try {
          mrun.invoke(verticle, (Object[])null);
        } catch (Throwable t) {
          reportException(t);
        }
      }

      public void stop() {
        if (mstop != null) {
          try {
            mstop.invoke(verticle, (Object[])null);
          } catch (Throwable t) {
            reportException(t);
          }
        }
      }
    };
  }

  public void reportException(Throwable t) {
    VerticleManager.instance.getLogger().error("Exception in Groovy verticle", t);
  }
}

