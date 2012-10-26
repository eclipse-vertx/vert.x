package org.vertx.java.deploy.impl;

public interface DeploymentMXBean {

  String getName();

  int getInstances();

  boolean getAutoRedeploy();

  String getObjectName();

}
