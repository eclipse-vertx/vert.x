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
import io.vertx.core.impl.Utils;
import io.vertx.core.spi.file.FileResolver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static io.vertx.core.internal.net.RFC3986.decodeURIComponent;

/**
 * Sometimes the file resources of an application are bundled into jars, or are somewhere on the classpath but not
 * available on the file system, e.g. in the case of a Vert.x webapp bundled as a fat jar.
 * <p>
 * In this case we want the application to access the resource from the classpath as if it was on the file system.
 * <p>
 * We can do this by looking for the file on the classpath, and if found, copying it to a temporary cache directory
 * on disk and serving it from there.
 * <p>
 * There is one cache dir per Vert.x instance and they are deleted on Vert.x shutdown.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="https://github.com/rworsnop/">Rob Worsnop</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FileResolverImpl implements FileResolver {

  private static final boolean NON_UNIX_FILE_SEP = File.separatorChar != '/';
  private static final String JAR_URL_SEP = "!/";

  private final boolean enableCaching;
  private final boolean enableCPResolving;
  private final FileCache cache;

  public FileResolverImpl() {
    this(new FileSystemOptions());
  }

  public FileResolverImpl(FileSystemOptions fileSystemOptions) {
    enableCaching = fileSystemOptions.isFileCachingEnabled();
    enableCPResolving = fileSystemOptions.isClassPathResolvingEnabled();

    if (enableCPResolving) {
      cache = FileCache.setupCache(fileSystemOptions.getFileCacheDir());
    } else {
      cache = null;
    }
  }

  public String cacheDir() {
    if (cache != null) {
      return cache.cacheDir();
    }
    return null;
  }

  public FileCache getFileCache() {
    return this.cache;
  }

  /**
   * Close this file resolver, this is a blocking operation.
   */
  public void close() throws IOException {
    if (enableCPResolving) {
      synchronized (cache) {
        cache.close();
      }
    }
  }

  public File resolveFile(String fileName) {
    int idx = fileName.length() - 1;
    if (idx >= 0 && fileName.charAt(idx) == '/') {
      fileName = fileName.substring(0, idx);
    }
    return resolveFile2(fileName);
  }

  public File resolveFile2(String fileName) {
    // First look for file with that name on disk
    File file = new File(fileName);
    boolean absolute = file.isAbsolute();

    if (this.cache == null) {
      return file;
    }
    // We need to synchronized here to avoid 2 different threads to copy the file to the cache directory and so
    // corrupting the content.
    if (!file.exists()) {
      synchronized (cache) {
        // When an absolute file is here, if it falls under the cache directory, then it should be made relative as it
        // could mean that a previous resolution has been used to resolve a non local file system resource
        File cacheFile = cache.getCanonicalFile(file);
        // the file may or may not be in the cache dir after canonicalization
        if (cacheFile == null) {
          // the given file cannot be resolved to the real FS
          return file;
        }
        // compute the difference of the path
        String relativize = cache.relativize(cacheFile.getPath());
        if (relativize != null) {
          // file is inside the cache dir, we can continue with the search
          if (this.enableCaching && cacheFile.exists()) {
            return cacheFile;
          }
          // as it wasn't found, maybe it hasn't been extracted yet
          // adjust the name and absolute references if needed
          if (absolute) {
            fileName = relativize;
            file = new File(relativize);
            absolute = false;
          }
        }
        // https://vertx.io/docs/vertx-core/java/#classpath
        // if a resource is still absolute, it means it's outside the cache, we don't need to handle it.
        // otherwise, we need to go over the classpath resources
        if (!absolute) {
          // Look for file on classpath
          ClassLoader cl = getClassLoader();

          //https://github.com/eclipse/vert.x/issues/2126
          //Cache all elements in the parent directory if it exists
          //this is so that listing the directory after an individual file has
          //been read works.
          File parentFile = file.getParentFile();
          while (parentFile != null) {
            String parentFileName = parentFile.getPath();
            if (NON_UNIX_FILE_SEP) {
              parentFileName = parentFileName.replace(File.separatorChar, '/');
            }
            URL directoryContents = getValidClassLoaderResource(cl, parentFileName);
            if (directoryContents != null) {
              unpackUrlResource(directoryContents, parentFileName, cl, true);
            }
            // https://github.com/eclipse-vertx/vert.x/issues/3654
            // go up one level until we reach the top, this way we ensure we extract the whole tree
            // not just a branch so directory listing will be correct
            parentFile = parentFile.getParentFile();
          }

          if (NON_UNIX_FILE_SEP) {
            fileName = fileName.replace(File.separatorChar, '/');
          }
          URL url = getValidClassLoaderResource(cl, fileName);
          if (url != null) {
            return unpackUrlResource(url, fileName, cl, false);
          }
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
      for (int i = 0; i < len; i++) {
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
   * <p>
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
      case "vfs":  // jboss-vfs
        return unpackFromBundleURL(url, fileName, isDir);
      default:
        throw new IllegalStateException("Invalid url protocol: " + prot);
    }
  }


  private File unpackFromFileURL(URL url, String fileName, ClassLoader cl) {
    final File resource = new File(decodeURIComponent(url.getPath(), false));
    boolean isDirectory = resource.isDirectory();
    File cacheFile;
    try {
      cacheFile = cache.cacheFile(fileName, resource, !enableCaching);
    } catch (IOException e) {
      throw new VertxException(FileSystemImpl.getFileAccessErrorMessage("unpack", resource.getAbsolutePath()), e);
    }
    if (isDirectory) {
      String[] listing = resource.list();
      if (listing != null) {
        for (String file : listing) {
          String subResource = fileName + "/" + file;
          URL url2 = getValidClassLoaderResource(cl, subResource);
          unpackFromFileURL(url2, subResource, cl);
        }
      }
    }
    return cacheFile;
  }

  /**
   * Parse the list of entries of a URL assuming the URL is a jar URL.
   *
   * <ul>
   *   <li>when the URL is a nested file within the archive, the list is the jar entry</li>
   *   <li>when the URL is a nested file within a nested file within the archive, the list is jar entry followed by the jar entry of the nested archive</li>
   *   <li>and so on.</li>
   * </ul>
   *
   * @param url the URL
   * @return the list of entries
   */
  private List<String> listOfEntries(URL url) {
    String path = url.getPath();
    List<String> list = new ArrayList<>();
    int last = path.length();
    for (int i = path.length() - 2; i >= 0;) {
      if (path.charAt(i) == '!' && path.charAt(i + 1) == '/') {
        list.add(path.substring(2 + i, last));
        last = i;
        i -= 2;
      } else {
        i--;
      }
    }
    return list;
  }

  private File unpackFromJarURL(URL url, String fileName, ClassLoader cl) {
    try {
      List<String> listOfEntries = listOfEntries(url);
      switch (listOfEntries.size()) {
        case 1: {
          JarURLConnection conn = (JarURLConnection) url.openConnection();
          if (conn.getContentLength() == -1) {
            // the entry is a directory, so, it does not really exist.
            // which means we cannot get the JarFile from the connection.

            // In general, we should not be here, as the URL should be a directory, and we should not have a JarURLConnection.
            // However, classloader implementation may provide an URL. In this case, we should not try to get the JarFile
            // as it does not exist (or is not valid), and it will throw an exception.

            // We still retrieve it from the cache if it exists (got unzipped before or concurrently)
            return cache.getFile(fileName);
          }
          ZipFile zip = conn.getJarFile();
          try {
            extractFilesFromJarFile(zip, fileName);
          } finally {
            if (!conn.getUseCaches()) {
              zip.close();
            }
          }
          break;
        }
        case 2:
          URL nestedURL = cl.getResource(listOfEntries.get(1));
          if (nestedURL != null && nestedURL.getProtocol().equals("jar")) {
            File root = unpackFromJarURL(nestedURL, listOfEntries.get(1), cl);
            if (root.isDirectory()) {
              // jar:file:/path/to/nesting.jar!/xxx-inf/classes
              // we need to unpack xxx-inf/classes and then copy the content as is in the cache
              Path path = root.toPath();
              Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                  Path relative = path.relativize(dir);
                  cache.cacheDir(relative.toString());
                  return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                  Path relative = path.relativize(file);
                  cache.cacheFile(relative.toString(), file.toFile(), false);
                  return FileVisitResult.CONTINUE;
                }
              });
            } else {
              // jar:file:/path/to/nesting.jar!/path/to/nested.jar
              try (ZipFile zip = new ZipFile(root)) {
                extractFilesFromJarFile(zip, fileName);
              }
            }
          } else {
            throw new VertxException("Unexpected nested url : " + nestedURL);
          }
          break;
        default:
          throw new VertxException("Nesting more than two levels is not supported");
      }
    } catch (IOException e) {
      throw new VertxException(FileSystemImpl.getFileAccessErrorMessage("unpack", url.toString()), e);
    }
    return cache.getFile(fileName);
  }

  /**
   * Extract a subset of the entries to the cache.
   */
  private void extractFilesFromJarFile(ZipFile zip, String entryFilter) throws IOException {
    Enumeration<? extends ZipEntry> entries = zip.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      String name = entry.getName();
      int len = name.length();
      if (len == 0) {
        return;
      }
      if (name.charAt(len - 1) != ' ' || !Utils.isWindows()) {
        if (name.startsWith(entryFilter)) {
          if (name.charAt(len - 1) == '/') {
            cache.cacheDir(name);
          } else {
            try (InputStream is = zip.getInputStream(entry)) {
              cache.cacheFile(name, is, !enableCaching);
            }
          }
        }
      }
    }
  }

  /**
   * It is possible to determine if a resource from a bundle is a directory based on whether or not the ClassLoader
   * returns null for a path (which does not already contain a trailing '/') *and* that path with an added trailing '/'
   *
   * @param url the url
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
   * @param url the url
   * @param  fileName the file name used to cache the content
   * @return the extracted file
   */
  private File unpackFromBundleURL(URL url, String fileName, boolean isDir) {
    try {
      if ((getClassLoader() != null && isBundleUrlDirectory(url)) || isDir) {
        // Directory
        cache.cacheDir(fileName);
      } else {
        try (InputStream is = url.openStream()) {
          cache.cacheFile(fileName, is, !enableCaching);
        }
      }
    } catch (IOException e) {
      throw new VertxException(FileSystemImpl.getFileAccessErrorMessage("unpack", url.toString()), e);
    }
    return cache.getFile(fileName);
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
