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

package io.vertx.core.eventbus;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.codegen.annotations.VertxGen;

/**
 * An event bus registration object which can be used to later unregister an event bus handler.
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
@VertxGen
public interface Registration {

  /**
   * @return The address the handler was registered with.
   */
  String address();

  /**
   * Optional method which can be called to indicate when the registration has been propagated across the cluster.
   *
   * @param completionHandler the completion handler
   */
  void completionHandler(Handler<AsyncResult<Void>> completionHandler);

  /**
   * Unregisters the handler which created this registration
   */
  void unregister();

  /**
   * Unregisters the handler which created this registration
   *
   * @param completionHandler the handler called when the unregister is done. For example in a cluster when all nodes of the
   * event bus have been unregistered.
   */
  void unregister(Handler<AsyncResult<Void>> completionHandler);
}
