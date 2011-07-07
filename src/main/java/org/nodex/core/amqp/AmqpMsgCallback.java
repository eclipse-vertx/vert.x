package org.nodex.core.amqp;

/**
 * User: tim
 * Date: 06/07/11
 * Time: 06:40
 */
public abstract class AmqpMsgCallback {
  public abstract void onMessage(AmqpProps props, byte[] body);
}
