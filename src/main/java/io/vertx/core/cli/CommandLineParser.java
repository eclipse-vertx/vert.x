/*
 *  Copyright (c) 2011-2013 The original author or authors
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

package io.vertx.core.cli;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.cli.impl.DefaultParser;

import java.util.List;

/**
 * Interface implemented by classes responsible for parsing the user command line argument and creating the
 * {@link CommandLine}.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
@VertxGen
public interface CommandLineParser {

  /**
   * Creates a new instance of {@link CommandLineParser} using the default implementation.
   *
   * @return the new instance
   */
  static CommandLineParser create() {
    return new DefaultParser();
  }

  /**
   * Parses the user command line and creates a new {@link CommandLine} object.
   *
   * @param cli  the command line model
   * @param args the user command line arguments
   * @return the created {@link CommandLine} object
   * @throws CLIException if something went wrong while parsing the user command line
   */
  CommandLine parse(CLI cli, List<String> args) throws CLIException;

}
