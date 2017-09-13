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

package io.vertx.core.http;


import io.vertx.core.AbstractMultiMap;
import io.vertx.core.MultiMap;

/**
 * This multi-map implementation has case insensitive keys, and can be used to hold some HTTP headers
 * prior to making an HTTP request.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class CaseInsensitiveHeaders extends AbstractMultiMap implements MultiMap {
  @Override
  protected int hash(String name) {
    int h = 0;
    for (int i = name.length() - 1; i >= 0; i --) {
      char c = name.charAt(i);
      if (c >= 'A' && c <= 'Z') {
        c += 32;
      }
      h = 31 * h + c;
    }

    if (h > 0) {
      return h;
    } else if (h == Integer.MIN_VALUE) {
      return Integer.MAX_VALUE;
    } else {
      return -h;
    }
  }

  @Override
  protected boolean eq(String name1, String name2) {
    int nameLen = name1.length();
    if (nameLen != name2.length()) {
      return false;
    }

    for (int i = nameLen - 1; i >= 0; i --) {
      char c1 = name1.charAt(i);
      char c2 = name2.charAt(i);
      if (c1 != c2) {
        if (c1 >= 'A' && c1 <= 'Z') {
          c1 += 32;
        }
        if (c2 >= 'A' && c2 <= 'Z') {
          c2 += 32;
        }
        if (c1 != c2) {
          return false;
        }
      }
    }
    return true;
  }
}
