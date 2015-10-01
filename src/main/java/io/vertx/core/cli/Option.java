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

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

import java.util.Objects;

/**
 * Models command line options. Options are values passed to a command line interface using -x or --x. Supported
 * syntaxes depend on the parser.
 * <p/>
 * Short name is generally used with a single dash, while long name requires a double-dash.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
@DataObject(generateConverter = true)
public class Option {

  /**
   * Default name in the usage message.
   */
  public static final String DEFAULT_ARG_NAME = "value";

  /**
   * The default long name / short name of the option. Notice that options requires at least a regular long name or
   * short name.
   */
  public static final String NO_NAME = "\0";

  /**
   * the option long name.
   */
  protected String longName = NO_NAME;

  /**
   * the option short name.
   */
  protected String shortName = NO_NAME;

  /**
   * the option name used in usage message.
   */
  protected String argName = DEFAULT_ARG_NAME;

  /**
   * The option description.
   */
  protected String description;

  /**
   * whether or not the option is required. A mandatory not set throws a {@link MissingOptionException}.
   */
  protected boolean required;

  /**
   * whether or not the option is hidden. Hidden options are not displayed in usage.
   */
  protected boolean hidden;

  /**
   * whether or not the option receives a single value. {@code true} by default.
   */
  protected boolean singleValued = true;

  /**
   * whether or not the option can recevie multiple values.
   */
  protected boolean multiValued;

  /**
   * the option default value.
   */
  protected String defaultValue;

  /**
   * whether or not the option is a flag. Flag option does not require a value. If an option is a flag, it is
   * evaluated to {@link true} if the option is used in the command line.
   */
  protected boolean flag;

  /**
   * Creates a new empty instance of {@link Option}.
   */
  public Option() {
  }

  /**
   * Creates a new instance of {@link Option} by copying the state of another {@link Option}.
   *
   * @param other the other option
   */
  public Option(Option other) {
    this();
    this.longName = other.longName;
    this.shortName = other.shortName;
    this.argName = other.argName;
    this.description = other.description;
    this.required = other.required;
    this.hidden = other.hidden;
    this.singleValued = other.singleValued;
    this.multiValued = other.multiValued;
    this.defaultValue = other.defaultValue;
    this.flag = other.flag;
  }

  /**
   * Creates a new instance of {@link Option} from the given {@link JsonObject}
   *
   * @param json the json object representing the option
   * @see #toJson()
   */
  public Option(JsonObject json) {
    this();
    OptionConverter.fromJson(json, this);
  }

  /**
   * Gets the json representation of this {@link Option}.
   *
   * @return the json representation
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    OptionConverter.toJson(this, json);
    return json;
  }

  /**
   * Checks whether or not the option is valid. This implementation check that it has a short name or a long name.
   * This method is intended to be extended by sub-class. Parser should check that the set of
   * option of a {@link CLI} is valid before starting the parsing.
   * <p/>
   * If the configuration is not valid, this method throws a {@link IllegalArgumentException}.
   */
  public void ensureValidity() {
    if ((shortName == null || shortName.equals(NO_NAME)) && (longName == null || longName.equals(NO_NAME))) {
      throw new IllegalArgumentException("An option needs at least a long name or a short name");
    }
  }

  /**
   * @return whether or not the option can receive a value.
   */
  public boolean acceptValue() {
    return singleValued || multiValued;
  }

  /**
   * @return the option name. It returns the long name if set, the short name otherwise. It cannot return {@code
   * null} for valid option
   * @see #ensureValidity()
   */
  public String getName() {
    if (longName != null && !longName.equals(NO_NAME)) {
      return longName;
    }
    // So by validity, it necessarily has a short name.
    return shortName;
  }

  /**
   * @return whether or not this option can receive several values.
   */
  public boolean isMultiValued() {
    return multiValued;
  }

  /**
   * Sets whether or not this option can receive several values.
   *
   * @param multiValued whether or not this option is multi-valued.
   * @return the current {@link Option} instance
   */
  public Option setMultiValued(boolean multiValued) {
    this.multiValued = multiValued;
    if (this.multiValued) {
      // If we accept more than one, we also accept 1.
      this.singleValued = true;
    }
    // Else we cannot say what is the desired value of singleValued, it needs to be explicitly set.
    return this;
  }

  /**
   * @return whether or not this option is single valued.
   */
  public boolean isSingleValued() {
    return singleValued;
  }

  /**
   * Sets whether or not this option can receive a value.
   *
   * @param singleValued whether or not this option is single-valued.
   * @return the current {@link Option} instance
   */
  public Option setSingleValued(boolean singleValued) {
    this.singleValued = singleValued;
    return this;
  }

  /**
   * @return the option arg name used in usage messages, {@code null} if not set.
   */
  public String getArgName() {
    return argName;
  }

  /**
   * Sets te arg name for this option.
   *
   * @param argName the arg name, must not be {@code null}
   * @return the current {@link Option} instance
   */
  public Option setArgName(String argName) {
    Objects.requireNonNull(argName);
    this.argName = argName;
    return this;
  }

  /**
   * @return the description of this option, {@code null} if not set.
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets te description of this option.
   *
   * @param description the description
   * @return the current {@link Option} instance
   */
  public Option setDescription(String description) {
    this.description = description;
    return this;
  }

  /**
   * @return whtehr or not this option is hidden.
   */
  public boolean isHidden() {
    return hidden;
  }

  /**
   * Sets whether or not this option should be hidden
   *
   * @param hidden {@code true} to make this option hidden, {@link false} otherwise
   * @return the current {@link Option} instance
   */
  public Option setHidden(boolean hidden) {
    this.hidden = hidden;
    return this;
  }

  /**
   * @return the option long name, {@code null} if not set.
   */
  public String getLongName() {
    return longName;
  }

  /**
   * Sets the long name of this option.
   *
   * @param longName the long name
   * @return the current {@link Option} instance
   */
  public Option setLongName(String longName) {
    this.longName = longName;
    return this;
  }

  /**
   * @return whether or not this option is mandatory.
   */
  public boolean isRequired() {
    return required;
  }

  /**
   * Sets whether or not this option is mandatory.
   *
   * @param required {@code true} to make this option mandatory, {@link false} otherwise
   * @return the current {@link Option} instance
   */
  public Option setRequired(boolean required) {
    this.required = required;
    return this;
  }

  /**
   * @return the short name of this option, {@code null} if not set.
   */
  public String getShortName() {
    return shortName;
  }

  /**
   * Sets the short name of this option.
   *
   * @param shortName the short name
   * @return the current {@link Option} instance
   */
  public Option setShortName(String shortName) {
    this.shortName = shortName;
    return this;
  }

  /**
   * @return the default value of this option, {@code null} if not set.
   */
  public String getDefaultValue() {
    return defaultValue;
  }

  /**
   * Sets the default value of this option
   *
   * @param defaultValue the default value
   * @return the current {@link Option} instance
   */
  public Option setDefaultValue(String defaultValue) {
    this.defaultValue = defaultValue;
    if (this.defaultValue != null) {
      setRequired(false);
    }
    return this;
  }

  /**
   * @return whether or not this option is a flag.
   */
  public boolean isFlag() {
    return flag;
  }

  /**
   * Configures the current {@link Option} to be a flag. It will be evaluated to {@code true} if it's found in
   * the command line. If you need a flag that may receive a value, use, in this order:
   * <code><pre>
   *   option.setFlag(true).setSingleValued(true)
   * </pre></code>
   *
   * @param flag whether or not the option is a flag.
   * @return the current {@link Option}
   */
  public Option setFlag(boolean flag) {
    this.flag = flag;
    setSingleValued(false);
    return this;
  }

}
