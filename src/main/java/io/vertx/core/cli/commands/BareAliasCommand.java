package io.vertx.core.cli.commands;

import io.vertx.core.cli.Description;
import io.vertx.core.cli.Hidden;
import io.vertx.core.cli.Summary;

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

}
