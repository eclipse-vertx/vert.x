/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.json.jackson;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.OffsetDateTime;

class OffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {
  @Override
  public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    String text = p.getText();
    try {
      return OffsetDateTime.from(ISO_OFFSET_DATE_TIME.parse(text));
    } catch (DateTimeException e) {
      throw new InvalidFormatException(
          p, "Expected an ISO 8601 formatted datetime", text, OffsetDateTime.class);
    }
  }
}
