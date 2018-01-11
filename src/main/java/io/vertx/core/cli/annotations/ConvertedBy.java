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

package io.vertx.core.cli.annotations;

import io.vertx.core.cli.converters.Converter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates {@link Option @Option} setters to indicate how the value is converted to the argument type.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ConvertedBy {

  /**
   * The converter class used to transform the value as String to the target type. This converter is also used for
   * the {@link DefaultValue}.
   */
  Class<? extends Converter<?>> value();

}
