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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * List of HTTP methods, this class is used to avoid exposing a modifiable field on {@link HttpMethod} and the
 * {@link io.vertx.core.http.impl.HttpMethodImpl} implementation cannot statically define this field {@link HttpMethod}
 * creates static {@link io.vertx.core.http.impl.HttpMethodImpl} instances.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class HttpMethods {

  static final List<HttpMethod> ALL = Collections.unmodifiableList(Arrays.asList(
    HttpMethod.OPTIONS,
    HttpMethod.GET,
    HttpMethod.HEAD,
    HttpMethod.POST,
    HttpMethod.PUT,
    HttpMethod.DELETE,
    HttpMethod.TRACE,
    HttpMethod.CONNECT,
    HttpMethod.PATCH,
    HttpMethod.PROPFIND,
    HttpMethod.PROPPATCH,
    HttpMethod.MKCOL,
    HttpMethod.COPY,
    HttpMethod.MOVE,
    HttpMethod.LOCK,
    HttpMethod.UNLOCK,
    HttpMethod.MKCALENDAR,
    HttpMethod.VERSION_CONTROL,
    HttpMethod.REPORT,
    HttpMethod.CHECKIN,
    HttpMethod.CHECKOUT,
    HttpMethod.UNCHECKOUT,
    HttpMethod.MKWORKSPACE,
    HttpMethod.UPDATE,
    HttpMethod.LABEL,
    HttpMethod.MERGE,
    HttpMethod.BASELINE_CONTROL,
    HttpMethod.MKACTIVITY,
    HttpMethod.ORDERPATCH,
    HttpMethod.ACL,
    HttpMethod.SEARCH
  ));
}
