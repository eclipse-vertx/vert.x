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

package io.vertx.core.impl;

import io.vertx.core.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Sometimes the file resources of an application are bundled into jars, or are somewhere on the classpath but not
 * available on the file system, e.g. in the case of a Vert.x webapp bundled as a fat jar.
 *
 * In this case we want the application to access the resource from the classpath as if it was on the file system.
 *
 * We can do this by looking for the file on the classpath, and if found, copying it to a temporary cache directory
 * on disk and serving it from there.
 *
 * There is one cache dir per Vert.x instance and they are deleted on Vert.x shutdown.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FileResolver {

  public static final String DISABLE_FILE_CACHING_PROP_NAME = "vertx.disableFileCaching";
  public static final String DISABLE_CP_RESOLVING_PROP_NAME = "vertx.disableFileCPResolving";

  private static final String FILE_SEP = System.getProperty("file.separator");
  private static boolean NON_UNIX_FILE_SEP = !FILE_SEP.equals("/");

  private final Vertx vertx;
  private final boolean enableCaching = System.getProperty(DISABLE_FILE_CACHING_PROP_NAME) == null;
  private final boolean enableCPResolving = System.getProperty(DISABLE_CP_RESOLVING_PROP_NAME) == null;
  private final File cwd;
  private File cacheDir;

  public FileResolver(Vertx vertx) {
    this.vertx = vertx;
    String cwdOverride = System.getProperty("vertx.cwd");
    if (cwdOverride != null) {
      cwd = new File(cwdOverride).getAbsoluteFile();
    } else {
      cwd = null;
    }
  }

  public void deleteCacheDir(Handler<AsyncResult<Void>> handler) {
    if (cacheDir != null) {
      vertx.fileSystem().deleteRecursive(cacheDir.getAbsolutePath(), true, handler);
    } else {
      handler.handle(Future.succeededFuture());
    }
  }

  public File resolveFile(String fileName) {
    // First look for file with that name on disk
    File file = new File(fileName);
    if (cwd != null && !file.isAbsolute()) {
      file = new File(cwd, fileName);
    }
    if (!enableCPResolving) {
      return file;
    }
    if (!file.exists()) {
      setupCacheDir();
      // Look for it in local file cache
      File cacheFile = new File(cacheDir, fileName);
      if (enableCaching && cacheFile.exists()) {
        return cacheFile;
      }
      // Look for file on classpath
      ClassLoader cl = getClassLoader();
      if (NON_UNIX_FILE_SEP) {
        fileName = fileName.replace(FILE_SEP, "/");
      }
      URL url = cl.getResource(fileName);
      if (url != null) {
        String prot = url.getProtocol();
        switch (prot) {
          case "file":
            return unpackFromFileURL(url, fileName, cl);
          case "jar":
            return unpackFromJarURL(url, fileName);
          default:
            throw new IllegalStateException("Invalid url protocol: " + prot);
        }
      }
    }
    return file;
  }

  private synchronized File unpackFromFileURL(URL url, String fileName, ClassLoader cl) {
    File resource;
    try {
      resource = new File(URLDecoder.decode(url.getPath(), "UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new VertxException(e);
    }
    boolean isDirectory = resource.isDirectory();
    File cacheFile = new File(cacheDir, fileName);
    if (!isDirectory) {
      cacheFile.getParentFile().mkdirs();
      try {
        Files.copy(resource.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        throw new VertxException(e);
      }
    } else {
      cacheFile.mkdirs();
      String[] listing = resource.list();
      for (String file: listing) {
        String subResource = fileName + "/" + file;
        URL url2 = cl.getResource(subResource);
        unpackFromFileURL(url2, subResource, cl);
      }
    }
    return cacheFile;
  }

  private synchronized  File unpackFromJarURL(URL url, String fileName) {

    String path = url.getPath();
    String jarFile = path.substring(5, path.lastIndexOf(".jar!") + 4);

    try {
      ZipFile zip = new ZipFile(jarFile);
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();
        if (name.startsWith(fileName)) {
          File file = new File(cacheDir, name);
          if (name.endsWith("/")) {
            // Directory
            file.mkdirs();
          } else {
            file.getParentFile().mkdirs();
            try (InputStream is = zip.getInputStream(entry)) {
              Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
          }
        }
      }
    } catch (IOException e) {
      throw new VertxException(e);
    }

    return new File(cacheDir, fileName);
  }


  private ClassLoader getClassLoader() {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) {
      cl = getClass().getClassLoader();
    }
    return cl;
  }

  private void setupCacheDir() {
    if (cacheDir == null) {
      String cacheDirName = ".vertx/file-cache-" + UUID.randomUUID().toString();
      cacheDir = new File(cacheDirName);
      if (cacheDir.exists()) {
        vertx.fileSystem().deleteRecursiveBlocking(cacheDir.getAbsolutePath(), true);
      } else {
        if (!cacheDir.mkdirs()) {
          throw new IllegalStateException("Failed to create cache dir");
        }
      }
    }
  }


}

