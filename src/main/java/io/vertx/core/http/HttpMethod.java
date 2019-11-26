/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
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
