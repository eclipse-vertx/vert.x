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

package io.vertx.core.net;

import io.vertx.core.buffer.Buffer;

import java.util.List;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface ClientOptions<T extends ClientOptions> extends TCPOptions<T> {

  boolean isTrustAll();

  T setTrustAll(boolean trustAll);

  List<String> getCrlPaths();

  T addCrlPath(String crlPath);

  List<Buffer> getCrlValues();

  T addCrlValue(Buffer crlValue);

  int getConnectTimeout();

  T setConnectTimeout(int connectTimeout);

}
