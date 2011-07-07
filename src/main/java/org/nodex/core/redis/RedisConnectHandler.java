package org.nodex.core.redis;

/**
 * User: tfox
 * Date: 07/07/11
 * Time: 11:46
 */
public abstract class RedisConnectHandler {
  public abstract void onConnect(RedisConnection connection);
}
