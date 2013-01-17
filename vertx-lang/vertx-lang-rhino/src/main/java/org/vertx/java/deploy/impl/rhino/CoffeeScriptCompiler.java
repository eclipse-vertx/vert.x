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
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.commonjs.module.provider.ModuleSource;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Scott Horn
 */
public class CoffeeScriptCompiler {
  private final Scriptable globalScope;

  public CoffeeScriptCompiler(ClassLoader classLoader) {
    try {
      Context context = Context.enter();
      try {
        globalScope = context.initStandardObjects();
        Script coffeeCompiler = (Script) Class.forName("org.vertx.java.deploy.impl.rhino.coffee_script").newInstance();        
        coffeeCompiler.exec(context, globalScope);
      } finally {
        Context.exit();
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public String coffeeScriptToJavaScript(URI coffeeScript) throws JavaScriptException,
      InvalidPathException, IOException, URISyntaxException {
    Path path = Paths.get(coffeeScript);
    String coffee = new String(Files.readAllBytes(path));
    return compile(coffee);
  }

  public ModuleSource coffeeScriptToJavaScript(ModuleSource source) throws JavaScriptException, InvalidPathException, IOException, URISyntaxException {
    try (Reader reader = source.getReader()) {
      StringBuilder coffee = new StringBuilder();
      char[] buf = new char[4096];
      int numRead;
      while ((numRead = reader.read(buf)) != -1) {
        coffee.append(new String(buf, 0, numRead));
      }
      String compiled = compile(coffee.toString());
      return new ModuleSource(new StringReader(compiled), source.getSecurityDomain(), source.getUri(), source.getBase(), source.getValidator());
    }
  }
  
  public String compile(String coffeeScriptSource) throws JavaScriptException {
    Context context = Context.enter();
    try {
      Scriptable compileScope = context.newObject(globalScope);
      compileScope.setParentScope(globalScope);
      compileScope.put("coffeeScriptSource", compileScope, coffeeScriptSource);

      Object src = context.evaluateString(compileScope,
          String.format("CoffeeScript.compile(coffeeScriptSource);"),
          "CoffeeScriptCompiler", 0, null);
      if (src != null) {
        return src.toString();
      } else {
        return null;
      }
    } finally {
      Context.exit();
    }
  }
}