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

package org.vertx.java.deploy.impl.rhino;

import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.RhinoException;

import org.mozilla.javascript.tools.debugger.Main;
import org.mozilla.javascript.tools.debugger.ScopeProvider;

import org.vertx.java.core.logging.Logger;
import org.vertx.java.deploy.Verticle;
import org.vertx.java.deploy.VerticleFactory;
import org.vertx.java.deploy.impl.VerticleManager;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RhinoVerticleFactory implements VerticleFactory {

  static boolean initializeContextFactory = false;

  private VerticleManager mgr;


  private static ContextFactory getRhinoDebugContextFactory() {
    // Certification of origin:  This function in based on
    // code found in the Mozilla Rhino mailing list:
    //   https://groups.google.com/forum/?fromgroups=#!msg/mozilla.dev.tech.js-engine.rhino/pNJlUd_2ueg/GBc2std9czEJ

	  ContextFactory contextFactory = new RhinoDebugContextFactory();

    Main main = new Main("JS Debugger");
    main.setExitAction(new Runnable() {
      public void run() {
        System.exit(0);
      }
    });

    main.attachTo(contextFactory);
    main.setScopeProvider(new ScopeProvider() {
      public org.mozilla.javascript.Scriptable getScope() {
        throw new UnsupportedOperationException();
      }
    });

    main.pack();
    main.setSize(600, 460);
    main.setVisible(true);

    return contextFactory;
  }


  public RhinoVerticleFactory() {
    if(!RhinoVerticleFactory.initializeContextFactory) {
      boolean rhinoDebuggingEnabled = "true".equals(System.getProperty("vertx.debugRhino", "false"));
      if(rhinoDebuggingEnabled) {
        ContextFactory.initGlobal(RhinoVerticleFactory.getRhinoDebugContextFactory());
      } else {
        ContextFactory.initGlobal(new RhinoContextFactory());
      }

      RhinoVerticleFactory.initializeContextFactory = true;
    }
  }

  @Override
  public void init(VerticleManager mgr) {
	  this.mgr = mgr;
  }

  public Verticle createVerticle(String main, ClassLoader cl) throws Exception {
    Verticle app = new RhinoVerticle(main, cl);
    return app;
  }

  public void reportException(Throwable t) {

    //t.printStackTrace();

    Logger logger = mgr.getLogger();

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

