package org.vertx.java.core.app.cli;

import org.vertx.java.core.app.AppManager;

import java.io.Serializable;

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

  public void execute(AppManager appMgr) throws Exception {
    appMgr.undeploy(name);
  }
}
