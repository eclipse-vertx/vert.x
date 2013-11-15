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
package org.vertx.java.core.streams;

import org.vertx.java.core.Handler;

/**
 * Allows to set a {@link Handler} which is notified once data was read.
 * It also allows to pause reading and resume later.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface ReadSupport<T, M> extends ExceptionSupport<T> {

  /**
   * Set a data handler. As data is read, the handler will be called with the data.
   */
  T dataHandler(Handler<M> handler);

  /**
   * Pause the {@code ReadSupport}. While it's paused, no data will be sent to the {@code dataHandler}
   */
  T pause();

  /**
   * Resume reading. If the {@code ReadSupport} has been paused, reading will recommence on it.
   */
  T resume();
}
