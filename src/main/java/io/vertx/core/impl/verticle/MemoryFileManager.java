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
// TODO: 16/12/30 by zmyer
public class MemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    //编译文件集合
    private final Map<String, ByteArrayOutputStream> compiledClasses = new HashMap<>();
    //包工具对象
    private final PackageHelper helper;

    // TODO: 16/12/30 by zmyer
    public MemoryFileManager(ClassLoader classLoader, JavaFileManager fileManager) {
        super(fileManager);
        helper = new PackageHelper(classLoader);
    }

    // TODO: 16/12/30 by zmyer
    @Override
    public JavaFileObject getJavaFileForOutput(Location location, final String className,
                                               JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        try {
            //返回简单的java文件内存对象
            return new SimpleJavaFileObject(new URI(""), kind) {
                public OutputStream openOutputStream() throws IOException {
                    //创建字节数组输出流对象
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    //将字节数组输出流对象注册到集合中
                    compiledClasses.put(className, outputStream);
                    return outputStream;
                }
            };
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    // TODO: 16/12/30 by zmyer
    public byte[] getCompiledClass(String name) {
        //查找具体的输出字节流对象
        ByteArrayOutputStream bytes = compiledClasses.get(name);
        if (bytes == null) {
            return null;
        }
        //返回字节流对象对应的字节码数组对象
        return bytes.toByteArray();
    }

    // TODO: 16/12/30 by zmyer
    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        if (file instanceof CustomJavaFileObject) {
            //返回用户自定义的二进制名称
            return ((CustomJavaFileObject) file).binaryName();
        } else {
            return super.inferBinaryName(location, file);
        }
    }

    // TODO: 16/12/30 by zmyer
    @Override
    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds,
                                         boolean recurse) throws IOException {
        if (location == StandardLocation.CLASS_PATH && kinds.contains(JavaFileObject.Kind.CLASS)) {
            return helper.find(packageName);
        }
        return super.list(location, packageName, kinds, recurse);
    }
}
