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

package io.vertx.core.net;

/**
 * The SSL engine implementation to use in a Vert.x server or client.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class SSLEngineOptions {

  /**
   * @deprecated use {@link #copy()} instead
   */
  @Deprecated
  public abstract SSLEngineOptions clone();

  public abstract SSLEngineOptions copy();

}
