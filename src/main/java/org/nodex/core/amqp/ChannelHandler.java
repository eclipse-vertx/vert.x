package org.nodex.core.amqp;

/**
 * User: tfox
 * Date: 07/07/11
 * Time: 11:30
 */
public abstract class ChannelHandler {
  public abstract void onCreate(Channel channel);
}
