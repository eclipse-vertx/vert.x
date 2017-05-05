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

import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;

/**
 * @author Janne Hietam&auml;ki
 */

// TODO: 17/1/1 by zmyer
public class PackageHelper {
    //类文件后缀
    private final static String CLASS_FILE = ".class";
    //类文件加载器对象
    private final ClassLoader classLoader;

    public PackageHelper(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    // TODO: 17/1/1 by zmyer
    public List<JavaFileObject> find(String packageName) throws IOException {
        //首先获取包名称
        String javaPackageName = packageName.replaceAll("\\.", "/");
        //结果集
        List<JavaFileObject> result = new ArrayList<>();
        //使用类加载器加载指定目录下的资源文件
        Enumeration<URL> urlEnumeration = classLoader.getResources(javaPackageName);
        while (urlEnumeration.hasMoreElements()) {
            URL resource = urlEnumeration.nextElement();
            //Need to urldecode it too, since bug in JDK URL class which does not url decode it, so if it contains spaces you are screwed
            File directory;
            try {
                //开始读取资源文件
                directory = new File(URLDecoder.decode(resource.getFile(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException("Failed to decode " + e.getMessage());
            }
            if (directory.isDirectory()) {
                //如果资源文件是目录,则需要读取整个目录
                result.addAll(browseDir(packageName, directory));
            } else {
                //否则需要将资源文件加入到结果集中
                result.addAll(browseJar(resource));
            }
        }
        return result;
    }

    // TODO: 17/1/1 by zmyer
    private static List<JavaFileObject> browseDir(String packageName, File directory) {
        //结果集对象
        List<JavaFileObject> result = new ArrayList<>();
        //依次遍历资源目录
        for (File childFile : directory.listFiles()) {
            //判断每个文件对象是否是类文件
            if (childFile.isFile() && childFile.getName().endsWith(CLASS_FILE)) {
                //获取二进制文件对象名称
                String binaryName = packageName + "." + childFile.getName().replaceAll(CLASS_FILE + "$", "");
                //保存结果对象
                result.add(new CustomJavaFileObject(childFile.toURI(), JavaFileObject.Kind.CLASS, binaryName));
            }
        }
        //返回结果集
        return result;
    }

    // TODO: 17/1/1 by zmyer
    private static List<JavaFileObject> browseJar(URL packageFolderURL) {
        //结果集
        List<JavaFileObject> result = new ArrayList<>();
        try {
            //开始读取jar文件路径信息
            String jarUri = packageFolderURL.toExternalForm().split("!")[0];
            //根据jar文件路径信息,创建jar链接对象
            JarURLConnection jarConn = (JarURLConnection) packageFolderURL.openConnection();
            //读取根对象名称
            String rootEntryName = jarConn.getEntryName();
            int rootEnd = rootEntryName.length() + 1;
            //从jar文件中读取包含的实体集
            Enumeration<JarEntry> entryEnum = jarConn.getJarFile().entries();
            while (entryEnum.hasMoreElements()) {
                //读取每个jar实体对象
                JarEntry jarEntry = entryEnum.nextElement();
                //读取实体名称
                String name = jarEntry.getName();
                //判断每个实体对象是否是类对象
                if (name.startsWith(rootEntryName) && name.indexOf('/', rootEnd) == -1 && name.endsWith(CLASS_FILE)) {
                    //替换类对象名
                    String binaryName = name.replaceAll("/", ".").replaceAll(CLASS_FILE + "$", "");
                    //将结果保存到结果集中
                    result.add(new CustomJavaFileObject(URI.create(jarUri + "!/" + name), JavaFileObject.Kind.CLASS, binaryName));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(packageFolderURL + " is not a JAR file", e);
        }
        //返回结果集
        return result;
    }
}