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

package org.vertx.java.deploy.impl.java;

import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import javax.tools.*;
import javax.tools.JavaFileObject.Kind;
import java.io.File;
import java.util.Collections;

/**
 *
 * Classloader for dynamic .java source file compilation and loading.
 *
 * @author Janne Hietam&auml;ki
 */
public class CompilingClassLoader extends ClassLoader {

  private static final Logger log = LoggerFactory.getLogger(CompilingClassLoader.class);

  private final String className;
  private final MemoryFileManager fileManager;

  private static final String FILE_SEP = System.getProperty("file.separator");

  public CompilingClassLoader(ClassLoader loader, String sourceName) {
    super(loader);
    String temp = sourceName.replace(FILE_SEP, ".");
    this.className = temp.substring(0, sourceName.length() - Kind.SOURCE.extension.length());
    File sourceFile = new File(sourceName).getAbsoluteFile();
    if (!sourceFile.canRead()) {
      throw new RuntimeException("File not found: " + sourceName);
    }
    try {
      DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
      JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
      StandardJavaFileManager standardFileManager = javaCompiler.getStandardFileManager(null, null, null);

      File sourceRoot;
      if (sourceName.contains(FILE_SEP)) {
        sourceRoot = new File(".").getAbsoluteFile();
      } else {
        sourceRoot = sourceFile.getParentFile();
      }
      standardFileManager.setLocation(StandardLocation.SOURCE_PATH, Collections.singleton(sourceRoot));
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
    return className;
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
