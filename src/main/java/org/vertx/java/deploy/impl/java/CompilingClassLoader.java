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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/**
 * @author Janne Hietam&auml;ki
 */
public class CompilingClassLoader extends ClassLoader {
  private final String sourceName;
  private final Map<String, ByteArrayOutputStream> compiledClasses = new HashMap<String, ByteArrayOutputStream>();

  public CompilingClassLoader(ClassLoader loader, String sourceName) {
    super(loader);
    this.sourceName = sourceName;
    compile();
  }

  public String resolveMainClassName() {
    return sourceName.substring(0, sourceName.length() - ".java".length());
  }
  
  private void compile() {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
    MemoryFileManager fileManager = new MemoryFileManager(javaCompiler.getStandardFileManager(null, null, null));
    JavaFileObject javaFile = new MemoryJavaFile(sourceName);

    JavaCompiler.CompilationTask task = javaCompiler.getTask(null, fileManager, diagnostics, null, null, Collections.singleton(javaFile));
    boolean valid = task.call();
    for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
      System.out.println(d);
    }
    if(!valid) {
      throw new RuntimeException("Compilation failed!");
    }
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    ByteArrayOutputStream bytecode = compiledClasses.get(name);
    if (bytecode == null) {
      throw new ClassNotFoundException(name);
    }
    return defineClass(name, bytecode.toByteArray(), 0, bytecode.size());
  }

  private class MemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {

    private URI DUMMY_URI;

    {
      try {
        DUMMY_URI = new URI("");
      } catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }

    public MemoryFileManager(JavaFileManager fileManager) {
      super(fileManager);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, final String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
      return new SimpleJavaFileObject(DUMMY_URI, kind) {
        public OutputStream openOutputStream() throws IOException {
          ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
          compiledClasses.put(className, outputStream);
          return outputStream;
        }
      };
    }
  }

  private static class MemoryJavaFile extends SimpleJavaFileObject {
    public MemoryJavaFile(String sourceName) {
      super(sourceUri(sourceName), Kind.SOURCE);
    }

    private static URI sourceUri(String sourceName) {
      try {
        return new URI(sourceName);
      } catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
      StringBuffer content = new StringBuffer();
      Reader reader = null;
      try {
        reader = new BufferedReader(new FileReader(new File(getName())));
        char[] buf = new char[2000];
        int numRead = 0;
        while ((numRead = reader.read(buf)) != -1) {
          content.append(String.valueOf(buf, 0, numRead));
        }
        return content;
      } finally {
        if (reader != null) {
          reader.close();
        }
      }
    }
  }
}
