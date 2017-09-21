/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.dns;

import java.util.Objects;

/**
 * Exception which is used to notify the {@link io.vertx.core.AsyncResult}
 * if the DNS query returns a {@link DnsResponseCode} which indicates and error.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class DnsException extends Exception {

  private DnsResponseCode code;

  public DnsException(DnsResponseCode code) {
    Objects.requireNonNull(code, "code");
    this.code = code;
  }

  /**
   * The {@link DnsResponseCode} which caused this {@link io.vertx.core.dns.DnsException} to be created.
   */
  public DnsResponseCode code() {
    return code;
  }
}
