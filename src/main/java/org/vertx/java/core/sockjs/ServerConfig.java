package org.vertx.java.core.sockjs;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ServerConfig {

  private boolean insertJSESSIONID = DEFAULT_INSERT_JSESSIONID;
  private long sessionTimeout = DEFAULT_SESSION_TIMEOUT;
  private long heartbeatPeriod = DEFAULT_HEARTBEAT_PERIOD;
  private int maxBytesStreaming = DEFAULT_MAX_BYTES_STREAMING;
  private String libraryURL = DEFAULT_LIBRARY_URL;

  public static final boolean DEFAULT_INSERT_JSESSIONID = true;
  public static final long DEFAULT_SESSION_TIMEOUT = 5 * 1000;
  public static final long DEFAULT_HEARTBEAT_PERIOD = 25000;
  public static final int DEFAULT_MAX_BYTES_STREAMING = 128 * 1024;
  public static final String DEFAULT_LIBRARY_URL = "http://cdn.sockjs.org/sockjs-0.1.min.js";

  public boolean isInsertJSESSIONID() {
    return insertJSESSIONID;
  }

  public ServerConfig setInsertJSESSIONID(boolean insertJSESSIONID) {
    this.insertJSESSIONID = insertJSESSIONID;
    return this;
  }

  public long getSessionTimeout() {
    return sessionTimeout;
  }

  public ServerConfig setSessionTimeout(long sessionTimeout) {
    this.sessionTimeout = sessionTimeout;
    return this;
  }

  public long getHeartbeatPeriod() {
    return heartbeatPeriod;
  }

  public ServerConfig setHeartbeatPeriod(long heartbeatPeriod) {
    this.heartbeatPeriod = heartbeatPeriod;
    return this;
  }

  public int getMaxBytesStreaming() {
    return maxBytesStreaming;
  }

  public ServerConfig setMaxBytesStreaming(int maxBytesStreaming) {
    this.maxBytesStreaming = maxBytesStreaming;
    return this;
  }

  public String getLibraryURL() {
    return libraryURL;
  }

  public ServerConfig setLibraryURL(String libraryURL) {
    this.libraryURL = libraryURL;
    return this;
  }
}
