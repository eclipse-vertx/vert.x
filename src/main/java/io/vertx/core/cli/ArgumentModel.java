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


import io.vertx.core.cli.converters.Converter;
import io.vertx.core.cli.converters.Converters;

import java.util.Objects;

public class ArgumentModel<T> {

  private final String description;
  private final int index;
  private String argName;
  private Class<T> type;

  private T defaultValue;
  private Converter<T> converter;
  private boolean hidden;
  private boolean required;

  private T value;

  public ArgumentModel(int index, Class<T> type, String description) {
    Objects.requireNonNull(type);
    if (index < 0) {
      throw new IllegalArgumentException("Index must be positive");
    }

    this.description = description;
    this.type = type;
    this.index = index;
  }

  public static <T> Builder<T> builder() {
    return new Builder<>();
  }

  public String getArgName() {
    if (argName != null) {
      return argName;
    }
    return "value";
  }

  public String getDescription() {
    return description;
  }

  public int getIndex() {
    return index;
  }

  public T getValue() {
    if (value != null) {
      return value;
    }
    return defaultValue;
  }

  public boolean hasValue() {
    return value != null;
  }

  protected T create(String value) throws InvalidValueException {
    try {
      if (converter != null) {
        return Converters.create(value, converter);
      } else {
        return Converters.create(type, value);
      }
    } catch (Exception e) {
      throw new InvalidValueException(this, value, e);
    }
  }

  public void validate() throws CommandLineException {
    if (required && !hasValue()) {
      throw new MissingValueException(this);
    }
  }

  public void process(String value) throws InvalidValueException {
    this.value = create(value);
  }

  public void clear() {
    value = null;
  }

  public boolean isRequired() {
    return required;
  }


  public static class Builder<T> {

    private int index;
    private String description;
    private boolean required;
    private boolean hidden;
    private Class<T> type;
    private String argName;
    private Class<? extends Converter<T>> converter;
    private String defaultValue;


    public ArgumentModel<T> build() {
      ArgumentModel<T> model = new ArgumentModel<>(index, type, description);
      model.required = required;
      model.hidden = hidden;
      if (converter != null) {
        model.converter = Converters.newInstance(converter);
      }
      model.argName = argName;
      if (defaultValue != null) {
        try {
          model.defaultValue = model.create(defaultValue);
        } catch (InvalidValueException e) {
          throw new IllegalArgumentException(e);
        }
        model.required = false;
      }

      return model;
    }

    public Builder<T> index(int index) {
      this.index = index;
      return this;
    }

    public Builder<T> description(String description) {
      this.description = description;
      return this;
    }

    public Builder<T> required(boolean required) {
      this.required = required;
      return this;
    }

    public Builder<T> required() {
      return required(true);
    }

    public Builder<T> type(Class<T> type) {
      this.type = type;
      return this;
    }

    public Builder<T> hidden() {
      this.hidden = true;
      return this;
    }

    public Builder<T> argName(String name) {
      this.argName = name;
      return this;
    }

    public Builder<T> defaultValue(String value) {
      this.defaultValue = value;
      return this;
    }

    public Builder<T> convertedBy(Class<? extends Converter<T>> converter) {
      this.converter = converter;
      return this;
    }
  }
}
