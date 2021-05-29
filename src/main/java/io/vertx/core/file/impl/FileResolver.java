/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.file.impl;

import io.netty.util.internal.PlatformDependent;
import io.vertx.core.VertxException;
import io.vertx.core.file.FileSystemOptions;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static io.vertx.core.net.impl.URIDecoder.decodeURIComponent;

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
 * @author <a href="https://github.com/rworsnop/">Rob Worsnop</a>
 */
public class FileResolver {

  public static final String DISABLE_FILE_CACHING_PROP_NAME = "vertx.disableFileCaching";
  public static final String DISABLE_CP_RESOLVING_PROP_NAME = "vertx.disableFileCPResolving";
  public static final String CACHE_DIR_BASE_PROP_NAME = "vertx.cacheDirBase";

  private static final String FILE_SEP = System.getProperty("file.separator");
  private static final boolean NON_UNIX_FILE_SEP = !FILE_SEP.equals("/");

  private final File cwd;
  private final boolean enableCaching;
  private final boolean closeCache;
  private final FileCache cache;

  public FileResolver() {
    this(new FileSystemOptions());
  }

  public FileResolver(FileSystemOptions fileSystemOptions) {
    this(
      fileSystemOptions.isFileCachingEnabled(),
      fileSystemOptions.isClassPathResolvingEnabled() ? FileCache.setupCache(fileSystemOptions.getFileCacheDir()) : null,
      fileSystemOptions.isClassPathResolvingEnabled());
  }

  public FileResolver(boolean enableCaching, FileCache cache, boolean closeCache) {
    this.enableCaching = enableCaching;
    this.cache = cache;
    this.closeCache = closeCache;

    String cwdOverride = System.getProperty("vertx.cwd");
    if (cwdOverride != null) {
      cwd = new File(cwdOverride).getAbsoluteFile();
    } else {
      cwd = null;
    }
  }

  /**
   * Close this file resolver, this is a blocking operation.
   */
  public void close() throws IOException {
    if (closeCache) {
      cache.close();
    }
  }

  public File resolveFile(String fileName) {
    // First look for file with that name on disk
    File file = new File(fileName);
    if (cwd != null && !file.isAbsolute()) {
      file = new File(cwd, fileName);
    }
    if (this.cache == null) {
      return file;
    }
    // We need to synchronized here to avoid 2 different threads to copy the file to the cache directory and so
    // corrupting the content.
    synchronized (cache) {
      if (!file.exists()) {
        // Look for it in local file cache
        File cacheFile = cache.getFile(fileName);
        if (this.enableCaching && cacheFile.exists()) {
          return cacheFile;
        }
        // Look for file on classpath
        ClassLoader cl = getClassLoader();

        //https://github.com/eclipse/vert.x/issues/2126
        //Cache all elements in the parent directory if it exists
        //this is so that listing the directory after an individual file has
        //been read works.
        String parentFileName = file.getParent();
        if (parentFileName != null) {
          if (NON_UNIX_FILE_SEP) {
            parentFileName = parentFileName.replace(FILE_SEP, "/");
          }
          URL directoryContents = getValidClassLoaderResource(cl, parentFileName);
          if (directoryContents != null) {
            unpackUrlResource(directoryContents, parentFileName, cl, true);
          }
        }

        if (NON_UNIX_FILE_SEP) {
          fileName = fileName.replace(FILE_SEP, "/");
        }
        URL url = getValidClassLoaderResource(cl, fileName);
        if (url != null) {
          return unpackUrlResource(url, fileName, cl, false);
        }
      }
    }
    return file;
  }

  private static boolean isValidWindowsCachePath(char c) {
    if (c < 32) {
      return false;
    }
    switch (c) {
      case '"':
      case '*':
      case ':':
      case '<':
      case '>':
      case '?':
      case '|':
        return false;
      default:
        return true;
    }
  }

  private static boolean isValidCachePath(String fileName) {
    if (PlatformDependent.isWindows()) {
      int len = fileName.length();
      for (int i = 0;i < len;i++) {
        char c = fileName.charAt(i);
        if (!isValidWindowsCachePath(c)) {
          return false;
        }
        // Space only valid when it's not ending a name
        if (c == ' ' && (i + 1 == len || fileName.charAt(i + 1) == '/')) {
          return false;
        }
      }
      return true;
    } else {
      return fileName.indexOf('\u0000') == -1;
    }
  }

  /**
   * Get a class loader resource that can unpack to a valid cache path.
   *
   * Some valid entries are avoided purposely when we cannot create the corresponding file in the file cache.
   */
  private static URL getValidClassLoaderResource(ClassLoader cl, String fileName) {
    URL resource = cl.getResource(fileName);
    if (resource != null && !isValidCachePath(fileName)) {
      return null;
    }
    return resource;
  }

