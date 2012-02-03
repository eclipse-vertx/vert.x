package org.vertx.java.core.app.cli;

import org.vertx.java.core.app.VerticleManager;
import org.vertx.java.core.app.VerticleType;

import java.net.URL;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DeployCommand extends VertxCommand {

  public boolean worker;
  public VerticleType type;
  public String name;
  public String main;
  public URL[] urls;
  public int instances;

  public DeployCommand(boolean worker, VerticleType type, String name, String main, URL[] urls, int instances) {
    this.worker = worker;
    this.type = type;
    this.name = name;
    this.main = main;
    this.urls = urls;
    this.instances = instances;
  }

  public DeployCommand() {
  }

  public String execute(VerticleManager appMgr) throws Exception {
    appMgr.deploy(worker, type, name, main, urls, instances, null);
    return null;
  }
}
