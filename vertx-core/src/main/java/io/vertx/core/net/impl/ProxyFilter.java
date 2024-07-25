/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl;

import io.vertx.core.net.SocketAddress;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Contains the logic for TCP client proxy filtering.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface ProxyFilter extends Predicate<SocketAddress> {

  ProxyFilter DEFAULT_PROXY_FILTER = so -> !so.isDomainSocket();

  static ProxyFilter nonProxyHosts(List<String> nonProxyHosts) {
    List<Object> filterElts = nonProxyHosts.stream().map(nonProxyHost -> {
      if (nonProxyHost.contains("*")) {
        String pattern = nonProxyHost
          .replaceAll("\\.", "\\.")
          .replaceAll("\\*", ".*");
        return Pattern.compile(pattern);
      } else {
        return nonProxyHost;
      }
    }).collect(Collectors.toList());
    return so -> {
      if (so.isDomainSocket()) {
        return false;
      } else {
        String host = so.host();
        for (Object filterElt : filterElts) {
          if (filterElt instanceof Pattern) {
            if (((Pattern) filterElt).matcher(host).matches()) {
              return false;
            }
          } else if (filterElt.equals(host)) {
            return false;
          }
        }
      }
      return true;
    };
  }
}
