package io.vertx.core.spi.cli;

import io.vertx.core.json.JsonObject;

public interface CliJsonProcessor {

  String name();

  JsonObject process(String content);

}
