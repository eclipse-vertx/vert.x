/*
 * Copyright (c) 2011-2017 The original author or authors
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

package io.vertx.core.shareddata;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Closeable;
import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;

/**
 * An {@link AsyncMap} keys, values, or entries {@link ReadStream stream}.
 * <p>
 * The stream will be automatically closed if it fails or ends.
 * Otherwise you must invoke {@link #close(Handler)} after usage to avoid leaking resources.
 *
 * @author Thomas Segismont
 */
@VertxGen
public interface AsyncMapStream<T> extends ReadStream<T>, Closeable {
}
