package org.vertx.java.core.app.jruby;

import org.jruby.embed.InvokeFailedException;
import org.jruby.embed.LocalContextScope;
import org.jruby.embed.ScriptingContainer;
import org.jruby.exceptions.RaiseException;
import org.vertx.java.core.app.Verticle;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JRubyVerticle implements Verticle {

  private static final Logger log = LoggerFactory.getLogger(JRubyVerticle.class);

  private final ScriptingContainer container;
  private final ClassLoader cl;
  private final String scriptName;

  JRubyVerticle(String scriptName, ClassLoader cl) {
    this.container = new ScriptingContainer(LocalContextScope.SINGLETHREAD);
    container.setClassLoader(cl);
    //Prevent JRuby from logging errors to stderr - we want to log ourselves
    container.setErrorWriter(new NullWriter());
    this.cl = cl;
    this.scriptName = scriptName;
  }

  public void start() throws Exception {
    InputStream is = cl.getResourceAsStream(scriptName);
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
