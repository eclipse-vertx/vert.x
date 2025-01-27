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
package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.ClientMultipartFormImpl;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * A form: a container for attributes.
 */
@DataObject
public interface ClientForm {

  /**
   * @return a blank form
   */
  static ClientForm form() {
    ClientMultipartFormImpl form = new ClientMultipartFormImpl(false);
    form.charset(StandardCharsets.UTF_8);
    return form;
  }

  /**
   * @param initial the initial content of the form
   * @return a form populated after the {@code initial} multimap
   */
  static ClientForm form(MultiMap initial) {
    ClientMultipartFormImpl form = new ClientMultipartFormImpl(false);
    for (Map.Entry<String, String> attribute : initial) {
      form.attribute(attribute.getKey(), attribute.getValue());
    }
    form.charset(StandardCharsets.UTF_8);
    return form;
  }

  ClientForm attribute(String name, String value);

  /**
   * Set the {@code charset} to use when encoding the form. The default charset is {@link java.nio.charset.StandardCharsets#UTF_8}.
   *
   * @param charset the charset to use
   * @return a reference to this, so the API can be used fluently
   */
  ClientForm charset(String charset);

  /**
   * Set the {@code charset} to use when encoding the form. The default charset is {@link java.nio.charset.StandardCharsets#UTF_8}.
   *
   * @param charset the charset to use
   * @return a reference to this, so the API can be used fluently
   */
  ClientForm charset(Charset charset);

  /**
   * @return the charset to use when encoding the form
   */
  Charset charset();

}
