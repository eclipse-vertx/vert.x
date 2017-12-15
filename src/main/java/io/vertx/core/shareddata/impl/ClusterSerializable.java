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

package io.vertx.core.shareddata.impl;

import io.vertx.core.buffer.Buffer;

/**
 * Objects implementing this interface will be write to and read from a {@link Buffer} when respectively
 * stored and read from an {@link io.vertx.core.shareddata.AsyncMap}.
 * <p>
 * Implementations must have a public no-argument constructor.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface ClusterSerializable {

  void writeToBuffer(Buffer buffer);

  int readFromBuffer(int pos, Buffer buffer);
}
