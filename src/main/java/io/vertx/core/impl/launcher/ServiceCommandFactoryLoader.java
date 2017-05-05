/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.impl.launcher;

import io.vertx.core.ServiceHelper;
import io.vertx.core.spi.launcher.CommandFactory;
import io.vertx.core.spi.launcher.CommandFactoryLookup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Looks for command factories using a service loader.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
// TODO: 16/12/13 by zmyer
public class ServiceCommandFactoryLoader implements CommandFactoryLookup {

    //指令工厂集合
    private Collection<CommandFactory> commands;

    /**
     * Creates a new instance of {@link ServiceCommandFactoryLoader} using the classloader having loaded the
     * {@link ServiceCommandFactoryLoader} class.
     */
    // TODO: 16/12/13 by zmyer
    public ServiceCommandFactoryLoader() {
        //加载指令工厂集合
        this.commands = ServiceHelper.loadFactories(CommandFactory.class, getClass().getClassLoader());
    }

    /**
     * Creates a new instance of {@link ServiceCommandFactoryLoader} using specified classloader.
     */
    // TODO: 16/12/13 by zmyer
    public ServiceCommandFactoryLoader(ClassLoader loader) {
        //加载指令工厂集合
        this.commands = ServiceHelper.loadFactories(CommandFactory.class, loader);
    }

    // TODO: 16/12/13 by zmyer
    @Override
    public Collection<CommandFactory<?>> lookup() {
        List<CommandFactory<?>> list = new ArrayList<>();
        commands.stream().forEach(list::add);
        return list;
    }

}
