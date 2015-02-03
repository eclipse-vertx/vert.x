/*
 * Copyright (c) 2011-2014 The original author or authors
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
package io.vertx.test.core;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
enum Trust {
  NONE,
  JKS,       // Self signed CA
  PKCS12,    // Self signed CA
  PEM,       // Self signed CA
  JKS_CA,    // Signed by CA
  PKCS12_CA, // Self by CA
  PEM_CA     // Signed by CA
}

