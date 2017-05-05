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

import java.io.Closeable;
import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
 */
// TODO: 16/12/29 by zmyer
public class FileResolver {
    //禁止文件缓存标记
    public static final String DISABLE_FILE_CACHING_PROP_NAME = "vertx.disableFileCaching";
    //
    public static final String DISABLE_CP_RESOLVING_PROP_NAME = "vertx.disableFileCPResolving";
    //缓存目录
    public static final String CACHE_DIR_BASE_PROP_NAME = "vertx.cacheDirBase";

    //默认的缓存目录后缀名
    private static final String DEFAULT_CACHE_DIR_BASE = ".vertx";
    //
    private static final String FILE_SEP = System.getProperty("file.separator");
    private static final boolean NON_UNIX_FILE_SEP = !FILE_SEP.equals("/");
    //是否正在缓存
    private static final boolean ENABLE_CACHING = !Boolean.getBoolean(DISABLE_FILE_CACHING_PROP_NAME);
    //
    private static final boolean ENABLE_CP_RESOLVING = !Boolean.getBoolean(DISABLE_CP_RESOLVING_PROP_NAME);
    //缓存目录
    private static final String CACHE_DIR_BASE = System.getProperty(CACHE_DIR_BASE_PROP_NAME, DEFAULT_CACHE_DIR_BASE);

    //vertx节点对象
    private final Vertx vertx;
    //文件对象
    private final File cwd;
    //缓存目录对象
    private File cacheDir;
    //关闭线程对象
    private Thread shutdownHook;

    // TODO: 16/12/29 by zmyer
    public FileResolver(Vertx vertx) {
        this.vertx = vertx;
        String cwdOverride = System.getProperty("vertx.cwd");
        if (cwdOverride != null) {
            cwd = new File(cwdOverride).getAbsoluteFile();
        } else {
            cwd = null;
        }
        if (ENABLE_CP_RESOLVING) {
            setupCacheDir();
        }
    }

