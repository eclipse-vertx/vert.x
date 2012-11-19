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
import org.jruby.embed.InvokeFailedException;
import org.jruby.embed.LocalContextScope;
import org.jruby.embed.ScriptingContainer;
import org.jruby.exceptions.RaiseException;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.Verticle;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JRubyVerticle extends Verticle {

  private static final Logger log = LoggerFactory.getLogger(JRubyVerticle.class);

  private final ScriptingContainer container;
  private final ClassLoader cl;
  private final String scriptName;

  JRubyVerticle(String scriptName, ClassLoader cl) {
    this.container = new ScriptingContainer(LocalContextScope.SINGLETHREAD);
    container.setCompatVersion(CompatVersion.RUBY1_9);
    container.setClassLoader(cl);
    //Prevent JRuby from logging errors to stderr - we want to log ourselves
    container.setErrorWriter(new NullWriter());
    this.cl = cl;
    this.scriptName = scriptName;
  }

  public void start() throws Exception {
    InputStream is = cl.getResourceAsStream(scriptName);
    if (is == null) {
      throw new IllegalArgumentException("Cannot find verticle: " + scriptName);
    }
    // Inject vertx as a variable in the script
    container.runScriptlet(is, scriptName);
    try {
      is.close();
    } catch (IOException ignore) {
    }
  }

  public void stop() throws Exception {
    try {
      // We call the script with receiver = null - this causes the method to be called on the top level
      // script
      container.callMethod(null, "vertx_stop");
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
    container.clear();
  }

  private class NullWriter extends Writer {

    public void write(char[] cbuf, int off, int len) throws IOException {
    }

    public void flush() throws IOException {
    }

    public void close() throws IOException {
    }
  }
}
