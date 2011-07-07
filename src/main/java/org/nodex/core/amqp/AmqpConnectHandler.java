package org.nodex.core.amqp;

/**
 * User: tfox
 * Date: 07/07/11
 * Time: 11:29
 */
public abstract class AmqpConnectHandler {
  public abstract void onConnect(AmqpConnection connection);
}