    // TODO: 16/12/29 by zmyer
    public void close(Handler<AsyncResult<Void>> handler) {
        deleteCacheDir(handler);
        if (shutdownHook != null) {
            // May throw IllegalStateException if called from other shutdown hook so ignore that
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignore) {
            }
        }
    }

    // TODO: 16/12/29 by zmyer
    public File resolveFile(String fileName) {
        // First look for file with that name on disk
        //读取文件对象
        File file = new File(fileName);
        if (cwd != null && !file.isAbsolute()) {
            //如果读取到的文件对象路径不是绝对路径。则需要根据文件名创建文件对象
            file = new File(cwd, fileName);
        }
        if (!ENABLE_CP_RESOLVING) {
            return file;
        }
        if (!file.exists()) {
            // Look for it in local file cache
            //读取缓存目录
            File cacheFile = new File(cacheDir, fileName);
            if (ENABLE_CACHING && cacheFile.exists()) {
                //如果开启了缓存机制,并且缓存中有当前的文件对象,则直接返回
                return cacheFile;
            }
            // Look for file on classpath
            //获取类加载器对象
            ClassLoader cl = getClassLoader();
            if (NON_UNIX_FILE_SEP) {
                fileName = fileName.replace(FILE_SEP, "/");
            }

            //根据给定的文件名称,读取对应的资源文件路径
            URL url = cl.getResource(fileName);
            if (url != null) {
                //从路径信息中读取协议属性
                String prot = url.getProtocol();
                switch (prot) {
                    case "file":
                        //如果是普通的文件
                        return unpackFromFileURL(url, fileName, cl);
                    case "jar":
                        //如果是jar文件
                        return unpackFromJarURL(url, fileName, cl);
                    case "bundle": // Apache Felix, Knopflerfish
                    case "bundleentry": // Equinox
                    case "bundleresource": // Equinox
                        //如果是包文件对象
                        return unpackFromBundleURL(url);
                    default:
                        throw new IllegalStateException("Invalid url protocol: " + prot);
                }
            }
        }
        return file;
    }

    // TODO: 16/12/29 by zmyer
    private synchronized File unpackFromFileURL(URL url, String fileName, ClassLoader cl) {
        File resource;
        try {
            //从给定的路径中读取文件对象
            resource = new File(URLDecoder.decode(url.getPath(), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new VertxException(e);
        }

        //判断读取到的资源文件是否是目录
        boolean isDirectory = resource.isDirectory();
        //创建缓存文件对象
        File cacheFile = new File(cacheDir, fileName);
        if (!isDirectory) {
            //直接创建缓存文件对象的父目录
            cacheFile.getParentFile().mkdirs();
            try {
                if (ENABLE_CACHING) {
                    //如果可以进行缓存,则需要及时将其拷贝到缓存中
                    Files.copy(resource.toPath(), cacheFile.toPath());
                } else {
                    Files.copy(resource.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (FileAlreadyExistsException ignore) {
            } catch (IOException e) {
                throw new VertxException(e);
            }
        } else {
            //开始为缓存文件创建目录
            cacheFile.mkdirs();
            //读取资源文件中的对象集合
            String[] listing = resource.list();
            for (String file : listing) {
                //获取下一级资源文件对象
                String subResource = fileName + "/" + file;
                //开始根据资源文件名称,读取对应的资源信息
                URL url2 = cl.getResource(subResource);
                //开始进行单文件解压操作
                unpackFromFileURL(url2, subResource, cl);
            }
        }
        return cacheFile;
    }

    // TODO: 16/12/29 by zmyer
    private synchronized File unpackFromJarURL(URL url, String fileName, ClassLoader cl) {
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
                File file = new File(URLDecoder.decode(path.substring(5, idx1 + 4), "UTF-8"));
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
                    File file = new File(cacheDir, name);
                    if (name.endsWith("/")) {
                        // Directory
                        file.mkdirs();
                    } else {
                        file.getParentFile().mkdirs();
                        try (InputStream is = zip.getInputStream(entry)) {
                            if (ENABLE_CACHING) {
                                Files.copy(is, file.toPath());
                            } else {
                                Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (FileAlreadyExistsException ignore) {
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new VertxException(e);
        } finally {
            closeQuietly(zip);
        }

        return new File(cacheDir, fileName);
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
     * bundle:// urls are used by OSGi implementations to refer to a file contained in a bundle, or in a fragment. There
     * is not much we can do to get the file from it, except reading it from the url. This method copies the files by
     * reading it from the url.
     *
     * @param url the url
     * @return the extracted file
     */
    // TODO: 16/12/29 by zmyer
    private synchronized File unpackFromBundleURL(URL url) {
        try {
            File file = new File(cacheDir, url.getHost() + File.separator + url.getFile());
            file.getParentFile().mkdirs();
            if (url.toExternalForm().endsWith("/")) {
                // Directory
                file.mkdirs();
            } else {
                file.getParentFile().mkdirs();
                try (InputStream is = url.openStream()) {
                    if (ENABLE_CACHING) {
                        Files.copy(is, file.toPath());
                    } else {
                        Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (FileAlreadyExistsException ignore) {
                }
            }
        } catch (IOException e) {
            throw new VertxException(e);
        }
        return new File(cacheDir, url.getHost() + File.separator + url.getFile());
    }


    private ClassLoader getClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = getClass().getClassLoader();
        }
        return cl;
    }

    // TODO: 16/12/29 by zmyer
    private void setupCacheDir() {
        String cacheDirName = CACHE_DIR_BASE + "/file-cache-" + UUID.randomUUID().toString();
        cacheDir = new File(cacheDirName);
        if (!cacheDir.mkdirs()) {
            throw new IllegalStateException("Failed to create cache dir");
        }
        // Add shutdown hook to delete on exit
        shutdownHook = new Thread(() -> {
            CountDownLatch latch = new CountDownLatch(1);
            deleteCacheDir(ar -> latch.countDown());
            try {
                latch.await(10, TimeUnit.SECONDS);
            } catch (Exception ignore) {
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    // TODO: 16/12/29 by zmyer
    private void deleteCacheDir(Handler<AsyncResult<Void>> handler) {
        if (cacheDir != null && cacheDir.exists()) {
            vertx.fileSystem().deleteRecursive(cacheDir.getAbsolutePath(), true, handler);
        } else {
            handler.handle(Future.succeededFuture());
        }
    }
}

