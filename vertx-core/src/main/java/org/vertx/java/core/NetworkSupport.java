/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
