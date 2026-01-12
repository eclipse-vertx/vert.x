/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.netty.handler.codec.http2.Http2CodecUtil;
import io.vertx.core.impl.Arguments;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.LongConsumer;

/**
 * An HTTP setting.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpSetting<T> {

  @FunctionalInterface
  interface From<T> {
    long convert(T val);
  }

  @FunctionalInterface
  interface To<T> {
    T convert(long val);
  }

  final int identifier;
  final String name;
  final long defaultValue;
  final From<T> to;
  final To<T> from;
  final LongConsumer validator;
  final Set<HttpVersion> versions;

  HttpSetting(int identifier,
                      String name,
                      T defaultValue,
                      From<T> to,
                      To<T> from,
                      LongConsumer validator,
                      Set<HttpVersion> versions) {
    this.identifier = identifier;
    this.name = name;
    this.defaultValue = to.convert(defaultValue);
    this.to = to;
    this.from = from;
    this.validator = validator;
    this.versions = versions;
  }

  /**
   * @return the setting identifier
   */
  public int identifier() {
    return identifier;
  }

  /**
   * @return the name of this setting
   */
  public String name() {
    return name;
  }

}
