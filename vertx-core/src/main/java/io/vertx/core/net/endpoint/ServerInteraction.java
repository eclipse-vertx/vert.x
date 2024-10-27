/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.endpoint;

import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.annotations.VertxGen;

/**
 * Request interaction with an endpoint, mostly callbacks to gather statistics.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Unstable
@VertxGen
public interface ServerInteraction {

  /**
   * Report a failure.
   * @param failure the failure to report
   */
  void reportFailure(Throwable failure);

  /**
   * The request has begun.
   */
  void reportRequestBegin();

  /**
   * The request has ended.
   */
  void reportRequestEnd();

  /**
   * The response has begun.
   */
  void reportResponseBegin();

  /**
   * The request has ended.
   */
  void reportResponseEnd();

}
