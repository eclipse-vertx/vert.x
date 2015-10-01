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

import io.vertx.core.cli.converters.Converter;

/**
 * An implementation of {@link Argument} for java specifying the type of object received by the argument. This
 * allows converting the given <em>raw</em> value into the specified type.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class TypedArgument<T> extends Argument {

  /**
   * The type of the argument.
   */
  protected Class<T> type;

  /**
   * The converter to use to create the value.
   */
  protected Converter<T> converter;

  /**
   * Creates a new instance of {@link TypedArgument} by copying the state of another {@link TypedArgument}.
   *
   * @param arg the copied argument
   */
  public TypedArgument(TypedArgument<T> arg) {
    super(arg);
    this.type = arg.getType();
    this.converter = arg.getConverter();
  }

  /**
   * Creates an empty instance of {@link TypedArgument}.
   */
  public TypedArgument() {
    super();
  }

  /**
   * @return the argument type, cannot be {@code null} for valid argument.
   */
  public Class<T> getType() {
    return type;
  }

  /**
   * Sets the argument type.
   *
   * @param type the type
   * @return the current {@link TypedArgument} instance
   */
  public TypedArgument<T> setType(Class<T> type) {
    this.type = type;
    return this;
  }

  /**
   * @return the converter used to create the value, {@code null} if not set
   */
  public Converter<T> getConverter() {
    return converter;
  }

  /**
   * Sets the converter used to create the value.
   *
   * @param converter the converter
   * @return the current {@link TypedArgument} instance
   */
  public TypedArgument<T> setConverter(Converter<T> converter) {
    this.converter = converter;
    return this;
  }

  /**
   * Checks whether or not the argument configuration is valid. In addition of the check made by the parent class it
   * ensures that the type is set.
   * If the configuration is not valid, this method throws a {@link IllegalArgumentException}.
   */
  @Override
  public void ensureValidity() {
    super.ensureValidity();
    if (type == null) {
      throw new IllegalArgumentException("Type must not be null");
    }
  }

  @Override
  public TypedArgument<T> setArgName(String argName) {
    super.setArgName(argName);
    return this;
  }

  @Override
  public TypedArgument<T> setDefaultValue(String defaultValue) {
    super.setDefaultValue(defaultValue);
    return this;
  }

  @Override
  public TypedArgument<T> setDescription(String description) {
    super.setDescription(description);
    return this;
  }

  @Override
  public TypedArgument<T> setHidden(boolean hidden) {
    super.setHidden(hidden);
    return this;
  }

  @Override
  public TypedArgument<T> setIndex(int index) {
    super.setIndex(index);
    return this;
  }

  @Override
  public TypedArgument<T> setRequired(boolean required) {
    super.setRequired(required);
    return this;
  }
}
