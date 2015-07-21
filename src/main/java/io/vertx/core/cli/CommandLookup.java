package io.vertx.core.cli;

import java.util.Collection;

/**
 * The interface to implement to look for commands.
 *
 * @see ServiceCommandLoader
 */
public interface CommandLookup {

  /**
   * Looks for command implementation and instantiated them.
   *
   * @return the set of commands, empty if none are found.
   */
  Collection<Command> lookup();

}
