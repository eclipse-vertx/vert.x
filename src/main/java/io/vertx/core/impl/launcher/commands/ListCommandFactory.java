/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
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
