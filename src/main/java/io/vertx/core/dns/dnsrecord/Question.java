package io.vertx.core.dns.dnsrecord;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */

@DataObject
@JsonGen(publicConverter = false)
public class Question {
  private JsonObject json;

  public Question() {
  }

  public Question(JsonObject json) {
    QuestionConverter.fromJson(json, this);
    this.json = json.copy();
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    QuestionConverter.toJson(this, json);
    return json;
  }

  private String name;
  private int type;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getType() {
    return type;
  }

  public void setType(int type) {
    this.type = type;
  }
}
