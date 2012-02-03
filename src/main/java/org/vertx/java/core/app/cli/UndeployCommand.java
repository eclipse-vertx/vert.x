package org.vertx.java.core.app.cli;

import org.vertx.java.core.app.VerticleManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class UndeployCommand extends VertxCommand {

  public String name;

  public UndeployCommand(String name) {
    this.name = name;
  }

  public UndeployCommand() {
  }

  public String execute(VerticleManager appMgr) throws Exception {
    return appMgr.undeploy(name, null);
  }
}
