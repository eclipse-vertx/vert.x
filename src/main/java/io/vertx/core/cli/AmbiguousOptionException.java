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
package io.vertx.core.cli;


import java.util.List;
import java.util.stream.Collectors;

/**
 * Exception thrown when the command line is ambiguous meaning it cannot determine exactly which option has to be set.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class AmbiguousOptionException extends CLIException {

  private final List<Option> options;
  private final String token;


  /**
   * Creates a new instance of {@link AmbiguousOptionException}.
   *
   * @param token        the ambiguous token
   * @param matchingOpts the list of potential matches
   */
  public AmbiguousOptionException(String token, List<Option> matchingOpts) {
    super("Ambiguous argument in command line: '" + token + "' matches "
        + matchingOpts.stream().map(Option::getName).collect(Collectors.toList()));
    this.token = token;
    this.options = matchingOpts;
  }

  /**
   * @return the list of potential matches.
   */
  public List<Option> getOptions() {
    return options;
  }

  /**
   * @return the token triggering the ambiguity.
   */
  public String getToken() {
    return token;
  }
}
