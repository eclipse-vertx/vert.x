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

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

import java.util.Objects;

/**
 * Defines a command line argument. Unlike options, argument don't have names and are identified using an index. The
 * first index is 0 (because we are in the computer world).
 *
 * @author Clement Escoffier <clement@apache.org>
 * @see OptionModel
 */
@DataObject(generateConverter = true)
public class ArgumentModel {

  /**
   * The default argument name displayed in the usage.
   */
  public static final String DEFAULT_ARG_NAME = "value";

  /**
   * The argument index. Must be positive or null.
   */
  protected int index;

  /**
   * The argument name used in the usage.
   */
  protected String argName = DEFAULT_ARG_NAME;

  /**
   * The argument description.
   */
  protected String description;
  /**
   * Whether or not this argument is hidden. Hidden argument are not shown in the usage.
   */
  protected boolean hidden;

  /**
   * Whether or not this argument is mandatory. Mandatory arguments throw a {@link MissingValueException} if the
   * user command line does not provide a value.
   */
  protected boolean required = true;

  /**
   * The optional default value of this argument.
   */
  protected String defaultValue;

  /**
   * Creates a new empty instance of {@link ArgumentModel}.
   */
  public ArgumentModel() {

  }

  /**
   * Creates a new instance of {@link ArgumentModel} by copying {@code other}.
   *
   * @param other the argument to copy
   */
  public ArgumentModel(ArgumentModel other) {
    this();
    index = other.index;
    argName = other.argName;
    description = other.description;
    hidden = other.hidden;
    required = other.required;
    defaultValue = other.defaultValue;
  }

  /**
   * Creates a new instance of {@link ArgumentModel} from the given JSON object.
   *
   * @param json the json object
   * @see #toJson()
   */
  public ArgumentModel(JsonObject json) {
    this();
    ArgumentModelConverter.fromJson(json, this);
  }

  /**
   * Exports this {@link ArgumentModel} to its corresponding JSON representation.
   *
   * @return the json object representing this {@link ArgumentModel}
   * @see #ArgumentModel(JsonObject)
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    ArgumentModelConverter.toJson(this, json);
    return json;
  }

  /**
   * @return the arg name, {@link null} if not defined.
   */
  public String getArgName() {
    return argName;
  }

  /**
   * Sets the argument name of this {@link ArgumentModel}.
   *
   * @param argName the argument name, must not be {@link null}
   * @return the current {@link ArgumentModel} instance
   */
  public ArgumentModel setArgName(String argName) {
    Objects.requireNonNull(argName);
    this.argName = argName;
    return this;
  }

  /**
   * @return the description, {@link null} if not defined.
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets the description of the {@link ArgumentModel}.
   *
   * @param description the description
   * @return the current {@link ArgumentModel} instance
   */
  public ArgumentModel setDescription(String description) {
    Objects.requireNonNull(description);
    this.description = description;
    return this;
  }

  /**
   * @return whether or not the current {@link ArgumentModel} is hidden.
   */
  public boolean isHidden() {
    return hidden;
  }

  /**
   * Sets whether or not the current {@link ArgumentModel} is hidden.
   *
   * @param hidden enables or disables the visibility of this {@link ArgumentModel}
   * @return the current {@link ArgumentModel} instance
   */
  public ArgumentModel setHidden(boolean hidden) {
    this.hidden = hidden;
    return this;
  }

  /**
   * @return the argument index.
   */
  public int getIndex() {
    return index;
  }

  /**
   * Sets the argument index.
   *
   * @param index the index, must not be negative
   * @return the current {@link ArgumentModel} instance
   */
  public ArgumentModel setIndex(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("Argument index cannot be negative");
    }
    this.index = index;
    return this;
  }

  /**
   * @return whether or not the current {@link ArgumentModel} is required.
   */
  public boolean isRequired() {
    return required;
  }

  /**
   * Sets whether or not the current {@link ArgumentModel} is required.
   *
   * @param required {@code true} to make this argument mandatory, {@link false} otherwise
   * @return the current {@link ArgumentModel} instance
   */
  public ArgumentModel setRequired(boolean required) {
    this.required = required;
    return this;
  }

  /**
   * @return the argument default value, {@link null} if not specified.
   */
  public String getDefaultValue() {
    return defaultValue;
  }

  /**
   * Sets the default value of this {@link ArgumentModel}.
   *
   * @param defaultValue the default value
   * @return the current {@link ArgumentModel} instance
   */
  public ArgumentModel setDefaultValue(String defaultValue) {
    this.defaultValue = defaultValue;
    return this;
  }

  /**
   * Checks that the argument configuration is valid. This method is mainly made for children classes adding
   * constraint to the configuration. The {@link CommandLineParser} verifies that arguments are valid before starting
   * the parsing.
   *
   * @return {@code true}
   */
  public boolean isValid() {
    return true;
  }

}
