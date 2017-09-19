/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 and the Apache License, Version 2.0
 * which accompanies this distribution. The Eclipse Public License 2.0 is
 * available at http://www.eclipse.org/legal/epl-2.0.html, and the Apache
 * License, Version 2.0 is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
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
