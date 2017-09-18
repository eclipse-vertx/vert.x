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

package io.vertx.core.http;

import io.vertx.codegen.annotations.VertxGen;

/**
 * Configures the engine to require/request client authentication.
 * <p/>
 * Created by manishk on 10/2/2015.
 */
@VertxGen
public enum ClientAuth {

  /**
   * No client authentication is requested or required.
   */
  NONE,

  /**
   * Accept authentication if presented by client. If this option is set and the client chooses
   * not to provide authentication information about itself, the negotiations will continue.
   */
  REQUEST,

  /**
   * Require client to present authentication, if not presented then negotiations will be declined.
   */
  REQUIRED
}
