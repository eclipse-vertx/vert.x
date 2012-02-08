package org.vertx.java.core.app.cli;

import org.vertx.java.core.app.VerticleManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DeployCommand extends VertxCommand {

  public boolean worker;
  public String name;
  public String main;
  public String path;
  public int instances;

  public DeployCommand(boolean worker, String name, String main, String path, int instances) {
    this.worker = worker;
    this.name = name;
    this.main = main;
    this.path = path;
    this.instances = instances;
  }

  public DeployCommand() {
  }

  public String execute(VerticleManager appMgr) throws Exception {
    String appName = appMgr.deploy(worker, name, main, null, path, instances, null);
    return "Deployment: " + appName;
  }
}
