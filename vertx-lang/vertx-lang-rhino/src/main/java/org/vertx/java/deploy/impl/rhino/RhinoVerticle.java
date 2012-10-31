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

import org.mozilla.javascript.*;
import org.mozilla.javascript.commonjs.module.ModuleScript;
import org.mozilla.javascript.commonjs.module.Require;
import org.mozilla.javascript.commonjs.module.RequireBuilder;
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider;
import org.mozilla.javascript.commonjs.module.provider.UrlModuleSourceProvider;
import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.Verticle;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.Scanner;

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
  private static CoffeeScriptCompiler coffeeScriptCompiler = null;

  RhinoVerticle(String scriptName, ClassLoader cl) {
    this.cl = cl;
    this.scriptName = scriptName;
  }

  public static void load(String moduleName) throws Exception {
    ScriptableObject scope = scopeThreadLocal.get();
    ClassLoader cl = clThreadLocal.get();
    Context cx = Context.getCurrentContext();
    loadScript(cl, cx, scope, moduleName);
  }

  private static void addStandardObjectsToScope(ScriptableObject scope) {
    Object jsStdout = Context.javaToJS(System.out, scope);
    ScriptableObject.putProperty(scope, "stdout", jsStdout);
    Object jsStderr = Context.javaToJS(System.err, scope);
    ScriptableObject.putProperty(scope, "stderr", jsStderr);
  }
  
  private static synchronized CoffeeScriptCompiler getCoffeeScriptCompiler(ClassLoader cl) {
    // Lazy load coffee script compiler
    if (RhinoVerticle.coffeeScriptCompiler == null) {
      RhinoVerticle.coffeeScriptCompiler = new CoffeeScriptCompiler(cl);
    }
    return RhinoVerticle.coffeeScriptCompiler;
  }
  
  // Support for loading from CommonJS modules
  private static Require installRequire(final ClassLoader cl, Context cx, ScriptableObject scope) {
    RequireBuilder rb = new RequireBuilder();
    rb.setSandboxed(false);

    rb.setModuleScriptProvider(
        new SoftCachingModuleScriptProvider(new UrlModuleSourceProvider(null, null)) {

          @Override
          public ModuleScript getModuleScript(Context cx, String moduleId, URI uri, URI base, Scriptable paths) throws Exception {

            // Check for cached version
            CachedModuleScript cachedModule = getLoadedModule(moduleId);
            if (cachedModule != null) {
              // cachedModule.getModule() is not public
              // super.getModuleScript uses moduleSourceProvider.loadSource to check for modifications
              return super.getModuleScript(cx, moduleId, uri, uri, paths);
            }

            // If loading from classpath get a proper URI
            // Must check for each possible file to avoid getting other folders
            // Could also use getResources and iterate
            if (uri == null) {
              URL url = cl.getResource(moduleId + File.separator + "package.json");
              if (url == null) {
                url = cl.getResource(moduleId + File.separator + "index.json");
              }
              if (url != null) {
                url = new File(url.getFile()).getParentFile().toURI().toURL();
              } else {
                if (!moduleId.endsWith(".js") && !moduleId.endsWith(".coffee")) {
                  url = cl.getResource(moduleId + ".js"); // Try .js first
                  if(url == null) {
                      url = cl.getResource(moduleId + ".coffee"); // Then try .coffee
                  }
                } else {
                  url = cl.getResource(moduleId);
                }
              }

              if (url != null) {
                uri = url.toURI();
              }
            }

            if (uri != null && uri.toString().startsWith("file:") && new File(uri).isDirectory()) {

              String main = "index";

              // Allow loading modules from <dir>/package.json
              File packageFile = new File(uri.getPath(), "package.json");

              if (packageFile.exists()) {

                String conf = null;
                try (Scanner scanner = new Scanner(packageFile).useDelimiter("\\A")){
                  conf = scanner.next();
                } catch (FileNotFoundException e) {
                }

                JsonObject json;
                try {
                  json = new JsonObject(conf);
                } catch (DecodeException e) {
                  throw new IllegalStateException("Module " + moduleId + " package.json contains invalid json");
                }

                main = json.getString("main");
              }

              // Allow loading modules from <dir>/<main>.js or <dir>/<main>.coffee
              File mainFile = new File(uri.getPath(), main.endsWith(".js") ? main : main+".js");
              if (!mainFile.exists() && !main.endsWith(".js") && !main.endsWith(".coffee")) {
                  mainFile = new File(uri.getPath(), main+".coffee");
                  if(mainFile.exists()) {
                      uri = mainFile.toURI();
                  }
              } else {
                uri = mainFile.toURI();
              } 

            }

            if (uri != null && uri.toString().endsWith(".coffee")) {
                uri = getCoffeeScriptCompiler(cl).coffeeScriptToJavaScript(uri);                
            }
            return super.getModuleScript(cx, moduleId, uri, uri, paths);
          }
        });

    // Force export of vertxStop
    rb.setPostExec(new Script() {
      @Override
      public Object exec(Context context, Scriptable scope) {
        String js = "if(typeof vertxStop == 'function'){ " +
            "module.exports.vertxStop = vertxStop;" +
            "}";
        return context.evaluateString(scope, js, "postExec", 1, null);
      }
    });

    Require require = rb.createRequire(cx, scope);

    return require;
  }

  private static void loadScript(ClassLoader cl, Context cx, ScriptableObject scope, String scriptName) throws Exception {
    if(scriptName != null && scriptName.endsWith(".coffee")) {
        URL resource = cl.getResource(scriptName);
        if(resource != null) {
            getCoffeeScriptCompiler(cl).coffeeScriptToJavaScript(resource.toURI());
            scriptName += ".js";
        } else {
            throw new FileNotFoundException("Cannot find script: " + scriptName);
        }
    } 
    InputStream is = cl.getResourceAsStream(scriptName);
    if (is == null) {
      throw new FileNotFoundException("Cannot find script: " + scriptName);
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    ClassLoader old = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      cx.evaluateReader(scope, reader, scriptName, 1, null);
      try {
        is.close();
      } catch (IOException ignore) {
      }
    } finally {
      Thread.currentThread().setContextClassLoader(old);
    }
  }

  public void start() throws Exception {
    Context cx = Context.enter();
    cx.setOptimizationLevel(2);
    try {
      scope = cx.initStandardObjects();

      addStandardObjectsToScope(scope);
      scope.defineFunctionProperties(new String[]{"load"}, RhinoVerticle.class, ScriptableObject.DONTENUM);

      // This is pretty ugly - we have to set some thread locals so we can get a reference to the scope and
      // classloader in the load() method - this is because Rhino insists load() must be static
      scopeThreadLocal.set(scope);
      clThreadLocal.set(cl);

      Thread.currentThread().setContextClassLoader(cl);
      Require require = installRequire(cl, cx, scope);

      Scriptable script = require.requireMain(cx, scriptName);
      try {
        stopFunction = (Function) script.get("vertxStop", scope);
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

  
