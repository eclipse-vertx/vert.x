package io.vertx.core.cli;

import io.vertx.core.spi.Command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Look for commands using a service loader.
 */
public class ServiceCommandLoader implements CommandLookup {


  private ServiceLoader<Command> loader;


  public ServiceCommandLoader() {
    this.loader = ServiceLoader.load(Command.class, null);
  }


  public ServiceCommandLoader(ClassLoader loader) {
    this.loader = ServiceLoader.load(Command.class, loader);
  }

  @Override
  public Collection<Command> lookup() {
    List<Command> commands = new ArrayList<>();
    loader.forEach(commands::add);
    return commands;
  }

}
