package org.vertx.java.deploy.impl;

public class DeploymentProxy implements DeploymentMXBean {

  private Deployment deployment;

  public DeploymentProxy(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  public String getName() {
    return deployment.name;
  }

  @Override
  public int getInstances() {
    return deployment.instances;
  }

  @Override
  public boolean getAutoRedeploy() {
    return deployment.autoRedeploy;
  }

  @Override
  public String getObjectName() {
    return String.format("org.vertx:type=Deployment,name=%s[%s]", deployment.name, deployment.instances);
  }

}
