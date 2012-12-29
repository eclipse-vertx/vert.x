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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.commonjs.module.ModuleScript;
import org.mozilla.javascript.commonjs.module.Require;
import org.mozilla.javascript.commonjs.module.RequireBuilder;
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider;
import org.mozilla.javascript.commonjs.module.provider.UrlModuleSourceProvider;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.Verticle;
import org.vertx.java.deploy.impl.VertxLocator;

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
              // super.getModuleScript uses moduleSourceProvider.loadSource to
              // check for modifications
              return super.getModuleScript(cx, moduleId, uri, uri, paths);
            }

            // If loading from classpath get a proper URI
            // Must check for each possible file to avoid getting other folders
            // Could also use getResources and iterate
            if (uri == null) {
              // Retrieve CommonJS package
              uri = getModule(moduleId);

              if (uri == null) {
                // Retrieve script
                uri = getScript(moduleId);
              }
            }

            // If it is a CommonJS package then retrieve the main file to
            // require.
            if (isPackage(uri)) {
              uri = getMainFile(uri);
            }

            // If the required script is .coffee, then compile it.
            if (isCoffeeScript(uri)) {
              uri = getCoffeeScriptCompiler(cl).coffeeScriptToJavaScript(uri);
            }

            return super.getModuleScript(cx, moduleId, uri, uri, paths);
          }

          /* Retrieves the uri of a require as a CommonJS package */
          public URI getModule(String moduleId) throws Exception {
            URL url = cl.getResource(Paths.get("mods", moduleId, "package.json").toString());
            if (url == null) {
              url = cl.getResource(Paths.get("mods", moduleId, "index.js").toString());
            }
            if (url == null) {
              url = cl.getResource(Paths.get("mods", moduleId, "index.coffee").toString());
            }

            if (url != null) {
              return new File(url.toURI()).getParentFile().toURI();
            }

            return null;
          }

          /* Retrieves the uri of a require as a single script */
          public URI getScript(String moduleId) throws Exception {
            /*
             * If no .js or .coffee extension specified, attempt to load them as
             * such
             */
            if (!moduleId.endsWith(".js") && !moduleId.endsWith(".coffee")) {
              URL url = cl.getResource(moduleId + ".js");
              if (url != null) {
                return url.toURI();
              }

              url = cl.getResource(moduleId + ".coffee");
              if (url != null) {
                return url.toURI();
              }
            } else {
              URL url = cl.getResource(moduleId);
              if (url != null) {
                return url.toURI();
              }
            }

            return null;
          }

          /* Gets the name of the main script file of a CommonJS Package */
          private URI getMainFile(URI uri) throws Exception {
            /* Retrieve the CommonJS package.json */

            // See: http://www-01.ibm.com/support/docview.wss?uid=swg24018970
            // PK64379; 6.1.0.15: ClassLoader.getResource Encodes Spaces in URLs
            // Fix spaces in URI
            Path packagePath = Paths.get(uri.toURL().getFile().replace("%20", " "));
            File packageFile = packagePath.resolve("package.json").toFile();

            String mainFileName = "index";

            if (packageFile.exists()) {
              JsonObject json;
              try {
                Buffer buffer = VertxLocator.vertx.fileSystem().readFileSync(packageFile.toString());
                json = new JsonObject(buffer.toString());
              } catch (DecodeException e) {
                throw new IllegalStateException(packageFile.toString() + " contains invalid json");
              }

              mainFileName = json.getString("main", "index");
            }

            File mainFile = null;
            // Load the main file directly
            if (mainFileName.endsWith(".coffee") || mainFileName.endsWith(".js")) {
              mainFile = packagePath.resolve(mainFileName).toFile();

              if (mainFile.exists()) {
                return mainFile.toURI();
              }
            } else {
              // Try to load it as a .coffee
              mainFile = packagePath.resolve(mainFileName + ".coffee").toFile();
              if (mainFile.exists()) {
                return mainFile.toURI();
              }
              // If not try to load it as a .js
              mainFile = packagePath.resolve(mainFileName + ".js").toFile();
              if (mainFile.exists()) {
                return mainFile.toURI();
              }

              // If not, load the file as is without extensions
              mainFile = packagePath.resolve(mainFileName).toFile();
              if (mainFile.exists()) {
                return mainFile.toURI();
              }
            }

            throw new IllegalStateException(packageFile.toString() + " specifies an invalid main script, or could not be found");
          }

          private boolean isCoffeeScript(URI uri) {
            return (uri != null && uri.toString().endsWith(".coffee"));
          }

          private boolean isPackage(URI uri) {
            return uri != null && uri.toString().startsWith("file:") && new File(uri).isDirectory();
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
    if (scriptName != null && scriptName.endsWith(".coffee")) {
      URL resource = cl.getResource(scriptName);
      if (resource != null) {
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
      scope.defineFunctionProperties(new String[] { "load" }, RhinoVerticle.class, ScriptableObject.DONTENUM);
      scope.defineProperty("console", new Console(), ScriptableObject.DONTENUM);

      // This is pretty ugly - we have to set some thread locals so we can get a
      // reference to the scope and
      // classloader in the load() method - this is because Rhino insists load()
      // must be static
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

  private class Console extends ScriptableObject {
    public Console() {
      defineFunctionProperties(new String[] {
          "log",
          "error",
          "warn",
          "info"
      }, Console.class, ScriptableObject.READONLY);
    }

    public void log(Object message) {
      container.getLogger().debug(message);
    }

    public void info(Object message) {
      container.getLogger().info(message);
    }

    public void error(Object message) {
      container.getLogger().error(message);
    }

    public void warn(Object message) {
      container.getLogger().warn(message);
    }

    @Override
    public String getClassName() {
      return "Console";
    }
  }
}
