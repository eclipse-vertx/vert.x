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

package org.vertx.java.deploy.impl.jruby;

import org.jruby.CompatVersion;
import org.jruby.RubyException;
import org.jruby.RubyNameError;
import org.jruby.embed.EvalFailedException;
import org.jruby.embed.InvokeFailedException;
import org.jruby.embed.LocalContextScope;
import org.jruby.embed.ScriptingContainer;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.builtin.IRubyObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.deploy.Verticle;
import org.vertx.java.deploy.impl.ModuleClassLoader;
import org.vertx.java.deploy.impl.VerticleFactory;
import org.vertx.java.deploy.impl.VerticleManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.List;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JRubyVerticleFactory implements VerticleFactory {

  private VerticleManager mgr;
  private ModuleClassLoader mcl;


  public JRubyVerticleFactory() {
  }

  @Override
  public void init(VerticleManager mgr, ModuleClassLoader mcl) {
	  this.mgr = mgr;
    this.mcl = mcl;
    if (System.getProperty("jruby.home") == null) {
      throw new IllegalStateException("In order to deploy Ruby applications you must set JRUBY_HOME to point " +
          "at your JRuby installation");
    }
  }

  @Override
  public Verticle createVerticle(String main) throws Exception {
    return new JRubyVerticle(main);
  }

  public void reportException(Throwable t) {
    Logger logger = mgr.getLogger();

    RaiseException je = null;
    if (t instanceof EvalFailedException) {
      EvalFailedException e = (EvalFailedException)t;
      Throwable cause = e.getCause();
      if (cause instanceof RaiseException) {
        je = (RaiseException)cause;
      }
    } else if (t instanceof RaiseException) {
      je = (RaiseException)t;
    }

    if (je != null) {

      RubyException re = je.getException();

      String msg;
      if (re instanceof RubyNameError) {
        RubyNameError rne = (RubyNameError)re;
        msg = "Invalid or undefined name: " + rne.name().toString();
      } else {
        msg = re.message.toString();
      }

      StringBuilder backtrace = new StringBuilder();
      IRubyObject bt = re.backtrace();

      if (bt instanceof List) {
        for (Object obj : (List<?>)bt) {
          if (obj instanceof String) {
            String line = (String)obj;
            addToBackTrace(backtrace, line);
          }
        }
      }

      logger.error("backtrace is " + backtrace);

      logger.error("Exception in Ruby verticle: " + msg +
        "\n" + backtrace);
    } else {
      logger.error("Unexpected exception in Ruby verticle", t);
    }
  }

  private void addToBackTrace(StringBuilder backtrace, String line) {
    if (line.contains(".rb")) {
      //We filter out any Java stack trace
      backtrace.append(line).append('\n');
    }
  }

  private class NullWriter extends Writer {

    public void write(char[] cbuf, int off, int len) throws IOException {
    }

    public void flush() throws IOException {
    }

    public void close() throws IOException {
    }
  }

  private class JRubyVerticle extends Verticle {

    private final String scriptName;
    private ScriptingContainer scontainer;

    JRubyVerticle(String scriptName) {
      this.scriptName = scriptName;
      this.scontainer = new ScriptingContainer(LocalContextScope.SINGLETHREAD);
      scontainer.setCompatVersion(CompatVersion.RUBY1_9);
      scontainer.setClassLoader(mcl);
      //Prevent JRuby from logging errors to stderr - we want to log ourselves
      scontainer.setErrorWriter(new NullWriter());
    }

    public void start() throws Exception {
      InputStream is = mcl.getResourceAsStream(scriptName);
      if (is == null) {
        throw new IllegalArgumentException("Cannot find verticle: " + scriptName);
      }
      scontainer.runScriptlet(is, scriptName);
      try {
        is.close();
      } catch (IOException ignore) {
      }
    }

    public void stop() throws Exception {
      try {
        // We call the script with receiver = null - this causes the method to be called on the top level
        // script
        scontainer.callMethod(null, "vertx_stop");
      } catch (InvokeFailedException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RaiseException) {
          // Gosh, this is a bit long winded!
          RaiseException re = (RaiseException)cause;
          String msg = "(NoMethodError) undefined method `vertx_stop'";
          if (re.getMessage().startsWith(msg)) {
            // OK - method is not mandatory
            return;
          }
        }
        throw e;
      }
    }


  }

}
