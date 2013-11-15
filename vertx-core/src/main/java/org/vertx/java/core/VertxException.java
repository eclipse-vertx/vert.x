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
 *
 * Vert.x only throws instances of VertxException which is a RuntimeException.<p>
 * Vert.x hates Java checked exceptions and doesn't want to pollute it's API with them.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public class VertxException extends RuntimeException {

  public VertxException(String message) {
    super(message);
  }

  public VertxException(String message, Throwable cause) {
    super(message, cause);
  }

  public VertxException(Throwable cause) {
    super(cause);
  }
}
