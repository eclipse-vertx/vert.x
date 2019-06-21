package io.vertx.core.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class JacksonMapper extends JsonCodecMapper {

  private final com.fasterxml.jackson.databind.ObjectMapper mapper;

  JacksonMapper() {
    this.mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    com.fasterxml.jackson.databind.module.SimpleModule module = new com.fasterxml.jackson.databind.module.SimpleModule();

    // custom types
    module.addSerializer(JsonObject.class, new com.fasterxml.jackson.databind.JsonSerializer<JsonObject>() {
      @Override
      public void serialize(JsonObject value, JsonGenerator jgen, com.fasterxml.jackson.databind.SerializerProvider serializerProvider) throws IOException {
        jgen.writeObject(value.getMap());
      }
    });
    module.addSerializer(JsonArray.class, new com.fasterxml.jackson.databind.JsonSerializer<JsonArray>() {
      @Override
      public void serialize(JsonArray value, JsonGenerator jgen, com.fasterxml.jackson.databind.SerializerProvider serializerProvider) throws IOException {
        jgen.writeObject(value.getList());
      }
    });
    // he have 2 extensions: RFC-7493
    module.addSerializer(Instant.class, new com.fasterxml.jackson.databind.JsonSerializer<Instant>() {
      @Override
      public void serialize(Instant value, JsonGenerator jgen, com.fasterxml.jackson.databind.SerializerProvider serializerProvider) throws IOException {
        jgen.writeString(ISO_INSTANT.format(value));
      }
    });
    module.addDeserializer(Instant.class, new com.fasterxml.jackson.databind.JsonDeserializer<Instant>() {
      @Override
      public Instant deserialize(JsonParser p, com.fasterxml.jackson.databind.DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        String text = p.getText();
        try {
          return Instant.from(ISO_INSTANT.parse(text));
        } catch (DateTimeException e) {
          throw new com.fasterxml.jackson.databind.exc.InvalidFormatException(p, "Expected an ISO 8601 formatted date time", text, Instant.class);
        }
      }
    });
    module.addSerializer(byte[].class, new com.fasterxml.jackson.databind.JsonSerializer<byte[]>() {
      @Override
      public void serialize(byte[] value, JsonGenerator jgen, com.fasterxml.jackson.databind.SerializerProvider serializerProvider) throws IOException {
        jgen.writeString(Base64.getEncoder().encodeToString(value));
      }
    });
    module.addDeserializer(byte[].class, new com.fasterxml.jackson.databind.JsonDeserializer<byte[]>() {
      @Override
      public byte[] deserialize(JsonParser p, com.fasterxml.jackson.databind.DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        String text = p.getText();
        try {
          return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
          throw new com.fasterxml.jackson.databind.exc.InvalidFormatException(p, "Expected a base64 encoded byte array", text, Instant.class);
        }
      }
    });

    mapper.registerModule(module);
  }

  @Override
  public Object encode(Object value) throws EncodeException, IllegalStateException {
    try {
      return super.encode(value);
    } catch (IllegalStateException e) {
      try {
        return mapper.convertValue(value, JsonObject.class);
      } catch (Exception e1) {
        throw new EncodeException(e1);
      }
    }
  }

  @Override
  public <T> T decode(Object json, Class<T> c) throws DecodeException, IllegalStateException {
    try {
      return super.decode(json, c);
    } catch (IllegalStateException e) {
      try {
        return mapper.convertValue(json, c);
      } catch (Exception e1) {
        throw new DecodeException(e1);
      }
    }
  }
}
