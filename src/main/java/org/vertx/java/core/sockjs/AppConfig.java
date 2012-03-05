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

package org.vertx.java.core.sockjs;

import org.vertx.java.core.sockjs.impl.Transport;

import java.util.Collections;
import java.util.Set;

/**
 *
 * Configuration for a SockJS application. See the SockJS website for more information.
 * <p>
 * Params are:
 * <p>
 * insertJSESSIONID - if true a JSESSIONID cookie will be inserted into responses if not already present. Default is true.
 * <p>
 * sessionTimeout - timeout in ms after a session has no receiving connection. Default is 5000 ms
 * <p>
 * heartbeatPeriod - heartbeat period in ms. Default is 25000 ms
 * <p>
 * maxBytesStreaming - maximum number of bytes that can be written to a streaming connection before it is closed. Default is 128KB
 * <p>
 * libraryURL - URL from where to download the sock-js client js library. Default is "http://cdn.sockjs.org/sockjs-0.1.min.js"
 * <p>
 * disabledTransports - set of transports not enabled for this application. Default is empty set.
 * <p>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AppConfig {

  private String prefix = DEFAULT_PREFIX;
  private boolean insertJSESSIONID = DEFAULT_INSERT_JSESSIONID;
  private long sessionTimeout = DEFAULT_SESSION_TIMEOUT;
  private long heartbeatPeriod = DEFAULT_HEARTBEAT_PERIOD;
  private int maxBytesStreaming = DEFAULT_MAX_BYTES_STREAMING;
  private String libraryURL = DEFAULT_LIBRARY_URL;
  private Set<Transport> disabledTransports = Collections.<Transport>emptySet();

  public static final String DEFAULT_PREFIX = "/";
  public static final boolean DEFAULT_INSERT_JSESSIONID = true;
  public static final long DEFAULT_SESSION_TIMEOUT = 5 * 1000;
  public static final long DEFAULT_HEARTBEAT_PERIOD = 25000;
  public static final int DEFAULT_MAX_BYTES_STREAMING = 128 * 1024;
  public static final String DEFAULT_LIBRARY_URL = "http://cdn.sockjs.org/sockjs-0.1.min.js";

  public boolean isInsertJSESSIONID() {
    return insertJSESSIONID;
  }

  public AppConfig setInsertJSESSIONID(boolean insertJSESSIONID) {
    this.insertJSESSIONID = insertJSESSIONID;
    return this;
  }

  public long getSessionTimeout() {
    return sessionTimeout;
  }

  public AppConfig setSessionTimeout(long sessionTimeout) {
    this.sessionTimeout = sessionTimeout;
    return this;
  }

  public long getHeartbeatPeriod() {
    return heartbeatPeriod;
  }

  public AppConfig setHeartbeatPeriod(long heartbeatPeriod) {
    this.heartbeatPeriod = heartbeatPeriod;
    return this;
  }

  public int getMaxBytesStreaming() {
    return maxBytesStreaming;
  }

  public AppConfig setMaxBytesStreaming(int maxBytesStreaming) {
    this.maxBytesStreaming = maxBytesStreaming;
    return this;
  }

  public String getLibraryURL() {
    return libraryURL;
  }

  public AppConfig setLibraryURL(String libraryURL) {
    this.libraryURL = libraryURL;
    return this;
  }

  public Set<Transport> getDisabledTransports() {
    return disabledTransports;
  }

  public AppConfig setDisabledTransports(Set<Transport> disabledTransports) {
    this.disabledTransports = disabledTransports;
    return this;
  }

  public String getPrefix() {
    return prefix;
  }

  public AppConfig setPrefix(String prefix) {
    this.prefix = prefix;
    return this;
  }
}
