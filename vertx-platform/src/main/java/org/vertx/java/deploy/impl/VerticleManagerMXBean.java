package org.vertx.java.deploy.impl;

import java.util.Map;

public interface VerticleManagerMXBean {

  String getObjectName();

  Map<String, Integer> getDeployments();

  void undeploy(String name);

}
