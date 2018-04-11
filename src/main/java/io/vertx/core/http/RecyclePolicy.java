package io.vertx.core.http;

/**
 * Defines the policy in which connections will be reused in the pool
 *
 */
public enum RecyclePolicy {
  FIFO, // Maintains the available connection as a queue, keep connections for longer
  LIFO // Maintains the available connection as a stack, allowing more connections to expire
}
