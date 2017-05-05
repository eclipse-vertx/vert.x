/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.impl;

import io.vertx.core.Verticle;
import io.vertx.core.impl.verticle.CompilingClassLoader;
import io.vertx.core.spi.VerticleFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 16/12/29 by zmyer
public class JavaVerticleFactory implements VerticleFactory {

    @Override
    public String prefix() {
        return "java";
    }

    // TODO: 16/12/29 by zmyer
    @Override
    public Verticle createVerticle(String verticleName, ClassLoader classLoader) throws Exception {
        verticleName = VerticleFactory.removePrefix(verticleName);
        Class clazz;
        if (verticleName.endsWith(".java")) {
            //如果是java源文件,则首先需要创建编译类加载器对象
            CompilingClassLoader compilingLoader = new CompilingClassLoader(classLoader, verticleName);
            //开始获取类名称
            String className = compilingLoader.resolveMainClassName();
            //开始加载类对象
            clazz = compilingLoader.loadClass(className);
        } else {
            //直接加载类对象
            clazz = classLoader.loadClass(verticleName);
        }
        return (Verticle) clazz.newInstance();
    }

}
