package org.nodex.core.redis;

/**
 * User: tfox
 * Date: 07/07/11
 * Time: 11:46
 */
public interface RedisConnectHandler {
  void onConnect(RedisConnection connection);
}
