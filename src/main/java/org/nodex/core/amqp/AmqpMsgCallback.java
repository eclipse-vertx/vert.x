package org.nodex.core.amqp;

import com.rabbitmq.client.AMQP;

/**
 * User: tim
 * Date: 06/07/11
 * Time: 06:40
 */
public abstract class AmqpMsgCallback {
  public abstract void onMessage(AMQP.BasicProperties props, byte[] body);
}
