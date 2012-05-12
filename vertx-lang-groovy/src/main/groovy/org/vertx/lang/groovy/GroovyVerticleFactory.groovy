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

package org.vertx.lang.groovy

import groovy.lang.Binding
import groovy.lang.GroovyClassLoader
import groovy.lang.GroovyCodeSource
import groovy.lang.Script
import org.vertx.groovy.core.Vertx
import org.vertx.java.core.VertxInternal
import org.vertx.lang.Verticle
import org.vertx.lang.VerticleFactory
import org.vertx.lang.VerticleManager
import org.vertx.java.deploy.impl.VertxLocator

import java.lang.reflect.Method
import java.net.URL

/**
 * TODO make this more Groovy
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class GroovyVerticleFactory implements VerticleFactory {

  private VerticleManager mgr;
  private Vertx gVertx

  public GroovyVerticleFactory() {
	  super()
  }

  @Override
  public void init(VerticleManager mgr) {
	this.mgr = mgr  
    this.gVertx = new Vertx((VertxInternal) VertxLocator.vertx)
  }

  @Override
  public String getLanguage() {
	return "groovy"
  }
  
  @Override
  public boolean isFactoryFor(String main) {
	// TODO do something smarter with this, no guarantees it'll always work
	if (main.endsWith(".groovy")) {
		return true
	}
	return false
  }

  public Verticle createVerticle(String main, ClassLoader cl) throws Exception {

    URL url = cl.getResource(main)
    GroovyCodeSource gcs = new GroovyCodeSource(url)
    GroovyClassLoader gcl = new GroovyClassLoader(cl)
    Class clazz = gcl.parseClass(gcs)

    Method stop
    try {
      stop = clazz.getMethod("vertxStop", (Class<?>[])null)
    } catch (NoSuchMethodException e) {
      stop = null
    }
    final Method mstop = stop

    Method run
    try {
      run = clazz.getMethod("run", (Class<?>[])null)
    } catch (NoSuchMethodException e) {
      run = null
    }
    final Method mrun = run

    if (run == null) {
      throw new IllegalStateException("Groovy script must have run() method [whether implicit or not]")
    }

    final Script verticle = (Script)clazz.newInstance()

    // Inject vertx into the script binding
    Binding binding = new Binding()
    binding.setVariable("vertx", gVertx)
    binding.setVariable("container", new Container(new org.vertx.lang.Container((mgr))))
    verticle.setBinding(binding)

    return new Verticle() {
      public void start() {
        try {
          mrun.invoke(verticle, (Object[])null)
        } catch (Throwable t) {
          reportException(t)
        }
      }

      public void stop() {
        if (mstop != null) {
          try {
            mstop.invoke(verticle, (Object[])null)
          } catch (Throwable t) {
            reportException(t)
          }
        }
      }
    }
  }

  public void reportException(Throwable t) {
    mgr.getLogger().error("Exception in Groovy verticle", t)
  }
}

