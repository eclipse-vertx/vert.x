package io.vertx.core.spi;

import io.vertx.core.impl.ServiceHelper;
import io.vertx.core.json.jackson.JacksonFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

class Utils {

  static io.vertx.core.spi.JsonFactory load() {
    List<JsonFactory> factories = new ArrayList<>(ServiceHelper.loadFactories(io.vertx.core.spi.JsonFactory.class));
    factories.sort(Comparator.comparingInt(JsonFactory::order));
    if (factories.size() > 0) {
      return factories.iterator().next();
    } else {
      return JacksonFactory.INSTANCE;
    }
  }
}
