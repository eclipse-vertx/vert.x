/*
 * Copyright (c) 2011-2013 The original author or authors
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
package io.vertx.core.spi;

import io.vertx.core.*;

/**
 * Factory for creating Vertx instances.<p>
 * Use this to create Vertx instances when embedding Vert.x core directly.<p>
 *
 * @author pidster
 */
// TODO: 16/12/14  by zmyer

public interface VertxFactory {
    //创建vertx节点
    Vertx vertx();

    //创建vertx节点
    Vertx vertx(VertxOptions options);

    //创建集群模式的vertx节点
    void clusteredVertx(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler);

    Context context();

}
