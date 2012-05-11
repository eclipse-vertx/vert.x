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

package org.vertx.java.deploy.impl.rhino;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.commonjs.module.ModuleScript;
import org.mozilla.javascript.commonjs.module.Require;
import org.mozilla.javascript.commonjs.module.RequireBuilder;
import org.mozilla.javascript.commonjs.module.provider.ModuleSource;
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider;
import org.mozilla.javascript.commonjs.module.provider.UrlModuleSourceProvider;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.Verticle;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RhinoVerticle extends Verticle {

  private static final Logger log = LoggerFactory.getLogger(RhinoVerticle.class);

  private final ClassLoader cl;
  private final String scriptName;
  private Function stopFunction;
  private ScriptableObject scope;

  private static ThreadLocal<ScriptableObject> scopeThreadLocal = new ThreadLocal<>();
  private static ThreadLocal<ClassLoader> clThreadLocal = new ThreadLocal<>();

  RhinoVerticle(String scriptName, ClassLoader cl) {
    this.cl = cl;
    this.scriptName = scriptName;
  }

  public static void load(String moduleName) throws Exception {
    ScriptableObject scope = scopeThreadLocal.get();
    ClassLoader cl = clThreadLocal.get();
    Context cx = Context.getCurrentContext();
    cx.setOptimizationLevel(0);
    loadScript(cl, cx, scope, moduleName);
  }

  private static void addStandardObjectsToScope(ScriptableObject scope) {
    Object jsStdout = Context.javaToJS(System.out, scope);
    ScriptableObject.putProperty(scope, "stdout", jsStdout);
    Object jsStderr = Context.javaToJS(System.err, scope);
    ScriptableObject.putProperty(scope, "stderr", jsStderr);
  }

  // Extracted from Rhino1_7R3_RELEASE/toolsrc/org/mozilla/javascript/tools/shell/Global.java
  public static Require installRequire(Context cx, ScriptableObject scope, List<String> modulePath, boolean sandboxed) {
    RequireBuilder rb = new RequireBuilder();
    rb.setSandboxed(sandboxed);
    List<URI> uris = new ArrayList<URI>();
    if (modulePath != null) {
      for (String path : modulePath) {
        try {
          URI uri = new URI(path);
          if (!uri.isAbsolute()) {
            // call resolve("") to canonify the path
            uri = new File(path).toURI().resolve("");
          }
          if (!uri.toString().endsWith("/")) {
            // make sure URI always terminates with slash to
            // avoid loading from unintended locations
            uri = new URI(uri + "/");
          }
          uris.add(uri);
        } catch (URISyntaxException usx) {
          throw new RuntimeException(usx);
        }
      }
    }
    rb.setModuleScriptProvider(
            new SoftCachingModuleScriptProvider(new UrlModuleSourceProvider(uris, null)){
              @Override
              public ModuleScript getModuleScript(Context cx, String moduleId, URI uri, Scriptable paths) throws Exception {
                // Allow loading modules from <dir>/index.js
                if(uri != null && new File(uri).isDirectory()){
                  uri = URI.create(moduleId + File.separator + "index.js");
                }
                return super.getModuleScript(cx, moduleId, uri, paths);
              }
            });
    Require require = rb.createRequire(cx, scope);
    require.install(scope);
    return require;
  }

  private static Require installRequire(ClassLoader cl, Context cx, ScriptableObject scope){
    List<String> modulePaths= new ArrayList<>();

    // Add the classpath URLs
    URL[] urls = ((URLClassLoader)cl).getURLs();
    for(URL url : urls){
      modulePaths.add(url.getPath());
    }

    // Hack to add the javascript core library to the module path
    String corePath = new File(cl.getResource("vertx.js").getPath()).getParent();
    modulePaths.add(corePath);

    return installRequire(cx, scope, modulePaths, false);
  }

  private static void loadScript(ClassLoader cl, Context cx, ScriptableObject scope, String scriptName) throws Exception {
    InputStream is = cl.getResourceAsStream(scriptName);
    if (is == null) {
      throw new FileNotFoundException("Cannot find script: " + scriptName);
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    cx.evaluateReader(scope, reader, scriptName, 1, null);
    try {
      is.close();
    } catch (IOException ignore) {
    }
  }

  public void start() throws Exception {
    Context cx = Context.enter();
    try {
      scope = cx.initStandardObjects();

      addStandardObjectsToScope(scope);

      scope.defineFunctionProperties(new String[] { "load" }, RhinoVerticle.class, ScriptableObject.DONTENUM);

      // This is pretty ugly - we have to set some thread locals so we can get a reference to the scope and
      // classloader in the load() method - this is because Rhino insists load() must be static
      scopeThreadLocal.set(scope);
      clThreadLocal.set(cl);

      Require require = installRequire(cl, cx, scope);
      require.requireMain(cx, scriptName);

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
