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

import java.util.Objects;

/**
 * An implementation of {@link Option} for java specifying the type of
 * object received by the option. This allows converting the given <em>raw</em> value into the specified type.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class TypedOption<T> extends Option {

  /**
   * The type of the option.
   */
  protected Class<T> type;

  /**
   * whether or not the raw value should be parsed as a list. The list if computed by splitting the value.
   */
  protected boolean parsedAsList;

  /**
   * the split character used if the raw value needs to be parsed as a list. {@code ','} is used by default.
   */
  protected String listSeparator = ",";

  /**
   * the converter to create the value.
   */
  protected Converter<T> converter;

  /**
   * Creates an empty instance of {@link TypedOption}.
   */
  public TypedOption() {
    super();
  }

  /**
   * Creates an instance of {@link TypedOption} by copying the state of another {@link TypedOption}
   *
   * @param option the copied option
   */
  public TypedOption(TypedOption<T> option) {
    super(option);
    this.type = option.getType();
    this.converter = option.getConverter();
    this.parsedAsList = option.isParsedAsList();
    this.listSeparator = option.getListSeparator();
  }

  @Override
  public TypedOption<T> setMultiValued(boolean acceptMultipleValues) {
    super.setMultiValued(acceptMultipleValues);
    return this;
  }

  @Override
  public TypedOption<T> setSingleValued(boolean acceptSingleValue) {
    super.setSingleValued(acceptSingleValue);
    return this;
  }

  @Override
  public TypedOption<T> setArgName(String argName) {
    super.setArgName(argName);
    return this;
  }

  @Override
  public TypedOption<T> setDefaultValue(String defaultValue) {
    super.setDefaultValue(defaultValue);
    return this;
  }

  @Override
  public TypedOption<T> setDescription(String description) {
    super.setDescription(description);
    return this;
  }

  @Override
  public TypedOption<T> setFlag(boolean flag) {
    super.setFlag(flag);
    return this;
  }

  @Override
  public TypedOption<T> setHidden(boolean hidden) {
    super.setHidden(hidden);
    return this;
  }

  @Override
  public TypedOption<T> setLongName(String longName) {
    super.setLongName(longName);
    return this;
  }

  @Override
  public TypedOption<T> setRequired(boolean required) {
    super.setRequired(required);
    return this;
  }

  @Override
  public TypedOption<T> setShortName(String shortName) {
    super.setShortName(shortName);
    return this;
  }

  public Class<T> getType() {
    return type;
  }

  public TypedOption<T> setType(Class<T> type) {
    this.type = type;
    return this;
  }

  public boolean isParsedAsList() {
    return parsedAsList;
  }

  public TypedOption<T> setParsedAsList(boolean isList) {
    this.parsedAsList = isList;
    return this;
  }

  public String getListSeparator() {
    return listSeparator;
  }

  public TypedOption<T> setListSeparator(String listSeparator) {
    Objects.requireNonNull(listSeparator);
    this.parsedAsList = true;
    this.listSeparator = listSeparator;
    return this;
  }

  public Converter<T> getConverter() {
    return converter;
  }

  public TypedOption<T> setConverter(Converter<T> converter) {
    this.converter = converter;
    return this;
  }

  @Override
  public void ensureValidity() {
    super.ensureValidity();
    if (type == null) {
      throw new IllegalArgumentException("Type must not be null");
    }
  }


}
