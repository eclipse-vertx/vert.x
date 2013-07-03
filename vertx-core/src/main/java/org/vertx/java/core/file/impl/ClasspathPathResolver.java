package org.vertx.java.core.file.impl;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */

/**
 *
 * This resolver resolves a file by trying to load it from the classpath of the context class loader.
 * This is used when running modules by specifying a classpath as opposed to from the file system.
 * In this case, the modules might access files on disk, e.g. attempt to serve a file and this needs to be
 * found on the classpath that's specified
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClasspathPathResolver implements PathResolver {

  private static final char FILE_SEP = System.getProperty("file.separator").charAt(0);

  @Override
  public Path resolve(Path path) {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl != null) {
      char sysSeparator = path.getFileSystem().getSeparator().charAt(0);
      String sPath = path.toString();
      URL url = cl.getResource(sysSeparator == '/' ? sPath : sPath.replace(sysSeparator, '/'));
      if (url != null) {
        String sfile = url.getFile();
        if (sfile != null) {
          String substituted = FILE_SEP == '/' ? sfile : sfile.replace(FILE_SEP, '/');
          return Paths.get(substituted);
        }
      }
    }
    return path;
  }
}
