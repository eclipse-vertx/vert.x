/*
 * Copyright (c) 2011-2013 The original author or authors
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

package org.vertx.java.core.file.impl;

import org.vertx.java.core.VertxException;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

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

  public static Path resolvePath(Path path) {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl != null) {
      String spath = path.toString();
      String substituted = FILE_SEP == '/' ? spath : spath.replace(FILE_SEP, '/');
      URL url = cl.getResource(substituted);
      if (url != null) {
        Path thePath = urlToPath(url);
        if (thePath != null) {
          return thePath;
        }
      }
    }
    return path;
  }

  public static Path urlToPath(URL url) {
    if (FILE_SEP == '/') {
      // *nix - a bit quicker than pissing around with URIs
      String sfile = url.getFile();
      if (sfile != null) {
        return Paths.get(url.getFile());
      } else {
        return null;
      }
    } else {
      // E.g. windows
      // See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4701321

      try {
        URI uri = url.toURI();
        if (uri.isOpaque()) {
          return Paths.get(url.getPath());
        } else {
          return Paths.get(uri);
        }
      } catch (Exception exc) {
        throw new VertxException(exc);
      }
    }
  }


  @Override
  public Path resolve(Path path) {
    return resolvePath(path);
  }
}
