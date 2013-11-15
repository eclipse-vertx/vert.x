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


/**
 * Offers methods that can be used to configure a service that provide network services.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface NetworkSupport<T> {

  /**
   * Set the send buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  T setSendBufferSize(int size);

  /**
   * Set the receive buffer size for connections created by this instance to {@code size} in bytes.
   * @return a reference to this so multiple method calls can be chained together
   */
  T setReceiveBufferSize(int size);

  /**
   * Set the reuseAddress setting for connections created by this instance to {@code reuse}.
   * @return a reference to this so multiple method calls can be chained together
   */
  T setReuseAddress(boolean reuse);

  /**
   * Set the trafficClass setting for connections created by this instance to {@code trafficClass}.
   * @return a reference to this so multiple method calls can be chained together
   */
  T setTrafficClass(int trafficClass);

  /**
   * @return The send buffer size
   */
  int getSendBufferSize();

  /**
   * @return The receive buffer size
   */
  int getReceiveBufferSize();

  /**
   *
   * @return The value of reuse address
   */
  boolean isReuseAddress();

  /**
   *
   * @return the value of traffic class
   */
  int getTrafficClass();

}
