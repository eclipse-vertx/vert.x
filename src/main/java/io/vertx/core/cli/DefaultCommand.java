package io.vertx.core.cli;


import io.vertx.core.spi.Command;

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link Command} using annotation to define itself. It is highly recommended to extend
 * this class when implementing a command.
 */
public abstract class DefaultCommand implements Command {

  private File cwd;
  private List<String> props;
  protected ExecutionContext executionContext;
  protected PrintStream out;
  private Map<String, String> sysProps = new HashMap<>();

  public File getCwd() {
    return cwd != null ? cwd : new File(".");
  }

  @Option(longName = "cwd", name = "dir")
  @Description("Specifies the current working directory for this command, default set to the Java current directory")
  @Hidden
  public void setCwd(File cwd) {
    this.cwd = cwd;
  }

  @Option(longName = "systemProperty", shortName = "D", name = "key>=<value")
  @Description("Set a system property")
  @Hidden
  public void setSystemProps(List<String> props) {
    this.props = props;
  }

  @Override
  public void initialize(ExecutionContext ec) throws CommandLineException {
    this.executionContext = ec;
    this.out = executionContext.getPrintStream();
  }

  /**
   * @return the print stream on which message should be written.
   */
  public PrintStream out() {
    return executionContext.getPrintStream();
  }

  @Override
  public void setup() throws CommandLineException {
    applySystemProperties();
  }

  @Override
  public void tearDown() throws CommandLineException {
    unapplySystemProperties();
  }

  /**
   * Gets the command summary by reading the {@link Summary} annotation.
   *
   * @return the summary (one sentence) of the command.
   */
  @Override
  public String summary() {
    String summary = null;
    Class clazz = this.getClass();
    while (summary == null && clazz != null) {
      Summary annotation = (Summary) clazz.getAnnotation(Summary.class);
      if (annotation != null) {
        summary = annotation.value();
      } else {
        clazz = clazz.getSuperclass();
      }
    }
    if (summary == null) {
      return "no summary";
    }
    return summary;
  }

  /**
   * @return the description of the command.
   */
  @Override
  public String description() {
    String desc = null;
    Class clazz = this.getClass();
    while (desc == null && clazz != null) {
      Description annotation = (Description) clazz.getAnnotation(Description.class);
      if (annotation != null) {
        desc = annotation.value();
      } else {
        clazz = clazz.getSuperclass();
      }
    }
    if (desc == null) {
      return "";
    }
    return desc;
  }

  /**
   * Checks whether or not the command is hidden based on the {@link Hidden} annotation. The check does not check for
   * parent classes.
   *
   * @return {@code true} if the command is hidden, {@code false} otherwise.
   */
  @Override
  public boolean hidden() {
    return this.getClass().getAnnotation(Hidden.class) != null;
  }

  /**
   * Extracts the set of {@link OptionModel} by analysing {@link Option} annotation on setter methods.
   *
   * @return the list of extracted options, empty if none.
   */
  @Override
  public List<OptionModel> options() {
    List<OptionModel> list = new ArrayList<>();

    for (Method method : ReflectionUtils.getSetterMethods(this.getClass())) {
      Option option = method.getAnnotation(Option.class);
      if (option != null) {
        list.add(define(option, method));
      }
    }
    return list;
  }


  /**
   * Extracts the set of {@link ArgumentModel} by analysing {@link Argument} annotation on setter methods.
   *
   * @return the list of extracted arguments, empty if none.
   */
  @Override
  public List<ArgumentModel> arguments() {
    List<ArgumentModel> list = new ArrayList<>();
    for (Method method : ReflectionUtils.getSetterMethods(this.getClass())) {
      final Argument arg = method.getAnnotation(Argument.class);
      if (arg != null) {
        list.add(define(arg, method));
      }
    }
    return list;
  }

  protected OptionModel define(Option annotation, Method method) {
    final OptionModel.Builder builder = OptionModel.builder()
        .longName(annotation.longName())
        .shortName(annotation.shortName())
        .argName(annotation.name())
        .isRequired(annotation.required())
        .acceptValue(annotation.acceptValue());

    // Get type.
    if (ReflectionUtils.isMultiple(method)) {
      builder.acceptMultipleValues();
      builder.type(ReflectionUtils.getComponentType(method.getParameters()[0]));
    } else {
      builder.type((Class) method.getParameterTypes()[0]);
    }

    // Companion annotations
    Description description = method.getAnnotation(Description.class);
    if (description != null) {
      builder.description(description.value());
    }

    ParsedAsList parsedAsList = method.getAnnotation(ParsedAsList.class);
    if (parsedAsList != null) {
      builder.listSeparator(parsedAsList.separator());
    }

    Hidden hidden = method.getAnnotation(Hidden.class);
    if (hidden != null) {
      builder.hidden();
    }

    ConvertedBy convertedBy = method.getAnnotation(ConvertedBy.class);
    if (convertedBy != null) {
      builder.convertedBy((Class) convertedBy.value());
    }

    DefaultValue defaultValue = method.getAnnotation(DefaultValue.class);
    if (defaultValue != null) {
      builder.defaultValue(defaultValue.value());
    }

    return builder.build();
  }

  protected ArgumentModel define(Argument annotation, Method method) {
    final ArgumentModel.Builder builder = ArgumentModel.builder()
        .index(annotation.index())
        .argName(annotation.name())
        .required(annotation.required());

    builder.type((Class) method.getParameterTypes()[0]);

    // Companion annotations
    Description description = method.getAnnotation(Description.class);
    if (description != null) {
      builder.description(description.value());
    }

    Hidden hidden = method.getAnnotation(Hidden.class);
    if (hidden != null) {
      builder.hidden();
    }

    ConvertedBy convertedBy = method.getAnnotation(ConvertedBy.class);
    if (convertedBy != null) {
      builder.convertedBy((Class) convertedBy.value());
    }

    DefaultValue defaultValue = method.getAnnotation(DefaultValue.class);
    if (defaultValue != null) {
      builder.defaultValue(defaultValue.value());
    }

    return builder.build();
  }

  protected void applySystemProperties() {
    if (props != null) {
      for (String prop : props) {
        int p = prop.indexOf('=');
        if (p > 0) {
          String key = prop.substring(0, p);
          String val = prop.substring(p + 1);
          System.setProperty(key, val);
          sysProps.put(key, val);
        }
      }
    }
  }

  protected void unapplySystemProperties() {
    if (props != null) {
      for (String prop : props) {
        int p = prop.indexOf('=');
        if (p > 0) {
          String key = prop.substring(0, p);
          System.clearProperty(key);
        }
      }
    }
  }

  protected Map<String, String> getSystemProperties() {
    return sysProps;
  }

}
