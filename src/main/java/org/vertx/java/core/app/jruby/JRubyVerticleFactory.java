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

package org.vertx.java.core.app.jruby;

import org.jruby.RubyException;
import org.jruby.RubyNameError;
import org.jruby.embed.EvalFailedException;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.builtin.IRubyObject;
import org.vertx.java.core.app.Verticle;
import org.vertx.java.core.app.VerticleFactory;
import org.vertx.java.core.app.VerticleManager;
import org.vertx.java.core.logging.Logger;

import java.util.List;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JRubyVerticleFactory implements VerticleFactory {
  public Verticle createVerticle(String main, ClassLoader cl) throws Exception {
    if (System.getProperty("jruby.home") == null) {
      throw new IllegalStateException("In order to deploy Ruby applications you must set JRUBY_HOME to point " +
          "at your JRuby installation");
    }
    Verticle app = new JRubyVerticle(main, cl);
    return app;
  }

  public void reportException(Throwable t) {
    Logger logger = VerticleManager.instance.getLogger();

    logger.error("Handling throwable " + t.getClass().getName());

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

      logger.info("je type is " + je.getClass().getName());

      RubyException re = je.getException();

      String msg;
      if (re instanceof RubyNameError) {
        RubyNameError rne = (RubyNameError)re;
        msg = "Invalid or undefined name: " + rne.name().toString();
      } else {
        msg = re.message.toString();
      }
      logger.info("re type is " + re.getClass().getName());

      StringBuilder backtrace = new StringBuilder();
      IRubyObject bt = re.backtrace();
      if (bt instanceof List) {
        for (Object obj : (List)bt) {
          if (obj instanceof String) {
            String line = (String)obj;
            if (line.contains(".rb")) {
              //We filter out any Java stack trace
              backtrace.append(line).append('\n');
            }
          }
        }
      }

      logger.error("Exception in Ruby verticle: " + msg +
        "\n" + backtrace);
    } else {
      logger.error("Unexpected exception in Ruby verticle", t);
    }
  }
}
