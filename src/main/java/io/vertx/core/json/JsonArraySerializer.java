package io.vertx.core.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

class JsonArraySerializer extends JsonSerializer<JsonArray> {
  @Override
  public void serialize(JsonArray value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
    jgen.writeObject(value.getList());
  }
}
