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
 * Factory to create the {@code version} command.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class VersionCommandFactory extends DefaultCommandFactory<VersionCommand> {

  /**
   * Creates a new instance of {@link VersionCommandFactory}.
   */
  public VersionCommandFactory() {
    super(VersionCommand.class, VersionCommand::new);
  }
}
