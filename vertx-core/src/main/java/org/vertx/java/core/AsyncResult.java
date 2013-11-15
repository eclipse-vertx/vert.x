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
 * Represents a result that may not have occurred yet.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface AsyncResult<T> {

  /**
   * The result of the operation. This will be null if the operation failed.
   */
  T result();

  /**
   * An exception describing failure. This will be null if the operation succeeded.
   */
  Throwable cause();

  /**
   * Did it succeed?
   */
  boolean succeeded();

  /**
   * Did it fail?
   */
  boolean failed();

}
