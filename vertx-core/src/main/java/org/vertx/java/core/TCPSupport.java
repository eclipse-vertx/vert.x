/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.core;

public interface TCPSupport<T> extends NetworkSupport<T> {
  /**
   * If {@code tcpNoDelay} is set to {@code true} then <a href="http://en.wikipedia.org/wiki/Nagle's_algorithm">Nagle's algorithm</a>
   * will turned <b>off</b> for the TCP connections created by this instance.
   * @return a reference to this so multiple method calls can be chained together
   */
  T setTCPNoDelay(boolean tcpNoDelay);

  /**
   * Set the TCP keepAlive setting for connections created by this instance to {@code keepAlive}.
   * @return a reference to this so multiple method calls can be chained together
   */
  T setTCPKeepAlive(boolean keepAlive);


  /**
   * Set the TCP soLinger setting for connections created by this instance to {@code linger}.
   * Using a negative value will disable soLinger.
   * @return a reference to this so multiple method calls can be chained together
   *
   */
  T setSoLinger(int linger);

  /**
   * Set if vertx should use pooled buffers for performance reasons. Doing so will give the best throughput but
   * may need a bit higher memory footprint.
   * @return a reference to this so multiple method calls can be chained together
   */
  T setUsePooledBuffers(boolean pooledBuffers);

  /**
   * @return true if Nagle's algorithm is disabled.
   */
  boolean isTCPNoDelay();
  /**
   *
   * @return true if TCP keep alive is enabled
   */
  boolean isTCPKeepAlive();

  /**
   *
   * @return the value of TCP so linger
   */
  int getSoLinger();

  /**
   * @return {@code true} if pooled buffers are used
   */
  boolean isUsePooledBuffers();
}
