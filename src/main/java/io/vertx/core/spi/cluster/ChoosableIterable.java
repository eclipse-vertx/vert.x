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

package io.vertx.core.spi.cluster;

/**
 *
 * An extension of Iterable which allows keeps track of an iterator internally to allow the next element to be chosen
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public interface ChoosableIterable<T> extends Iterable<T>{

  /**
   * Is it empty?
   */
  boolean isEmpty();

  /**
   * Return the next element T in a round robin fashion. The implementation should internally maintain some state
   * which allows the next element to be returned
   * @return The next element
   */
  T choose();
}
