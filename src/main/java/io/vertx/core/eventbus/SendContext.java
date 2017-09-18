/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.VertxGen;

/**
 *
 * Encapsulates a message being sent from Vert.x. Used with event bus interceptors
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface SendContext<T> {

  /**
   * @return  The message being sent
   */
  Message<T> message();

  /**
   * Call the next interceptor
   */
  void next();

  /**
   * @return true if the message is being sent (point to point) or False if the message is being published
   */
  boolean send();

  /**
   * @return the value sent or published (before being processed by the codec)
   */
  Object sentBody();
}
