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

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import javax.tools.*;
import javax.tools.JavaFileObject.Kind;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Classloader for dynamic .java source file compilation and loading.
 *
 * @author Janne Hietam&auml;ki
 */
// TODO: 16/12/29 by zmyer
public class CompilingClassLoader extends ClassLoader {

    private static final Logger log = LoggerFactory.getLogger(CompilingClassLoader.class);
    //vertx编译器选项
    private static final String JAVA_COMPILER_OPTIONS_PROP_NAME = "vertx.javaCompilerOptions";
    //编译器选项配置
    private final static List<String> COMPILER_OPTIONS;

    static {
        //读取系统编译器选项
        String props = System.getProperty(JAVA_COMPILER_OPTIONS_PROP_NAME);
        if (props != null) {
            String[] array = props.split(",");
            List<String> compilerProps = new ArrayList<>(array.length);

            for (String prop : array) {
                compilerProps.add(prop.trim());
            }
            //读取编译器选项配置
            COMPILER_OPTIONS = Collections.unmodifiableList(compilerProps);
        } else {
            COMPILER_OPTIONS = null;
        }
    }

    //执行上下文对象
    private final JavaSourceContext javaSourceContext;
    //内存文件管理器
    private final MemoryFileManager fileManager;

    // TODO: 16/12/30 by zmyer
    public CompilingClassLoader(ClassLoader loader, String sourceName) {
        super(loader);
        URL resource = getResource(sourceName);
        if (resource == null) {
            throw new RuntimeException("Resource not found: " + sourceName);
        }
        //Need to urldecode it too, since bug in JDK URL class which does not url decode it, so if it contains spaces you are screwed
        File sourceFile;
        try {
            //读取源文件对象
            sourceFile = new File(URLDecoder.decode(resource.getFile(), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Failed to decode " + e.getMessage());
        }
        if (!sourceFile.canRead()) {
            throw new RuntimeException("File not found: " + sourceFile.getAbsolutePath() + " current dir is: " + new File(".").getAbsolutePath());
        }

        //根据源文件对象创建执行上下文对象
        this.javaSourceContext = new JavaSourceContext(sourceFile);

        try {
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            //java编译器对象
            JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
            if (javaCompiler == null) {
                throw new RuntimeException("Unable to detect java compiler, make sure you're using a JDK not a JRE!");
            }
            //根据java编译器对象获取java文件管理器
            StandardJavaFileManager standardFileManager = javaCompiler.getStandardFileManager(null, null, null);
            //设置java文件管理器源文件位置信息
            standardFileManager.setLocation(StandardLocation.SOURCE_PATH, Collections.singleton(javaSourceContext.getSourceRoot()));
            //根据源文件管理器,创建内存文件管理器
            fileManager = new MemoryFileManager(loader, standardFileManager);

            // TODO - this needs to be fixed so it can compile classes from the classpath otherwise can't include
            // other .java resources from other modules
            //从标准的文件管理器中读取文件对象
            JavaFileObject javaFile = standardFileManager.getJavaFileForInput(StandardLocation.SOURCE_PATH, resolveMainClassName(), Kind.SOURCE);
            //创建文件编译对象
            JavaCompiler.CompilationTask task = javaCompiler.getTask(null, fileManager, diagnostics, COMPILER_OPTIONS, null, Collections.singleton(javaFile));
            //开始进行编译
            boolean valid = task.call();
            if (valid) {
                for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
                    String code = d.getCode();
                    if (code == null || (!code.startsWith("compiler.warn.annotation.method.not.found") &&
                            !"compiler.warn.proc.processor.incompatible.source.version".equals(code))) {
                        log.info(d);
                    }
                }
            } else {
                for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
                    log.warn(d);
                }
                throw new RuntimeException("Compilation failed!");
            }
        } catch (Exception e) {
            throw new RuntimeException("Compilation failed", e);
        }
    }

    public String resolveMainClassName() {
        return javaSourceContext.getClassName();
    }

    // TODO: 16/12/30 by zmyer
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        //根据类对象名称获取字节码数组
        byte[] bytecode = getClassBytes(name);
        if (bytecode == null) {
            throw new ClassNotFoundException(name);
        }
        //根据字节码数组创建对应的类对象
        return defineClass(name, bytecode, 0, bytecode.length);
    }

    //获取类对象
    public byte[] getClassBytes(String name) {
        return fileManager.getCompiledClass(name);
    }
}
