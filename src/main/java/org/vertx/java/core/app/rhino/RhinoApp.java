package org.vertx.java.core.app.rhino;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;
import org.vertx.java.core.app.VertxApp;
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
  private Function stopFunction;
  private ScriptableObject scope;

  private static ThreadLocal<ScriptableObject> scopeThreadLocal = new ThreadLocal<>();
  private static ThreadLocal<ClassLoader> clThreadLocal = new ThreadLocal<>();

  RhinoApp(String scriptName, ClassLoader cl) {
    this.cl = cl;
    this.scriptName = scriptName;
  }

  public static void load(String moduleName) {
    try {
      log.info("In load " + moduleName);

      ScriptableObject scope = scopeThreadLocal.get();
      ClassLoader cl = clThreadLocal.get();
      Context cx = Context.getCurrentContext();
      loadScript(cl, cx, scope, moduleName);
    } catch (Exception e) {
      log.error("Failed to load module: " + moduleName, e);
    }
  }

  private static void loadScript(ClassLoader cl, Context cx, ScriptableObject scope, String scriptName) throws Exception {
    InputStream is = cl.getResourceAsStream(scriptName);
    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    cx.evaluateReader(scope, reader, "net.js", 0, null);
    try {
      is.close();
    } catch (IOException ignore) {
    }
  }

  private static void addStandardObjectsToScope(ScriptableObject scope) {
    Object jsLog = Context.javaToJS(System.out, scope);
    ScriptableObject.putProperty(scope, "log", jsLog);
  }

  public void start() throws Exception {
    log.info("in rhinoapp start");

    Context cx = Context.enter();
    try {
      scope = cx.initStandardObjects();

      addStandardObjectsToScope(scope);
      scope.defineFunctionProperties(new String[] { "load" }, RhinoApp.class, ScriptableObject.DONTENUM);

      // This is pretty ugly - we have to set some thread locals so we can get a reference to the scope and
      // classloader in the load() method - this is because Rhino insists load() must be static
      scopeThreadLocal.set(scope);
      clThreadLocal.set(cl);

      loadScript(cl, cx, scope, scriptName);

      try {
        stopFunction = (Function)scope.get("vertxStop", scope);
      } catch (ClassCastException e) {
        // Get CCE if no such function
        stopFunction = null;
      }

    } finally {
      Context.exit();
    }
  }

  public void stop() throws Exception {
    if (stopFunction != null) {
      Context cx = Context.enter();
      try {
        stopFunction.call(cx, scope, scope, null);
      } finally {
        Context.exit();
      }
    }
  }
}
