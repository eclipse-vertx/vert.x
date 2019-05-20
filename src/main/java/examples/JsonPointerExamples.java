package examples;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;

import java.net.URI;

public class JsonPointerExamples {

  public void example1Pointers() {
    // Build a pointer from a string
    JsonPointer pointer1 = JsonPointer.from("/hello/world");
    // Build a pointer manually
    JsonPointer pointer2 = JsonPointer.create()
      .append("hello")
      .append("world");
  }

  public void example2Pointers(JsonPointer objectPointer, JsonObject jsonObject, JsonPointer arrayPointer, JsonArray jsonArray) {
    // Query a JsonObject
    Object result1 = objectPointer.queryJson(jsonObject);
    // Query a JsonArray
    Object result2 = arrayPointer.queryJson(jsonArray);
    // Write starting from a JsonObject
    objectPointer.writeJson(jsonObject, "new element");
    // Write starting from a JsonObject
    arrayPointer.writeJson(jsonArray, "new element");
  }

}
