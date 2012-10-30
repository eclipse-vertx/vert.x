package org.vertx.java.deploy.impl;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;

import org.vertx.java.core.Handler;


public class VerticleManagerProxy implements VerticleManagerMXBean, NotificationBroadcaster {

  private static final String UNDEPLOY_EVENT = "undeploy";

  private final NotificationBroadcasterSupport support = new NotificationBroadcasterSupport();

  private final AtomicLong sequence = new AtomicLong(0L);

  private VerticleManager verticleManager;

  public VerticleManagerProxy(VerticleManager verticleManager) {
    this.verticleManager = verticleManager;
  }

  @Override
  public String getObjectName() {
    return String.format("org.vertx:type=VerticleManager");
  }

  @Override
  public Map<String, Integer> getDeployments() {
    return verticleManager.listInstances();
  }

  @Override
  public void undeploy(final String name) {
    verticleManager.undeploy(name, new Handler<Void>() {
      public void handle(Void event) {
        String msg = String.format("Undeployed %s", name);
        long timestamp = System.currentTimeMillis();
        Notification notification = new Notification(UNDEPLOY_EVENT, VerticleManagerProxy.this, sequence.incrementAndGet(), timestamp, msg);
        support.sendNotification(notification);
      }
    });
  }

  @Override
  public void addNotificationListener(NotificationListener listener,
      NotificationFilter filter, Object handback)
      throws IllegalArgumentException {
    support.addNotificationListener(listener, filter, handback);
  }

  @Override
  public void removeNotificationListener(NotificationListener listener)
      throws ListenerNotFoundException {
    support.removeNotificationListener(listener);
  }

  @Override
  public MBeanNotificationInfo[] getNotificationInfo() {
    return support.getNotificationInfo();
  }

}
