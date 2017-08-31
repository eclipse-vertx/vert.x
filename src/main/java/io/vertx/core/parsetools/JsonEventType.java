/*
 * Copyright (c) 2011-2017 The original author or authors
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
package io.vertx.core.parsetools;

/**
 * The possibles types of {@link JsonEvent} emitted by the {@link JsonParser}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public enum JsonEventType {

  /**
   * Signals the start of a JSON object.
   */
  START_OBJECT,

  /**
   * Signals the end of a JSON object.
   */
  END_OBJECT,

  /**
   * Signals the start of a JSON array.
   */
  START_ARRAY,

  /**
   * Signals the end of a JSON array.
   */
  END_ARRAY,

  /**
   * Signals a JSON value.
   */
  VALUE,

}
