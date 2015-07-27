package io.vertx.core.cli.commands;

import io.vertx.core.cli.*;

import java.util.ArrayList;
import java.util.List;

@Summary("Creates a bare instance of vert.x.")
@Description("This command launches a vert.x instance but do not deploy any verticles. It will " +
    "receive a verticle if another node of the cluster dies. This command is an alias on the 'bare' command.")
@Hidden
public class BareAliasCommand extends BareCommand {

  /**
   * @return alias on the 'bare' command just for compatibility reason.
   */
  @Override
  public String name() {
    return "-ha";
  }

  /**
   * Executes the command.
   *
   * @throws CommandLineException If anything went wrong.
   */
  @Override
  public void run() throws CommandLineException {
    if (executionContext.getInterface().getMainVerticle() != null) {
      // Do not execute 'bare' we need to deploy the main verticle with -ha
      System.out.println(VertxCommandLineInterface.getProcessArguments());
      List<String> args = new ArrayList<>();
      args.add("run");
      args.add(executionContext.getInterface().getMainVerticle());
      args.addAll(VertxCommandLineInterface.getProcessArguments());
      executionContext.getInterface().dispatch(args.toArray(new String[args.size()]));
    } else {
      super.run();
    }
  }
}
