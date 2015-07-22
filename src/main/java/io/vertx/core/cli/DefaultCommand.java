package io.vertx.core.cli;


import io.vertx.core.spi.Command;

import java.io.File;
import java.util.List;

public abstract class DefaultCommand implements Command {

  private File cwd;
  private List<String> props;
  protected ExecutionContext executionContext;

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

  protected void applySystemProperties() {
    if (props != null) {
      for (String prop : props) {
        int p = prop.indexOf('=');
        if (p > 0) {
          String key = prop.substring(0, p);
          String val = prop.substring(p + 1);
          System.setProperty(key, val);
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

  @Override
  public void initialize(ExecutionContext ec) throws CommandLineException {
    this.executionContext = ec;
  }


  @Override
  public void setup() throws CommandLineException {
    applySystemProperties();
  }

  @Override
  public void tearDown() throws CommandLineException {
    unapplySystemProperties();
  }

}
