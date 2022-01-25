/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net;

import java.net.BindException;
import java.net.SocketAddress;

/**
 * An exception that is meant to stand in for {@link BindException} and provide information
 * about the target which caused the bind exception.
 */
public class VertxBindException extends BindException {

  private final SocketAddress bindTarget;

  public VertxBindException(BindException cause, SocketAddress bindTarget) {
    this.bindTarget = bindTarget;
    initCause(cause);
  }

  public SocketAddress getBindTarget() {
    return bindTarget;
  }
}
