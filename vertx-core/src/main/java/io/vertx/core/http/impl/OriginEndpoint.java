/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.vertx.core.http.HttpProtocol;
import io.vertx.core.spi.endpoint.EndpointBuilder;

import java.util.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class OriginEndpoint<L> {

  // Should we move these to the address resolver thing ? most likely ...
  public static final String ALPN_KEY = "alpn";
  public static final String AUTHORITY_KEY = "auth";
  public static final String ADDR_RESOLVED = "resolved";

  final Origin origin;
  final OriginServer primary;
  final List<OriginServer> primaries;
  final EndpointBuilder<L, OriginServer> builder;
  final L list;
  List<OriginAlternative> alternatives;
  private volatile boolean valid;

  OriginEndpoint(Origin origin, OriginServer primary, EndpointBuilder<L, OriginServer> builder, List<OriginAlternative> alternatives) {
    this(origin, List.of(primary), builder, alternatives);
  }

  OriginEndpoint(Origin origin, List<OriginServer> primaries, EndpointBuilder<L, OriginServer> builder, List<OriginAlternative> alternatives) {

    L list = refresh(builder, primaries, alternatives);

    this.primary = primaries.get(0);
    this.primaries = primaries;
    this.origin = origin;
    this.builder = builder;
    this.alternatives = alternatives;
    this.list = list;
    this.valid = true;
  }

  private L refresh(EndpointBuilder<L, OriginServer> builder, List<OriginServer> primaries, List<OriginAlternative> alternatives) {
    for (OriginServer primary : primaries) {
      builder = builder.addServer(primary);
    }
    for (OriginAlternative entry : alternatives) {
      builder.addServer(new OriginServer(entry.protocol, entry.authority, entry.socketAddress));
    }
    return builder.build();
  }

  boolean validate() {
    if (valid) {
      long now = System.currentTimeMillis();
      for (OriginAlternative alternative : alternatives) {
        if (now >= alternative.expirationTimestamp) {
          valid = false;
          return false;
        }
      }
    }
    return valid;
  }

  void clearAlternatives() {
    alternatives = Collections.emptyList();
    valid = false;
  }

  void updateAlternatives(AltSvc.ListOfValue altSvc) {
    long now = System.currentTimeMillis();
    List<OriginAlternative> list = new ArrayList<>();
    for (AltSvc.Value altSvcValue : altSvc) {
      HttpProtocol protocol = HttpProtocol.fromId(altSvcValue.protocolId());
      if (protocol != null) {
        long maxAge;
        String ma = altSvcValue.parameters().get("ma");
        if (ma != null) {
          try {
            maxAge = Long.parseLong(ma);
          } catch (NumberFormatException ex) {
            continue;
          }
        } else {
          maxAge = 24 * 3600;
        }
        OriginAlternative alternative = new OriginAlternative(protocol, altSvcValue.altAuthority(), null, now + maxAge * 1000);
        list.add(alternative);
      }
    }
    alternatives = list;
    valid = false;
  }
}
