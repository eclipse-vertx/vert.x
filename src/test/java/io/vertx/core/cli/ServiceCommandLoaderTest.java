package io.vertx.core.cli;

import io.vertx.core.spi.Command;
import org.junit.Test;

import java.util.Collection;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.fail;


public class ServiceCommandLoaderTest {

  ServiceCommandLoader loader = new ServiceCommandLoader();

  @Test
  public void testLookup() throws Exception {
    final Collection<Command> commands = loader.lookup();
    ensureCommand(commands, "Hello");
    ensureCommand(commands, "Bye");

  }

  private void ensureCommand(Collection<Command> commands, String name) {
    for (Command command : commands) {
      if (command.name().equalsIgnoreCase(name)) {
        return;
      }
    }
    fail("Cannot find '" + name + "' in " + commands.stream().map(Command::name).collect(Collectors.toList()));
  }
}