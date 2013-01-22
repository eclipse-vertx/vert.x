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

import org.python.core.*;
import org.python.util.PythonInterpreter;
import org.vertx.java.deploy.Verticle;
import org.vertx.java.deploy.impl.ModuleClassLoader;
import org.vertx.java.deploy.impl.VerticleFactory;
import org.vertx.java.deploy.impl.VerticleManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="https://github.com/sjhorn">Scott Horn</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JythonVerticleFactory implements VerticleFactory {

  private VerticleManager mgr;
  private ModuleClassLoader mcl;
  private PythonInterpreter py;
  private String stopFuncName;
  private static final AtomicInteger seq = new AtomicInteger();

  public JythonVerticleFactory() {
  }

  @Override
  public void init(VerticleManager mgr, ModuleClassLoader mcl) {
    this.mgr = mgr;
    this.mcl = mcl;
    System.setProperty("python.options.internalTablesImpl","weak");
    Options.includeJavaStackInExceptions = false;
    this.py = new PythonInterpreter(null, new PySystemState());
  }

  public Verticle createVerticle(String main) throws Exception {
    return new JythonVerticle(main);
  }

  public void reportException(Throwable t) {
    mgr.getLogger().error("Exception in Python verticle", t);
  }

  private class JythonVerticle extends Verticle {

    private final String scriptName;


    JythonVerticle(String scriptName) {
      this.scriptName = scriptName;
    }

    public void start() throws Exception {
      ClassLoader old = Thread.currentThread().getContextClassLoader();
      try (InputStream is = mcl.getResourceAsStream(scriptName)) {
        if (is == null) {
          throw new IllegalArgumentException("Cannot find verticle: " + scriptName);
        }

        // We wrap the python verticle in a function so different instances don't see each others top level vars
        String genName = "__VertxInternalVert__" + seq.incrementAndGet();
        String funcName = "f" + genName;
        StringBuilder sWrap = new StringBuilder("def ").append(funcName).append("():\n");
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        for (String line = br.readLine(); line != null; line = br.readLine()) {
          // Append line indented by a tab
          sWrap.append("\t").append(line).append("\n");
        }
        br.close();
        // The return value of the wrapping function is the vertx_stop function (if defined)
        sWrap.append("\tif 'vertx_stop' in locals():\n");
        sWrap.append("\t\treturn vertx_stop\n");
        sWrap.append("\telse:\n");
        sWrap.append("\t\treturn None\n");

        // And then we have to add a top level wrapper method that calls the actual vertx_stop method
        String stopFuncVar = "v" + genName;
        sWrap.append(stopFuncVar).append(" = ").append(funcName).append("()\n");
        stopFuncName = funcName + "_stop";
        sWrap.append("def ").append(stopFuncName).append("():\n");
        sWrap.append("\tif ").append(stopFuncVar).append(" is not None:\n");
        sWrap.append("\t\t").append(stopFuncVar).append("()\n");

        Thread.currentThread().setContextClassLoader(mcl);
        // Inject vertx and container as a variable in the script
        py.set("vertx", getVertx());
        py.set("container", getContainer());

//        System.out.println("===========================================");
//        System.out.println(sWrap.toString());
//        System.out.println("===========================================");
        py.exec(sWrap.toString());

      } finally {
        Thread.currentThread().setContextClassLoader(old);
      }
    }

    public void stop() throws Exception {
      py.exec(stopFuncName + "()");
      py.cleanup();
    }
  }


}