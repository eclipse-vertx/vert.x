/*
 * Copyright (c) 2013 The Netty Project
 * ------------------------------------
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
package io.vertx.core.dns.impl.netty;

/**
 * Represents the possible response codes a server may send after receiving a
 * query. A response code of 0 indicates no error.
 */
public enum DnsResponseCode {

  /**
   * ID 0, no error
   */
  NOERROR(0, "no error"),

  /**
   * ID 1, format error
   */
  FORMERROR(1, "format error"),

  /**
   * ID 2, server failure
   */
  SERVFAIL(2, "server failure"),

  /**
   * ID 3, name error
   */
  NXDOMAIN(3, "name error"),

  /**
   * ID 4, not implemented
   */
  NOTIMPL(4, "not implemented"),

  /**
   * ID 5, operation refused
   */
  REFUSED(5, "operation refused"),

  /**
   * ID 6, domain name should not exist
   */
  YXDOMAIN(6, "domain name should not exist"),

  /**
   * ID 7, resource record set should not exist
   */
  YXRRSET(7, "resource record set should not exist"),

  /**
   * ID 8, rrset does not exist
   */
  NXRRSET(8, "rrset does not exist"),

  /**
   * ID 9, not authoritative for zone
   */
  NOTAUTH(9, "not authoritative for zone"),

  /**
   * ID 10, name not in zone
   */
  NOTZONE(10, "name not in zone"),

  /**
   * ID 11, bad extension mechanism for version
   */
  BADVERS(11, "bad extension mechanism for version"),

  /**
   * ID 12, bad signature
   */
  BADSIG(12, "bad signature"),

  /**
   * ID 13, bad key
   */
  BADKEY(13, "bad key"),

  /**
   * ID 14, bad timestamp
   */
  BADTIME(14, "bad timestamp");

  private final int errorCode;
  private final String message;

  /**
   * Returns the {@link io.vertx.core.dns.impl.netty.DnsResponseCode} that corresponds with the given
   * {@code responseCode}.
   *
   * @param responseCode the error code's id
   * @return corresponding {@link io.vertx.core.dns.impl.netty.DnsResponseCode}
   */
  public static DnsResponseCode valueOf(int responseCode) {
    DnsResponseCode[] errors = DnsResponseCode.values();
    for (DnsResponseCode e : errors) {
      if (e.errorCode == responseCode) {
        return e;
      }
    }
    return null;
  }

  private DnsResponseCode(int errorCode, String message) {
    this.errorCode = errorCode;
    this.message = message;
  }

  /**
   * Returns the error code for this {@link io.vertx.core.dns.impl.netty.DnsResponseCode}.
   */
  public int code() {
    return errorCode;
  }

  /**
   * Returns a formatted error message for this {@link io.vertx.core.dns.impl.netty.DnsResponseCode}.
   */
  @Override
  public String toString() {
    return name() + ": type " + errorCode + ", " + message;
  }

}
