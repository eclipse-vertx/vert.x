package org.vertx.java.core.app.cli;

import org.vertx.java.core.app.VerticleManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class StopCommand extends VertxCommand {

  public String execute(VerticleManager appMgr) {
    appMgr.unblock();
    return null;
  }

  @Override
  public boolean isBlock() {
    return false;
  }
}
