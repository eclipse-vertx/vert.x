package org.vertx.java.core.spi.cluster;

/**
 *  @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface ExtendedClusterManager extends ClusterManager {

  void beforeLeave();
}
