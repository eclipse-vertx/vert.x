/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
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
