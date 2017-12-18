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
