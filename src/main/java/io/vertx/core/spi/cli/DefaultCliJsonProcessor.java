package io.vertx.core.spi.cli;

import io.vertx.core.json.JsonObject;

public class DefaultCliJsonProcessor implements CliJsonProcessor {

  @Override
  public String name() {
    return "json";
  }

  @Override
  public JsonObject process(String content) {
    return new JsonObject(content);
  }
}
