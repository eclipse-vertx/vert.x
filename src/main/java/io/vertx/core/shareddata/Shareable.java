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

package io.vertx.core.shareddata;

/**
 * A marker interface which allows you to put arbitrary objects into a {@link io.vertx.core.shareddata.LocalMap}.
 * <p>
 * Normally local maps only allow immutable objects or other copiable objects such as {@link io.vertx.core.buffer.Buffer}
 * instances in order to avoid shared access to mutable state.
 * <p>
 * However if you have an object that you know is thread-safe you can mark it with this interface and then you
 * will be able to add it to {@link io.vertx.core.shareddata.LocalMap} instances.
 * <p>
 * Use this interface with caution.
 * <p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Shareable {
}
