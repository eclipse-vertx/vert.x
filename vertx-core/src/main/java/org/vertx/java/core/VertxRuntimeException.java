package org.vertx.java.core;

public class VertxRuntimeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public VertxRuntimeException(String message, Throwable cause,
      boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  public VertxRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  public VertxRuntimeException(String message) {
    super(message);
  }

  public VertxRuntimeException(Throwable cause) {
    super(cause);
  }

}
