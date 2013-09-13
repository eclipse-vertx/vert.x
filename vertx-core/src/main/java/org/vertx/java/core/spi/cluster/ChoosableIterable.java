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
 */

package org.vertx.java.core.spi.cluster;

import java.util.Iterator;

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
