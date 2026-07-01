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
package io.vertx.test.proxy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.METHOD})
public @interface WithProxy {

  String username() default "";

  ProxyKind kind();

  /**
   * Only meaningful for {@link ProxyKind#HTTPS}: when {@code true} the proxy requires and validates a
   * client certificate (mutual TLS on leg 1). This is a server-side knob only; the client presenting a
   * certificate remains the responsibility of the test's own {@code ProxyOptions.sslOptions}.
   */
  boolean requireSslClientAuth() default false;

  String[] localhosts() default {};

}
