/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.VertxGen;

/**
 *
 * Encapsulates a message being delivered by Vert.x as well as providing control over the message delivery.
 * <p/>
 * Used with event bus interceptors.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface DeliveryContext<T> {

  /**
   * @return  The message being delivered
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
   * @return the value delivered by the message (before or after being processed by the codec)
   */
  Object body();
}
