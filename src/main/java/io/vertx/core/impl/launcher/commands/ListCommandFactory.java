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

package io.vertx.core.impl.launcher.commands;

import io.vertx.core.spi.launcher.DefaultCommandFactory;

/**
 * Defines the `list` command.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class ListCommandFactory extends DefaultCommandFactory<ListCommand> {
  /**
   * Creates a new {@link ListCommandFactory}.
   */
  public ListCommandFactory() {
    super(ListCommand.class);
  }
}
