package org.vertx.java.core;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface TCPSupport<T> extends NetworkSupport {
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
