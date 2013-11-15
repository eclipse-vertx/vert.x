/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.busmods;

import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

/**
 * Base helper class for Java modules which use the event bus.<p>
 * You don't have to use this class but it contains some useful functionality.<p>
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
    config = container.config();
    logger = container.logger();
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
    String val = message.body().getString(field);
    if (val == null) {
      sendError(message, field + " must be specified");
    }
    return val;
  }

  protected JsonObject getMandatoryObject(String field, Message<JsonObject> message) {
    JsonObject val = message.body().getObject(field);
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
    String s = config.getString(fieldName);
    return s == null ? defaultValue : s;
  }

  protected int getOptionalIntConfig(String fieldName, int defaultValue) {
    Number i = config.getNumber(fieldName);
    return i == null ? defaultValue : i.intValue();
  }

  protected long getOptionalLongConfig(String fieldName, long defaultValue) {
    Number l = config.getNumber(fieldName);
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
    Number i = config.getNumber(fieldName);
    if (i == null) {
      throw new IllegalArgumentException(fieldName + " must be specified in config for busmod");
    }
    return i.intValue();
  }

  protected long getMandatoryLongConfig(String fieldName) {
    Number l = config.getNumber(fieldName);
    if (l == null) {
      throw new IllegalArgumentException(fieldName + " must be specified in config for busmod");
    }
    return l.longValue();
  }

}
