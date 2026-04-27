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
package io.vertx.core.json.jackson.v3;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;
import io.vertx.core.buffer.Buffer;

import static io.vertx.core.json.impl.JsonUtil.BASE64_ENCODER;

class BufferSerializer extends StdSerializer<Buffer> {

  BufferSerializer() {
    super(Buffer.class);
  }

  @Override
  public void serialize(Buffer value, JsonGenerator jgen, SerializationContext provider) {
    jgen.writeString(BASE64_ENCODER.encodeToString(value.getBytes()));
  }
}
