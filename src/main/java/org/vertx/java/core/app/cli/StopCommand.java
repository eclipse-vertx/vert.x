package org.vertx.java.core.app.cli;

import org.vertx.java.core.app.AppManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class StopCommand extends VertxCommand {

  public void execute(AppManager appMgr) {
    appMgr.unblock();
  }

  @Override
  public boolean isBlock() {
    return false;
  }
}
