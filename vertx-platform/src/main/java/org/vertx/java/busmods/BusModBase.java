/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.busmods;

import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

/**
 * Base helper class for Java busmods
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BusModBase extends Verticle {

  protected EventBus eb;
  protected JsonObject config;
  protected Logger logger;


  /**
   * Start the busmod
   */
  public void start() {
    eb = vertx.eventBus();
    config = container.getConfig();
    logger = container.getLogger();
  }

  protected void sendOK(Message<JsonObject> message) {
    sendOK(message, null);
  }

  protected void sendStatus(String status, Message<JsonObject> message) {
    sendStatus(status, message, null);
  }

  protected void sendStatus(String status, Message<JsonObject> message, JsonObject json) {
    if (json == null) {
      json = new JsonObject();
    }
    json.putString("status", status);
    message.reply(json);
  }

  protected void sendOK(Message<JsonObject> message, JsonObject json) {
    sendStatus("ok", message, json);
  }

  protected void sendError(Message<JsonObject> message, String error) {
    sendError(message, error, null);
  }

  protected void sendError(Message<JsonObject> message, String error, Exception e) {
    logger.error(error, e);
    JsonObject json = new JsonObject().putString("status", "error").putString("message", error);
    message.reply(json);
  }

  protected String getMandatoryString(String field, Message<JsonObject> message) {
    String val = message.body.getString(field);
    if (val == null) {
      sendError(message, field + " must be specified");
    }
    return val;
  }

  protected JsonObject getMandatoryObject(String field, Message<JsonObject> message) {
    JsonObject val = message.body.getObject(field);
    if (val == null) {
      sendError(message, field + " must be specified");
    }
    return val;
  }

  protected boolean getOptionalBooleanConfig(String fieldName, boolean defaultValue) {
    Boolean b = config.getBoolean(fieldName);
    return b == null ? defaultValue : b.booleanValue();
  }

  protected String getOptionalStringConfig(String fieldName, String defaultValue) {
    String b = config.getString(fieldName);
    return b == null ? defaultValue : b;
  }

  protected int getOptionalIntConfig(String fieldName, int defaultValue) {
    Integer b = (Integer)config.getNumber(fieldName);
    return b == null ? defaultValue : b.intValue();
  }

  protected long getOptionalLongConfig(String fieldName, long defaultValue) {
    Long l = (Long)config.getNumber(fieldName);
    return l == null ? defaultValue : l.longValue();
  }

  protected JsonObject getOptionalObjectConfig(String fieldName, JsonObject defaultValue) {
    JsonObject o = config.getObject(fieldName);
    return o == null ? defaultValue : o;
  }

  protected JsonArray getOptionalArrayConfig(String fieldName, JsonArray defaultValue) {
    JsonArray a = config.getArray(fieldName);
    return a == null ? defaultValue : a;
  }

  protected boolean getMandatoryBooleanConfig(String fieldName) {
    Boolean b = config.getBoolean(fieldName);
    if (b == null) {
      throw new IllegalArgumentException(fieldName + " must be specified in config for busmod");
    }
    return b;
  }

  protected String getMandatoryStringConfig(String fieldName) {
    String s = config.getString(fieldName);
    if (s == null) {
      throw new IllegalArgumentException(fieldName + " must be specified in config for busmod");
    }
    return s;
  }

  protected int getMandatoryIntConfig(String fieldName) {
    Integer i = (Integer)config.getNumber(fieldName);
    if (i == null) {
      throw new IllegalArgumentException(fieldName + " must be specified in config for busmod");
    }
    return i;
  }

  protected long getMandatoryLongConfig(String fieldName) {
    Long l = (Long)config.getNumber(fieldName);
    if (l == null) {
      throw new IllegalArgumentException(fieldName + " must be specified in config for busmod");
    }
    return l;
  }

}
