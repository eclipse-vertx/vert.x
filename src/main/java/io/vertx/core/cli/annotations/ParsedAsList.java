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
package io.vertx.core.cli.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a setter to be called with the value of a command line option. The setter must also have been annotated
 * with {@link Option}.
 * <p/>
 * When annotated with {@link ParsedAsList}, the option value is parsed as a list. The value is split and then each
 * segment is trimmed.
 *
 * @author Clement Escoffier <clement@apache.org>
 * @see Option
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ParsedAsList {

  /**
   * The separator used to split the value. {@code ,} is used by default.
   */
  String separator() default ",";
}
