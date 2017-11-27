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

package io.vertx.core.http;

import io.vertx.codegen.annotations.VertxGen;

/**
 * Represents an HTTP method
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public enum HttpMethod {
  // http://www.iana.org/assignments/http-methods/http-methods.xhtml
  ACL, BIND, CHECKIN, CHECKOUT, CONNECT, COPY, DELETE, GET, HEAD,
  LABEL, LINK, LOCK, MERGE, MKACTIVITY, MKCALENDAR, MKCOL, MKREDIRECTREF,
  MKWORKSPACE, MOVE, OPTIONS, ORDERPATCH, PATCH, POST, PRI, PROPFIND, PROPPATCH,
  PUT, REBIND, REPORT, SEARCH, TRACE, UNBIND, UNCHECKOUT, UNLINK, UNLOCK, UPDATE, UPDATEREDIRECTREF,
  // https://stackoverflow.com/questions/25857508/what-is-the-http-method-purge
  PURGE
}