  private File unpackUrlResource(URL url, String fileName, ClassLoader cl, boolean isDir) {
    String prot = url.getProtocol();
    switch (prot) {
      case "file":
        return unpackFromFileURL(url, fileName, cl);
      case "jar":
        return unpackFromJarURL(url, fileName, cl);
      case "bundle": // Apache Felix, Knopflerfish
      case "bundleentry": // Equinox
      case "bundleresource": // Equinox
      case "jrt": // java run-time (JEP 220)
      case "resource":  // substratevm (graal native image)
        return unpackFromBundleURL(url, isDir);
      default:
        throw new IllegalStateException("Invalid url protocol: " + prot);
    }
  }


  private File unpackFromFileURL(URL url, String fileName, ClassLoader cl) {
    final File resource = new File(decodeURIComponent(url.getPath(), false));
    boolean isDirectory = resource.isDirectory();
    File cacheFile;
    try {
      cacheFile = cache.cache(fileName, resource, !enableCaching);
    } catch (IOException e) {
      throw new VertxException(e);
    }
    if (isDirectory) {
      String[] listing = resource.list();
      if (listing != null) {
        for (String file: listing) {
          String subResource = fileName + "/" + file;
          URL url2 = getValidClassLoaderResource(cl, subResource);
          if (url2 == null) {
            throw new VertxException("Invalid resource: " + subResource);
          }
          unpackFromFileURL(url2, subResource, cl);
        }
      }
    }
    return cacheFile;
  }

  private File unpackFromJarURL(URL url, String fileName, ClassLoader cl) {
    ZipFile zip = null;
    try {
      String path = url.getPath();
      int idx1 = path.lastIndexOf(".jar!");
      if (idx1 == -1) {
        idx1 = path.lastIndexOf(".zip!");
      }
      int idx2 = path.lastIndexOf(".jar!", idx1 - 1);
      if (idx2 == -1) {
        idx2 = path.lastIndexOf(".zip!", idx1 - 1);
      }
      if (idx2 == -1) {
        File file = new File(decodeURIComponent(path.substring(5, idx1 + 4), false));
        zip = new ZipFile(file);
      } else {
        String s = path.substring(idx2 + 6, idx1 + 4);
        File file = resolveFile(s);
        zip = new ZipFile(file);
      }

      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();
        if (name.startsWith(fileName)) {
          if (name.endsWith("/")) {
            // Directory
            cache.cacheDir(name);
          } else {
            try (InputStream is = zip.getInputStream(entry)) {
              cache.cacheFile(name, is, !enableCaching);
            }
          }

        }
      }
    } catch (IOException e) {
      throw new VertxException(e);
    } finally {
      closeQuietly(zip);
    }

    return cache.getFile(fileName);
  }

  private void closeQuietly(Closeable zip) {
    if (zip != null) {
      try {
        zip.close();
      } catch (IOException e) {
        // Ignored.
      }
    }
  }

  /**
   * It is possible to determine if a resource from a bundle is a directory based on whether or not the ClassLoader
   * returns null for a path (which does not already contain a trailing '/') *and* that path with an added trailing '/'
   *
   * @param url      the url
   * @return if the bundle resource represented by the bundle URL is a directory
   */
  private boolean isBundleUrlDirectory(URL url) {
    return url.toExternalForm().endsWith("/") ||
      getValidClassLoaderResource(getClassLoader(), url.getPath().substring(1) + "/") != null;
  }

  /**
   * bundle:// urls are used by OSGi implementations to refer to a file contained in a bundle, or in a fragment. There
   * is not much we can do to get the file from it, except reading it from the url. This method copies the files by
   * reading it from the url.
   *
   * @param url      the url
   * @return the extracted file
   */
  private File unpackFromBundleURL(URL url, boolean isDir) {
    String file = url.getHost() + File.separator + url.getFile();
    try {
      if ((getClassLoader() != null && isBundleUrlDirectory(url))  || isDir) {
        // Directory
        cache.cacheDir(file);
      } else {
        try (InputStream is = url.openStream()) {
          cache.cacheFile(file, is, !enableCaching);
        }
      }
    } catch (IOException e) {
      throw new VertxException(e);
    }
    return cache.getFile(file);
  }


  private ClassLoader getClassLoader() {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) {
      cl = getClass().getClassLoader();
    }
    // when running on substratevm (graal) the access to class loaders
    // is very limited and might be only available from compile time
    // known classes. (Object is always known, so we do a final attempt
    // to get it here).
    if (cl == null) {
      cl = Object.class.getClassLoader();
    }
    return cl;
  }
}
