/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
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
 * @see Option
 */
@DataObject(generateConverter = true, publicConverter = false)
public class Argument {

  /**
   * The default argument name displayed in the usage.
   */
  public static final String DEFAULT_ARG_NAME = "value";

  /**
   * The argument index. Must be positive or null.
   * It is set fo -1 by default to check that the index was set.
   */
  protected int index = -1;

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
   * Whether or not this argument can receive multiple value. Only the last argument can receive multiple values.
   */
  protected boolean multiValued = false;

  /**
   * Creates a new empty instance of {@link Argument}.
   */
  public Argument() {

  }

  /**
   * Creates a new instance of {@link Argument} by copying {@code other}.
   *
   * @param other the argument to copy
   */
  public Argument(Argument other) {
    this();
    index = other.index;
    argName = other.argName;
    description = other.description;
    hidden = other.hidden;
    required = other.required;
    defaultValue = other.defaultValue;
    multiValued = other.multiValued;
  }

  /**
   * Creates a new instance of {@link Argument} from the given JSON object.
   *
   * @param json the json object
   * @see #toJson()
   */
  public Argument(JsonObject json) {
    this();
    ArgumentConverter.fromJson(json, this);
  }

  /**
   * Exports this {@link Argument} to its corresponding JSON representation.
   *
   * @return the json object representing this {@link Argument}
   * @see #Argument(JsonObject)
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    ArgumentConverter.toJson(this, json);
    return json;
  }

  /**
   * @return the arg name, {@code null} if not defined.
   */
  public String getArgName() {
    return argName;
  }

  /**
   * Sets the argument name of this {@link Argument}.
   *
   * @param argName the argument name, must not be {@code null}
   * @return the current {@link Argument} instance
   */
  public Argument setArgName(String argName) {
    Objects.requireNonNull(argName);
    this.argName = argName;
    return this;
  }

  /**
   * @return the description, {@code null} if not defined.
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets the description of the {@link Argument}.
   *
   * @param description the description
   * @return the current {@link Argument} instance
   */
  public Argument setDescription(String description) {
    Objects.requireNonNull(description);
    this.description = description;
    return this;
  }

  /**
   * @return whether or not the current {@link Argument} is hidden.
   */
  public boolean isHidden() {
    return hidden;
  }

  /**
   * Sets whether or not the current {@link Argument} is hidden.
   *
   * @param hidden enables or disables the visibility of this {@link Argument}
   * @return the current {@link Argument} instance
   */
  public Argument setHidden(boolean hidden) {
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
   * @return the current {@link Argument} instance
   */
  public Argument setIndex(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("Argument index cannot be negative");
    }
    this.index = index;
    return this;
  }

  /**
   * @return whether or not the current {@link Argument} is required.
   */
  public boolean isRequired() {
    return required;
  }

  /**
   * Sets whether or not the current {@link Argument} is required.
   *
   * @param required {@code true} to make this argument mandatory, {@link false} otherwise
   * @return the current {@link Argument} instance
   */
  public Argument setRequired(boolean required) {
    this.required = required;
    return this;
  }

  /**
   * @return the argument default value, {@code null} if not specified.
   */
  public String getDefaultValue() {
    return defaultValue;
  }

  /**
   * Sets the default value of this {@link Argument}.
   *
   * @param defaultValue the default value
   * @return the current {@link Argument} instance
   */
  public Argument setDefaultValue(String defaultValue) {
    this.defaultValue = defaultValue;
    return this;
  }

  /**
   * @return whether or not the argument can receive several values.
   */
  public boolean isMultiValued() {
    return multiValued;
  }

  /**
   * Sets whether or not the argument can receive several values. Only the last argument can receive several values.
   *
   * @param multiValued {@code true} to mark this argument as multi-valued.
   * @return the current {@link Argument} instance
   */
  public Argument setMultiValued(boolean multiValued) {
    this.multiValued = multiValued;
    return this;
  }

  /**
   * Checks that the argument configuration is valid. This method is mainly made for children classes adding
   * constraint to the configuration. The parser verifies that arguments are valid before starting
   * the parsing.
   * <p/>
   * If the configuration is not valid, this method throws a {@link IllegalArgumentException}.
   */
  public void ensureValidity() {
    if (index < 0) {
      throw new IllegalArgumentException("The index cannot be negative");
    }
  }

}
