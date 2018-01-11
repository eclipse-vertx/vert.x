/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
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
