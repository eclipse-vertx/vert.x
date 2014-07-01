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
package io.vertx.core.http.impl;


/**
 * Simple parser which helps to parse out parts of uris without to much validation.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class UriParser {

  private UriParser() {
  }

  /**
   * Extract the path out of the uri.
   */
  static String path(String uri) {
    int i;
    if (uri.charAt(0) == '/') {
      i = 0;
    } else {
      i = uri.indexOf("://");
      if (i == -1) {
        i = 0;
      } else {
        i = uri.indexOf('/', i + 3);
        if (i == -1) {
          // contains no /
          return "/";
        }
      }
    }

    int queryStart = uri.indexOf('?', i);
    if (queryStart == -1) {
      queryStart = uri.length();
    }
    return uri.substring(i, queryStart);
  }

  /**
   * Extract the query out of a uri or returns {@code null} if no query was found.
   */
  static String query(String uri) {
    int i = uri.indexOf('?');
    if (i == -1) {
      return null;
    } else {
      return uri.substring(i + 1 , uri.length());
    }
  }

}
