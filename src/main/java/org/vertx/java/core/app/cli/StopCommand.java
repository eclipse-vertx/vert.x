package org.vertx.java.core.app.cli;

import org.vertx.java.core.app.AppManager;
import org.vertx.java.core.internal.VertxInternal;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class StopCommand extends VertxCommand {

  public void execute(AppManager appMgr) {
    VertxInternal.instance.exit();
  }

  @Override
  public boolean isBlock() {
    return false;
  }
}
