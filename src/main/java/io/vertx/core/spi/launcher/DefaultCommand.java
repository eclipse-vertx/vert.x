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

package io.vertx.core.spi.launcher;


import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.annotations.Description;
import io.vertx.core.cli.annotations.Hidden;
import io.vertx.core.cli.annotations.Option;

import java.io.File;
import java.io.PrintStream;
import java.util.List;

/**
 * Default implementation of {@link Command} using annotation to define itself. It is highly recommended
 * to extend this class when implementing a command.
 * <p/>
 * It defines two hidden {@link Option}s to create system properties ({@code -Dkey=value}) and a way to
 * configure the current working directory.
 */
public abstract class DefaultCommand implements Command {

  private File cwd;
  protected List<String> systemProperties;

  /**
   * The execution context of the command.
   */
  protected ExecutionContext executionContext;

  /**
   * The {@link PrintStream} that the command can use to write on the <em>console</em>.
   */
  protected PrintStream out;

  /**
   * @return the configure current working directory. If not set use the "regular" Java current working
   * directory.
   */
  public File getCwd() {
    return cwd != null ? cwd : new File(".");
  }

  /**
   * Sets the current working directory. This method is called when the user configure the "cwd" option as
   * follows: {@code --cwd=the-directory}.
   *
   * @param cwd the directory
   */
  @SuppressWarnings("unused")
  @Option(longName = "cwd", argName = "dir")
  @Description("Specifies the current working directory for this command, default set to the Java current directory")
  @Hidden
  public void setCwd(File cwd) {
    this.cwd = cwd;
  }

  /**
   * Gets system properties passed in the user command line. The user can configure system properties using
   * {@code -Dkey=value}.
   *
   * @param props the properties
   */
  @SuppressWarnings("unused")
  @Option(longName = "systemProperty", shortName = "D", argName = "key>=<value")
  @Description("Set a system property")
  @Hidden
  public void setSystemProps(List<String> props) {
    this.systemProperties = props;
  }

  @Override
  public void setUp(ExecutionContext ec) throws CLIException {
    this.executionContext = ec;
    this.out = executionContext.getPrintStream();
    applySystemProperties();
  }

  /**
   * @return the print stream on which message should be written.
   */
  public PrintStream out() {
    return executionContext.getPrintStream();
  }

  @Override
  public void tearDown() throws CLIException {
    // Default implementation - does nothing.
  }

  /**
   * Sets the system properties specified by the user command line.
   */
  protected void applySystemProperties() {
    if (systemProperties != null) {
      for (String prop : systemProperties) {
        int p = prop.indexOf('=');
        if (p > 0) {
          String key = prop.substring(0, p);
          String val = prop.substring(p + 1);
          System.setProperty(key, val);
        }
      }
    }
  }
}
