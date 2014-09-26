/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.impl.verticle;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Java in-memory file manager used by {@link CompilingClassLoader} to handle
 * compiled classes
 *
 * @author Janne Hietam&auml;ki
 */
public class MemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {
  private final Map<String, ByteArrayOutputStream> compiledClasses = new HashMap<>();
  private final PackageHelper helper;

  public MemoryFileManager(ClassLoader classLoader, JavaFileManager fileManager) {
    super(fileManager);
    helper = new PackageHelper(classLoader);
  }

  @Override
  public JavaFileObject getJavaFileForOutput(Location location, final String className,
                                             JavaFileObject.Kind kind, FileObject sibling) throws IOException {
    try {
      return new SimpleJavaFileObject(new URI(""), kind) {
        public OutputStream openOutputStream() throws IOException {
          ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
          compiledClasses.put(className, outputStream);
          return outputStream;
        }
      };
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  public byte[] getCompiledClass(String name) {
    ByteArrayOutputStream bytes = compiledClasses.get(name);
    if (bytes == null) {
      return null;
    }
    return bytes.toByteArray();
  }

  @Override
  public String inferBinaryName(Location location, JavaFileObject file) {
    if (file instanceof CustomJavaFileObject) {
      return ((CustomJavaFileObject) file).binaryName();
    } else {
      return super.inferBinaryName(location, file);
    }
  }

  @Override
  public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds,
                                       boolean recurse) throws IOException {
    if (location == StandardLocation.CLASS_PATH && kinds.contains(JavaFileObject.Kind.CLASS)) {
      return helper.find(packageName);
    }
    return super.list(location, packageName, kinds, recurse);
  }
}
