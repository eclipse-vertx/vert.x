package io.vertx.core.dns.dnsrecord;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */

@DataObject
@JsonGen(publicConverter = false)
public class DohResourceRecord {
  private JsonObject json;

  public DohResourceRecord() {
  }

  public DohResourceRecord(JsonObject json) {
    DohResourceRecordConverter.fromJson(json, DohResourceRecord.this);
    this.json = json.copy();
  }
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    DohResourceRecordConverter.toJson(this, json);
    return json;
  }
  private String name;
  private int type;
  private int ttl;
  private String data;

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

  public int getTtl() {
    return ttl;
  }

  public void setTtl(int ttl) {
    this.ttl = ttl;
  }

  public String getData() {
    return data;
  }

  public void setData(String data) {
    this.data = data;
  }
}
