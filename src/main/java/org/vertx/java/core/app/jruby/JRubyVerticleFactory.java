package org.vertx.java.core.app.jruby;

import org.jruby.RubyException;
import org.jruby.embed.EvalFailedException;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.backtrace.BacktraceData;
import org.jruby.runtime.backtrace.RubyStackTraceElement;
import org.mozilla.javascript.JavaScriptException;
import org.vertx.java.core.app.Verticle;
import org.vertx.java.core.app.VerticleFactory;
import org.vertx.java.core.app.VerticleManager;
import org.vertx.java.core.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

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

    if (t instanceof EvalFailedException) {
      EvalFailedException je = (EvalFailedException)t;
      Throwable cause = je.getCause();
      if (cause instanceof RaiseException) {
        // Gosh, this is a bit long winded!
        RaiseException re = (RaiseException)cause;
        RubyException rbe = re.getException();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        rbe.printBacktrace(ps);
        ps.flush();
        String stack = baos.toString();
        logger.error("Exception in Ruby verticle: " + rbe.message +
          "\n" + stack);
      }
    }


    logger.error("Exception in Ruby verticle", t);
  }
}
