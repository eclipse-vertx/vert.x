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

package io.vertx.core;

import java.util.*;

/**
 * A helper class for loading factories from the classpath and from the vert.x OSGi bundle.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 16/12/13 by zmyer
public class ServiceHelper {

    // TODO: 16/12/13 by zmyer
    public static <T> T loadFactory(Class<T> clazz) {
        //首先根据类对象,加载工厂对象
        T factory = loadFactoryOrNull(clazz);
        if (factory == null) {
            throw new IllegalStateException("Cannot find META-INF/services/" + clazz.getName() + " on classpath");
        }
        //返回工厂对象
        return factory;
    }

    // TODO: 16/12/13 by zmyer
    public static <T> T loadFactoryOrNull(Class<T> clazz) {
        //根据类名,加载具体的工厂对象
        Collection<T> collection = loadFactories(clazz);
        if (!collection.isEmpty()) {
            //如果加载到的工厂对象集合不为空,则返回第一个工厂对象
            return collection.iterator().next();
        } else {
            return null;
        }
    }

    // TODO: 16/12/13 by zmyer
    public static <T> Collection<T> loadFactories(Class<T> clazz) {
        return loadFactories(clazz, null);
    }

    // TODO: 16/12/13 by zmyer
    public static <T> Collection<T> loadFactories(Class<T> clazz, ClassLoader classLoader) {
        List<T> list = new ArrayList<>();
        ServiceLoader<T> factories;
        if (classLoader != null) {
            //根据提供的类对象以及类加载器,加载对应的工厂对象
            factories = ServiceLoader.load(clazz, classLoader);
        } else {
            // this is equivalent to:
            // ServiceLoader.load(clazz, TCCL);
            //否则需要使用默认的类加载器对象
            factories = ServiceLoader.load(clazz);
        }
        if (factories.iterator().hasNext()) {
            //将工厂对象集合插入到给定的列表中
            factories.iterator().forEachRemaining(list::add);
            //并返回
            return list;
        } else {
            // By default ServiceLoader.load uses the TCCL, this may not be enough in environment dealing with
            // classloaders differently such as OSGi. So we should try to use the  classloader having loaded this
            // class. In OSGi it would be the bundle exposing vert.x and so have access to all its classes.
            //如果之前加载失败,则需要重新加载
            factories = ServiceLoader.load(clazz, ServiceHelper.class.getClassLoader());
            if (factories.iterator().hasNext()) {
                //集合转换
                factories.iterator().forEachRemaining(list::add);
                //返回结果
                return list;
            } else {
                //返回空集合
                return Collections.emptyList();
            }
        }
    }
}
