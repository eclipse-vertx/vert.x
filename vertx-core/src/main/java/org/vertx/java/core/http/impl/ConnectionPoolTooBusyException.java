/*
 * Copyright 2011-2012 the original author or authors.
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

package org.vertx.java.core.http.impl;

/**
 * Exception thrown by the ConnectionPool class
 *
 * @author <a href="mailto:wangjunbo924@gmail.com">Junbo Wang</a>
 */
public class ConnectionPoolTooBusyException extends RuntimeException {

  /**
   * Construct a {@code ConnectionPoolTooBusyException} with a message as specified by {@code msg}
   */
  public ConnectionPoolTooBusyException(String msg) {
    super(msg);
  }
}
