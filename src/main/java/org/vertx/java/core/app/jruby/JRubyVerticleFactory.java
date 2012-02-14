package org.vertx.java.core.app.jruby;

import org.jruby.RubyException;
import org.jruby.RubyNameError;
import org.jruby.embed.EvalFailedException;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.backtrace.BacktraceData;
import org.jruby.runtime.backtrace.RubyStackTraceElement;
import org.jruby.runtime.builtin.IRubyObject;
import org.mozilla.javascript.JavaScriptException;
import org.vertx.java.core.app.Verticle;
import org.vertx.java.core.app.VerticleFactory;
import org.vertx.java.core.app.VerticleManager;
import org.vertx.java.core.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
