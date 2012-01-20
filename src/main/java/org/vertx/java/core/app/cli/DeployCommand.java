package org.vertx.java.core.app.cli;

import org.vertx.java.core.app.AppManager;
import org.vertx.java.core.app.AppType;

import java.net.URL;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DeployCommand extends VertxCommand {

  public boolean worker;
  public AppType type;
  public String name;
  public String main;
  public URL[] urls;
  public int instances;

  public DeployCommand(boolean worker, AppType type, String name, String main, URL[] urls, int instances) {
    this.worker = worker;
    this.type = type;
    this.name = name;
    this.main = main;
    this.urls = urls;
    this.instances = instances;
  }

  public DeployCommand() {
  }

  public void execute(AppManager appMgr) throws Exception {
    appMgr.deploy(worker, type, name, main, urls, instances, null);
  }
}
