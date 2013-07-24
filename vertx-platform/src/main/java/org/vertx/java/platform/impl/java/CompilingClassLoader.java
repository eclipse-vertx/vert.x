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

package org.vertx.java.platform.impl.java;

import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import javax.tools.*;
import javax.tools.JavaFileObject.Kind;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Collections;

/**
 *
 * Classloader for dynamic .java source file compilation and loading.
 *
 * @author Janne Hietam&auml;ki
 */
public class CompilingClassLoader extends ClassLoader {

  private static final Logger log = LoggerFactory.getLogger(CompilingClassLoader.class);

  private final JavaSourceContext javaSourceContext;
  private final MemoryFileManager fileManager;

  public CompilingClassLoader(ClassLoader loader, String sourceName) {
    super(loader);
    URL resource = getResource(sourceName);
    if(resource == null) {
      throw new RuntimeException("Resource not found: " + sourceName);
    }
    //Need to urldecode it too, since bug in JDK URL class which does not url decode it, so if it contains spaces you are screwed
    File sourceFile;
    try {
      sourceFile = new File(URLDecoder.decode(resource.getFile(), "UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("Failed to decode " + e.getMessage());
    }
    if (!sourceFile.canRead()) {
      throw new RuntimeException("File not found: " + sourceFile.getAbsolutePath() + " current dir is: " + new File(".").getAbsolutePath());
    }

    this.javaSourceContext = new JavaSourceContext(sourceFile);

    try {
      DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
      JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
      StandardJavaFileManager standardFileManager = javaCompiler.getStandardFileManager(null, null, null);

      standardFileManager.setLocation(StandardLocation.SOURCE_PATH, Collections.singleton(javaSourceContext.getSourceRoot()));
      fileManager = new MemoryFileManager(loader, standardFileManager);
      JavaFileObject javaFile = standardFileManager.getJavaFileForInput(StandardLocation.SOURCE_PATH, resolveMainClassName(), Kind.SOURCE);
      JavaCompiler.CompilationTask task = javaCompiler.getTask(null, fileManager, diagnostics, null, null, Collections.singleton(javaFile));
      boolean valid = task.call();
      if (valid) {
        for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
          log.info(d);
        }
      } else {
        for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
          log.warn(d);
        }
        throw new RuntimeException("Compilation failed!");
      }
    } catch (Exception e) {
      throw new RuntimeException("Compilation failed", e);
    }
  }

  public String resolveMainClassName() {
    return javaSourceContext.getClassName();
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    byte[] bytecode = fileManager.getCompiledClass(name);
    if (bytecode == null) {
      throw new ClassNotFoundException(name);
    }
    return defineClass(name, bytecode, 0, bytecode.length);
  }
}
