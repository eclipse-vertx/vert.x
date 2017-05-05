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

package io.vertx.core.net;

import io.vertx.codegen.annotations.VertxGen;

/**
 * The type of a TCP proxy server.
 *
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 */
// TODO: 17/1/1 by zmyer
@VertxGen
public enum ProxyType {
    /**
     * HTTP CONNECT ssl proxy
     */
    HTTP,
    /**
     * SOCKS4/4a tcp proxy
     */
    SOCKS4,
    /**
     * SOCSK5 tcp proxy
     */
    SOCKS5
}
