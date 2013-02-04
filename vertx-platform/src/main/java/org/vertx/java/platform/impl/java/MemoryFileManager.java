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

import javax.tools.*;
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
