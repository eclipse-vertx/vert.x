package org.vertx.java.core.app.cli;

import org.vertx.java.core.app.VerticleManager;
import org.vertx.java.core.json.JsonObject;

import java.net.URL;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DeployCommand extends VertxCommand {

  public boolean worker;
  public String name;
  public String main;
  public String conf;
  public URL[] urls;
  public int instances;

  public DeployCommand(boolean worker, String name, String main, String conf, URL[] urls, int instances) {
    this.worker = worker;
    this.name = name;
    this.conf = conf;
    this.main = main;
    this.urls = urls;
    this.instances = instances;
  }

  public DeployCommand() {
  }

  public String execute(VerticleManager appMgr) throws Exception {
    JsonObject jsonConf;
    if (conf != null) {
      jsonConf = new JsonObject(conf);
    } else {
      jsonConf = null;
    }
    String appName = appMgr.deploy(worker, name, main, jsonConf, urls, instances, null);
    return "Deployment: " + appName;
  }
}
