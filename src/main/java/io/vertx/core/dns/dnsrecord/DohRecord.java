package io.vertx.core.dns.dnsrecord;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */

@DataObject
@JsonGen(publicConverter = false)
public class DohRecord {
  private JsonObject json;

  public DohRecord() {
  }

  public DohRecord(JsonObject json) {
    DohRecordConverter.fromJson(json, this);
    this.json = json.copy();
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    DohRecordConverter.toJson(this, json);
    return json;
  }

  private int status;

  private boolean tc;

  private boolean rd;

  private boolean ra;

  private boolean ad;

  private boolean cd;

  private List<Question> question;

  private List<DohResourceRecord> answer;
  private List<DohResourceRecord> authority;
  private List<DohResourceRecord> additional;

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public boolean isTc() {
    return tc;
  }

  public void setTc(boolean tc) {
    this.tc = tc;
  }

  public boolean isRd() {
    return rd;
  }

  public void setRd(boolean rd) {
    this.rd = rd;
  }

  public boolean isRa() {
    return ra;
  }

  public void setRa(boolean ra) {
    this.ra = ra;
  }

  public boolean isAd() {
    return ad;
  }

  public void setAd(boolean ad) {
    this.ad = ad;
  }

  public boolean isCd() {
    return cd;
  }

  public void setCd(boolean cd) {
    this.cd = cd;
  }

  public List<Question> getQuestion() {
    return question;
  }

  public void setQuestion(List<Question> question) {
    this.question = question;
  }

  public List<DohResourceRecord> getAnswer() {
    return answer;
  }

  public void setAnswer(List<DohResourceRecord> answer) {
    this.answer = answer;
  }

  public List<DohResourceRecord> getAuthority() {
    return authority;
  }

  public void setAuthority(List<DohResourceRecord> authority) {
    this.authority = authority;
  }

  public List<DohResourceRecord> getAdditional() {
    return additional;
  }

  public void setAdditional(List<DohResourceRecord> additional) {
    this.additional = additional;
  }
}


