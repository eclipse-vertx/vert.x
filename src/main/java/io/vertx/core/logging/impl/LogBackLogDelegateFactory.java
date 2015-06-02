package io.vertx.core.logging.impl;

/**
 * 
 * @author Patrick Sauts
 *
 */
public class LogBackLogDelegateFactory implements LogDelegateFactory
{
   public LogDelegate createDelegate(final String clazz)
   {
      return new SLF4JLogDelegate(clazz);
   }

}
