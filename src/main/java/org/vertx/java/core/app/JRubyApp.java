package org.vertx.java.core.app;

import org.jruby.embed.LocalContextScope;
import org.jruby.embed.ScriptingContainer;
import org.vertx.java.core.logging.Logger;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JRubyApp implements VertxApp {

  private static final Logger log = Logger.getLogger(JRubyApp.class);

  private final ScriptingContainer container;
  private final ClassLoader cl;
  private final String scriptName;
  private Object receiver;

  JRubyApp(String scriptName, ClassLoader cl) {
    this.container = new ScriptingContainer(LocalContextScope.SINGLETHREAD);
    container.setClassLoader(cl);
    this.cl = cl;
    this.scriptName = scriptName;
  }

  public void start() throws Exception {
    InputStream is = cl.getResourceAsStream(scriptName);
    receiver = container.runScriptlet(is, scriptName);
    try {
      is.close();
    } catch (IOException ignore) {
    }
  }

  public void stop() throws Exception {
    // TODO check if methods exists before calling it
    container.callMethod(receiver, "vertx_stop");
  }
}
