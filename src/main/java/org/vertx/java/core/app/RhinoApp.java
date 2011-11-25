package org.vertx.java.core.app;

import org.jruby.embed.LocalContextScope;
import org.jruby.embed.ScriptingContainer;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.vertx.java.core.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RhinoApp implements VertxApp {

  private static final Logger log = Logger.getLogger(RhinoApp.class);


  private final ClassLoader cl;
  private final String scriptName;

  RhinoApp(String scriptName, ClassLoader cl) {

    this.cl = cl;
    this.scriptName = scriptName;
  }

  public void start() throws Exception {
    InputStream is = cl.getResourceAsStream(scriptName);

    BufferedReader reader = new BufferedReader(new InputStreamReader(is));

    Context cx = Context.enter();
    Scriptable scope = cx.initStandardObjects();
    cx.evaluateReader(scope, reader, scriptName, 0, null);



    try {
      is.close();
    } catch (IOException ignore) {
    }
  }

  public void stop() throws Exception {
    // TODO check if methods exists before calling it
   // container.callMethod(receiver, "vertx_stop");
  }
}
