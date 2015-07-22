package io.vertx.core.cli;


import io.vertx.core.cli.converters.Converter;
import io.vertx.core.cli.converters.Converters;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OptionModel<T> {

  private final String description;
  private final String shortName;
  private final String longName;
  private final Class<T> type;

  private boolean acceptSingleValue;
  private boolean isRequired;
  private boolean acceptMultipleValues;
  private boolean isList;
  private String listSeparator = ",";
  private T defaultValue;

  private boolean setInCommandLine = false;

  private List<T> values = new ArrayList<>();
  private String argName;
  private boolean hidden;
  private Converter<T> converter;

  public OptionModel(String shortName, String longName, String description, Class<T> type) {

    if (isValid(shortName)) {
      this.shortName = shortName;
    } else {
      this.shortName = null;
    }
    if (isValid(longName)) {
      this.longName = longName;
    } else {
      this.longName = null;
    }

    if (this.longName == null && this.shortName == null) {
      throw new IllegalArgumentException("An option must have at least a valid long name or short name");
    }

    if (type == null) {
      throw new IllegalStateException("Option's type must be defined");
    }
    this.type = type;

    this.description = description;
  }

  private boolean isValid(String name) {
    return name != null && !name.equals(Option.NO_NAME) && name.length() > 0;
  }

  public static <T> Builder<T> builder() {
    return new Builder<>();
  }

  public boolean acceptMoreValues() {
    return (acceptSingleValue && values.isEmpty()) || acceptMultipleValues;
  }

  public String getDescription() {
    return description;
  }

  public boolean isHidden() {
    return hidden;
  }

  public boolean acceptMultipleValues() {
    return acceptMultipleValues;
  }

  public void acceptMultipleValues(boolean acceptMultipleValues) {
    this.acceptMultipleValues = acceptMultipleValues;
  }

  public boolean acceptSingleValue() {
    return acceptSingleValue;
  }

  public String getArgName() {
    if (argName == null) {
      return "value";
    }
    return argName;
  }

  public void acceptSingleValue(boolean acceptSingleValue) {
    this.acceptSingleValue = acceptSingleValue;
  }

  public boolean isList() {
    return isList;
  }

  public void isList(boolean isList) {
    this.isList = isList;
  }

  public boolean isRequired() {
    return isRequired;
  }

  public void isRequired(boolean isRequired) {
    this.isRequired = isRequired;
  }

  public String getListSeparator() {
    return listSeparator;
  }

  public void setListSeparator(String listSeparator) {
    Objects.requireNonNull(listSeparator);
    this.listSeparator = listSeparator;
  }

  public String getLongName() {
    return longName;
  }

  public String getShortName() {
    return shortName;
  }

  public Class<T> getType() {
    return type;
  }

  public T getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(T defaultValue) {
    this.defaultValue = defaultValue;
  }

  public void setInCommandLine() {
    setInCommandLine = true;
  }

  public void process(String value) throws CommandLineException {
    if (value.isEmpty() && (type == Boolean.class || type == Boolean.TYPE)) {
      value = "true";
    } else if (!acceptMoreValues()) {
      throw new CommandLineException("The option " + toString() + " does not accept value or has already been set");
    }


    if (isList()) {
      final String[] segments = value.split(listSeparator);
      for (String segment : segments) {
        values.add(create(segment.trim()));
      }
    } else {
      values.add(create(value));
    }
  }

  public T getValue() {
    if (hasValue()) {
      return values.get(0);
    } else {
      if (defaultValue != null) {
        return defaultValue;
      }
      if (maybeFlag()) {
        try {
          if (setInCommandLine) {
            return create("true");
          } else {
            return create("false");
          }
        } catch (InvalidValueException e) {
          throw new IllegalArgumentException(e);
        }
      }
    }
    return null;
  }

  public T getValue(int index) {
    if (hasValue()) {
      return values.get(index);
    } else {
      return defaultValue;
    }
  }

  public List<T> getValues() {
    return values;
  }

  public boolean hasValue() {
    return !values.isEmpty();
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

  @Override
  public String toString() {
    return "'" + (longName != null ? longName : shortName) + "'";
  }

  public void clear() {
    values.clear();
    setInCommandLine = false;
  }

  public boolean acceptValue() {
    return acceptMultipleValues || acceptSingleValue;
  }

  public boolean matches(String name) {
    Objects.requireNonNull(name);
    return name.equalsIgnoreCase(longName) || name.equalsIgnoreCase(shortName);
  }

  public boolean maybeFlag() {
    return type == Boolean.class || type == Boolean.TYPE;
  }

  public void validate() throws CommandLineException {
    // Do nothing, already checked in the parser.
  }

  public String getKey() {
    if (longName != null) {
      return longName;
    } else {
      return shortName;
    }
  }

  public boolean hasBeenSet() {
    return setInCommandLine;
  }

  public static class Builder<T> {

    private String description;
    private String shortName;
    private String longName;
    private boolean acceptSingleValue;
    private boolean isRequired;
    private boolean acceptMultipleValue;
    private Class<T> type;
    private boolean isList;
    private String listSeparator = ",";
    private String argName;
    private boolean hidden;
    private Class<? extends Converter<T>> converter;
    private String defaultValueAsString;

    public OptionModel<T> build() {
      OptionModel<T> option = new OptionModel<>(shortName, longName, description, type);
      option.acceptSingleValue = acceptSingleValue;
      option.acceptMultipleValues = acceptMultipleValue;
      option.isRequired = isRequired;
      option.isList = isList;
      option.setListSeparator(listSeparator);
      option.argName = argName;
      option.hidden = hidden;
      if (converter != null) {
        option.converter = Converters.newInstance(converter);
      }
      if (defaultValueAsString != null) {
        try {
          option.defaultValue = option.create(defaultValueAsString);
        } catch (InvalidValueException e) {
          throw new IllegalArgumentException(e);
        }
      }

      return option;
    }

    public Builder<T> shortName(String s) {
      if (s == null || s.isEmpty()) {
        throw new IllegalArgumentException("Short name must not be null or empty");
      }
      shortName = s;
      return this;
    }

    public Builder<T> longName(String s) {
      if (s == null || s.isEmpty()) {
        throw new IllegalArgumentException("Long name must not be null or empty");
      }
      this.longName = s;
      return this;
    }

    public Builder<T> description(String s) {
      if (s == null || s.isEmpty()) {
        throw new IllegalArgumentException("Description must not be null or empty");
      }
      this.description = s;
      return this;
    }

    public Builder<T> acceptValue() {
      return acceptValue(true);
    }

    public Builder<T> acceptValue(boolean v) {
      acceptSingleValue = v;
      return this;
    }

    public Builder<T> acceptMultipleValues(boolean v) {
      acceptMultipleValue = v;
      return this;
    }

    public Builder<T> acceptMultipleValues() {
      return acceptMultipleValues(true);
    }

    public Builder<T> isRequired() {
      return isRequired(true);
    }

    public Builder<T> isRequired(boolean v) {
      isRequired = v;
      return this;
    }

    public Builder<T> type(Class<T> type) {
      this.type = type;
      return this;
    }

    public Builder<T> defaultValue(String val) {
      this.defaultValueAsString = val;
      return this;
    }

    public Builder<T> list(boolean v) {
      isList = v;
      return this;
    }

    public Builder<T> list() {
      acceptMultipleValue = true;
      return list(true);
    }

    public Builder<T> listSeparator(String s) {
      if (s == null || s.isEmpty()) {
        throw new IllegalArgumentException("Separator must not be null or empty");
      }
      this.listSeparator = s;
      return list();
    }

    public Builder<T> argName(String s) {
      if (s == null || s.isEmpty()) {
        throw new IllegalArgumentException("Separator must not be null or empty");
      }
      this.argName = s;
      return this;
    }

    public Builder<T> hidden() {
      this.hidden = true;
      return this;
    }

    public Builder<T> convertedBy(Class<? extends Converter<T>> converter) {
      this.converter = converter;
      return this;
    }
  }
}
